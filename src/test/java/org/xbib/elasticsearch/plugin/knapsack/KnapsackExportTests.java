package org.xbib.elasticsearch.plugin.knapsack;

import org.elasticsearch.action.index.IndexRequest;
import org.junit.Test;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportResponse;
import org.xbib.elasticsearch.action.knapsack.state.KnapsackStateRequestBuilder;
import org.xbib.elasticsearch.action.knapsack.state.KnapsackStateResponse;
import org.xbib.elasticsearch.plugin.helper.AbstractNodeTestHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KnapsackExportTests extends AbstractNodeTestHelper {

    @Test
    public void testMinimalExport() throws Exception {
        File exportFile = File.createTempFile("minimal-export-", ".bulk");
        Path exportPath = Paths.get(URI.create("file:" + exportFile.getAbsolutePath()));
        client("1").index(new IndexRequest().index("index1").type("test1").id("doc1").source("content","Hello World").refresh(true)).actionGet();
        KnapsackExportRequestBuilder requestBuilder = new KnapsackExportRequestBuilder(client("1").admin().indices())
                .setPath(exportPath)
                .setOverwriteAllowed(true);
        KnapsackExportResponse knapsackExportResponse = requestBuilder.execute().actionGet();
        if (!knapsackExportResponse.isRunning()) {
            logger.error(knapsackExportResponse.getReason());
        }
        assertTrue(knapsackExportResponse.isRunning());
        KnapsackStateRequestBuilder knapsackStateRequestBuilder =
               new KnapsackStateRequestBuilder(client("2").admin().indices());
        KnapsackStateResponse knapsackStateResponse = knapsackStateRequestBuilder.execute().actionGet();
        assertTrue(knapsackStateResponse.isExportActive(exportPath));
        Thread.sleep(1000L);
        BufferedReader reader = new BufferedReader(new FileReader(exportFile));
        assertEquals("{\"index\":{\"_index\":\"index1\",\"_type\":\"test1\",\"_id\":\"doc1\"}", reader.readLine());
        assertEquals("{\"content\":\"Hello World\"}", reader.readLine());
        reader.close();
    }

}
