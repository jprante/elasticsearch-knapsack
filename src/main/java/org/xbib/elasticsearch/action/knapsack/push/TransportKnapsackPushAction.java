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
package org.xbib.elasticsearch.action.knapsack.push;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesAction;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexAction;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollAction;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.service.NodeService;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.joda.time.DateTime;
import org.xbib.elasticsearch.helper.client.BulkTransportClient;
import org.xbib.elasticsearch.helper.client.ClientBuilder;
import org.xbib.elasticsearch.knapsack.KnapsackService;
import org.xbib.elasticsearch.knapsack.KnapsackState;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.client.Requests.createIndexRequest;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.clientSettings;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.getAliases;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.getMapping;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.getSettings;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.mapIndex;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.mapType;

public class TransportKnapsackPushAction extends TransportAction<KnapsackPushRequest, KnapsackPushResponse> {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackPushAction.class.getSimpleName());

    private final Client client;

    private final NodeService nodeService;

    private final KnapsackService knapsack;

    @Inject
    public TransportKnapsackPushAction(Settings settings, ThreadPool threadPool,
                                       Client client, NodeService nodeService, ActionFilters actionFilters,
                                       IndexNameExpressionResolver indexNameExpressionResolver,
                                       TransportService transportService,
                                       KnapsackService knapsack) {
        super(settings, KnapsackPushAction.NAME, threadPool, actionFilters, indexNameExpressionResolver, transportService.getTaskManager());
        this.client = client;
        this.nodeService = nodeService;
        this.knapsack = knapsack;
    }

    @Override
    protected void doExecute(final KnapsackPushRequest request, ActionListener<KnapsackPushResponse> listener) {
        final KnapsackState state = new KnapsackState()
                .setMode("push")
                .setNodeName(nodeService.nodeName());
        final KnapsackPushResponse response = new KnapsackPushResponse()
                .setState(state);
        try {
            final BulkTransportClient bulkClient = ClientBuilder.builder()
                    .put(ClientBuilder.MAX_ACTIONS_PER_REQUEST, request.getMaxActionsPerBulkRequest())
                    .put(ClientBuilder.MAX_CONCURRENT_REQUESTS, request.getMaxBulkConcurrency())
                    .put(ClientBuilder.FLUSH_INTERVAL, TimeValue.timeValueSeconds(5))
                    .put(clientSettings(client, request))
                    .toBulkTransportClient();
            state.setTimestamp(new DateTime());
            response.setRunning(true);
            knapsack.submit(new Thread() {
                public void run() {
                    performPush(request, state, bulkClient);
                }
            });
            // ensure to add export to state before response is sent
            knapsack.addExport(state);
            listener.onResponse(response);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            listener.onFailure(e);
        }
    }

    /**
     * Push action thread
     *
     * @param request    the request
     * @param state      the state
     * @param bulkClient the bulk client
     */
    final void performPush(final KnapsackPushRequest request,
                           final KnapsackState state,
                           final BulkTransportClient bulkClient) {
        try {
            logger.info("start of push: {}", state);
            long count = 0L;
            Map<String, Set<String>> indices = new HashMap<>();
            for (String s : Strings.commaDelimitedListToSet(request.getIndex())) {
                indices.put(s, Strings.commaDelimitedListToSet(request.getType()));
            }
            if (request.withMetadata()) {
                logger.info("map={}", request.getIndexTypeNames());
                if (request.getIndexTypeNames() != null) {
                    for (Object spec : request.getIndexTypeNames().keySet()) {
                        if (spec == null) {
                            continue;
                        }
                        String[] s = spec.toString().split("/");
                        String index = s[0];
                        String type = s.length > 1 ? s[1] : null;
                        if (!"_all".equals(index)) {
                            Set<String> types = indices.get(index);
                            if (types == null) {
                                types = new HashSet<>();
                            }
                            if (type != null) {
                                types.add(type);
                            }
                            indices.put(index, types);
                        }
                    }
                }
                // get settings for all indices
                logger.info("getting settings for indices {}", indices.keySet());
                Set<String> settingsIndices = new HashSet<>(indices.keySet());
                settingsIndices.remove("_all");
                Map<String, String> settings = getSettings(client,
                        settingsIndices.toArray(new String[settingsIndices.size()]));
                logger.info("found indices: {}", settings.keySet());
                // we resolved the specs in indices to the real indices in the settings
                // get mapping and alias per index and create index if copy mode is enabled
                for (String index : settings.keySet()) {
                    CreateIndexRequest createIndexRequest = createIndexRequest(mapIndex(request, index));
                    Set<String> types = indices.get(index);
                    createIndexRequest.settings(settings.get(index));
                    logger.info("getting mappings for index {} and types {}", index, types);
                    Map<String, String> mappings = getMapping(client, index,
                            types != null ? new HashSet<>(types) : null);
                    logger.info("found mappings: {}", mappings.keySet());
                    for (String type : mappings.keySet()) {
                        logger.info("adding mapping: {}", mapType(request, index, type));
                        createIndexRequest.mapping(mapType(request, index, type), mappings.get(type));
                    }
                    // create index
                    logger.info("creating index: {}", mapIndex(request, index));
                    try {
                        bulkClient.client().execute(CreateIndexAction.INSTANCE, createIndexRequest).actionGet();
                        logger.info("index created: {}", mapIndex(request, index));
                    } catch (Exception e) {
                        // maybe an index already exists exception, check if index is empty, throw exception only if not empty
                        SearchResponse searchResponse = bulkClient.client()
                                .execute(SearchAction.INSTANCE, new SearchRequest(mapIndex(request, index)))
                                .actionGet();
                        logger.info("count={} status={}", searchResponse.getHits().getTotalHits());
                        if (searchResponse.getHits().getTotalHits() > 0L) {
                            throw e;
                        }
                    }
                    logger.info("getting aliases for index {}", index);
                    Map<String, String> aliases = getAliases(client, index);
                    logger.info("found {} aliases", aliases.size());
                    if (!aliases.isEmpty()) {
                        IndicesAliasesRequestBuilder requestBuilder = new IndicesAliasesRequestBuilder(client, IndicesAliasesAction.INSTANCE);
                        for (String alias : aliases.keySet()) {
                            if (aliases.get(alias).isEmpty()) {
                                requestBuilder.addAlias(index, alias);
                            } else {
                                requestBuilder.addAlias(index, alias, aliases.get(alias)); // with filter
                            }
                        }
                        requestBuilder.execute().actionGet();
                        logger.info("aliases created", aliases.size());
                    }
                }
            }
            SearchRequest searchRequest = request.getSearchRequest();
            if (searchRequest == null) {
                searchRequest = new SearchRequestBuilder(client, SearchAction.INSTANCE)
                        .setQuery(QueryBuilders.matchAllQuery()).request();
            }
            long total = 0L;
            for (String index : indices.keySet()) {
                if (!"_all".equals(index)) {
                    searchRequest.indices(index);
                }
                Set<String> types = indices.get(index);
                if (types != null) {
                    searchRequest.types(types.toArray(new String[types.size()]));
                }
                searchRequest.scroll(request.getTimeout());
                // use local node client here
                SearchResponse searchResponse = client.execute(SearchAction.INSTANCE, searchRequest).actionGet();
                do {
                    total += searchResponse.getHits().getHits().length;
                    logger.debug("total={} hits={} took={}", total,
                            searchResponse.getHits().getHits().length,
                            searchResponse.getTookInMillis());
                    for (SearchHit hit : searchResponse.getHits()) {
                        IndexRequest indexRequest = new IndexRequest(mapIndex(request, hit.getIndex()),
                                mapType(request, hit.getIndex(), hit.getType()), hit.getId());
                        for (String f : hit.getFields().keySet()) {
                            switch (f) {
                                case "_parent":
                                    indexRequest.parent(hit.getFields().get(f).getValue().toString());
                                    break;
                                case "_routing":
                                    indexRequest.routing(hit.getFields().get(f).getValue().toString());
                                    break;
                                case "_timestamp":
                                    indexRequest.timestamp(hit.getFields().get(f).getValue().toString());
                                    break;
                                case "_version":
                                    indexRequest.versionType(VersionType.EXTERNAL)
                                            .version(Long.parseLong(hit.getFields().get(f).getValue().toString()));
                                    break;
                                case "_source":
                                    indexRequest.source(hit.getSourceAsString());
                                    break;
                                default:
                                    indexRequest.source(f, hit.getFields().get(f).getValue().toString());
                                    break;
                            }
                            count++;
                        }
                        if (!hit.getFields().keySet().contains("_source")) {
                            indexRequest.source(hit.getSourceAsString());
                            count++;
                        }
                        bulkClient.bulkIndex(indexRequest);
                    }
                    searchResponse = client.execute(SearchScrollAction.INSTANCE,
                            new SearchScrollRequest(searchResponse.getScrollId()).scroll(request.getTimeout())).actionGet();
                } while (searchResponse.getHits().getHits().length > 0 && !Thread.interrupted());
            }
            bulkClient.flushIngest();
            bulkClient.waitForResponses(TimeValue.timeValueSeconds(60));
            for (String index : indices.keySet()) {
                bulkClient.refreshIndex(index);
            }
            bulkClient.shutdown();
            logger.info("end of push: {}, count = {}", state, count);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                knapsack.removeExport(state);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

}
