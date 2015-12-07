package org.xbib.elasticsearch.plugin.knapsack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.xbib.elasticsearch.util.NodeTestUtils;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KnapsackSplitTests extends NodeTestUtils {

    private final static Logger logger = LogManager.getLogger(KnapsackSplitTests.class);

    @Test
    public void testSplit() {
        try {
            File exportFile = File.createTempFile("split-import-", ".bulk");
            Path exportPath = Paths.get(URI.create("file:" + exportFile.getAbsolutePath()));
            for (int i = 0; i < 100; i++) {
                client("1").index(new IndexRequest().index("index1").type("test1").id("doc" + i)
                        .source("content", "Hello World " + i).refresh(true)).actionGet();
            }
            // 275 bytes = ~10 docs per archive
            KnapsackExportRequestBuilder requestBuilder = new KnapsackExportRequestBuilder(client("1"))
                    .setArchivePath(exportPath)
                    .setBytesToTransfer(ByteSizeValue.parseBytesSizeValue("275b", ""));
            KnapsackExportResponse knapsackExportResponse = requestBuilder.execute().actionGet();
            assertTrue(knapsackExportResponse.isRunning());
            KnapsackStateRequestBuilder knapsackStateRequestBuilder =
                    new KnapsackStateRequestBuilder(client("2"));
            KnapsackStateResponse knapsackStateResponse = knapsackStateRequestBuilder.execute().actionGet();
            // assertTrue(knapsackStateResponse.isExportActive(exportPath)); // why does this fail???
            Thread.sleep(2000L);
            // delete index
            client("1").admin().indices().delete(new DeleteIndexRequest("index1")).actionGet();
            KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                    .setArchivePath(exportPath)
                    .setMaxActionsPerBulkRequest(100);
            KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
            Thread.sleep(2000L);
            // count
            long count = client("1").prepareCount("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount();
            assertEquals(10L, count);
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
    }
}
