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

import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.support.RestActions;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.xbib.elasticsearch.action.knapsack.pull.KnapsackPullAction;
import org.xbib.elasticsearch.action.knapsack.pull.KnapsackPullRequest;
import org.xbib.elasticsearch.action.knapsack.pull.KnapsackPullResponse;
import org.xbib.elasticsearch.knapsack.KnapsackHelper;
import org.xbib.elasticsearch.knapsack.KnapsackParameter;
import org.xbib.elasticsearch.rest.action.support.BytesRestResponse;
import org.xbib.elasticsearch.rest.action.support.RestToXContentListener;

import java.util.Map;

import static org.elasticsearch.common.unit.TimeValue.parseTimeValue;
import static org.elasticsearch.rest.RestRequest.Method.POST;

/**
 * The REST knapsack pull action transfers a remote index into an Elasticsearch cluster
 */
public class RestKnapsackPullAction extends BaseRestHandler implements KnapsackParameter {

    private final static ESLogger logger = ESLoggerFactory.getLogger(RestKnapsackPullAction.class.getSimpleName());

    @Inject
    public RestKnapsackPullAction(Settings settings, Client client, RestController controller) {
        super(settings, client);

        controller.registerHandler(POST, "/_pull", this);
        controller.registerHandler(POST, "/{index}/_pull", this);
        controller.registerHandler(POST, "/{index}/{type}/_pull", this);
    }

    @Override
    public void handleRequest(final RestRequest request, RestChannel channel) {
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
            for (Map.Entry<String,String> e : request.params().entrySet()) {
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
        SearchRequest searchRequest;
        // override search action "size" (default = 10) by bulk request size. The size is per shard!
        request.params().put("size", request.param(MAX_BULK_ACTIONS_PER_REQUEST_PARAM, "1000"));
        searchRequest = parseSearchRequest(request);
        searchRequest.listenerThreaded(false);
        return searchRequest;
    }

    /**
     * copy from RestSearchAction
     */

    private SearchRequest parseSearchRequest(RestRequest request) {
        String[] indices = RestActions.splitIndices(request.param("index"));
        SearchRequest searchRequest = new SearchRequest(indices);
        // get the content, and put it in the body
        if (request.hasContent()) {
            searchRequest.source(request.content(), request.contentUnsafe());
        } else {
            String source = request.param("source");
            if (source != null) {
                searchRequest.source(source);
            }
        }
        // add extra source based on the request parameters
        searchRequest.extraSource(parseSearchSource(request));

        searchRequest.searchType(request.param("search_type"));

        String scroll = request.param("scroll");
        if (scroll != null) {
            searchRequest.scroll(new Scroll(parseTimeValue(scroll, null)));
        }

        searchRequest.types(RestActions.splitTypes(request.param("type")));
        searchRequest.queryHint(request.param("query_hint"));
        searchRequest.routing(request.param("routing"));
        searchRequest.preference(request.param("preference"));

        return searchRequest;
    }

    private SearchSourceBuilder parseSearchSource(RestRequest request) {
        SearchSourceBuilder searchSourceBuilder = null;
        String queryString = request.param("q");
        if (queryString != null) {
            QueryStringQueryBuilder queryBuilder = QueryBuilders.queryString(queryString);
            queryBuilder.defaultField(request.param("df"));
            queryBuilder.analyzer(request.param("analyzer"));
            queryBuilder.analyzeWildcard(request.paramAsBoolean("analyze_wildcard", false));
            queryBuilder.lowercaseExpandedTerms(request.paramAsBoolean("lowercase_expanded_terms", true));
            queryBuilder.lenient(request.paramAsBooleanOptional("lenient", null));
            String defaultOperator = request.param("default_operator");
            if (defaultOperator != null) {
                if ("OR".equals(defaultOperator)) {
                    queryBuilder.defaultOperator(QueryStringQueryBuilder.Operator.OR);
                } else if ("AND".equals(defaultOperator)) {
                    queryBuilder.defaultOperator(QueryStringQueryBuilder.Operator.AND);
                } else {
                    throw new ElasticSearchIllegalArgumentException("Unsupported defaultOperator [" + defaultOperator + "], can either be [OR] or [AND]");
                }
            }
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.query(queryBuilder);
        }

        int from = request.paramAsInt("from", -1);
        if (from != -1) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.from(from);
        }
        int size = request.paramAsInt("size", -1);
        if (size != -1) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.size(size);
        }

        if (request.hasParam("explain")) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.explain(request.paramAsBooleanOptional("explain", null));
        }
        if (request.hasParam("version")) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.version(request.paramAsBooleanOptional("version", null));
        }
        if (request.hasParam("timeout")) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.timeout(request.paramAsTime("timeout", null));
        }

        String sField = request.param("fields");
        if (sField != null) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            if (!Strings.hasText(sField)) {
                searchSourceBuilder.noFields();
            } else {
                String[] sFields = Strings.splitStringByCommaToArray(sField);
                if (sFields != null) {
                    for (String field : sFields) {
                        searchSourceBuilder.field(field);
                    }
                }
            }
        }

        String sSorts = request.param("sort");
        if (sSorts != null) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            String[] sorts = Strings.splitStringByCommaToArray(sSorts);
            for (String sort : sorts) {
                int delimiter = sort.lastIndexOf(":");
                if (delimiter != -1) {
                    String sortField = sort.substring(0, delimiter);
                    String reverse = sort.substring(delimiter + 1);
                    if ("asc".equals(reverse)) {
                        searchSourceBuilder.sort(sortField, SortOrder.ASC);
                    } else if ("desc".equals(reverse)) {
                        searchSourceBuilder.sort(sortField, SortOrder.DESC);
                    }
                } else {
                    searchSourceBuilder.sort(sort);
                }
            }
        }

        String sIndicesBoost = request.param("indices_boost");
        if (sIndicesBoost != null) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            String[] indicesBoost = Strings.splitStringByCommaToArray(sIndicesBoost);
            for (String indexBoost : indicesBoost) {
                int divisor = indexBoost.indexOf(',');
                if (divisor == -1) {
                    throw new ElasticSearchIllegalArgumentException("Illegal index boost [" + indexBoost + "], no ','");
                }
                String indexName = indexBoost.substring(0, divisor);
                String sBoost = indexBoost.substring(divisor + 1);
                try {
                    searchSourceBuilder.indexBoost(indexName, Float.parseFloat(sBoost));
                } catch (NumberFormatException e) {
                    throw new ElasticSearchIllegalArgumentException("Illegal index boost [" + indexBoost + "], boost not a float number");
                }
            }
        }

        String sStats = request.param("stats");
        if (sStats != null) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.stats(Strings.splitStringByCommaToArray(sStats));
        }

        return searchSourceBuilder;
    }
}
