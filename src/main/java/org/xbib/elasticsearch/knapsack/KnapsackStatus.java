package org.xbib.elasticsearch.knapsack;

import static org.elasticsearch.common.base.Objects.firstNonNull;
import static org.elasticsearch.common.collect.Lists.newArrayList;
import static org.elasticsearch.common.xcontent.XContentParser.Token.END_ARRAY;
import static org.elasticsearch.common.xcontent.XContentParser.Token.END_OBJECT;
import static org.elasticsearch.common.xcontent.XContentParser.Token.FIELD_NAME;
import static org.elasticsearch.common.xcontent.XContentParser.Token.START_ARRAY;
import static org.elasticsearch.common.xcontent.XContentParser.Token.VALUE_NULL;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.elasticsearch.common.joda.DateMathParser;
import org.elasticsearch.common.joda.Joda;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParser.Token;

public class KnapsackStatus implements ToXContent {

    private Date timestamp;

    private String[] indices;

    private String[] types;

    private String target;

    protected KnapsackStatus() {
    }

    public KnapsackStatus(Date timestamp, String[] indices, String[] types, String target) {
        this.timestamp = timestamp;
        this.indices = firstNonNull(indices, new String[] {});
        this.types = firstNonNull(types, new String[] {});
        this.target = target;
    }

    public KnapsackStatus(String[] indices, String[] types, String target) {
        this(new Date(), indices, types, target);
    }

    public KnapsackStatus fromXContent(XContentParser parser) throws IOException {
        DateMathParser dateParser = new DateMathParser(Joda.forPattern("dateOptionalTime"), TimeUnit.MILLISECONDS);
        Long startTimestamp = null;
        String[] indices = null;
        String[] types = null;
        String target = null;
        String currentFieldName = null;
        Token token;
        while ((token = parser.nextToken()) != END_OBJECT) {
            if (token == FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue() || token == VALUE_NULL) {
                if ("started".equals(currentFieldName)) {
                    startTimestamp =  dateParser.parse(parser.text(), 0);
                } else if ("target".equals(currentFieldName)) {
                    target = parser.text();
                }
            } else if (token == START_ARRAY) {
                List<String> values = newArrayList();
                while ((parser.nextToken()) != END_ARRAY) {
                    values.add(parser.text());
                }
                if ("indices".equals(currentFieldName)) {
                    indices = values.toArray(new String[values.size()]);
                } else if ("types".equals(currentFieldName)) {
                    types = values.toArray(new String[values.size()]);
                }
            }
        }
        return new KnapsackStatus(new Date(startTimestamp), indices, types, target);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.startObject()
                .field("started", timestamp)
                .field("indices", indices)
                .field("types", types)
                .field("target", target)
                .endObject();
    }

    public String id() {
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp.getTime()).append(target);
        if (indices != null) {
            for (String index : indices) {
                sb.append(index);
            }
        }
        if (types != null) {
            for (String type: types) {
                sb.append(type);
            }
        }
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

}