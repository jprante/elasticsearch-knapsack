package org.xbib.elasticsearch.plugin.knapsack.tar;

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
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
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KnapsackTarTests extends NodeTestUtils {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackTarTests.class.getName());

    @Test
    public void testTar() throws Exception {
        File exportFile = File.createTempFile("knapsack-tar-", ".tar");
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
        Thread.sleep(2000L);
        // delete index
        client("1").admin().indices().delete(new DeleteIndexRequest("index1")).actionGet();
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                .setArchivePath(exportPath);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        if (!knapsackImportResponse.isRunning()) {
            logger.error(knapsackImportResponse.getReason());
        }
        assertTrue(knapsackImportResponse.isRunning());
        Thread.sleep(2000L);
        // count
        long count = client("1").prepareCount("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount();
        assertEquals(1L, count);
    }

    @Test
    public void testTarGz() throws Exception {
        File exportFile = File.createTempFile("knapsack-tar-", ".tar.gz");
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
        Thread.sleep(2000L);
        // delete index
        client("1").admin().indices().delete(new DeleteIndexRequest("index1")).actionGet();
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                .setArchivePath(exportPath);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        if (!knapsackImportResponse.isRunning()) {
            logger.error(knapsackImportResponse.getReason());
        }
        assertTrue(knapsackImportResponse.isRunning());
        Thread.sleep(2000L);
        // count
        long count = client("1").prepareCount("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount();
        assertEquals(1L, count);
    }

    @Test
    public void testTarBz2() throws Exception {
        File exportFile = File.createTempFile("knapsack-tar-", ".tar.bz2");
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
        Thread.sleep(2000L);
        // delete index
        client("1").admin().indices().delete(new DeleteIndexRequest("index1")).actionGet();
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                .setArchivePath(exportPath);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        if (!knapsackImportResponse.isRunning()) {
            logger.error(knapsackImportResponse.getReason());
        }
        assertTrue(knapsackImportResponse.isRunning());
        Thread.sleep(2000L);
        // count
        long count = client("1").prepareCount("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount();
        assertEquals(1L, count);
    }

    @Test
    public void testTarXz() throws Exception {
        File exportFile = File.createTempFile("knapsack-tar-", ".tar.xz");
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
        Thread.sleep(2000L);
        // delete index
        client("1").admin().indices().delete(new DeleteIndexRequest("index1")).actionGet();
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                .setArchivePath(exportPath);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        if (!knapsackImportResponse.isRunning()) {
            logger.error(knapsackImportResponse.getReason());
        }
        assertTrue(knapsackImportResponse.isRunning());
        Thread.sleep(2000L);
        // count
        long count = client("1").prepareCount("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount();
        assertEquals(1L, count);
    }


    /**
     * This test checks if a tar created from Max OS X tar can be processed.
     * @throws Exception
     */
    public void testAlienTar() throws Exception {
        // delete index
        try {
            client("1").admin().indices().delete(new DeleteIndexRequest("index1")).actionGet();
        } catch (Exception e) {
            // ignore
        }
        URL testTar = getClass().getResource("/macosx-knapsack-tar-test.tar.gz");
        Path path = Paths.get(testTar.toURI());
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                .setArchivePath(path);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        if (!knapsackImportResponse.isRunning()) {
            logger.error(knapsackImportResponse.getReason());
        }
        assertTrue(knapsackImportResponse.isRunning());
        Thread.sleep(2000L);
        // count
        long count = client("1").prepareCount("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().getCount();
        assertEquals(1L, count);
    }

    @Test
    public void testLongName() throws Exception {
        File exportFile = File.createTempFile("knapsack-long-tar-", ".tar.bz2");
        Path exportPath = Paths.get(URI.create("file:" + exportFile.getAbsolutePath()));
        client("1").index(new IndexRequest()
                .index("index1")
                .type("test1")
                .id("veryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryverylong")
                .source("content", "Hello World").refresh(true)).actionGet();
        client("1").index(new IndexRequest()
                .index("index1")
                .type("test1")
                .id("anotherveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryveryverylong")
                .source("content", "foo bar").refresh(true)).actionGet();
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
        client("1").admin().indices().delete(new DeleteIndexRequest("index1")).actionGet();
        KnapsackImportRequestBuilder knapsackImportRequestBuilder = new KnapsackImportRequestBuilder(client("1"))
                .setArchivePath(exportPath);
        KnapsackImportResponse knapsackImportResponse = knapsackImportRequestBuilder.execute().actionGet();
        if (!knapsackImportResponse.isRunning()) {
            logger.error(knapsackImportResponse.getReason());
        }
        assertTrue(knapsackImportResponse.isRunning());
        Thread.sleep(2000L);
        // count
        SearchResponse response = client("1").prepareSearch("index1").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet();
        assertEquals(2L, response.getHits().getTotalHits());
        for (SearchHit hit : response.getHits().getHits()) {
            logger.info("{}/{}/{}", hit.getIndex(), hit.getType(), hit.getId());
            assertTrue(hit.getIndex().length() + hit.getType().length() + hit.getId().length() > 100);
        }
    }

}
