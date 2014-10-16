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

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.single.custom.SingleCustomOperationRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.xbib.elasticsearch.knapsack.KnapsackRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.elasticsearch.common.collect.Maps.newHashMap;

public class KnapsackExportRequest extends SingleCustomOperationRequest
        implements KnapsackRequest {

    private Path path;

    private TimeValue timeout;

    private int limit;

    private Map<String, Object> indexTypeNames = newHashMap();

    private boolean withMetadata;

    private String index = "_all";

    private String type;

    private SearchRequest searchRequest;

    private boolean overwrite;

    private boolean encodeEntry;

    private ByteSizeValue bytesToTransfer = ByteSizeValue.parseBytesSizeValue("0");

    public String getCluster() {
        return null;
    }

    public String getHost() {
        return null;
    }

    public int getPort() {
        return -1;
    }

    public TimeValue getTimeout() {
        return null;
    }

    public boolean getSniff() {
        return false;
    }

    public KnapsackExportRequest setPath(Path path) {
        this.path = path;
        return this;
    }

    public Path getPath() {
        return path;
    }

    public KnapsackExportRequest setIndex(String index) {
        this.index = index;
        return this;
    }

    public String getIndex() {
        return index;
    }

    public KnapsackExportRequest setType(String type) {
        this.type = type;
        return this;
    }

    public String getType() {
        return type;
    }

    public KnapsackExportRequest setLimit(int limit) {
        this.limit = limit;
        return this;
    }

    public int getLimit() {
        return limit;
    }

    public KnapsackExportRequest setIndexTypeNames(Map indexTypeNames) {
        this.indexTypeNames = indexTypeNames;
        return this;
    }

    public Map<String, Object> getIndexTypeNames() {
        return indexTypeNames;
    }

    public KnapsackExportRequest withMetadata(boolean withMetadata) {
        this.withMetadata = withMetadata;
        return this;
    }

    public boolean withMetadata() {
        return withMetadata;
    }

    public KnapsackExportRequest setSearchRequest(SearchRequest searchRequest) {
        this.searchRequest = searchRequest;
        return this;
    }

    public SearchRequest getSearchRequest() {
        return searchRequest;
    }

    public KnapsackExportRequest setOverwriteAllowed(boolean overwrite) {
        this.overwrite = overwrite;
        return this;
    }

    public boolean isOverwriteAllowed() {
        return overwrite;
    }

    public KnapsackExportRequest setEncodeEntry(boolean encodeEntry) {
        this.encodeEntry = encodeEntry;
        return this;
    }

    public boolean isEncodeEntry() {
        return encodeEntry;
    }

    public KnapsackExportRequest setBytesToTransfer(ByteSizeValue bytesToTransfer) {
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
        if (timeout != null) {
            out.writeBoolean(true);
            timeout.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeInt(limit);
        out.writeMap(indexTypeNames);
        out.writeBoolean(withMetadata);
        out.writeBoolean(overwrite);
        out.writeBoolean(encodeEntry);
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
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        path = Paths.get(URI.create(in.readString()));
        if (in.readBoolean()) {
            timeout = TimeValue.readTimeValue(in);
        }
        limit = in.readInt();
        indexTypeNames = in.readMap();
        withMetadata = in.readBoolean();
        overwrite = in.readBoolean();
        encodeEntry = in.readBoolean();
        index = in.readString();
        type = in.readString();
        if (in.readBoolean()) {
            searchRequest = new SearchRequest();
            searchRequest.readFrom(in);
        }
        bytesToTransfer.readFrom(in);
    }
}
