package org.xbib.elasticsearch.plugin.knapsack;

import org.junit.Test;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportResponse;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportResponse;
import org.xbib.elasticsearch.util.NodeTestUtils;

public class KnapsackSimpleTests extends NodeTestUtils {

    @Test
    public void testSimpleEmptyExport() throws Exception {
        KnapsackExportRequestBuilder requestBuilder =
                new KnapsackExportRequestBuilder(client("1"));
        KnapsackExportResponse knapsackExportResponse = requestBuilder.execute().actionGet();
        Thread.sleep(2000L);
        // delete for other test
        knapsackExportResponse.getState().getPath().toFile().delete();
    }

    @Test
    public void testSimpleEmptyImport() throws Exception {
        KnapsackExportRequestBuilder knapsackExportRequestBuilder =
                new KnapsackExportRequestBuilder(client("1"));
        knapsackExportRequestBuilder.execute().actionGet();
        Thread.sleep(2000L);
        KnapsackImportRequestBuilder knapsackImportRequestBuilder =
                new KnapsackImportRequestBuilder(client("1"));
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        Thread.sleep(2000L);
        // delete for other test
        knapsackImportResponse.getState().getPath().toFile().delete();
    }
}
