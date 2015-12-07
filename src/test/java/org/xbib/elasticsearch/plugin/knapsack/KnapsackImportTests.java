package org.xbib.elasticsearch.plugin.knapsack;

import org.elasticsearch.action.get.GetResponse;
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
import org.xbib.elasticsearch.util.NodeTestUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KnapsackImportTests extends NodeTestUtils {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackImportTests.class.getName());

    @Test
    public void testMinimalImport() throws Exception {
        File exportFile = File.createTempFile("minimal-import-", ".bulk");
        Path exportPath = Paths.get(URI.create("file:" + exportFile.getAbsolutePath()));
        client("1").index(new IndexRequest().index("index1").type("test1").id("doc1")
                .source("content", "Hello Jörg").refresh(true)).actionGet();
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
        Thread.sleep(2000L);
        BufferedReader reader = new BufferedReader(new FileReader(exportFile));
        assertEquals("{\"index\":{\"_index\":\"index1\",\"_type\":\"test1\",\"_id\":\"doc1\"}", reader.readLine());
        assertEquals("{\"content\":\"Hello Jörg\"}", reader.readLine());
        reader.close();
        // delete index
        client("1").admin().indices().prepareDelete("index1").execute().actionGet();
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                .setArchivePath(exportPath);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        if (!knapsackImportResponse.isRunning()) {
            logger.error(knapsackImportResponse.getReason());
        }
        Thread.sleep(2000L);
        // count
        long count = client("1").prepareCount("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount();
        logger.info("testMinimalImport count={}", count);
        assertEquals(1L, count);
    }

    @Test
    public void testEncodedBulkEntry() throws Exception {
        File exportFile = File.createTempFile("test-encoded-", ".bulk");
        Path exportPath = Paths.get(URI.create("file:" + exportFile.getAbsolutePath()));
        client("1").index(new IndexRequest().index("index1").type("test1").id("https://www.google.de")
                .source("content", "Hello Jörg").refresh(true)).actionGet();
        KnapsackExportRequestBuilder requestBuilder = new KnapsackExportRequestBuilder(client("1"))
                .setArchivePath(exportPath)
                .setOverwriteAllowed(true);
        KnapsackExportResponse knapsackExportResponse = requestBuilder.execute().actionGet();
        if (!knapsackExportResponse.isRunning()) {
            logger.error(knapsackExportResponse.getReason());
        }
        assertTrue(knapsackExportResponse.isRunning());
        Thread.sleep(2000L);
        KnapsackStateRequestBuilder knapsackStateRequestBuilder =
                new KnapsackStateRequestBuilder(client("2"));
        KnapsackStateResponse knapsackStateResponse = knapsackStateRequestBuilder.execute().actionGet();
        assertFalse(knapsackStateResponse.isExportActive(exportPath));
        BufferedReader reader = new BufferedReader(new FileReader(exportFile));
        assertEquals("{\"index\":{\"_index\":\"index1\",\"_type\":\"test1\",\"_id\":\"https://www.google.de\"}", reader.readLine());
        assertEquals("{\"content\":\"Hello Jörg\"}", reader.readLine());
        reader.close();
        // delete index
        client("1").admin().indices().prepareDelete("index1").execute().actionGet();
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                .setArchivePath(exportPath);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        if (!knapsackImportResponse.isRunning()) {
            logger.error(knapsackImportResponse.getReason());
        }
        Thread.sleep(2000L);
        // count
        long count = client("1").prepareCount("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount();
        logger.info("testEncodedEntry count={}", count);
        assertEquals(1L, count);
    }

    @Test
    public void testEncodedTarEntry() throws Exception {
        File exportFile = File.createTempFile("encoded-tar-", ".tar");
        Path exportPath = Paths.get(URI.create("file:" + exportFile.getAbsolutePath()));
        client("1").index(new IndexRequest().index("index1").type("test1").id("https://www.google.de")
                .source("content", "Hello World").refresh(true)).actionGet();
        KnapsackExportRequestBuilder requestBuilder = new KnapsackExportRequestBuilder(client("1"))
                .setArchivePath(exportPath)
                .setOverwriteAllowed(true);
        KnapsackExportResponse knapsackExportResponse = requestBuilder.execute().actionGet();
        if (!knapsackExportResponse.isRunning()) {
            logger.error(knapsackExportResponse.getReason());
        }
        assertTrue(knapsackExportResponse.isRunning());
        Thread.sleep(2000L);
        KnapsackStateRequestBuilder knapsackStateRequestBuilder =
                new KnapsackStateRequestBuilder(client("2"));
        KnapsackStateResponse knapsackStateResponse = knapsackStateRequestBuilder.execute().actionGet();
        assertFalse(knapsackStateResponse.isExportActive(exportPath));
        Thread.sleep(2000L);
        // delete index
        client("1").admin().indices().prepareDelete("index1").execute().actionGet();
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                .setArchivePath(exportPath);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        if (!knapsackImportResponse.isRunning()) {
            logger.error(knapsackImportResponse.getReason());
        }
        Thread.sleep(2000L);
        // get content
        GetResponse getResponse = client("1").prepareGet().setIndex("index1").setType("test1")
                .setId("https://www.google.de").execute().actionGet();
        assertTrue(getResponse.isExists());
    }

    @Test
    public void testEncodedZipEntry() throws Exception {
        File exportFile = File.createTempFile("encoded-zip-", ".zip");
        Path exportPath = Paths.get(URI.create("file:" + exportFile.getAbsolutePath()));
        client("1").index(new IndexRequest().index("index1").type("test1").id("https://www.google.de")
                .source("content", "Hello World").refresh(true)).actionGet();
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
        Thread.sleep(2000L);
        // delete index
        client("1").admin().indices().prepareDelete("index1").execute().actionGet();
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                .setArchivePath(exportPath);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        if (!knapsackImportResponse.isRunning()) {
            logger.error(knapsackImportResponse.getReason());
        }
        Thread.sleep(2000L);
        // get content
        GetResponse getResponse = client("1").prepareGet().setIndex("index1").setType("test1").setId("https://www.google.de").execute().actionGet();
        assertTrue(getResponse.isExists());
    }

}
