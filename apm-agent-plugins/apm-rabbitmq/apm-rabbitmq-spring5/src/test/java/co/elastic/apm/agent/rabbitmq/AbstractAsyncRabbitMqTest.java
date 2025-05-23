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
package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.junit.Test;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static co.elastic.apm.agent.rabbitmq.TestConstants.ROUTING_KEY;
import static co.elastic.apm.agent.rabbitmq.TestConstants.TOPIC_EXCHANGE_NAME;

public abstract class AbstractAsyncRabbitMqTest extends RabbitMqTestBase {

    private static final Logger logger = LoggerFactory.getLogger(AbstractAsyncRabbitMqTest.class);

    private static final String MESSAGE = "foo-bar";

    @Autowired
    @Qualifier("asyncRabbitTemplateWithDefaultListener")
    private AsyncRabbitTemplate asyncRabbitTemplate;

    @Test
    public void verifyThatTransactionWithSpanCreated() throws TimeoutException {
        logger.info("Trying to send to async rabbit template");
        Future<String> future = invokeConvertAndSend(TOPIC_EXCHANGE_NAME, ROUTING_KEY, MESSAGE);
        try {
            String response = future.get(5, TimeUnit.SECONDS);
            logger.info("Got response = {}", response);
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Got exception", e);
        }

        reporter.awaitTransactionCount(2);
    }

    /**
     * {@link AsyncRabbitTemplate#convertSendAndReceive(String, String, Object)} changed it's return type
     * in Spring 6, so we use reflection to invoke it safely for both spring 5 and 6.
     */
    @SuppressWarnings("unchecked")
    private Future<String> invokeConvertAndSend(String topic, String routingKey, Object message) {
        try {
            return (Future<String>) asyncRabbitTemplate.getClass()
                .getMethod("convertSendAndReceive", String.class, String.class, Object.class)
                .invoke(asyncRabbitTemplate, topic, routingKey, message);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
