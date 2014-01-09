
package org.xbib.elasticsearch.plugin.knapsack;

import org.elasticsearch.common.joda.DateMathParser;
import org.elasticsearch.common.joda.Joda;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParser.Token;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.common.xcontent.XContentParser.Token.END_OBJECT;
import static org.elasticsearch.common.xcontent.XContentParser.Token.FIELD_NAME;
import static org.elasticsearch.common.xcontent.XContentParser.Token.START_OBJECT;
import static org.elasticsearch.common.xcontent.XContentParser.Token.VALUE_NULL;

public class KnapsackStatus implements ToXContent {

    /**
     * The time stamp of this Knapsack operation
     */
    private Date timestamp;

    /**
     * The index/type map of this Knapsack operation
     */
    private Map<String,Object> map;

    /**
     * The path of the archive involved in this Knapsack operation
     */
    private String path;

    /**
     * The URI of the Elasticsearch cluster
     */
    private URI uri;

    /**
     * direct copy flag
     */
    private boolean copy;

    /**
     * S3 flag
     */
    private boolean s3;

    public KnapsackStatus() {
    }

    public KnapsackStatus setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public KnapsackStatus setMap(Map<String,Object> map) {
        this.map = map;
        return this;
    }

    public KnapsackStatus setPath(String path) {
        this.path = path;
        return this;
    }

    public KnapsackStatus setURI(URI uri) {
        this.uri = uri;
        return this;
    }

    public KnapsackStatus setCopy(boolean copy) {
        this.copy = copy;
        return this;
    }

    public KnapsackStatus setS3(boolean s3) {
        this.s3 = s3;
        return this;
    }

    public KnapsackStatus fromXContent(XContentParser parser) throws IOException {
        DateMathParser dateParser = new DateMathParser(Joda.forPattern("dateOptionalTime"), TimeUnit.MILLISECONDS);
        Long startTimestamp = new Date().getTime();
        Map<String,Object> map = null;
        String path = null;
        URI uri = null;
        String currentFieldName = null;
        Token token;
        while ((token = parser.nextToken()) != END_OBJECT) {
            if (token == FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue() || token == VALUE_NULL) {
                if ("started".equals(currentFieldName)) {
                    startTimestamp = dateParser.parse(parser.text(), 0);
                } else if ("path".equals(currentFieldName)) {
                    path = parser.text();
                } else if ("uri".equals(currentFieldName)) {
                    uri = URI.create(parser.text());
                } else if ("copy".equals(currentFieldName)) {
                    copy = parser.booleanValue();
                } else if ("s3".equals(currentFieldName)) {
                    s3 = parser.booleanValue();
                }
            } else if (token == START_OBJECT) {
                map = parser.map();
            }
        }
        return new KnapsackStatus()
             .setTimestamp(new Date(startTimestamp))
             .setMap(map)
             .setPath(path)
             .setURI(uri)
             .setCopy(copy)
             .setS3(s3);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.startObject()
                .field("started", timestamp)
                .field("path", path)
                .field("map", map)
                .field("uri", uri)
                .field("copy", copy)
                .field("s3", s3)
                .endObject();
    }

    public String id() {
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp.getTime())
            .append(path)
            .append(map)
            .append(uri)
            .append(copy)
            .append(s3);
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
        KnapsackStatus other = (KnapsackStatus) obj;
        return id().equals(other.id());
    }

    public String toString() {
        try {
            return toXContent(jsonBuilder(), EMPTY_PARAMS).string();
        } catch (IOException e) {
            // ignore
        }
        return "";
    }

}