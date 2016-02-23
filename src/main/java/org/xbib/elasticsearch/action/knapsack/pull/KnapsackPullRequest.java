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

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.xbib.elasticsearch.knapsack.KnapsackRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class KnapsackPullRequest extends ActionRequest<KnapsackPullRequest>
        implements KnapsackRequest {

    private Path path;

    private String host;

    private int port;

    private String cluster;

    private boolean sniff;

    private TimeValue timeout;

    private int maxActionsPerBulkRequest = 1000;

    private int maxBulkConcurrency = 2 * Runtime.getRuntime().availableProcessors();

    private Map<String,Object> indexTypeNames = new HashMap<>();

    private Map<String,Object> indexTypeDefinitions = new HashMap<>();

    private String index = "_all";

    private String type;

    private SearchRequest searchRequest;

    private boolean withMetadata;

    private boolean decodeEntry;

    private ByteSizeValue bytesToTransfer = ByteSizeValue.parseBytesSizeValue("0", "");

    public KnapsackPullRequest setHost(String host) {
        this.host = host;
        return this;
    }

    public String getHost() {
        return host;
    }

    public KnapsackPullRequest setPort(int port) {
        this.port = port;
        return this;
    }

    public int getPort() {
        return port;
    }

    public KnapsackPullRequest setCluster(String cluster) {
        this.cluster = cluster;
        return this;
    }

    public String getCluster() {
        return cluster;
    }

    public KnapsackPullRequest setSniff(boolean sniff) {
        this.sniff = sniff;
        return this;
    }

    public boolean getSniff() {
        return sniff;
    }

    public KnapsackPullRequest setIndex(String index) {
        this.index = index;
        return this;
    }

    public String getIndex() {
        return index;
    }

    public KnapsackPullRequest setType(String type) {
        this.type = type;
        return this;
    }

    public String getType() {
        return type;
    }

    public KnapsackPullRequest setTimeout(TimeValue timeValue) {
        this.timeout = timeValue;
        return this;
    }

    public TimeValue getTimeout() {
        return timeout;
    }

    public KnapsackPullRequest setMaxActionsPerBulkRequest(int maxActionsPerBulkRequest) {
        this.maxActionsPerBulkRequest = maxActionsPerBulkRequest;
        return this;
    }

    public int getMaxActionsPerBulkRequest() {
        return maxActionsPerBulkRequest;
    }

    public KnapsackPullRequest setMaxBulkConcurrency(int maxBulkConcurrency) {
        this.maxBulkConcurrency = maxBulkConcurrency;
        return this;
    }

    public int getMaxBulkConcurrency() {
        return maxBulkConcurrency;
    }

    public KnapsackPullRequest setIndexTypeNames(Map<String,Object> indexTypeNames) {
        this.indexTypeNames = indexTypeNames;
        return this;
    }

    public Map getIndexTypeNames() {
        return indexTypeNames;
    }

    public KnapsackPullRequest addIndexSettings(String index, String settingsSpec) {
        indexTypeDefinitions.put(index, settingsSpec);
        return this;
    }

    public KnapsackPullRequest addIndexTypeMapping(String indexType, String mappingSpec) {
        indexTypeDefinitions.put(indexType, mappingSpec);
        return this;
    }

    public boolean hasIndexSettings(String index) {
        return indexTypeDefinitions.containsKey(index + "_settings");
    }

    public String getIndexSettings(String index) {
        return (String) indexTypeDefinitions.get(index + "_settings");
    }

    public boolean hasIndexTypeMapping(String index, String type) {
        return indexTypeDefinitions.containsKey(index + "_" + type + "_mapping");
    }

    public String getIndexTypeMapping(String index, String type) {
        return (String) indexTypeDefinitions.get(index + "_" + type + "_mapping");
    }

    public KnapsackPullRequest setSearchRequest(SearchRequest searchRequest) {
        this.searchRequest = searchRequest;
        return this;
    }

    public SearchRequest getSearchRequest() {
        return searchRequest;
    }

    public KnapsackPullRequest withMetadata(boolean withMetadata) {
        this.withMetadata = withMetadata;
        return this;
    }

    public boolean withMetadata() {
        return withMetadata;
    }

    public KnapsackPullRequest setPath(Path path) {
        this.path = path;
        return this;
    }

    public Path getPath() {
        return path;
    }

    public KnapsackPullRequest setDecodeEntry(boolean decodeEntry) {
        this.decodeEntry = decodeEntry;
        return this;
    }

    public boolean isDecodeEntry() {
        return decodeEntry;
    }

    public KnapsackPullRequest setBytesToTransfer(ByteSizeValue bytesToTransfer) {
        this.bytesToTransfer = bytesToTransfer;
        return this;
    }

    public ByteSizeValue getBytesToTransfer() {
        return bytesToTransfer;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(path.toUri().toString());
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
        out.writeInt(maxActionsPerBulkRequest);
        out.writeInt(maxBulkConcurrency);
        out.writeMap(indexTypeDefinitions);
        out.writeMap(indexTypeNames);
        out.writeBoolean(withMetadata);
        out.writeBoolean(decodeEntry);
        out.writeString(index);
        out.writeString(type);
        if (searchRequest != null) {
            out.writeBoolean(true);
            searchRequest.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        bytesToTransfer.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        path = Paths.get(URI.create(in.readString()));
        host = in.readString();
        port = in.readInt();
        cluster = in.readString();
        sniff = in.readBoolean();
        if (in.readBoolean()) {
            timeout = TimeValue.readTimeValue(in);
        }
        maxActionsPerBulkRequest = in.readInt();
        maxBulkConcurrency = in.readInt();
        indexTypeDefinitions = in.readMap();
        indexTypeNames = in.readMap();
        withMetadata = in.readBoolean();
        decodeEntry = in.readBoolean();
        index = in.readString();
        type = in.readString();
        if (in.readBoolean()) {
            searchRequest = new SearchRequest();
            searchRequest.readFrom(in);
        }
        bytesToTransfer.readFrom(in);
    }

}
