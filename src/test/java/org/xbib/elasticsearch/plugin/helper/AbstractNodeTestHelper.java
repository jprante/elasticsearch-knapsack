package org.xbib.elasticsearch.plugin.helper;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.network.NetworkUtils;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.node.Node;
import org.junit.After;
import org.junit.Before;

import java.util.Map;

import static org.elasticsearch.common.collect.Maps.newHashMap;
import static org.elasticsearch.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS;
import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

public abstract class AbstractNodeTestHelper {

    protected final static ESLogger logger = ESLoggerFactory.getLogger("test");

    protected final String CLUSTER = "test-cluster-" + NetworkUtils.getLocalAddress().getHostName();

    protected Settings defaultSettings = ImmutableSettings
            .settingsBuilder()
            .put("cluster.name", CLUSTER)
            .build();

    private Map<String, Node> nodes = newHashMap();

    private Map<String, Client> clients = newHashMap();

    private Map<String, InetSocketTransportAddress> addresses = newHashMap();

    @Before
    public void setUp() throws Exception {
        startNode("1");
        startNode("2"); // two nodes, for testing state
    }

    @After
    public void closeIndices() {
        closeAllNodes();
    }

    public Node startNode(String id) {
        return buildNode(id).start();
    }

    public Node buildNode(String id) {
        return buildNode(id, EMPTY_SETTINGS);
    }

    public Node buildNode(String id, Settings settings) {
        String settingsSource = getClass().getName().replace('.', '/') + ".yml";
        Settings finalSettings = settingsBuilder()
                .loadFromClasspath(settingsSource)
                .put(defaultSettings)
                .put(settings)
                .put("name", id)
                .put("gateway.type", "none")
                .put("cluster.routing.schedule", "50ms")
                .put("index.store.type", "memory")
                .put("http.enabled", false)
                .put("discovery.zen.multicast.enabled", false)
                .build();
        Node node = nodeBuilder().local(false).settings(finalSettings).build();
        Client client = node.client();
        nodes.put(id, node);
        clients.put(id, client);
        return node;
    }

    public Client client(String id) {
        return clients.get(id);
    }

    public void closeAllNodes() {
        for (Client client : clients.values()) {
            client.close();
        }
        clients.clear();
        for (Node node : nodes.values()) {
            if (node != null) {
                node.close();
            }
        }
        nodes.clear();
    }

}
