package org.xbib.elasticsearch.knapsack;

import static org.elasticsearch.common.base.Preconditions.checkState;
import static org.elasticsearch.common.collect.Maps.newLinkedHashMap;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.Map;

import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class KnapsackPluginTest extends AbstractKnapsackPluginTest {

	@Test
	public void testExport() throws Exception {
		// Clean
		File tarFile = new File("target/archive.tar.gz");
		if (tarFile.exists()) {
			checkState(tarFile.delete());
		}

		// Setup
		for (int i = 1; i <= 10; i++) {
			String id = String.valueOf(i);
			checkState(id.equals(client.prepareIndex("test", "test", id)
					.setSource("id", id)
					.setRefresh(true)
					.execute()
					.actionGet()
					.getId()));
		}

		// Execute
		String target = tarFile.getAbsolutePath();
		assertTrue(exportResource.queryParam("target", target)
				.post(ObjectNode.class).get("ok").asBoolean());

		// Verify state
		while (true) {
			ObjectNode exportState = exportStateResource.get(ObjectNode.class);
			System.out.println(exportState);
			if (!isExporting()) {
				break;
			}

			ArrayNode exports = exportState.withArray("exports");
			assertEquals(exports.size(), 1);

			JsonNode actual = exports.get(0);
			JsonNode expected = $("{'indices':['_all'],'types':[],'target':'" + target + "'}");

			assertEquals(actual.get("indices"), expected.get("indices"));
			assertEquals(actual.get("types"), expected.get("types"));
			assertEquals(actual.get("target"), expected.get("target"));
		}

		// Verify result
		Map<String, ObjectNode> actual = readTarFile(tarFile);
		Map<String, ObjectNode> expected = newLinkedHashMap();

		expected.put("test/_settings",
				$("{'index.number_of_shards':'1','index.number_of_replicas':'0','index.version.created':'900399'}"));
		expected.put("test/test/_mapping",
				$("{'test':{'properties':{'id':{'type':'string'}}}}"));
		for (int i = 1; i <= 10; i++) {
			expected.put("test/test/" + i, $("{'id':'" + i + "'}"));
		}

		assertEquals(actual, expected);
	}

	@Test
	public void testImport() throws Exception {
		// Clean
		File tarFile = new File("target/archive.tar.gz");
		if (tarFile.exists()) {
			checkState(tarFile.delete());
		}

		// Setup
		Map<String, ObjectNode> entries = newLinkedHashMap();
		entries.put("test/_settings",
				$("{'index.number_of_shards':'1','index.number_of_replicas':'0','index.version.created':'900399'}"));
		entries.put("test/test/_mapping",
				$("{'test':{'properties':{'id':{'type':'string'}}}}"));
		for (int i = 1; i <= 10; i++) {
			String id = String.valueOf(i);
			entries.put("test/test/" + id, $("{'id':'" + id + "'}"));
		}

		writeTarFile(tarFile, entries);

		// Execute
		String target = new File(tarFile.getParentFile(), "archive").getAbsolutePath();
		assertTrue(importResource.queryParam("target", target)
				.post(ObjectNode.class).get("ok").asBoolean());

		// Verify state
		while (true) {
			ObjectNode importState = importStateResource.get(ObjectNode.class);
			System.out.println(importState);
			if (!isImporting()) {
				break;
			}

			ArrayNode imports = importState.withArray("imports");
			assertEquals(imports.size(), 1);

			JsonNode actual = imports.get(0);
			JsonNode expected = $("{'indices':['_all'],'types':[],'target':'" + target + "'}");

			assertEquals(actual.get("indices"), expected.get("indices"));
			assertEquals(actual.get("types"), expected.get("types"));
			assertEquals(actual.get("target"), expected.get("target"));
		}

		// Verify result
		IndexMetaData metaData = client.admin().cluster().prepareState()
				.setFilterIndices("test")
				.execute()
				.actionGet()
				.getState()
				.getMetaData()
				.index("test");
		Map<String, String> settings = metaData.settings().getAsMap();
		String mapping = metaData.mapping("test").source().toString();

		assertEquals($(settings), entries.get("test/_settings"));
		assertEquals($(mapping), entries.get("test/test/_mapping"));
		for (int i = 1; i <= 10; i++) {
			String id = String.valueOf(i);
			String source = client.prepareGet("test", "test", id)
					.execute()
					.actionGet()
					.getSourceAsString();
			assertEquals($(source), entries.get("test/test/" + id));
		}
	}

	private static boolean isExporting() {
		delay();
		return matchesThread("[Knapsack export [_all]]");
	}

	private static boolean isImporting() {
		delay();
		return matchesThread("[Knapsack import [_all]]");
	}

	private static void delay() {
		try {
			Thread.sleep(100L);
		} catch (InterruptedException e) {
		}
	}

}
