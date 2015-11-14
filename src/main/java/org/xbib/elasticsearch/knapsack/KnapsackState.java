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
package org.xbib.elasticsearch.knapsack;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.joda.DateMathParser;
import org.elasticsearch.common.joda.Joda;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParser.Token;
import org.joda.time.DateTime;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.Callable;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.common.xcontent.XContentParser.Token.END_OBJECT;
import static org.elasticsearch.common.xcontent.XContentParser.Token.FIELD_NAME;
import static org.elasticsearch.common.xcontent.XContentParser.Token.VALUE_NULL;

public class KnapsackState implements Streamable, ToXContent {

    /**
     * The mode of the knapsack operation
     */
    private String mode;

    /**
     * The time stamp of this Knapsack operation
     */
    private DateTime timestamp;

    /**
     * The path of the archive involved in this Knapsack operation
     */
    private Path path;

    /**
     * The address URI of the Elasticsearch cluster
     */
    private String address;

    /**
     * The node name where the knapsack operation is executed
     */
    private String nodeName;

    public KnapsackState() {
    }

    public KnapsackState setMode(String mode) {
        this.mode = mode;
        return this;
    }

    public String getMode() {
        return mode;
    }

    public KnapsackState setTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public DateTime getTimestamp() {
        return timestamp;
    }

    public KnapsackState setPath(Path path) {
        this.path = path;
        return this;
    }

    public Path getPath() {
        return path;
    }

    public KnapsackState setClusterAddress(String address) {
        this.address = address;
        return this;
    }

    public String getClusterAddress() {
        return address;
    }


    public KnapsackState setNodeName(String nodeName) {
        this.nodeName = nodeName;
        return this;
    }

    public String getNodeName() {
        return nodeName;
    }

    private static Callable<Long> now() {
        return new Callable<Long>() {
            @Override
            public Long call() {
                return 0L;
            }
        };
    }

    private final static DateMathParser dateParser = new DateMathParser(Joda.forPattern("dateOptionalTime"));

    private final static ESLogger logger = ESLoggerFactory.getLogger("state");

    public KnapsackState fromXContent(XContentParser parser) throws IOException {
        Long startTimestamp = new Date().getTime();
        Path path = null;
        String address = null;
        String nodeName = null;
        String currentFieldName = null;
        Token token;
        while ((token = parser.nextToken()) != null) {
            if (token == END_OBJECT) {
                break;
            }
            else if (token == FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue() || token == VALUE_NULL) {
                if (currentFieldName != null) {
                    switch (currentFieldName) {
                        case "mode":
                            mode = parser.text();
                            break;
                        case "started":
                            startTimestamp = parser.text() != null ? dateParser.parse(parser.text(), now()) : null;
                            break;
                        case "path":
                            path = parser.text() != null ? Paths.get(URI.create(parser.text())) : null;
                            break;
                        case "cluster_address":
                            address = parser.text();
                            break;
                        case "node_name":
                            nodeName = parser.text();
                            break;
                    }
                }
            }
        }
        return new KnapsackState()
                .setMode(mode)
                .setTimestamp(new DateTime(startTimestamp))
                .setPath(path)
                .setClusterAddress(address)
                .setNodeName(nodeName);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject()
                .field("mode", mode);
        if (timestamp != null) {
            builder.field("started", timestamp);
        }
        if (path != null) {
            builder.field("path", path.toUri().toString());
        }
        if (address != null) {
            builder.field("cluster_address", address);
        }
        if (nodeName != null) {
            builder.field("node_name", nodeName);
        }
        builder.endObject();
        return builder;
    }

    public String id() {
        StringBuilder sb = new StringBuilder();
        sb.append(mode)
                .append(timestamp)
                .append(path)
                .append(address)
                .append(nodeName);
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return id().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        KnapsackState other = (KnapsackState) obj;
        return id().equals(other.id());
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        mode = in.readString();
        timestamp = new DateTime(in.readLong());
        path = Paths.get(URI.create(in.readString()));
        address = in.readString();
        nodeName = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(mode);
        out.writeLong(timestamp.getMillis());
        out.writeString(path.toUri().toString());
        out.writeString(address);
        out.writeString(nodeName);
    }

    @Override
    public String toString() {
        try {
            return toXContent(jsonBuilder(), EMPTY_PARAMS).string();
        } catch (IOException e) {
            // ignore
        }
        return "";
    }
}