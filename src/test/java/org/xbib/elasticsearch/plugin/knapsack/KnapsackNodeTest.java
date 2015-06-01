package org.xbib.elasticsearch.plugin.knapsack;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.network.NetworkUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.junit.Test;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportResponse;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportResponse;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

public class KnapsackNodeTest  {

    @Test
    public void testNodeExport() throws Exception {
        Settings settings = settingsBuilder()
                .put("cluster.name", "test-cluster-" + NetworkUtils.getLocalAddress().getHostName())
                .put("gateway.type", "none")
                .put("index.store.type", "memory")
                .put("http.enabled", false)
                .put("discovery.zen.multicast.enabled", false)
                .build();
        Node node = nodeBuilder().settings(settings).build();
        node.start();
        Client client = node.client();

        // cluster healthy?
        client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();

        // dummy export
        KnapsackExportRequestBuilder knapsackExportRequestBuilder =
                new KnapsackExportRequestBuilder(client.admin().indices());
        KnapsackExportResponse knapsackExportResponse =
                knapsackExportRequestBuilder.execute().actionGet();

        Thread.sleep(1000L);

        // dummy import
        KnapsackImportRequestBuilder knapsackImportRequestBuilder =
                new KnapsackImportRequestBuilder(client.admin().indices());
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();

        // delete _all.tar.gz for next test
        knapsackImportResponse.getState().getPath().toFile().delete();

        node.stop();
    }

}
