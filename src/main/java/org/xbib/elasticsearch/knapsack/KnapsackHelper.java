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

import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequestBuilder;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.hppc.cursors.ObjectCursor;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.env.Environment;
import org.xbib.classloader.uri.URIClassLoader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.common.collect.Maps.newHashMap;
import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class KnapsackHelper {

    private KnapsackHelper() {
    }

    public static Map<String, Object> toMap(String s, ESLogger logger) throws IOException {
        Map<String, Object> map = s == null ? new HashMap<String,Object>()
                : XContentFactory.xContent(XContentType.JSON).createParser(s).mapAndClose();
        if (map.isEmpty() && s != null && s.length() > 0) {
            logger.warn("can not parse map, check URI escape, got param: {}", s);
        }
        return map;
    }

    public static Map<String, String> getSettings(Client client, SettingsFilter settingsFilter, String... index) throws IOException {
        Map<String, String> settings = newHashMap();
        ClusterStateRequestBuilder request = client.admin().cluster().prepareState()
                .setFilterIndices(index);
        ClusterStateResponse response = request.execute().actionGet();
        MetaData metaData = response.getState().metaData();
        if (!metaData.getIndices().isEmpty()) {
            // filter out the settings from the metadata
            for (IndexMetaData indexMetaData : metaData) {
                final XContentBuilder builder = jsonBuilder();
                builder.startObject();
                for (Map.Entry<String, String> entry :
                        settingsFilter.filterSettings(indexMetaData.getSettings()).getAsMap().entrySet()) {
                    builder.field(entry.getKey(), entry.getValue());
                }
                builder.endObject();
                settings.put(indexMetaData.getIndex(), builder.string());
            }
        }
        return settings;
    }

    public static Map<String, String> getMapping(Client client, String index, Set<String> types) throws IOException {
        Map<String, String> mappings = newHashMap();
        ClusterStateRequestBuilder request = client.admin().cluster().prepareState();
        if (!"_all".equals(index)) {
            request.setFilterIndices(index);
        }
        ClusterStateResponse response = request.execute().actionGet();
        MetaData metaData = response.getState().getMetaData();
        if (!metaData.getIndices().isEmpty()) {
            for (IndexMetaData indexMetaData : metaData) {
                for (ObjectCursor<MappingMetaData> c : indexMetaData.getMappings().values()) {
                    MappingMetaData mappingMetaData = c.value;
                    if (types == null || types.isEmpty() || types.contains(mappingMetaData.type())) {
                        final XContentBuilder builder = jsonBuilder();
                        builder.startObject();
                        builder.field(mappingMetaData.type());
                        builder.map(mappingMetaData.sourceAsMap());
                        builder.endObject();
                        mappings.put(mappingMetaData.type(), builder.string());
                    }
                }
            }
        }
        return mappings;
    }

    public static Map<String,String> getAliases(Client client, String index) throws IOException {
        Map<String,String> aliases = newHashMap();
        ClusterStateRequestBuilder request = client.admin().cluster().prepareState();
        if (!"_all".equals(index)) {
            request.setFilterIndices(index);
        }
        ClusterStateResponse response = request.execute().actionGet();
        MetaData metaData = response.getState().getMetaData();
        if (!metaData.getIndices().isEmpty()) {
            for (IndexMetaData indexMetaData : metaData) {
                for (ObjectCursor<AliasMetaData> c : indexMetaData.getAliases().values()) {
                    AliasMetaData aliasMetaData = c.value;
                    String alias = aliasMetaData.getAlias();
                    if (aliasMetaData.getFilter() != null) {
                        aliases.put(alias, new String(aliasMetaData.getFilter().uncompressed()));
                    } else {
                        aliases.put(alias, ""); // empty string = no filter
                    }
                }
            }
        }
        return aliases;
    }

    public static String mapIndex(KnapsackRequest request, String index) {
        return request.getIndexTypeNames().containsKey(index) ? request.getIndexTypeNames().get(index).toString() : index;
    }

    public static String mapType(KnapsackRequest request, String index, String type) {
        String s = index + "/" + type;
        return request.getIndexTypeNames().containsKey(s) ? request.getIndexTypeNames().get(s).toString() : type;
    }

    public static Settings clientSettings(Client client, Environment environment, KnapsackRequest request) {
        String cluster = request.getCluster();
        String host = request.getHost();
        int port = request.getPort();
        if (host == null) {
            NodesInfoResponse response = client.admin().cluster()
                    .nodesInfo(new NodesInfoRequest().transport(true)).actionGet();
            InetSocketTransportAddress address = (InetSocketTransportAddress)response.iterator().next()
                    .getTransport().getAddress().publishAddress();
            host = address.address().getAddress().getHostAddress();
            port = address.address().getPort();
            if (cluster == null) {
                cluster = response.getClusterName().value();
            }
        }
        if (cluster == null) {
            NodesInfoResponse response = client.admin().cluster()
                    .nodesInfo(new NodesInfoRequest().transport(true)).actionGet();
            cluster = response.getClusterName().value();
        }
        return settingsBuilder()
                .put("host", host)
                .put("port", port)
                .put("cluster.name", cluster)
                .put("timeout", request.getTimeout()) // our timeout for instantiating TransportClient
                .put("client.transport.sniff", request.getSniff()) // sniff = look for other nodes
                .put("client.transport.ping_timeout", request.getTimeout()) // timeout for the transport connection
                .put("client.transport.ignore_cluster_name", true) // we want to connect to other clusters, not ours
                .put("path.plugins", ".dontexist") // this disables site plugins when instantiating TransportClient
                .classLoader(getClassLoader(environment)) // this disables all jvm plugins when instantiating TransportClient
                .build();
    }

    /**
     * We have to add Elasticsearch to our classpath, but not the jvm plugins
     * for starting our TransportClient.
     *
     * @param environment the environment
     * @return a custom class loader with our dependencies
     */
    private static ClassLoader getClassLoader(Environment environment) {
        URIClassLoader classLoader = new URIClassLoader();
        File[] libs = new File(environment.homeFile() + "/lib").listFiles();
        if (libs != null) {
            for (File file : libs) {
                if (file.getName().toLowerCase().endsWith(".jar")) {
                    classLoader.addURI(file.toURI());
                }
            }
        }
        return classLoader;
    }
}
