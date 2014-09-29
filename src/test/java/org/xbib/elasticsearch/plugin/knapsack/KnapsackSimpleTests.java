package org.xbib.elasticsearch.plugin.knapsack;

import org.junit.Test;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportResponse;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportResponse;
import org.xbib.elasticsearch.plugin.helper.AbstractNodeTestHelper;

public class KnapsackSimpleTests extends AbstractNodeTestHelper {

    @Test
    public void testSimpleEmptyExport() throws Exception {
        KnapsackExportRequestBuilder requestBuilder =
                new KnapsackExportRequestBuilder(client("1").admin().indices());
        KnapsackExportResponse knapsackExportResponse = requestBuilder.execute().actionGet();
        // delete for other test
        knapsackExportResponse.getState().getPath().toFile().delete();
    }

    @Test
    public void testSimpleEmptyImport() throws Exception {
        KnapsackExportRequestBuilder knapsackExportRequestBuilder =
                new KnapsackExportRequestBuilder(client("1").admin().indices());
        knapsackExportRequestBuilder.execute().actionGet();
        Thread.sleep(1000L);
        KnapsackImportRequestBuilder knapsackImportRequestBuilder =
                new KnapsackImportRequestBuilder(client("1").admin().indices());
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        // delete for other test
        knapsackImportResponse.getState().getPath().toFile().delete();
    }
}
