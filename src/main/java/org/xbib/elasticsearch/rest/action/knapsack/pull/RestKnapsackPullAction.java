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
package org.xbib.elasticsearch.rest.action.knapsack.pull;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.search.RestSearchAction;
import org.elasticsearch.rest.action.support.RestToXContentListener;
import org.xbib.elasticsearch.action.knapsack.pull.KnapsackPullAction;
import org.xbib.elasticsearch.action.knapsack.pull.KnapsackPullRequest;
import org.xbib.elasticsearch.action.knapsack.pull.KnapsackPullResponse;
import org.xbib.elasticsearch.knapsack.KnapsackHelper;
import org.xbib.elasticsearch.knapsack.KnapsackParameter;

import java.util.Map;

import static org.elasticsearch.rest.RestRequest.Method.POST;

/**
 * The REST knapsack pull action transfers a remote index into an Elasticsearch cluster
 */
public class RestKnapsackPullAction extends BaseRestHandler implements KnapsackParameter {

    private final static ESLogger logger = ESLoggerFactory.getLogger(RestKnapsackPullAction.class.getSimpleName());

    @Inject
    public RestKnapsackPullAction(Settings settings, Client client, RestController controller) {
        super(settings, controller, client);

        controller.registerHandler(POST, "/_pull", this);
        controller.registerHandler(POST, "/{index}/_pull", this);
        controller.registerHandler(POST, "/{index}/{type}/_pull", this);
    }

    @Override
    public void handleRequest(final RestRequest request, RestChannel channel, Client client) {
        try {
            final String index = request.param(INDEX_PARAM, "_all");
            final String type = request.param(TYPE_PARAM);
            KnapsackPullRequest pullRequest = new KnapsackPullRequest()
                    .setIndex(index)
                    .setType(type)
                    .setHost(request.param(HOST_PARAM, "localhost"))
                    .setPort(request.paramAsInt(PORT_PARAM, 9300))
                    .setCluster(request.param(CLUSTER_PARAM))
                    .setSniff(request.paramAsBoolean(SNIFF_PARAM, true))
                    .setMaxActionsPerBulkRequest(request.paramAsInt(MAX_BULK_ACTIONS_PER_REQUEST_PARAM, 1000))
                    .setMaxBulkConcurrency(request.paramAsInt(MAX_BULK_CONCURRENCY_PARAM,
                            Runtime.getRuntime().availableProcessors() * 2))
                    .setTimeout(request.paramAsTime(TIMEOUT_PARAM, TimeValue.timeValueSeconds(30)))
                    .withMetadata(request.paramAsBoolean(WITH_METADATA_PARAM, true))
                    .setIndexTypeNames(KnapsackHelper.toMap(request.param(MAP_PARAM), logger))
                    .setSearchRequest(toSearchRequest(request));
            // add user-defined settings and mappings
            for (Map.Entry<String, String> e : request.params().entrySet()) {
                if (e.getKey().endsWith("_settings")) {
                    pullRequest.addIndexSettings(e.getKey(), e.getValue());
                } else if (e.getKey().endsWith("_mapping")) {
                    pullRequest.addIndexTypeMapping(e.getKey(), e.getValue());
                }
            }
            client.admin().indices().execute(KnapsackPullAction.INSTANCE, pullRequest,
                    new RestToXContentListener<KnapsackPullResponse>(channel));
        } catch (Throwable ex) {
            try {
                logger.error(ex.getMessage(), ex);
                channel.sendResponse(new BytesRestResponse(channel, ex));
            } catch (Exception ex2) {
                logger.error(ex2.getMessage(), ex2);
            }
        }
    }

    private SearchRequest toSearchRequest(RestRequest request) {
        // override search action "size" (default = 10) by bulk request size. The size is per shard!
        request.params().put("size", request.param(MAX_BULK_ACTIONS_PER_REQUEST_PARAM, "1000"));
        SearchRequest searchRequest = new SearchRequest();
        RestSearchAction.parseSearchRequest(searchRequest, request, parseFieldMatcher, null);
        return searchRequest;
    }
}
