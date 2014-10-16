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
package org.xbib.elasticsearch.action.knapsack.imp;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.BaseRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;

import java.nio.file.Path;
import java.util.Map;

public class KnapsackImportRequestBuilder extends BaseRequestBuilder<KnapsackImportRequest, KnapsackImportResponse> {

    public KnapsackImportRequestBuilder(Client client) {
        super(client, new KnapsackImportRequest());
    }

    public KnapsackImportRequestBuilder setPath(Path path) {
        request.setPath(path);
        return this;
    }

    public KnapsackImportRequestBuilder setTimeout(TimeValue timeValue) {
        request.setTimeout(timeValue);
        return this;
    }

    public KnapsackImportRequestBuilder setHost(String host) {
        request.setHost(host);
        return this;
    }

    public KnapsackImportRequestBuilder setPort(int port) {
        request.setPort(port);
        return this;
    }

    public KnapsackImportRequestBuilder setCluster(String cluster) {
        request.setCluster(cluster);
        return this;
    }

    public KnapsackImportRequestBuilder setSniff(boolean sniff) {
        request.setSniff(sniff);
        return this;
    }

    public KnapsackImportRequestBuilder setIndex(String index) {
        request.setIndex(index);
        return this;
    }

    public KnapsackImportRequestBuilder setType(String type) {
        request.setType(type);
        return this;
    }

    public KnapsackImportRequestBuilder setMaxActionsPerBulkRequest(int maxActionsPerBulkRequest) {
        request.setMaxActionsPerBulkRequest(maxActionsPerBulkRequest);
        return this;
    }

    public KnapsackImportRequestBuilder setMaxBulkConcurrency(int maxBulkConcurrency) {
        request.setMaxBulkConcurrency(maxBulkConcurrency);
        return this;
    }

    public KnapsackImportRequestBuilder setIndexTypeNames(Map indexTypeNames) {
        request.setIndexTypeNames(indexTypeNames);
        return this;
    }

    public KnapsackImportRequestBuilder withMetadata(boolean withMetadata) {
        request.withMetadata(withMetadata);
        return this;
    }

    public KnapsackImportRequestBuilder setSearchRequest(SearchRequest searchRequest) {
        request.setSearchRequest(searchRequest);
        return this;
    }

    public KnapsackImportRequestBuilder withDecodedEntry(boolean decodedEntry) {
        request.setDecodeEntry(decodedEntry);
        return this;
    }

    public KnapsackImportRequestBuilder setBytesToTransfer(ByteSizeValue bytesToTransfer) {
        request.setBytesToTransfer(bytesToTransfer);
        return this;
    }

    public KnapsackImportRequestBuilder addIndexSettings(String index, String settingsSpec) {
        request.addIndexSettings(index, settingsSpec);
        return this;
    }

    public KnapsackImportRequestBuilder addIndexTypeMapping(String indexType, String mappingSpec) {
        request.addIndexTypeMapping(indexType, mappingSpec);
        return this;
    }

    @Override
    protected void doExecute(ActionListener<KnapsackImportResponse> listener) {
        ((IndicesAdminClient)client).execute(KnapsackImportAction.INSTANCE, request, listener);
    }
}
