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
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;

import java.nio.file.Path;
import java.util.Map;

public class KnapsackPullRequestBuilder extends ActionRequestBuilder<KnapsackPullRequest, KnapsackPullResponse, KnapsackPullRequestBuilder> {

    public KnapsackPullRequestBuilder(ElasticsearchClient client) {
        super(client, KnapsackPullAction.INSTANCE, new KnapsackPullRequest());
    }

    public KnapsackPullRequestBuilder setPath(Path path) {
        request.setPath(path);
        return this;
    }

    public KnapsackPullRequestBuilder setTimeout(TimeValue timeValue) {
        request.setTimeout(timeValue);
        return this;
    }

    public KnapsackPullRequestBuilder setHost(String host) {
        request.setHost(host);
        return this;
    }

    public KnapsackPullRequestBuilder setPort(int port) {
        request.setPort(port);
        return this;
    }

    public KnapsackPullRequestBuilder setCluster(String cluster) {
        request.setCluster(cluster);
        return this;
    }

    public KnapsackPullRequestBuilder setSniff(boolean sniff) {
        request.setSniff(sniff);
        return this;
    }

    public KnapsackPullRequestBuilder setIndex(String index) {
        request.setIndex(index);
        return this;
    }

    public KnapsackPullRequestBuilder setType(String type) {
        request.setType(type);
        return this;
    }

    public KnapsackPullRequestBuilder setMaxActionsPerBulkRequest(int maxActionsPerBulkRequest) {
        request.setMaxActionsPerBulkRequest(maxActionsPerBulkRequest);
        return this;
    }

    public KnapsackPullRequestBuilder setMaxBulkConcurrency(int maxBulkConcurrency) {
        request.setMaxBulkConcurrency(maxBulkConcurrency);
        return this;
    }

    public KnapsackPullRequestBuilder setIndexTypeNames(Map<String, Object> indexTypeNames) {
        request.setIndexTypeNames(indexTypeNames);
        return this;
    }

    public KnapsackPullRequestBuilder withMetadata(boolean withMetadata) {
        request.withMetadata(withMetadata);
        return this;
    }

    public KnapsackPullRequestBuilder setSearchRequest(SearchRequest searchRequest) {
        request.setSearchRequest(searchRequest);
        return this;
    }

    public KnapsackPullRequestBuilder withDecodedEntry(boolean decodedEntry) {
        request.setDecodeEntry(decodedEntry);
        return this;
    }

    public KnapsackPullRequestBuilder setBytesToTransfer(ByteSizeValue bytesToTransfer) {
        request.setBytesToTransfer(bytesToTransfer);
        return this;
    }

    public KnapsackPullRequestBuilder addIndexSettings(String index, String settingsSpec) {
        request.addIndexSettings(index, settingsSpec);
        return this;
    }

    public KnapsackPullRequestBuilder addIndexTypeMapping(String indexType, String mappingSpec) {
        request.addIndexTypeMapping(indexType, mappingSpec);
        return this;
    }
}
