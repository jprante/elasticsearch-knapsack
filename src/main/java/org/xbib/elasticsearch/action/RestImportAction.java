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
package org.xbib.elasticsearch.action;

import static org.elasticsearch.client.Requests.putMappingRequest;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.action.support.RestXContentBuilder.restContentBuilder;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.XContentRestResponse;
import org.elasticsearch.rest.XContentThrowableRestResponse;
import org.xbib.io.BulkOperation;
import org.xbib.io.Connection;
import org.xbib.io.ConnectionFactory;
import org.xbib.io.ConnectionService;
import org.xbib.io.Packet;
import org.xbib.io.Session;
import org.xbib.io.StreamCodecService;

public class RestImportAction extends BaseRestHandler {

    private final static ConnectionService service = ConnectionService.getInstance();
    private final Map<String, String> indices = new HashMap();
    private final Map<String, String> mappings = new HashMap();
    private final Set<String> created = new HashSet();

    @Inject
    public RestImportAction(Settings settings, Client client,
            RestController controller) {
        super(settings, client);

        controller.registerHandler(POST, "/_import", this);
        controller.registerHandler(POST, "/{index}/_import", this);
        controller.registerHandler(POST, "/{index}/{type}/_import", this);
    }

    @Override
    public void handleRequest(final RestRequest request, RestChannel channel) {
        final Map<String, String> params = request.params();
        final String newIndex = request.param("index", "_all");
        final String newType = request.param("type");
        final int size = request.paramAsInt("bulk_size", 100);
        final int maxActiveBulkRequest = request.paramAsInt("max_active_bulks", 10);
        final String desc = newIndex + (newType != null ? "_" + newType : "");
        try {
            XContentBuilder builder = restContentBuilder(request)
                    .startObject()
                    .field("ok", true)
                    .endObject();
            channel.sendResponse(new XContentRestResponse(request, OK, builder));

            EsExecutors.daemonThreadFactory(settings, "Knapsack import [" + desc + "]")
                    .newThread(new Thread() {
                @Override
                public void run() {
                    final String target = request.param("target", desc);
                    String scheme = request.param("scheme", "targz");
                    for (String codec : StreamCodecService.getCodecs()) {
                        if (target.endsWith(codec)) {
                            scheme = "tar" + codec;
                        }
                    }

                    BulkOperation op = new BulkOperation(client, logger)
                            .setBulkSize(size)
                            .setMaxActiveRequests(maxActiveBulkRequest);

                    try {
                        logger.info("cluster 'yellow' check before import of {}", target);

                        ClusterHealthResponse healthResponse =
                                client.admin().cluster().prepareHealth().setWaitForYellowStatus()
                                .setTimeout("30s").execute().actionGet(30000);

                        if (healthResponse.isTimedOut()) {
                            throw new IOException("cluster not healthy, cowardly refusing to continue with export");
                        }

                        ConnectionFactory factory = service.getConnectionFactory(scheme);
                        Connection<Session> connection = factory.getConnection(URI.create(scheme + ":" + target));
                        Session session = connection.createSession();
                        session.open(Session.Mode.READ);

                        logger.info("starting import of {}", target);

                        Packet<String> packet;
                        while ((packet = session.read()) != null) {
                            String[] entry = packet.getName().split("/");
                            String index = entry.length > 0 ? entry[0] : null;
                            String type = entry.length > 1 ? entry[1] : null;
                            String id = entry.length > 2 ? entry[2] : null;
                            // re-map to new index/type
                            if (newIndex != null && !newIndex.equals("_all")) {
                                index = newIndex;
                            }
                            if (newType != null) {
                                type = newType;
                            }
                            if (entry.length == 2) {
                                if ("_settings".equals(entry[1])) {
                                    String settings;
                                    if (params.containsKey(index + "_settings")) {
                                        InputStreamReader reader = new InputStreamReader(new FileInputStream(params.get(index + "_settings")), "UTF-8");
                                        settings = Streams.copyToString(reader);
                                        reader.close();
                                    } else {
                                        settings = packet.getPacket();
                                    }
                                    indices.put(index, settings);
                                    if (!createIndex(index)) {
                                        throw new IOException("unable to create index '" + index + "' with settings " + settings);
                                    }
                                }
                            } else if (entry.length == 3) {
                                String mapping;
                                if ("_mapping".equals(entry[2])) {
                                    if (params.containsKey(index + "_" + type + "_mapping")) {
                                        InputStreamReader reader = new InputStreamReader(new FileInputStream(params.get(index + "_" + type + "_mapping")), "UTF-8");
                                        mapping = Streams.copyToString(reader);
                                        reader.close();                                        
                                    } else {
                                        mapping = packet.getPacket();
                                    }
                                    mappings.put(index + "/" + type, mapping);
                                    if (!createMapping(index, type)) {
                                        throw new IOException("unable to create index type '" + index + "/" + type + "' with mapping " + mapping);
                                    }
                                } else {
                                    // index document
                                    op.index(index, type, id, packet.getPacket());
                                }
                            }
                        }

                        logger.info("import of {} completed", target);

                        session.close();
                        connection.close();

                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    } finally {
                        try {
                            op.flush();

                        } catch (IOException ex) {
                            logger.error(ex.getMessage(), ex);
                        }
                    }
                }
            }).start();

        } catch (IOException ex) {
            try {
                logger.error(ex.getMessage(), ex);
                channel.sendResponse(new XContentThrowableRestResponse(request, ex));
            } catch (Exception ex2) {
                logger.error(ex2.getMessage(), ex2);
            }
        }
    }

    private boolean createIndex(String index) {
        if ("_all".equals(index)) {
            return true;
        }
        if (created.contains(index)) {
            return true;
        }
        String s = indices.get(index);
        if (s == null) {
            s = indices.get("_all");
        }
        created.add(index);
        logger.info("creating index {} from import", index);
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(index);
        createIndexRequest.source(s);
        CreateIndexResponse response = client.admin().indices().create(createIndexRequest).actionGet();
        return response.isAcknowledged();
    }

    private boolean createMapping(String index, String type) throws IOException {
        if ("_all".equals(index)) {
            return true;
        }
        if (!createIndex(index)) {
            throw new IOException("unable to create index " + index);
        }
        String desc = index + "/" + type;
        if (created.contains(desc)) {
            return true;
        }
        String m = mappings.get(desc);
        if (m == null) {
            m = mappings.get("_all/" + type);
        }
        created.add(desc);
        logger.info("creating mapping {} from import", desc);
        PutMappingRequest putMappingRequest = putMappingRequest(index);
        putMappingRequest.listenerThreaded(false);
        putMappingRequest.type(type);
        putMappingRequest.source(m);
        putMappingRequest.timeout(timeValueSeconds(10));
        PutMappingResponse response = client.admin().indices().putMapping(putMappingRequest).actionGet();
        return response.isAcknowledged();
    }
}