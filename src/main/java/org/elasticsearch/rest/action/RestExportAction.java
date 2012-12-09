/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.rest.action;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.action.support.RestXContentBuilder.restContentBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.collect.ImmutableSet;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.plugin.knapsack.io.Connection;
import org.elasticsearch.plugin.knapsack.io.ConnectionFactory;
import org.elasticsearch.plugin.knapsack.io.ConnectionService;
import org.elasticsearch.plugin.knapsack.io.Packet;
import org.elasticsearch.plugin.knapsack.io.Session;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.XContentRestResponse;
import org.elasticsearch.rest.XContentThrowableRestResponse;
import org.elasticsearch.rest.action.support.RestActions;
import org.elasticsearch.search.SearchHit;

public class RestExportAction extends BaseRestHandler {

    private final SettingsFilter settingsFilter;
    private final static ConnectionService service = ConnectionService.getInstance();

    @Inject
    public RestExportAction(Settings settings, Client client,
            RestController controller, SettingsFilter settingsFilter) {
        super(settings, client);
        this.settingsFilter = settingsFilter;

        controller.registerHandler(POST, "/_export", this);
        controller.registerHandler(POST, "/{index}/_export", this);
        controller.registerHandler(POST, "/{index}/{type}/_export", this);
    }

    @Override
    public void handleRequest(final RestRequest request, RestChannel channel) {
        final String[] indices = RestActions.splitIndices(request.param("index", "_all"));
        final String[] types = RestActions.splitTypes(request.param("type"));
        try {

            XContentBuilder builder = restContentBuilder(request)
                    .startObject()
                    .field("ok", true)
                    .endObject();
            channel.sendResponse(new XContentRestResponse(request, OK, builder));

            new Thread() {
                @Override
                public void run() {
                    String index = request.param("index");
                    String type = request.param("type");
                    String desc = (index != null ? index : "_all")
                            + (type != null ? "_" + type : "");
                    setName("[Exporter Thread " + desc + "]");
                    long millis = request.paramAsLong("millis", 30000L);
                    int size = request.paramAsInt("size", 1000);
                    final String scheme = request.param("scheme", "targz");
                    final String target = request.param("target", desc);

                    try {
                        logger.info("cluster 'yellow' check before exporting to {}", target);

                        ClusterHealthResponse healthResponse =
                                client.admin().cluster().prepareHealth().setWaitForYellowStatus()
                                .setTimeout("30s").execute().actionGet(30000);

                        if (healthResponse.isTimedOut()) {
                            String msg = "cluster not healthy, cowardly refusing to continue with export";
                            logger.error(msg);
                            throw new IOException(msg);
                        }

                        logger.info("starting export to {}", target);

                        // fire up TAR session
                        ConnectionFactory factory = service.getConnectionFactory(scheme);
                        Connection<Session> connection = factory.getConnection(URI.create(scheme + ":" + target));
                        Session session = connection.createSession();
                        session.open(Session.Mode.WRITE);

                        // export settings
                        for (String s : indices) {
                            session.write(new ElasticPacket(s, "_settings", null, getSettings(s)));
                        }

                        // export mappings
                        for (String s : indices) {
                            Map<String, String> mappings = getMapping(s, ImmutableSet.copyOf(types));
                            for (String t : mappings.keySet()) {
                                session.write(new ElasticPacket(s, t, "_mapping", mappings.get(t)));
                            }
                        }

                        // export document _source fields
                        SearchRequestBuilder searchRequest = client.prepareSearch()
                                .setSize(size)
                                .setSearchType(SearchType.SCAN)
                                .setScroll(TimeValue.timeValueMillis(millis));
                        if (indices != null) {
                            searchRequest.setIndices(indices);
                        }
                        if (types != null) {
                            searchRequest.setTypes(types);
                        }
                        SearchResponse searchResponse = searchRequest.execute().actionGet();

                        while (true) {
                            searchResponse = client.prepareSearchScroll(searchResponse.getScrollId())
                                    .setScroll(TimeValue.timeValueMillis(millis))
                                    .execute().actionGet();
                            for (SearchHit hit : searchResponse.hits()) {
                                ElasticPacket packet =
                                        new ElasticPacket(hit.getIndex(), hit.getType(), hit.getId(), hit.getSourceAsString());
                                session.write(packet);
                            }
                            if (searchResponse.hits().hits().length == 0) {
                                break;
                            }
                        }

                        session.close();
                        connection.close();

                        logger.info("export to {} completed", target);

                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    } finally {
                        //client.admin().indices().prepareOpen(index).execute().actionGet();
                    }
                }
            }.start();

        } catch (IOException ex) {
            try {
                channel.sendResponse(new XContentThrowableRestResponse(request, ex));
            } catch (Exception ex2) {
                logger.error(ex2.getMessage(), ex2);
            }
        }
    }

    private String getSettings(String index) throws IOException {
        final XContentBuilder builder = jsonBuilder();
        ClusterStateResponse response = client.admin().cluster().prepareState()
                .setFilterRoutingTable(true)
                .setFilterNodes(true)
                .setFilterIndices(index)
                .execute().actionGet();
        MetaData metaData = response.state().metaData();
        if (!metaData.indices().isEmpty()) {
            builder.startObject();
            for (IndexMetaData indexMetaData : metaData) {
                builder.startObject(indexMetaData.index(), XContentBuilder.FieldCaseConversion.NONE);
                builder.startObject("settings");
                for (Map.Entry<String, String> entry :
                        settingsFilter.filterSettings(indexMetaData.settings()).getAsMap().entrySet()) {
                    builder.field(entry.getKey(), entry.getValue());
                }
                builder.endObject();
                builder.endObject();
            }
            builder.endObject();
        }
        return builder.string();
    }

    private Map<String, String> getMapping(String index, Set<String> types) throws IOException {
        Map<String, String> mappings = new HashMap();
        ClusterStateResponse response = client.admin().cluster().prepareState()
                .setFilterRoutingTable(true)
                .setFilterNodes(true)
                .setFilterIndices(index)
                .execute().actionGet();
        MetaData metaData = response.state().metaData();
        if (!metaData.indices().isEmpty()) {
            IndexMetaData indexMetaData = metaData.iterator().next();
            for (MappingMetaData mappingMd : indexMetaData.mappings().values()) {
                if (types == null || types.isEmpty() || types.contains(mappingMd.type())) {
                    final XContentBuilder builder = jsonBuilder();
                    builder.startObject();
                    builder.field(mappingMd.type());
                    builder.map(mappingMd.sourceAsMap());
                    builder.endObject();
                    mappings.put(mappingMd.type(), builder.string());
                }
            }
        }
        return mappings;
    }

    private class ElasticPacket implements Packet<String> {

        String name;
        String packet;

        ElasticPacket(String index, String type, String id, String packet) {
            this.name = index + "/" + type + (id != null ? "/" + id : "");
            this.packet = packet;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getNumber() {
            return null; // not numbered at all
        }

        @Override
        public String getPacket() {
            return packet;
        }

        @Override
        public String toString() {
            return packet;
        }
    }
}