/*
 * Copyright (C) 2014 Jörg Prante
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xbib.elasticsearch.action.knapsack.state;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.xbib.elasticsearch.knapsack.KnapsackService;
import org.xbib.elasticsearch.knapsack.KnapsackState;

public class TransportKnapsackStateAction extends TransportAction<KnapsackStateRequest, KnapsackStateResponse> {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackStateAction.class.getSimpleName());

    private final Client client;

    private final KnapsackService knapsack;

    @Inject
    public TransportKnapsackStateAction(Settings settings, ThreadPool threadPool, Client client, KnapsackService knapsack) {
        super(settings, threadPool);
        this.client = client;
        this.knapsack = knapsack;
    }

    @Override
    protected void doExecute(final KnapsackStateRequest request, ActionListener<KnapsackStateResponse> listener) {
        final KnapsackStateResponse response = new KnapsackStateResponse();
        try {
            for (KnapsackState state : knapsack.getExports(client)) {
                response.addState(state);
            }
            for (KnapsackState state : knapsack.getImports(client)) {
                response.addState(state);
            }
            listener.onResponse(response);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            listener.onFailure(e);
        }
    }

}
