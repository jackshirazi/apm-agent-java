/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.dubbo.api.AnotherApi;
import co.elastic.apm.agent.dubbo.api.DubboTestApi;
import co.elastic.apm.agent.dubbo.api.exception.BizException;
import co.elastic.apm.agent.dubbo.api.impl.AnotherApiImpl;
import co.elastic.apm.agent.dubbo.api.impl.DubboTestApiImpl;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MethodConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


public class ApacheDubboInstrumentationTest extends AbstractDubboInstrumentationTest {

    @Override
    protected DubboTestApi buildDubboTestApi(int port1, int port2) {

        RegistryConfig registryConfig = createRegistryConfig();
        ApplicationConfig appConfig = createApplicationConfig();

        //build AnotherApi provider
        ProtocolConfig anotherApiProtocolConfig = createProtocolConfig(port2);
        createAndExportServiceConfig(registryConfig, AnotherApi.class, new AnotherApiImpl(), appConfig, anotherApiProtocolConfig);

        // build AnotherApi consumer
        ReferenceConfig<AnotherApi> anotherApiReferenceConfig = createReferenceConfig(AnotherApi.class, appConfig, port2);

        AnotherApi anotherApi = withRetry(anotherApiReferenceConfig::get);

        // build DubboTestApi provider
        ProtocolConfig protocolConfig = createProtocolConfig(port1);
        createAndExportServiceConfig(registryConfig, DubboTestApi.class, new DubboTestApiImpl(anotherApi), appConfig, protocolConfig);

        // build DubboTestApi consumer
        ReferenceConfig<DubboTestApi> dubboTestApi = createReferenceConfig(DubboTestApi.class, appConfig, port1);

        List<MethodConfig> methodConfigList = new LinkedList<>();
        dubboTestApi.setMethods(methodConfigList);

        MethodConfig asyncConfig = new MethodConfig();
        asyncConfig.setName("async");
        asyncConfig.setAsync(true);
        methodConfigList.add(asyncConfig);

        MethodConfig asyncNoReturnConfig = new MethodConfig();
        asyncNoReturnConfig.setName("asyncNoReturn");
        asyncNoReturnConfig.setAsync(true);
        asyncNoReturnConfig.setReturn(false);
        methodConfigList.add(asyncNoReturnConfig);

        return dubboTestApi.get();
    }

    private static RegistryConfig createRegistryConfig() {
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setAddress("N/A");
        return registryConfig;
    }

    private static ApplicationConfig createApplicationConfig() {
        ApplicationConfig appConfig = new ApplicationConfig();
        appConfig.setName("all-in-one-app");
        return appConfig;
    }

    private static ProtocolConfig createProtocolConfig(int port) {
        ProtocolConfig protocolConfig = new ProtocolConfig();
        protocolConfig.setName("dubbo");
        protocolConfig.setPort(port);
        protocolConfig.setThreads(10);
        return protocolConfig;
    }

    private static <T> void createAndExportServiceConfig(RegistryConfig registryConfig,
                                                         Class<T> interfaceClass,
                                                         T interfaceImpl,
                                                         ApplicationConfig applicationConfig,
                                                         ProtocolConfig protocolConfig) {

        ServiceConfig<T> serviceConfig = new ServiceConfig<T>();
        serviceConfig.setApplication(applicationConfig);
        serviceConfig.setProtocol(protocolConfig);
        serviceConfig.setInterface(interfaceClass);
        serviceConfig.setRef(interfaceImpl);
        serviceConfig.setRegistry(registryConfig);

        withRetry(() -> {
            serviceConfig.export();
            return null;
        });

    }

    private static <T> ReferenceConfig<T> createReferenceConfig(Class<T> interfaceClass, ApplicationConfig applicationConfig, int port) {
        ReferenceConfig<T> referenceConfig = new ReferenceConfig<>();
        referenceConfig.setApplication(applicationConfig);
        referenceConfig.setInterface(interfaceClass);
        referenceConfig.setUrl(String.format("dubbo://localhost:%d", port));
        referenceConfig.setTimeout(3000);
        return referenceConfig;
    }


    @Test
    public void testAsync() throws Exception {
        String arg = "hello";
        DubboTestApi dubboTestApi = getDubboTestApi();
        String ret = dubboTestApi.async(arg);
        assertThat(ret).isNull();
        Future<Object> future = RpcContext.getContext().getFuture();
        assertThat(future).isNotNull();
        ret = (String) future.get();
        assertThat(ret).isEqualTo(arg);

        TransactionImpl transaction = reporter.getFirstTransaction(1000);
        validateDubboTransaction(transaction, "async");

        assertThat(reporter.getFirstSpan(500))
            .hasName("DubboTestApi#async")
            .isAsync();
        assertThat(reporter.getSpans()).hasSize(1);
    }

    @Test
    public void testAsyncException() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        String arg = "error";
        try {
            dubboTestApi.async(arg);
            Future<Object> future = RpcContext.getContext().getFuture();
            assertThat(future).isNotNull();
            future.get();
            fail("not ok");
        } catch (Exception e) {
            // exception from Future will be wrapped as RpcException by dubbo implementation
            assertThat(e.getCause()).isInstanceOf(BizException.class);
            TransactionImpl transaction = reporter.getFirstTransaction(1000);
            assertThat(reporter.getFirstSpan(500)).isNotNull();
            assertThat(reporter.getSpans()).hasSize(1);
            assertThat(reporter.getSpanByName("DubboTestApi#async")).isAsync();

            List<ErrorCaptureImpl> errors = reporter.getErrors();
            assertThat(errors.size()).isEqualTo(2);
            for (ErrorCaptureImpl error : errors) {
                assertThat(error.getException()).isInstanceOf(BizException.class);
            }
        }
    }

    @Test
    public void testAsyncByFuture() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        String arg = "hello";
        CompletableFuture<String> future = dubboTestApi.asyncByFuture(arg);
        assertThat(future).isNotNull();
        assertThat(future.get()).isEqualTo(arg);

        TransactionImpl transaction = reporter.getFirstTransaction(1000);
        validateDubboTransaction(transaction, "asyncByFuture");

        assertThat(reporter.getFirstSpan(500)).isNotNull();
        reporter.awaitSpanCount(2);

        assertThat(reporter.getSpanByName("DubboTestApi#asyncByFuture"))
            .isAsync();
    }

    @Test
    public void testAsyncByFutureException() {
        DubboTestApi dubboTestApi = getDubboTestApi();
        String arg = "error";
        CompletableFuture<String> future = dubboTestApi.asyncByFuture(arg);
        try {
            future.get();
            fail("not ok");
        } catch (Exception e) {
            TransactionImpl transaction = reporter.getFirstTransaction(1000);
            validateDubboTransaction(transaction, "asyncByFuture");

            assertThat(reporter.getFirstSpan(500)).isNotNull();
            reporter.awaitSpanCount(2);

            assertThat(reporter.getSpanByName("DubboTestApi#asyncByFuture"))
                .isAsync();

            List<ErrorCaptureImpl> errors = reporter.getErrors();
            assertThat(errors).hasSize(2);
            for (ErrorCaptureImpl error : errors) {
                assertThat(error.getException()).isInstanceOf(BizException.class);
            }
        }
    }

    @Test
    public void testAsyncByAsyncContext() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        String arg = "hello";
        String ret = dubboTestApi.asyncByAsyncContext(arg);
        assertThat(ret).isEqualTo(arg);

        TransactionImpl transaction = reporter.getFirstTransaction(1000);
        validateDubboTransaction(transaction, "asyncByAsyncContext");

        assertThat(reporter.getFirstSpan(500)).isNotNull();
        List<SpanImpl> spans = reporter.getSpans();
        assertThat(spans.size()).isEqualTo(2);

        assertThat(reporter.getSpanByName("DubboTestApi#asyncByAsyncContext")).isNotNull();
    }

    @Test
    public void testAsyncByAsyncContextException() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        try {
            dubboTestApi.asyncByAsyncContext("error");
            fail("not ok");
        } catch (BizException e) {
            TransactionImpl transaction = reporter.getFirstTransaction(1000);
            validateDubboTransaction(transaction, "asyncByAsyncContext");

            assertThat(reporter.getFirstSpan(5000)).isNotNull();
            List<SpanImpl> spans = reporter.getSpans();

            assertThat(spans.size()).isEqualTo(2);
            assertThat(reporter.getSpanByName("DubboTestApi#asyncByAsyncContext")).isNotNull();

            List<ErrorCaptureImpl> errors = reporter.getErrors();
            assertThat(errors).hasSize(2);
            for (ErrorCaptureImpl error : errors) {
                assertThat(error.getException()).isInstanceOf(BizException.class);
            }
        }
    }
}
