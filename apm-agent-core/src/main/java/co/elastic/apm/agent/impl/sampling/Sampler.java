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
package co.elastic.apm.agent.impl.sampling;

import co.elastic.apm.agent.impl.transaction.IdImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;

/**
 * A sampler is responsible for determining whether a {@link TransactionImpl} should be sampled.
 * <p>
 * In contrast other tracing systems,
 * in Elastic APM,
 * non-sampled {@link TransactionImpl}s do get reported to the APM server.
 * However,
 * to keep the size at a minimum,
 * the reported {@link TransactionImpl} only contains the transaction name,
 * the duration and the id.
 * Also,
 * {@link SpanImpl}s of non sampled {@link TransactionImpl}s are not reported.
 * </p>
 */
public interface Sampler {

    /**
     * Determines whether the given transaction should be sampled.
     *
     * @param traceId The id of the transaction.
     * @return The sampling decision.
     */
    boolean isSampled(IdImpl traceId);

    /**
     * @return current sample rate
     */
    double getSampleRate();


    /**
     * @return sample rate as (constant) header for context propagation
     *
     * <p>
     * While the {@code tracestate} header is not related to sampler itself, putting this here allows to reuse the same
     * {@link String} instance as long as the sample rate does not change to minimize allocation
     * </p>
     */
    String getTraceStateHeader();
}
