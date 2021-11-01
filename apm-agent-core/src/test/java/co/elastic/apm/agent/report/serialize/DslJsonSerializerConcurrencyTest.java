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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.context.Message;
import co.elastic.apm.agent.impl.metadata.MetaData;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.report.ApmServerClient;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/*
Tests for https://github.com/elastic/apm-agent-java/issues/2217

The problem being tested: serializing an object to JSON
while the object being serialized has concurrent operations
possible on it

The particular error seen was while
DslJsonSerializer.serializeMessageHeaders()
was being executed, the serialized JSON lost some fields
and additionally produced invalid JSON with a comma before
the object termination '}' which is invalid



 */
class DslJsonSerializerConcurrencyTest {

    @Test
    void testCopyFromConcurrently() throws Exception {
        //setup a serializer
        StacktraceConfiguration stacktraceConfiguration = mock(StacktraceConfiguration.class);
        ApmServerClient apmServerClient = mock(ApmServerClient.class);
        Future<MetaData> metaData = MetaData.create(SpyConfiguration.createSpyConfig(), null);
        DslJsonSerializer serializer = new DslJsonSerializer(stacktraceConfiguration, apmServerClient, metaData);
        serializer.blockUntilReady();

        //setup a span with a message
        Message messageForSpan = createMessage(100L, "test-conc-body", "test-concq", "test-*", Map.of("test-header", "test-value"));
        Span span = new Span(MockTracer.create());
        span.getContext().getMessage().copyFrom(messageForSpan);
        Message firstMessage = span.getContext().getMessage();

        //a second message to copy data in when reusing the first
        Message secondMessage = createMessage(999L, "updated-conc-body", "updated-test-concq", "updated-test-*", Map.of("updated-test-header", "updated-test-value"));

        AtomicInteger totalWrites = new AtomicInteger();
        AtomicInteger badWrites = new AtomicInteger();
        AtomicInteger illegalStateWrites = new AtomicInteger();
        AtomicInteger indexBadWrites = new AtomicInteger();
        AtomicInteger npeWrites = new AtomicInteger();

        //concurrent serialization operation
        Runnable first = ()-> {
            totalWrites.incrementAndGet();
            try {
                String json = serializer.toJsonString(span);
                if (json.contains(",},")) {
                    badWrites.incrementAndGet();
                    //if you want to see some examples
                    //if(badWrites.get()<10) System.out.println(json);
                }
            } catch (IllegalStateException e) {
                illegalStateWrites.incrementAndGet();
            } catch (IndexOutOfBoundsException e) {
                indexBadWrites.incrementAndGet();
                //if you want to see some examples
                //if (indexBadWrites.incrementAndGet()<5) e.printStackTrace();
            } catch (NullPointerException e) {
                npeWrites.incrementAndGet();
            }
        };
        //concurrent reuse operation
        Runnable second = () -> firstMessage.copyFrom(secondMessage);

        AtomicBoolean firstpoolTerminated = new AtomicBoolean(false);
        //4 threads should be enough
        long start = System.currentTimeMillis();
        Executor firstpool = Executors.newFixedThreadPool(1);
        Executor secondpool = Executors.newFixedThreadPool(1);
        for (int i = 0; i < 50_000_000; i++) {
            firstpool.execute(first);
            secondpool.execute(second);
            if (System.currentTimeMillis()-start>2000L){
                start = System.currentTimeMillis();
                //System.out.println("STATS bad writes: "+badWrites+", index errors: "+indexBadWrites + ", bad state: "+illegalStateWrites + ", npes: " + npeWrites + ", total: "+totalWrites);
                //failfast
                assertThat(badWrites.get()).isEqualTo(0);
                assertThat(indexBadWrites.get()).isEqualTo(0);
                assertThat(illegalStateWrites.get()).isEqualTo(0);
                assertThat(npeWrites.get()).isEqualTo(0);
            }
        }
        firstpool.execute(() -> firstpoolTerminated.set(true));
        while (firstpoolTerminated.get()) {
            if (System.currentTimeMillis()-start>2000L){
                start = System.currentTimeMillis();
                //System.out.println("STATS bad writes: "+badWrites+", index errors: "+indexBadWrites + ", bad state: "+illegalStateWrites + ", npes: " + npeWrites + ", total: "+totalWrites);
                //failfast
                assertThat(badWrites.get()).isEqualTo(0);
                assertThat(indexBadWrites.get()).isEqualTo(0);
                assertThat(illegalStateWrites.get()).isEqualTo(0);
                assertThat(npeWrites.get()).isEqualTo(0);
            }
        }

        assertThat(badWrites.get()).isEqualTo(0);
        assertThat(indexBadWrites.get()).isEqualTo(0);
        assertThat(illegalStateWrites.get()).isEqualTo(0);
        assertThat(npeWrites.get()).isEqualTo(0);

    }

    private Message createMessage(long age, String body, String queue, String routingKey, Map<String, String> headers) {
        Message message = new Message()
            .withAge(age)
            .withBody(body)
            .withQueue(queue)
            .withRoutingKey(routingKey);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            message.addHeader(entry.getKey(), entry.getValue());
        }
        return message;
    }
}
