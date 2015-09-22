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
package org.xbib.elasticsearch.action.knapsack.state;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.xbib.elasticsearch.knapsack.KnapsackState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class KnapsackStateResponse extends ActionResponse implements ToXContent {

    private final static ESLogger logger= ESLoggerFactory.getLogger("state");

    private List<KnapsackState> states = new LinkedList<>();

    public KnapsackStateResponse addState(KnapsackState state) {
        this.states.add(state);
        return this;
    }

    public List<KnapsackState> getStates() {
        return states;
    }

    public boolean isExportActive(Path path) {
        if (states != null && path != null) {
            for (KnapsackState ks : states) {
                if (ks != null && "export".equals(ks.getMode()) && ks.getPath() != null && path.toString().equals(ks.getPath().toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isImportActive(Path path) {
        if (states != null && path != null) {
            for (KnapsackState ks : states) {
                if (ks != null && "import".equals(ks.getMode()) && ks.getPath() != null && path.toString().equals(ks.getPath().toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isPushActive() {
        if (states != null) {
            for (KnapsackState ks : states) {
                if (ks != null && "push".equals(ks.getMode())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isPullActive() {
        if (states != null) {
            for (KnapsackState ks : states) {
                if (ks != null && "pull".equals(ks.getMode())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field("count", states.size())
                .startArray("states");
        for (KnapsackState ks : states) {
            ks.toXContent(builder, params);
        }
        builder.endArray();
        return builder;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        states = new LinkedList<>();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            KnapsackState ks = new KnapsackState();
            ks.readFrom(in);
            states.add(ks);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (states != null) {
            out.writeInt(states.size());
            for (KnapsackState state : states) {
                state.writeTo(out);
            }
        }
    }

    @Override
    public String toString() {
        try {
            XContentBuilder builder = jsonBuilder();
            builder.startObject();
            builder = toXContent(builder, EMPTY_PARAMS);
            builder.endObject();
            return builder.string();
        } catch (IOException e) {
            return "";
        }
    }
}
