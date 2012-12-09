/*
 * Licensed to Jörg Prante and xbib under one or more contributor 
 * license agreements. See the NOTICE.txt file distributed with this work
 * for additional information regarding copyright ownership.
 *
 * Copyright (C) 2012 Jörg Prante and xbib
 * 
 * This program is free software; you can redistribute it and/or modify 
 * it under the terms of the GNU Affero General Public License as published 
 * by the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the 
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License 
 * along with this program; if not, see http://www.gnu.org/licenses 
 * or write to the Free Software Foundation, Inc., 51 Franklin Street, 
 * Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * The interactive user interfaces in modified source and object code 
 * versions of this program must display Appropriate Legal Notices, 
 * as required under Section 5 of the GNU Affero General Public License.
 * 
 * In accordance with Section 7(b) of the GNU Affero General Public 
 * License, these Appropriate Legal Notices must retain the display of the 
 * "Powered by xbib" logo. If the display of the logo is not reasonably 
 * feasible for technical reasons, the Appropriate Legal Notices must display
 * the words "Powered by xbib".
 */
package org.elasticsearch.rest.action;

import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.action.support.RestXContentBuilder.restContentBuilder;

import java.io.IOException;
import java.net.URI;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.XContentRestResponse;
import org.elasticsearch.rest.XContentThrowableRestResponse;
import org.elasticsearch.search.SearchHit;
import org.xbib.io.Connection;
import org.xbib.io.ConnectionFactory;
import org.xbib.io.ConnectionService;
import org.xbib.io.Packet;
import org.xbib.io.Session;

public class RestExportAction extends BaseRestHandler {

    private final static ConnectionService service = ConnectionService.getInstance();

    @Inject
    public RestExportAction(Settings settings, Client client,
            RestController controller) {
        super(settings, client);

        controller.registerHandler(POST, "/{index}/_export", this);
        controller.registerHandler(POST, "/{index}/{type}/_export", this);
    }

    @Override
    public void handleRequest(final RestRequest request, RestChannel channel) {
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
                    String desc = index + (type != null ? "_" + type : "");
                    setName("[Exporter Thread " + desc + "]");
                    long millis = request.paramAsLong("millis", 30000L);
                    int size = request.paramAsInt("size", 1000);
                    final String scheme = request.param("scheme", "targz");
                    final String target = request.param("target", desc);

                    try {
                        logger.info("cluster 'yellow' check before export of {}", target);

                        ClusterHealthResponse healthResponse =
                                client.admin().cluster().prepareHealth().setWaitForYellowStatus()
                                .setTimeout("30s").execute().actionGet(30000);

                        if (healthResponse.isTimedOut()) {
                            throw new IOException("cluster not healthy, cowardly refusing to continue with export");
                        }

                       // client.admin().indices().prepareClose(index).execute().actionGet();

                        logger.info("starting export of {}", target);

                        ConnectionFactory factory = service.getConnectionFactory(scheme);
                        Connection<Session> connection = factory.getConnection(URI.create(scheme + ":" + target));
                        Session session = connection.createSession();
                        session.open(Session.Mode.WRITE);
                        
                        SearchRequestBuilder searchRequest = client.prepareSearch()
                                .setIndices(index)
                                .setSize(size)
                                .setSearchType(SearchType.SCAN)
                                .setScroll(TimeValue.timeValueMillis(millis));
                        if (type != null) {
                                searchRequest.setTypes(type);                  
                        }                         
                        SearchResponse searchResponse = searchRequest.execute().actionGet();

                        while (true) {
                            searchResponse = client.prepareSearchScroll(searchResponse.getScrollId())
                                    .setScroll(TimeValue.timeValueMillis(millis))
                                    .execute().actionGet();
                            for (SearchHit hit : searchResponse.hits()) {
                                SearchHitPacket packet =
                                        new SearchHitPacket(hit.getIndex(), hit.getType(), hit.getId(), hit.getSourceAsString());
                                session.write(packet);
                            }
                            if (searchResponse.hits().hits().length == 0) {
                                break;
                            }
                        }
                        
                        session.close();
                        connection.close();

                        logger.info("export of {} completed", target);

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

    private class SearchHitPacket implements Packet<String> {

        String name;
        String packet;

        SearchHitPacket(String index, String type, String id, String packet) {
            this.name = index + "/" + type + "/" + id;
            this.packet = packet;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getNumber() {
            return null;
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