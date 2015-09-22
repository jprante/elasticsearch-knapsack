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

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.unit.TimeValue;
import org.xbib.elasticsearch.knapsack.KnapsackRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class KnapsackPushRequest extends ActionRequest<KnapsackPushRequest>
        implements KnapsackRequest {

    private String host;

    private int port;

    private String cluster;

    private boolean sniff;

    private TimeValue timeout;

    private int limit;

    private int maxActionsPerBulkRequest = 1000;

    private int maxBulkConcurrency = 2 * Runtime.getRuntime().availableProcessors();

    private Map<String, Object> indexTypeNames = new HashMap<>();

    private boolean withMetadata;

    private String index = "_all";

    private String type;

    private SearchRequest searchRequest;

    public KnapsackPushRequest setHost(String host) {
        this.host = host;
        return this;
    }

    public String getHost() {
        return host;
    }

    public KnapsackPushRequest setPort(int port) {
        this.port = port;
        return this;
    }

    public int getPort() {
        return port;
    }

    public KnapsackPushRequest setCluster(String cluster) {
        this.cluster = cluster;
        return this;
    }

    public String getCluster() {
        return cluster;
    }


    public KnapsackPushRequest setSniff(boolean sniff) {
        this.sniff = sniff;
        return this;
    }

    public boolean getSniff() {
        return sniff;
    }

    public KnapsackPushRequest setIndex(String index) {
        this.index = index;
        return this;
    }

    public String getIndex() {
        return index;
    }

    public KnapsackPushRequest setType(String type) {
        this.type = type;
        return this;
    }

    public String getType() {
        return type;
    }

    public KnapsackPushRequest setTimeout(TimeValue timeValue) {
        this.timeout = timeValue;
        return this;
    }

    public TimeValue getTimeout() {
        return timeout;
    }

    public KnapsackPushRequest setLimit(int limit) {
        this.limit = limit;
        return this;
    }

    public int getLimit() {
        return limit;
    }

    public KnapsackPushRequest setMaxActionsPerBulkRequest(int maxActionsPerBulkRequest) {
        this.maxActionsPerBulkRequest = maxActionsPerBulkRequest;
        return this;
    }

    public int getMaxActionsPerBulkRequest() {
        return maxActionsPerBulkRequest;
    }

    public KnapsackPushRequest setMaxBulkConcurrency(int maxBulkConcurrency) {
        this.maxBulkConcurrency = maxBulkConcurrency;
        return this;
    }

    public int getMaxBulkConcurrency() {
        return maxBulkConcurrency;
    }

    public KnapsackPushRequest setIndexTypeNames(Map<String, Object> indexTypeNames) {
        this.indexTypeNames = indexTypeNames;
        return this;
    }

    public Map<String, Object> getIndexTypeNames() {
        return indexTypeNames;
    }

    public KnapsackPushRequest withMetadata(boolean withMetadata) {
        this.withMetadata = withMetadata;
        return this;
    }

    public boolean withMetadata() {
        return withMetadata;
    }

    public KnapsackPushRequest setSearchRequest(SearchRequest searchRequest) {
        this.searchRequest = searchRequest;
        return this;
    }

    public SearchRequest getSearchRequest() {
        return searchRequest;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(host);
        out.writeInt(port);
        out.writeString(cluster);
        out.writeBoolean(sniff);
        if (timeout != null) {
            out.writeBoolean(true);
            timeout.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeInt(limit);
        out.writeInt(maxActionsPerBulkRequest);
        out.writeInt(maxBulkConcurrency);
        out.writeMap(indexTypeNames);
        out.writeBoolean(withMetadata);
        out.writeString(index);
        out.writeString(type);
        if (searchRequest != null) {
            out.writeBoolean(true);
            searchRequest.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        host = in.readString();
        port = in.readInt();
        cluster = in.readString();
        sniff = in.readBoolean();
        if (in.readBoolean()) {
            timeout = TimeValue.readTimeValue(in);
        }
        limit = in.readInt();
        maxActionsPerBulkRequest = in.readInt();
        maxBulkConcurrency = in.readInt();
        indexTypeNames = in.readMap();
        withMetadata = in.readBoolean();
        index = in.readString();
        type = in.readString();
        if (in.readBoolean()) {
            searchRequest = new SearchRequest();
            searchRequest.readFrom(in);
        }
    }
}
