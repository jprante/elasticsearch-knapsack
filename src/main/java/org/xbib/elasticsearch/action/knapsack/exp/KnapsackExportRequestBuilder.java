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
package org.xbib.elasticsearch.action.knapsack.exp;

import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.unit.ByteSizeValue;

import java.nio.file.Path;
import java.util.Map;

/**
 * Build request for knapsack export action
 */
public class KnapsackExportRequestBuilder extends ActionRequestBuilder<KnapsackExportRequest, KnapsackExportResponse, KnapsackExportRequestBuilder> {

    public KnapsackExportRequestBuilder(ElasticsearchClient client) {
        super(client, KnapsackExportAction.INSTANCE, new KnapsackExportRequest());
    }

    public KnapsackExportRequestBuilder setArchivePath(Path archivePath) {
        request.setArchivePath(archivePath);
        return this;
    }

    public KnapsackExportRequestBuilder setIndex(String index) {
        request.setIndex(index);
        return this;
    }

    public KnapsackExportRequestBuilder setType(String type) {
        request.setType(type);
        return this;
    }

    public KnapsackExportRequestBuilder setLimit(int limit) {
        request.setLimit(limit);
        return this;
    }

    public KnapsackExportRequestBuilder setIndexTypeNames(Map<String, Object> indexTypeNames) {
        request.setIndexTypeNames(indexTypeNames);
        return this;
    }

    public KnapsackExportRequestBuilder setSearchRequest(SearchRequest searchRequest) {
        request.setSearchRequest(searchRequest);
        return this;
    }

    public KnapsackExportRequestBuilder setBytesToTransfer(ByteSizeValue bytesToTransfer) {
        request.setBytesToTransfer(bytesToTransfer);
        return this;
    }

    public KnapsackExportRequestBuilder withMetadata(boolean withMetadata) {
        request.withMetadata(withMetadata);
        return this;
    }

    public KnapsackExportRequestBuilder setOverwriteAllowed(boolean overwrite) {
        request.setOverwriteAllowed(overwrite);
        return this;
    }

    public KnapsackExportRequestBuilder withAliases(boolean withAliases) {
        request.withAliases(withAliases);
        return this;
    }
}
