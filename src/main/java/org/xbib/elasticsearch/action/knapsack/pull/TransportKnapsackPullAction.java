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
package org.xbib.elasticsearch.action.knapsack.pull;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.ImmutableSet;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.joda.time.DateTime;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.service.NodeService;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.threadpool.ThreadPool;
import org.xbib.elasticsearch.knapsack.KnapsackService;
import org.xbib.elasticsearch.knapsack.KnapsackState;
import org.xbib.elasticsearch.support.client.Ingest;
import org.xbib.elasticsearch.support.client.bulk.BulkTransportClient;
import org.xbib.elasticsearch.support.client.node.BulkNodeClient;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.client.Requests.createIndexRequest;
import static org.elasticsearch.common.collect.Maps.newHashMap;
import static org.elasticsearch.common.collect.Sets.newHashSet;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.clientSettings;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.getAliases;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.getMapping;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.getSettings;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.mapIndex;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.mapType;

public class TransportKnapsackPullAction extends TransportAction<KnapsackPullRequest, KnapsackPullResponse> {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackPullAction.class.getSimpleName());

    private final Environment environment;

    private final SettingsFilter settingsFilter;

    private final Client client;

    private final NodeService nodeService;

    private final KnapsackService knapsack;

    @Inject
    public TransportKnapsackPullAction(Settings settings, Environment environment, SettingsFilter settingsFilter,
                                       ThreadPool threadPool,
                                       Client client, NodeService nodeService, ActionFilters actionFilters,
                                       KnapsackService knapsack) {
        super(settings, KnapsackPullAction.NAME, threadPool, actionFilters);
        this.environment = environment;
        this.settingsFilter = settingsFilter;
        this.client = client;
        this.nodeService = nodeService;
        this.knapsack = knapsack;
    }

    @Override
    protected void doExecute(final KnapsackPullRequest request, ActionListener<KnapsackPullResponse> listener) {
        final KnapsackState state = new KnapsackState()
                .setMode("pull")
                .setNodeName(nodeService.nodeName());
        final KnapsackPullResponse response = new KnapsackPullResponse()
                .setState(state);
        try {
            final BulkTransportClient transportClient = new BulkTransportClient();
            transportClient.flushIngestInterval(TimeValue.timeValueSeconds(5))
                    .maxActionsPerBulkRequest(request.getMaxActionsPerBulkRequest())
                    .maxConcurrentBulkRequests(request.getMaxBulkConcurrency())
                    .newClient(clientSettings(client, environment, request));
            if (transportClient.getConnectedNodes().isEmpty()) {
                response.setRunning(false);
                transportClient.shutdown();
            } else {
                final BulkNodeClient nodeClient = new BulkNodeClient();
                nodeClient.flushIngestInterval(TimeValue.timeValueSeconds(5))
                        .maxActionsPerBulkRequest(request.getMaxActionsPerBulkRequest())
                        .maxConcurrentBulkRequests(request.getMaxBulkConcurrency())
                        .newClient(client);
                state.setTimestamp(new DateTime());
                response.setRunning(true);
                knapsack.submit(new Thread() {
                    public void run() {
                        performPull(request, state, transportClient, nodeClient);
                    }
                });
            }
            listener.onResponse(response);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            listener.onFailure(e);
        }
    }

    /**
     * Pull action thread
     *
     * @param request         request
     * @param state           state
     * @param transportClient bulk client for remote cluster access
     * @param nodeClient      bulk client for local cluster access
     */
    final void performPull(final KnapsackPullRequest request,
                           final KnapsackState state,
                           final Ingest transportClient,
                           final Ingest nodeClient) {
        try {
            logger.info("start of pull: {}", state);
            long count = 0L;
            Map<String, Set<String>> indices = newHashMap();
            for (String s : Strings.commaDelimitedListToSet(request.getIndex())) {
                indices.put(s, Strings.commaDelimitedListToSet(request.getType()));
            }
            if (request.withMetadata()) {
                // renaming indices/types
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
                                types = newHashSet();
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
                Set<String> settingsIndices = newHashSet(indices.keySet());
                settingsIndices.remove("_all");
                Map<String, String> settings = getSettings(transportClient.client(), settingsFilter,
                        settingsIndices.toArray(new String[settingsIndices.size()]));
                logger.info("found indices: {}", settings.keySet());
                // we resolved the specs in indices to the real indices in the settings
                // get mapping and alias per index and create index if copy mode is enabled
                for (String index : settings.keySet()) {
                    CreateIndexRequest createIndexRequest = createIndexRequest(mapIndex(request, index));
                    Set<String> types = indices.get(index);
                    createIndexRequest.settings(settings.get(index));
                    logger.info("getting mappings for index {} and types {}", index, types);
                    Map<String, String> mappings = getMapping(transportClient.client(),
                            index, types != null ? ImmutableSet.copyOf(types) : null);
                    logger.info("found mappings: {}", mappings.keySet());
                    for (String type : mappings.keySet()) {
                        logger.info("adding mapping: {}", mapType(request, index, type));
                        createIndexRequest.mapping(mapType(request, index, type), mappings.get(type));
                    }
                    // create index
                    logger.info("creating index: {}", mapIndex(request, index));
                    nodeClient.client().admin().indices().create(createIndexRequest).actionGet();
                    logger.info("index created: {}", mapIndex(request, index));
                    logger.info("getting aliases for index {}", index);
                    Map<String, String> aliases = getAliases(client, index);
                    logger.info("found {} aliases", aliases.size());
                    if (!aliases.isEmpty()) {
                        IndicesAliasesRequestBuilder requestBuilder = nodeClient.client().admin().indices().prepareAliases();
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
                searchRequest = new SearchRequestBuilder(transportClient.client())
                        .setQuery(QueryBuilders.matchAllQuery()).request();
            }
            for (String index : indices.keySet()) {
                searchRequest.searchType(SearchType.SCAN).scroll(request.getTimeout());
                if (!"_all".equals(index)) {
                    searchRequest.indices(index);
                }
                Set<String> types = indices.get(index);
                if (types != null) {
                    searchRequest.types(types.toArray(new String[types.size()]));
                }
                SearchResponse searchResponse = transportClient.client().search(searchRequest).actionGet();
                long total = 0L;
                while (searchResponse.getScrollId() != null && !Thread.interrupted()) {
                    searchResponse = transportClient.client().prepareSearchScroll(searchResponse.getScrollId())
                            .setScroll(request.getTimeout())
                            .execute().actionGet();
                    long hits = searchResponse.getHits().getHits().length;
                    if (hits == 0) {
                        break;
                    }
                    total += hits;
                    logger.debug("total={} hits={} took={}", total, hits, searchResponse.getTookInMillis());
                    for (SearchHit hit : searchResponse.getHits()) {
                        indexSearchHit(nodeClient, request, hit);
                        count++;
                    }
                }
            }
            nodeClient.flushIngest();
            nodeClient.waitForResponses(TimeValue.timeValueSeconds(60));
            for (String index : indices.keySet()) {
                nodeClient.refresh(index);
            }
            nodeClient.shutdown();
            transportClient.shutdown();
            logger.info("end of pull: {}, count = {}", state, count);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                knapsack.removeImport(client, state);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private void indexSearchHit(Ingest nodeBulkClient, KnapsackPullRequest request, SearchHit hit)
            throws IOException {
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
        }
        if (!hit.getFields().keySet().contains("_source")) {
            indexRequest.source(hit.getSourceAsString());
        }
        nodeBulkClient.bulkIndex(indexRequest);
    }


}
