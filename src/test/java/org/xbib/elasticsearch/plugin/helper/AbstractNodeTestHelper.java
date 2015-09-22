package org.xbib.elasticsearch.plugin.helper;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.client.support.AbstractClient;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.node.Node;
import org.junit.After;
import org.junit.Before;
import org.xbib.elasticsearch.plugin.knapsack.KnapsackPlugin;
import org.xbib.elasticsearch.support.client.ClientHelper;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.elasticsearch.common.settings.Settings.settingsBuilder;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

public abstract class AbstractNodeTestHelper {

    private final static ESLogger logger = ESLoggerFactory.getLogger("test");

    private Map<String, Node> nodes = new HashMap<>();

    private Map<String, AbstractClient> clients = new HashMap<>();

    private AtomicInteger counter = new AtomicInteger();

    private String cluster;

    private String host;

    private int port;

    protected void setClusterName() {
        this.cluster = "test-aggregations-cluster-"
                + getLocalAddress().getHostName()
                + "-" + System.getProperty("user.name")
                + "-" + counter.incrementAndGet();
    }

    protected String getClusterName() {
        return cluster;
    }

    protected String getHome() {
        return System.getProperty("path.home");
    }

    protected Settings getNodeSettings() {
        return settingsBuilder()
                .put("cluster.name", cluster)
                .put("path.home", getHome())
                .put("http.enabled", false)
                .put("index.number_of_replicas", 0)
                .put("plugin.types", KnapsackPlugin.class.getName())
                .build();
    }

    @Before
    public void startNodes() {
        try {
            setClusterName();
            startNode("1");
            startNode("2");
            findNodeAddress();
            ClientHelper.waitForCluster(client("1"), ClusterHealthStatus.GREEN, TimeValue.timeValueSeconds(30));
        } catch (Throwable t) {
            logger.error("startNodes failed", t);
        }
    }

    @After
    public void stopNodes() {
        logger.info("stopping nodes");
        try {
            //ClientHelper.waitForCluster(client("1"), ClusterHealthStatus.YELLOW, TimeValue.timeValueSeconds(30));
            //logger.info("waiting for recovery");
            //ClientHelper.waitForRecovery(client("1"));
            //logger.info("recovered");
            //client("1").admin().indices().prepareDelete("_all").execute().actionGet();
        } catch (Throwable e) {
            logger.error("stop failed", e);
        } finally {
            logger.info("closing nodes");
            try {
                closeAllNodes();
            } catch (Throwable t) {

            }
        }
    }

    protected Node startNode(String id) {
        return buildNode(id).start();
    }

    public AbstractClient client(String id) {
        return clients.get(id);
    }

    private Node buildNode(String id) {
        Settings finalSettings = settingsBuilder()
                .put(getNodeSettings())
                .put("name", id)
                .build();
        Node node = nodeBuilder().settings(finalSettings).local(true).build();
        AbstractClient client = (AbstractClient)node.client();
        nodes.put(id, node);
        clients.put(id, client);
        return node;
    }

    protected void findNodeAddress() {
        NodesInfoRequest nodesInfoRequest = new NodesInfoRequest().transport(true);
        NodesInfoResponse response = client("1").admin().cluster().nodesInfo(nodesInfoRequest).actionGet();
        Object obj = response.iterator().next().getTransport().getAddress()
                .publishAddress();
        if (obj instanceof InetSocketTransportAddress) {
            InetSocketTransportAddress address = (InetSocketTransportAddress) obj;
            host = address.address().getHostName();
            port = address.address().getPort();
        }
    }

    public void closeAllNodes() throws IOException {
        for (AbstractClient client : clients.values()) {
            client.close();
        }
        clients.clear();
        for (Node node : nodes.values()) {
            if (node != null) {
                node.close();
            }
        }
        nodes.clear();
        deleteFiles();
    }

    private void deleteFiles() throws IOException {
        Path directory = Paths.get(getHome() + "/data");
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }

        });

    }

    private final static InetAddress localAddress;

    static {
        InetAddress address;
        try {
            address = InetAddress.getLocalHost();
        } catch (Throwable e) {
            address = InetAddress.getLoopbackAddress();
        }
        localAddress = address;
    }

    public static InetAddress getLocalAddress() {
        return localAddress;
    }

}
