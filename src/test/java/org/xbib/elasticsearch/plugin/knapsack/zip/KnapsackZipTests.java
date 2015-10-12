package org.xbib.elasticsearch.plugin.knapsack.zip;

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
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

public class KnapsackZipTests extends AbstractNodeTestHelper {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackZipTests.class.getName());

    @Test
    public void testZip() throws Exception {
        File exportFile = File.createTempFile("knapsack-zip-", ".zip");
        Path exportPath = Paths.get(URI.create("file:" + exportFile.getAbsolutePath()));
        client("1").index(new IndexRequest().index("index1").type("test1").id("doc1").source("content","Hello World").refresh(true)).actionGet();
        KnapsackExportRequestBuilder requestBuilder = new KnapsackExportRequestBuilder(client("1"))
                .setArchivePath(exportPath)
                .setOverwriteAllowed(true);
        KnapsackExportResponse knapsackExportResponse = requestBuilder.execute().actionGet();
        if (!knapsackExportResponse.isRunning()) {
            logger.error(knapsackExportResponse.getReason());
        }
        assertTrue(knapsackExportResponse.isRunning());
        KnapsackStateRequestBuilder knapsackStateRequestBuilder =
               new KnapsackStateRequestBuilder(client("2"));
        KnapsackStateResponse knapsackStateResponse = knapsackStateRequestBuilder.execute().actionGet();
        knapsackStateResponse.isExportActive(exportPath);
        Thread.sleep(1000L);
        // delete index
        client("1").admin().indices().delete(new DeleteIndexRequest("index1")).actionGet();
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                .setArchivePath(exportPath);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        if (!knapsackImportResponse.isRunning()) {
            logger.error(knapsackImportResponse.getReason());
        }
        assertTrue(knapsackImportResponse.isRunning());
        Thread.sleep(1000L);
        // count
        long count = client("1").prepareCount("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount();
        assertEquals(1L, count);
        knapsackImportResponse.getState().getPath().toFile().delete();
    }

}
