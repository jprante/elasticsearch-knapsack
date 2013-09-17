package org.xbib.elasticsearch.knapsack;

import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES;
import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static org.apache.commons.compress.archivers.ArchiveStreamFactory.TAR;
import static org.elasticsearch.common.collect.Maps.newLinkedHashMap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.io.FileSystemUtils;
import org.elasticsearch.common.network.NetworkUtils;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

public abstract class AbstractKnapsackPluginTest {

	protected Node node;
	protected Client client;
	protected com.sun.jersey.api.client.Client rest;

	protected WebResource exportResource;
	protected WebResource exportStateResource;
	protected WebResource importResource;
	protected WebResource importStateResource;

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.configure(ALLOW_SINGLE_QUOTES, true)
			.configure(AUTO_CLOSE_SOURCE, false);

	@BeforeMethod
	public void before() {
		node = NodeBuilder.nodeBuilder().settings(
				ImmutableSettings.settingsBuilder()
						.put("node.name", "node-test-" + System.currentTimeMillis())
						.put("node.data", true)
						.put("cluster.name", "cluster-test-" + NetworkUtils.getLocalAddress().getHostName())
						.put("index.store.type", "memory")
						.put("index.store.fs.memory.enabled", "true")
						.put("gateway.type", "none")
						.put("path.data", "./target/elasticsearch-test/data")
						.put("path.work", "./target/elasticsearch-test/work")
						.put("path.logs", "./target/elasticsearch-test/logs")
						.put("index.number_of_shards", "1")
						.put("index.number_of_replicas", "0")
						.put("cluster.routing.schedule", "50ms")
						.put("node.local", true)
						.build())
				.node();

		client = node.client();

		// Wait for yellow status
		client.admin().cluster().prepareHealth()
				.setWaitForYellowStatus()
				.setTimeout(TimeValue.timeValueMinutes(1))
				.execute()
				.actionGet();

		// Create rest client
		ClientConfig config = new DefaultClientConfig();
		config.getClasses().add(JacksonJsonProvider.class);
		rest = com.sun.jersey.api.client.Client.create(config);

		// Create rest resources
		WebResource baseResource = rest.resource("http://localhost:9200");
		exportResource = baseResource.path("_export");
		exportStateResource = exportResource.path("state");
		importResource = baseResource.path("_import");
		importStateResource = importResource.path("state");
	}

	@AfterMethod
	public void after() {
		if (client != null) {
			client.close();
		}
		if ((node != null) && (!node.isClosed())) {
			node.close();
			FileSystemUtils.deleteRecursively(new File("./target/elasticsearch-test/"), true);
		}
		if (rest != null) {
			rest.destroy();
		}
	}

	protected static ObjectNode $(String json) throws JsonParseException, JsonMappingException, IOException {
		return MAPPER.readValue(json, ObjectNode.class);
	}

	protected static ObjectNode $(InputStream inputStream) throws JsonParseException, JsonMappingException, IOException {
		return MAPPER.readValue(inputStream, ObjectNode.class);
	}

	protected static ObjectNode $(Map<String, String> map) {
		return MAPPER.convertValue(map, ObjectNode.class);
	}

	protected static Map<String, ObjectNode> readTarFile(File tarFile) throws FileNotFoundException, IOException,
			ArchiveException {
		Map<String, ObjectNode> entries = newLinkedHashMap();
		ArchiveInputStream input = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(
				new GZIPInputStream(new FileInputStream(tarFile))));

		if (input instanceof TarArchiveInputStream) {
			TarArchiveInputStream tar = (TarArchiveInputStream) input;
			TarArchiveEntry entry = tar.getNextTarEntry();
			while (entry != null) {
				entries.put(entry.getName(), $(tar));

				entry = tar.getNextTarEntry();
			}
		}

		return entries;
	}

	protected static void writeTarFile(File tarFile, Map<String, ObjectNode> entries) throws IOException,
			ArchiveException {
		ArchiveOutputStream output = new ArchiveStreamFactory().createArchiveOutputStream(TAR,
				new GZIPOutputStream(new FileOutputStream(tarFile)));

		for (Entry<String, ObjectNode> entry : entries.entrySet()) {
			byte[] bytes = entry.getValue().toString().getBytes();
			TarArchiveEntry archiveEntry = new TarArchiveEntry(entry.getKey());
			archiveEntry.setSize(bytes.length);

			output.putArchiveEntry(archiveEntry);
			output.write(bytes);
			output.closeArchiveEntry();
		}

		output.close();
	}
	
	protected static boolean matchesThread(String name) {
		for (Thread thread : Thread.getAllStackTraces().keySet()) {
			if (thread.getName().contains(name)) {
				return true;
			}
		}

		return false;
	}

}
