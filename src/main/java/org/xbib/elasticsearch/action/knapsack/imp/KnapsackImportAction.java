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

import org.elasticsearch.action.Action;
import org.elasticsearch.client.ElasticsearchClient;

public class KnapsackImportAction extends Action<KnapsackImportRequest, KnapsackImportResponse, KnapsackImportRequestBuilder> {

    public final static String NAME = "org.xbib.elasticsearch.knapsack.import";

    public final static KnapsackImportAction INSTANCE = new KnapsackImportAction(NAME);

    protected KnapsackImportAction(String name) {
        super(name);
    }

    @Override
    public KnapsackImportResponse newResponse() {
        return new KnapsackImportResponse();
    }

    @Override
    public KnapsackImportRequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new KnapsackImportRequestBuilder(client);
    }
}
