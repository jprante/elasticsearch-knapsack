package org.xbib.elasticsearch.knapsack;

import static org.elasticsearch.common.base.Objects.firstNonNull;
import static org.elasticsearch.common.collect.Lists.newArrayList;
import static org.elasticsearch.common.xcontent.XContentParser.Token.END_ARRAY;
import static org.elasticsearch.common.xcontent.XContentParser.Token.END_OBJECT;
import static org.elasticsearch.common.xcontent.XContentParser.Token.FIELD_NAME;
import static org.elasticsearch.common.xcontent.XContentParser.Token.START_ARRAY;
import static org.elasticsearch.common.xcontent.XContentParser.Token.VALUE_NULL;

import java.io.IOException;
import java.util.List;

import org.elasticsearch.ElasticSearchParseException;
import org.elasticsearch.common.UUID;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParser.Token;

public class Knapsack implements ToXContent {

	private final String id;
	private final long startTimestamp;
	private final String[] indices;
	private final String[] types;
	private final String target;

	public Knapsack(String id, long startTimestamp, String[] indices, String[] types, String target) {
		super();
		this.id = id;
		this.startTimestamp = startTimestamp;
		this.indices = firstNonNull(indices, new String[] {});
		this.types = firstNonNull(types, new String[] {});
		this.target = target;
	}

	public Knapsack(long startTimestamp, String[] indices, String[] types, String target) {
		this(UUID.randomBase64UUID(), startTimestamp, indices, types, target);
	}

	public Knapsack(String[] indices, String[] types, String target) {
		this(System.currentTimeMillis(), indices, types, target);
	}

	public String getId() {
		return id;
	}

	public long getStartTimestamp() {
		return startTimestamp;
	}

	public String[] getIndices() {
		return indices;
	}

	public String[] getTypes() {
		return types;
	}

	public String getTarget() {
		return target;
	}

	public static Knapsack fromXContent(XContentParser parser) throws IOException {
		String id = null;
		long startTimestamp = -1L;
		String[] indexes = null;
		String[] types = null;
		String target = null;

		String currentFieldName = null;
		Token token;
		while ((token = parser.nextToken()) != END_OBJECT) {
			if (token == FIELD_NAME) {
				currentFieldName = parser.currentName();
			} else if (token.isValue() || token == VALUE_NULL) {
				if ("id".equals(currentFieldName)) {
					id = parser.text();
				} else if ("start_timestamp".equals(currentFieldName)) {
					startTimestamp = parser.longValue();
				} else if ("target".equals(currentFieldName)) {
					target = parser.text();
				} else {
					throw new ElasticSearchParseException("[knapsack] does not support field [" + currentFieldName + "]");
				}
			} else if (token == START_ARRAY) {
				List<String> values = newArrayList();
				while ((token = parser.nextToken()) != END_ARRAY) {
					values.add(parser.text());
				}

				if ("indices".equals(currentFieldName)) {
					indexes = values.toArray(new String[values.size()]);
				} else if ("types".equals(currentFieldName)) {
					types = values.toArray(new String[values.size()]);
				} else {
					throw new ElasticSearchParseException("[knapsack] unexpected array [" + token + "]");
				}
			} else {
				throw new ElasticSearchParseException("[knapsack] unexpected token [" + token + "]");
			}
		}

		if (id == null) {
			throw new ElasticSearchParseException("[knapsack] missing the id parameter");
		}

		return new Knapsack(id, startTimestamp, indexes, types, target);
	}

	@Override
	public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
		return builder.startObject()
				.field("id", id)
				.field("start_timestamp", startTimestamp)
				.field("indices", indices)
				.field("types", types)
				.field("target", target)
				.endObject();
	}

	@Override
	public String toString() {
		try {
			XContentBuilder builder = XContentFactory.jsonBuilder().prettyPrint();
			builder.startObject();
			toXContent(builder, EMPTY_PARAMS);
			builder.endObject();
			return builder.string();
		} catch (IOException e) {
			return "{ \"error\" : \"" + e.getMessage() + "\"}";
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
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
		Knapsack other = (Knapsack) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		return true;
	}

}
