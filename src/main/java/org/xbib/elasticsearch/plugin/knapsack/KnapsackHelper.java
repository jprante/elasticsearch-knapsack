
package org.xbib.elasticsearch.plugin.knapsack;

import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.collect.ImmutableList.Builder;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.env.Environment;
import org.xbib.classloader.uri.URIClassLoader;
import org.xbib.io.URIUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS;
import static org.elasticsearch.common.xcontent.XContentFactory.xContent;
import static org.elasticsearch.common.xcontent.XContentParser.Token.END_ARRAY;
import static org.elasticsearch.common.xcontent.XContentType.JSON;

public class KnapsackHelper {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackHelper.class.getSimpleName());

    public static final String EXPORT_STATE_SETTING_NAME = "plugin.knapsack.export.state";

    public static final String IMPORT_STATE_SETTING_NAME = "plugin.knapsack.import.state";

    private final Client client;

    private final ClusterService clusterService;

    public KnapsackHelper(Client client, ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;
    }

    public List<KnapsackStatus> getImports() throws IOException {
        return get(IMPORT_STATE_SETTING_NAME);
    }

    public void addImport(KnapsackStatus newImport) throws IOException {
        add(IMPORT_STATE_SETTING_NAME, getImports(), newImport);
    }

    public void removeImport(KnapsackStatus targetImport) throws IOException {
        remove(IMPORT_STATE_SETTING_NAME, getImports(), targetImport);
    }

    public void updateImport(KnapsackStatus targetImport) throws IOException {
        update(IMPORT_STATE_SETTING_NAME, getImports(), targetImport);
    }

    public List<KnapsackStatus> getExports() throws IOException {
        return get(EXPORT_STATE_SETTING_NAME);
    }

    public void addExport(KnapsackStatus newExport) throws IOException {
        add(EXPORT_STATE_SETTING_NAME, getExports(), newExport);
    }

    public void removeExport(KnapsackStatus targetExport) throws IOException {
        remove(EXPORT_STATE_SETTING_NAME, getExports(), targetExport);
    }

    public void updateExport(KnapsackStatus targetExport) throws IOException {
        update(EXPORT_STATE_SETTING_NAME, getExports(), targetExport);
    }

    private List<KnapsackStatus> get(String name) throws IOException {
        String value = getSetting(name);

        return parseSetting(value);
    }

    private void add(String name, List<KnapsackStatus> values, KnapsackStatus newValue) throws IOException {
        String value = generateSetting(ImmutableList.<KnapsackStatus>builder()
                .addAll(values)
                .add(newValue)
                .build());
        updateSetting(name, value);
    }

    private void remove(String name, List<KnapsackStatus> values, KnapsackStatus targetValue) throws IOException {
        Builder<KnapsackStatus> updatedValues = ImmutableList.builder();
        for (KnapsackStatus value : values) {
            if (!value.equals(targetValue)) {
                updatedValues.add(value);
            }
        }
        String value = generateSetting(updatedValues.build());
        updateSetting(name, value);
    }

    private void update(String name, List<KnapsackStatus> values, KnapsackStatus targetValue) throws IOException {
        Builder<KnapsackStatus> updatedValues = ImmutableList.builder();
        for (KnapsackStatus value : values) {
            if (value.equals(targetValue)) {
                updatedValues.add(targetValue);
            } else {
                updatedValues.add(value);
            }
        }

        String value = generateSetting(updatedValues.build());
        updateSetting(name, value);
    }

    private Settings getSettings() {
        return clusterService.state().getMetaData().transientSettings();
    }

    private String getSetting(String name) {
        return getSettings().get(name, "[]");
    }

    private void updateSetting(String name, String value) {
        client.admin().cluster().prepareUpdateSettings()
                .setTransientSettings(ImmutableSettings.settingsBuilder()
                        .put(getSettings())
                        .put(name, value)
                        .build())
                .execute()
                .actionGet();
    }

    private static List<KnapsackStatus> parseSetting(String value) throws IOException {
        XContentParser parser = xContent(JSON).createParser(value);
        Builder<KnapsackStatus> builder = ImmutableList.builder();
        parser.nextToken();
        while (parser.nextToken() != END_ARRAY) {
            KnapsackStatus status = new KnapsackStatus();
            builder.add(status.fromXContent(parser));
        }

        return builder.build();
    }

    private static String generateSetting(List<KnapsackStatus> values) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startArray();
        for (KnapsackStatus value : values) {
            value.toXContent(builder, EMPTY_PARAMS);
        }
        builder.endArray();
        return builder.string();
    }

    public static URI getAddress(Client client, ClusterName clusterName, String host, int port, String cluster) {
        NodesInfoResponse response = client.admin().cluster()
                .nodesInfo(new NodesInfoRequest().transport(true)).actionGet();
        InetSocketTransportAddress address = (InetSocketTransportAddress)response.iterator().next()
                .getTransport().getAddress().publishAddress();
        if (host == null) {
            host = address.address().getAddress().getHostAddress();
        }
        if (port == 0) {
            port = address.address().getPort();
        }
        if (cluster == null) {
            cluster = clusterName.value();
        }
        return URI.create("es://" + host + ":" + port + "?es.cluster.name=" + cluster);
    }

    public static Settings clientSettings(Environment environment, URI uri) {
        return settingsBuilder()
                .put("network.server", false)
                .put("node.client", true)
                .put("cluster.name", URIUtil.parseQueryString(uri).get("es.cluster.name"))
                .put("client.transport.sniff", false)
                .put("client.transport.ignore_cluster_name", false)
                .put("client.transport.ping_timeout", "30s")
                .put("client.transport.nodes_sampler_interval", "30s")
                .put("path.plugins", ".dontexist") // this disables site plugins
                .classLoader(getClassLoader(environment)) // this disables jvm plugins
                .build();
    }

    /**
     * Filter out all jvm plugins
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