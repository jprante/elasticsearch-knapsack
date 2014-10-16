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

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.xbib.elasticsearch.knapsack.KnapsackState;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class KnapsackImportResponse implements ActionResponse, ToXContent {

    private KnapsackState state;

    private boolean running;

    private String reason;

    public KnapsackImportResponse setState(KnapsackState state) {
        this.state = state;
        return this;
    }

    public KnapsackState getState() {
        return state;
    }

    public KnapsackImportResponse setRunning(boolean running) {
        this.running = running;
        return this;
    }

    public boolean isRunning() {
        return running;
    }

    public KnapsackImportResponse setReason(String reason) {
        this.reason = reason;
        return this;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field("running", running).field("state");
        state.toXContent(builder, params);
        return builder;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        state = new KnapsackState();
        state.readFrom(in);
        running = in.readBoolean();
        reason = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        state.writeTo(out);
        out.writeBoolean(running);
        out.writeString(reason);
    }

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
