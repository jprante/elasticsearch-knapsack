package org.xbib.elasticsearch.plugin.knapsack;

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Test;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportResponse;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportResponse;
import org.xbib.elasticsearch.action.knapsack.state.KnapsackStateRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.state.KnapsackStateResponse;
import org.xbib.elasticsearch.plugin.helper.AbstractNodeTestHelper;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KnapsackSplitTests extends AbstractNodeTestHelper {

    @Test
    public void testSplit() throws Exception {
        File exportFile = File.createTempFile("split-import-", ".bulk");
        Path exportPath = Paths.get(URI.create("file:" + exportFile.getAbsolutePath()));
        for (int i = 0; i < 100; i++) {
            client("1").index(new IndexRequest().index("index1").type("test1").id("doc" + i)
                    .source("content", "Hello World " + i).refresh(true)).actionGet();
        }
        // 275 byes = ~10 docs per archive
        KnapsackExportRequestBuilder requestBuilder = new KnapsackExportRequestBuilder(client("1").admin().indices())
                .setPath(exportPath)
                .setBytesToTransfer(ByteSizeValue.parseBytesSizeValue("275"));
        KnapsackExportResponse knapsackExportResponse = requestBuilder.execute().actionGet();
        assertTrue(knapsackExportResponse.isRunning());
        KnapsackStateRequestBuilder knapsackStateRequestBuilder =
                new KnapsackStateRequestBuilder(client("1").admin().indices());
        KnapsackStateResponse knapsackStateResponse = knapsackStateRequestBuilder.execute().actionGet();
        assertTrue(knapsackStateResponse.isExportActive(exportPath));
        Thread.sleep(1000L);
        // delete index
        client("1").admin().indices().delete(new DeleteIndexRequest("index1")).actionGet();
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1").admin().indices())
                .setPath(exportPath)
                .setMaxActionsPerBulkRequest(100);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        Thread.sleep(1000L);
        // count
        long count = client("1").prepareCount("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount();
        assertEquals(10L, count);
    }
}
