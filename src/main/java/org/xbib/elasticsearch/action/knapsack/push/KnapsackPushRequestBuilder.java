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

import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.unit.TimeValue;

import java.util.Map;

/**
 * Build request for knapsack push action
 */
public class KnapsackPushRequestBuilder extends ActionRequestBuilder<KnapsackPushRequest, KnapsackPushResponse, KnapsackPushRequestBuilder> {

    public KnapsackPushRequestBuilder(ElasticsearchClient client) {
        super(client, KnapsackPushAction.INSTANCE, new KnapsackPushRequest());
    }

    public KnapsackPushRequestBuilder setTimeout(TimeValue timeValue) {
        request.setTimeout(timeValue);
        return this;
    }

    public KnapsackPushRequestBuilder setHost(String host) {
        request.setHost(host);
        return this;
    }

    public KnapsackPushRequestBuilder setPort(int port) {
        request.setPort(port);
        return this;
    }

    public KnapsackPushRequestBuilder setCluster(String cluster) {
        request.setCluster(cluster);
        return this;
    }

    public KnapsackPushRequestBuilder setSniff(boolean sniff) {
        request.setSniff(sniff);
        return this;
    }

    public KnapsackPushRequestBuilder setIndex(String index) {
        request.setIndex(index);
        return this;
    }

    public KnapsackPushRequestBuilder setType(String type) {
        request.setType(type);
        return this;
    }

    public KnapsackPushRequestBuilder setLimit(int limit) {
        request.setLimit(limit);
        return this;
    }

    public KnapsackPushRequestBuilder setMaxActionsPerBulkRequest(int maxActionsPerBulkRequest) {
        request.setMaxActionsPerBulkRequest(maxActionsPerBulkRequest);
        return this;
    }

    public KnapsackPushRequestBuilder setMaxBulkConcurrency(int maxBulkConcurrency) {
        request.setMaxBulkConcurrency(maxBulkConcurrency);
        return this;
    }

    public KnapsackPushRequestBuilder setIndexTypeNames(Map<String, Object> indexTypeNames) {
        request.setIndexTypeNames(indexTypeNames);
        return this;
    }

    public KnapsackPushRequestBuilder withMetadata(boolean withMetadata) {
        request.withMetadata(withMetadata);
        return this;
    }

    public KnapsackPushRequestBuilder setSearchRequest(SearchRequest searchRequest) {
        request.setSearchRequest(searchRequest);
        return this;
    }
}
