package org.xbib.elasticsearch.plugin.knapsack;

import org.junit.Test;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportResponse;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportResponse;
import org.xbib.elasticsearch.util.NodeTestUtils;

public class KnapsackNodeTest extends NodeTestUtils {

    @Test
    public void testNodeExport() throws Exception {
        client("1").admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
        KnapsackExportRequestBuilder knapsackExportRequestBuilder =
                new KnapsackExportRequestBuilder(client("1"));
        KnapsackExportResponse knapsackExportResponse =
                knapsackExportRequestBuilder.execute().actionGet();
        Thread.sleep(1000L); // instead we could wait for state
        KnapsackImportRequestBuilder knapsackImportRequestBuilder =
                new KnapsackImportRequestBuilder(client("1"));
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        knapsackImportResponse.getState().getPath().toFile().delete();
    }

}
