/*
 * Copyright (C) 2014 Jörg Prante
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xbib.elasticsearch.action.knapsack.imp;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesAction;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexAction;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.node.service.NodeService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.joda.time.DateTime;
import org.xbib.elasticsearch.helper.client.BulkNodeClient;
import org.xbib.elasticsearch.helper.client.ClientBuilder;
import org.xbib.elasticsearch.knapsack.KnapsackParameter;
import org.xbib.elasticsearch.knapsack.KnapsackService;
import org.xbib.elasticsearch.knapsack.KnapsackState;
import org.xbib.io.BytesProgressWatcher;
import org.xbib.io.Session;
import org.xbib.io.StringPacket;
import org.xbib.io.archive.ArchiveService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.client.Requests.createIndexRequest;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.mapIndex;
import static org.xbib.elasticsearch.knapsack.KnapsackHelper.mapType;

public class TransportKnapsackImportAction extends TransportAction<KnapsackImportRequest, KnapsackImportResponse> {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackImportAction.class.getSimpleName());

    private final Client client;

    private final NodeService nodeService;

    private final KnapsackService knapsack;

    @Inject
    public TransportKnapsackImportAction(Settings settings, ThreadPool threadPool,
                                         Client client, NodeService nodeService, ActionFilters actionFilters,
                                         IndexNameExpressionResolver indexNameExpressionResolver,
                                         TransportService transportService,
                                         KnapsackService knapsack) {
        super(settings, KnapsackImportAction.NAME, threadPool, actionFilters, indexNameExpressionResolver, transportService.getTaskManager());
        this.client = client;
        this.nodeService = nodeService;
        this.knapsack = knapsack;
    }

    @Override
    protected void doExecute(final KnapsackImportRequest request, ActionListener<KnapsackImportResponse> listener) {
        final KnapsackState state = new KnapsackState()
                .setMode("import")
                .setNodeName(nodeService.nodeName());
        final KnapsackImportResponse response = new KnapsackImportResponse()
                .setState(state);
        try {
            Path path = request.getArchivePath();
            if (path == null) {
                String dataPath = settings.get(KnapsackParameter.KNAPSACK_PATH, settings.get(KnapsackParameter.KNAPSACK_DEFAULT_PATH, "."));
                path = new File(dataPath + File.separator + "_all.tar.gz").toPath();
            }
            ByteSizeValue bytesToTransfer = request.getBytesToTransfer();
            BytesProgressWatcher watcher = new BytesProgressWatcher(bytesToTransfer.bytes());
            final Session<StringPacket> session = ArchiveService.newSession(path, watcher);
            EnumSet<Session.Mode> mode = EnumSet.of(Session.Mode.READ);
            session.open(mode, path);
            if (session.isOpen()) {
                final BulkNodeClient bulkNodeClient = ClientBuilder.builder()
                        .put(ClientBuilder.MAX_ACTIONS_PER_REQUEST, request.getMaxActionsPerBulkRequest())
                        .put(ClientBuilder.MAX_CONCURRENT_REQUESTS, request.getMaxBulkConcurrency())
                        .put(ClientBuilder.FLUSH_INTERVAL, TimeValue.timeValueSeconds(5))
                        .toBulkNodeClient(client);
                state.setTimestamp(new DateTime())
                        .setPath(path);
                response.setRunning(true);
                knapsack.submit(new Thread() {
                    public void run() {
                        try {
                            performImport(request, state, session, bulkNodeClient);
                        } catch (Throwable t) {
                            //
                        }
                    }
                });
            } else {
                response.setRunning(false).setReason("session can not be opened: mode=" + mode + " path=" + path);
            }
            listener.onResponse(response);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            listener.onFailure(e);
        }
    }

    /**
     * Import thread
     *
     * @param request request
     * @param state   state
     * @param session session
     */
    final void performImport(final KnapsackImportRequest request,
                             final KnapsackState state,
                             final Session<StringPacket> session,
                             final BulkNodeClient bulkNodeClient) {
        try {
            logger.info("start of import: {}", state);
            knapsack.addImport(state);
            final Map<String, CreateIndexRequest> indexRequestMap = new HashMap<>();
            final Set<String> indexCreated = new HashSet<>();
            final Map<String, String> indexReplicaMap = new HashMap<>();
            final Map<String, Map<String, String>> aliasRequestMap = new HashMap<>();
            // per field
            Map<String, StringPacket> packets = new LinkedHashMap<>();
            StringPacket packet;
            String lastCoord = null;
            long count = 0L;
            while ((packet = session.read()) != null && !Thread.interrupted()) {
                count++;
                String index = (String) packet.meta().get("index");
                String type = (String) packet.meta().get("type");
                String id = (String) packet.meta().get("id");
                String field = (String) packet.meta().get("field");
                if (field == null) {
                    field = "_source";
                }
                if ("_settings".equals(type)) {
                    index = mapIndex(request, index);
                    String settingsStr;
                    // override settings by user settings
                    if (request.hasIndexSettings(index)) {
                        InputStreamReader reader =
                                new InputStreamReader(new FileInputStream(request.getIndexSettings(index)), "UTF-8");
                        settingsStr = Streams.copyToString(reader);
                        reader.close();
                    } else {
                        settingsStr = packet.payload();
                    }
                    if (!"_all".equals(index)) {
                        logger.info("index {}: found settings {}", index, settingsStr);
                        CreateIndexRequest createIndexRequest = indexRequestMap.get(index);
                        if (createIndexRequest == null) {
                            createIndexRequest = createIndexRequest(index);
                            indexRequestMap.put(index, createIndexRequest);
                        }
                        Settings.Builder indexSettingsBuilder = Settings.settingsBuilder()
                                .loadFromSource(settingsStr);
                        indexReplicaMap.put(index, indexSettingsBuilder.get("index.number_of_replicas"));
                        // get settings, but overwrite replica, and disable refresh for faster bulk
                        Settings indexSettings = indexSettingsBuilder
                                .put("index.refresh_interval", "-1s")
                                .put("index.number_of_replicas", 0)
                                .build();
                        logger.info("switching index {} for bulk indexing: {}", index, indexSettings.getAsMap());
                        createIndexRequest.settings(indexSettings);
                    }
                } else if ("_mapping".equals(id)) {
                    // first map type, then index
                    type = mapType(request, index, type);
                    index = mapIndex(request, index);
                    String mapping;
                    // override mappings by user request
                    if (request.hasIndexTypeMapping(index, type)) {
                        InputStreamReader reader =
                                new InputStreamReader(new FileInputStream(request.getIndexTypeMapping(index, type)), "UTF-8");
                        mapping = Streams.copyToString(reader);
                        reader.close();
                    } else {
                        mapping = packet.payload();
                    }
                    if (!"_all".equals(index)) {
                        logger.info("index {}: found mapping {}", index, mapping);
                        CreateIndexRequest createIndexRequest = indexRequestMap.get(index);
                        if (createIndexRequest == null) {
                            createIndexRequest = createIndexRequest(index);
                            indexRequestMap.put(index, createIndexRequest);
                        }
                        createIndexRequest.mapping(type, mapping);
                    }
                } else if ("_alias".equals(id)) {
                    Map<String, String> aliases = new HashMap<>();
                    if (aliasRequestMap.containsKey(index)) {
                        aliases = aliasRequestMap.get(index);
                    }
                    aliases.put(type, packet.payload());
                    aliasRequestMap.put(index, aliases);
                } else {
                    // index normal document fields. Check for sane entries here.
                    if (index != null && type != null && id != null && packet.payload() != null) {
                        // additional check for Mac tar "." artifacts and skip them (should we check for lowercase here?)
                        if (!type.startsWith(".") && !id.startsWith(".")) {
                            String coord = index + File.separator + type + File.separator + id;
                            if (!coord.equals(lastCoord) && !packets.isEmpty()) {
                                indexPackets(bulkNodeClient, indexRequestMap, indexCreated, aliasRequestMap, request, packets);
                                packets.clear();
                            }
                            packets.put(field, packet);
                            lastCoord = coord;
                        }
                    }
                }
            }
            if (!packets.isEmpty()) {
                indexPackets(bulkNodeClient, indexRequestMap, indexCreated, aliasRequestMap, request, packets);
            }
            bulkNodeClient.flushIngest();
            bulkNodeClient.waitForResponses(TimeValue.timeValueSeconds(60));
            for (String index : indexReplicaMap.keySet()) {
                try {
                    logger.info("resetting refresh rate for index {}", index);
                    bulkNodeClient.stopBulk(index);
                    Integer replica = Integer.parseInt(indexReplicaMap.get(index));
                    logger.info("resetting replica level {} for index {}", replica, index);
                    bulkNodeClient.updateReplicaLevel(index, replica);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
            for (String index : indexCreated) {
                bulkNodeClient.refreshIndex(index);
            }
            bulkNodeClient.shutdown();
            logger.info("end of import: {}, count = {}", state, count);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                knapsack.removeImport(state);
                session.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private void indexPackets(BulkNodeClient bulkNodeClient, Map<String, CreateIndexRequest> indexRequestMap, Set<String> indexCreated,
                              Map<String, Map<String, String>> aliasRequestMap,
                              KnapsackImportRequest request, Map<String, StringPacket> packets) {
        StringPacket packet = packets.values().iterator().next(); // first packet
        String index = (String) packet.meta().get("index");
        String type = (String) packet.meta().get("type");
        String id = (String) packet.meta().get("id");
        // check if index must be created
        if (indexRequestMap.containsKey(index)) {
            CreateIndexRequest createIndexRequest = indexRequestMap.remove(index);
            if (request.withMetadata()) {
                logger.info("creating index {}", index);
                createIndexRequest.timeout(request.getTimeout());
                try {
                    CreateIndexResponse response =
                            bulkNodeClient.client().execute(CreateIndexAction.INSTANCE, createIndexRequest).actionGet();
                    if (!response.isAcknowledged()) {
                        logger.warn("index creation was not acknowledged");
                    }
                    indexCreated.add(index);
                } catch (IndexAlreadyExistsException e) {
                    logger.warn("index already exists: {}", index);
                }
            }
        } else {
            // we assume this one is automatically created by the bulk API
            indexCreated.add(index);
        }
        if (aliasRequestMap.containsKey(index)) {
            Map<String, String> aliases = aliasRequestMap.remove(index);
            if (request.withMetadata()) {
                IndicesAliasesRequestBuilder requestBuilder =
                        new IndicesAliasesRequestBuilder(bulkNodeClient.client(), IndicesAliasesAction.INSTANCE);
                for (String alias : aliases.keySet()) {
                    requestBuilder.addAlias(index, alias, aliases.get(alias));
                }
                logger.info("creating {} aliases for index {}", aliases.size(), index);
                requestBuilder.execute().actionGet();
            }
        }
        // index document begins here
        IndexRequest indexRequest = new IndexRequest(mapIndex(request, index), mapType(request, index, type), id);
        for (String f : packets.keySet()) {
            if (f == null) {
                continue;
            }
            Object o = packets.get(f).payload();
            if (o == null) {
                logger.error("empty payload detected");
                continue;
            }
            String payload = o.toString();
            switch (f) {
                case "_parent":
                    indexRequest.parent(payload);
                    break;
                case "_routing":
                    indexRequest.routing(payload);
                    break;
                case "_timestamp":
                    indexRequest.timestamp(payload);
                    break;
                case "_version":
                    indexRequest.versionType(VersionType.EXTERNAL).version(Long.parseLong(payload));
                    break;
                case "_source":
                    indexRequest.source(payload);
                    break;
                default:
                    if (!f.startsWith(".")) {
                        indexRequest.source(f, payload);
                    }
                    break;
            }
        }
        bulkNodeClient.bulkIndex(indexRequest);
    }

}
