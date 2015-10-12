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
package org.xbib.elasticsearch.rest.action.knapsack.abort;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.support.RestToXContentListener;
import org.xbib.elasticsearch.action.knapsack.abort.KnapsackAbortAction;
import org.xbib.elasticsearch.action.knapsack.abort.KnapsackAbortRequest;
import org.xbib.elasticsearch.action.knapsack.abort.KnapsackAbortResponse;

import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestRequest.Method.DELETE;

public class RestKnapsackAbortAction extends BaseRestHandler {

    private final static ESLogger logger = ESLoggerFactory.getLogger(RestKnapsackAbortAction.class.getSimpleName());

    @Inject
    public RestKnapsackAbortAction(Settings settings, Client client, RestController controller) {
        super(settings, controller, client);

        controller.registerHandler(POST, "/_export/abort", this);
        controller.registerHandler(POST, "/_import/abort", this);
        controller.registerHandler(DELETE, "/_export/abort", this);
        controller.registerHandler(DELETE, "/_import/abort", this);
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel, final Client client) {
        try {
            KnapsackAbortRequest abortRequest = new KnapsackAbortRequest();
            abortRequest.setReset(request.method().equals(DELETE));
            client.admin().indices().execute(KnapsackAbortAction.INSTANCE, abortRequest,
                    new RestToXContentListener<KnapsackAbortResponse>(channel));
        } catch (Throwable ex) {
            try {
                logger.error(ex.getMessage(), ex);
                channel.sendResponse(new BytesRestResponse(channel, ex));
            } catch (Exception ex2) {
                logger.error(ex2.getMessage(), ex2);
            }
        }
    }


}
