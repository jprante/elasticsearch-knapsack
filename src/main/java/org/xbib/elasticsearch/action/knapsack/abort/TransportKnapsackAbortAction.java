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
package org.xbib.elasticsearch.action.knapsack.abort;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.xbib.elasticsearch.knapsack.KnapsackService;

public class TransportKnapsackAbortAction extends TransportAction<KnapsackAbortRequest, KnapsackAbortResponse> {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackAbortAction.class.getSimpleName());

    private final KnapsackService knapsack;

    @Inject
    public TransportKnapsackAbortAction(Settings settings, TransportService transportService,
                                        ThreadPool threadPool, ActionFilters actionFilters,
                                        IndexNameExpressionResolver indexNameExpressionResolver,
                                        KnapsackService knapsack) {
        super(settings, KnapsackAbortAction.NAME, threadPool, actionFilters, indexNameExpressionResolver, transportService.getTaskManager());
        this.knapsack = knapsack;
    }

    @Override
    protected void doExecute(final KnapsackAbortRequest request, ActionListener<KnapsackAbortResponse> listener) {
        final KnapsackAbortResponse response = new KnapsackAbortResponse();
        try {
            knapsack.abort(request.getReset());
            listener.onResponse(response);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            listener.onFailure(e);
        }
    }

}
