
package org.xbib.elasticsearch.action;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.net.MalformedURLException;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.EsExecutors;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.XContentRestResponse;
import org.elasticsearch.rest.XContentThrowableRestResponse;

import org.xbib.elasticsearch.knapsack.KnapsackService;
import org.xbib.elasticsearch.knapsack.KnapsackStatus;
import org.xbib.io.BulkOperation;
import org.xbib.io.Connection;
import org.xbib.io.ConnectionFactory;
import org.xbib.io.ConnectionService;
import org.xbib.io.Packet;
import org.xbib.io.Session;
import org.xbib.io.StreamCodecService;
import org.xbib.elasticsearch.s3.S3;
import org.xbib.elasticsearch.s3.S3Factory;
import org.xbib.elasticsearch.s3.S3Service;

import static org.elasticsearch.client.Requests.createIndexRequest;
import static org.elasticsearch.client.Requests.putMappingRequest;
import static org.elasticsearch.common.collect.Maps.newHashMap;
import static org.elasticsearch.common.collect.Sets.newHashSet;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;
import static org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS;
import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.action.support.RestXContentBuilder.restContentBuilder;

public class RestImportAction extends BaseRestHandler {

    private final KnapsackService knapsackService;

    @Inject
    public RestImportAction(Settings settings, Client client,
            RestController controller, KnapsackService knapsackService) {
        super(settings, client);
        this.knapsackService = knapsackService;

        controller.registerHandler(POST, "/_import", this);
        controller.registerHandler(POST, "/{index}/_import", this);
        controller.registerHandler(POST, "/{index}/{type}/_import", this);
        controller.registerHandler(GET, "/_import/state", new RestKnapsackImportStatus());

        controller.registerHandler(POST, "/_import/{s3}", this);
        controller.registerHandler(POST, "/{index}/_import/{s3}", this);
        controller.registerHandler(POST, "/{index}/{type}/_import/{s3}", this);
    }

    @Override
    public void handleRequest(final RestRequest request, RestChannel channel) {
        final String newIndex = request.param("index", "_all");
        final String newType = request.param("type");
        final String desc = newIndex + (newType != null ? "_" + newType : "");
        try {
            XContentBuilder builder = restContentBuilder(request)
                    .startObject()
                    .field("ok", true)
                    .endObject();

            ClusterHealthResponse healthResponse =
                    client.admin().cluster().prepareHealth().setWaitForYellowStatus()
                            .setTimeout("30s").execute().actionGet(30000);

            if (healthResponse.isTimedOut()) {
                throw new IOException("cluster not healthy, cowardly refusing to continue with export");
            }

            final String target = request.param("target", desc);
            String scheme = request.param("scheme", "targz");
            for (String codec : StreamCodecService.getCodecs()) {
                if (target.endsWith(codec)) {
                    scheme = "tar" + codec;
                }
            }

            final String s3path = request.param("s3path", null);
            final String s3bucket = request.param("s3bucket", null);
            final String s3 = request.param("s3", null);

            if(s3 != null){
                if(s3path == null || s3bucket == null){
                    logger.error("s3bucket and s3path parameters are required for s3 transfers");
                    channel.sendResponse(new XContentThrowableRestResponse(request, RestStatus.BAD_REQUEST, 
                            new MalformedURLException("s3bucket and s3path parameters are required for s3 transfers")));
                }
                S3Service s3service = S3Service.getInstance();
                S3Factory s3Factory = s3service.getS3Factory();
                S3 s3client = s3Factory.getS3(target, request.param("accesskey"), request.param("secretkey"));
                s3client.readFromS3(s3bucket, s3path);
                logger.info("transfer of {} to s3 completed", target);
            }

            ConnectionService service = ConnectionService.getInstance();
            ConnectionFactory factory = service.getConnectionFactory(scheme);
            final Connection<Session> connection = factory.getConnection(URI.create(scheme + ":" + target));
            final Session session = connection.createSession();
            session.open(Session.Mode.READ);
            EsExecutors.daemonThreadFactory(settings, "Knapsack import [" + desc + "]")
                    .newThread(new ImportThread(request, connection, session, target)).start();
            channel.sendResponse(new XContentRestResponse(request, OK, builder));
        } catch (Exception ex) {
            try {
                logger.error(ex.getMessage(), ex);
                channel.sendResponse(new XContentThrowableRestResponse(request, ex));
            } catch (Exception ex2) {
                logger.error(ex2.getMessage(), ex2);
            }
        }
    }

    private class ImportThread extends Thread {

        private final Map<String, String> indices = newHashMap();

        private final Map<String, String> mappings = newHashMap();

        private final Set<String> created = newHashSet();

        private final RestRequest request;

        private final Connection<Session> connection;

        private final Session session;

        private final String target;

        private String timeout;

        ImportThread(RestRequest request, Connection<Session> connection, Session session, String target) {
            this.request = request;
            this.connection = connection;
            this.session = session;
            this.target = target;
        }

        @Override
        public void run() {

            final Map<String, String> params = request.params();
            final int size = request.paramAsInt("bulk_size", 100);
            final int maxActiveBulkRequest = request.paramAsInt("max_active_bulks", 10);
            final String newIndex = request.param("index", "_all");
            final String newType = request.param("type");
            timeout = request.param("timeout", "30s");

            BulkOperation op = new BulkOperation(client, logger)
                    .setBulkSize(size)
                    .setMaxActiveRequests(maxActiveBulkRequest);

            final KnapsackStatus status = new KnapsackStatus(new String[] { newIndex },
                    newType != null ? new String[] { newType } : null, target);
            try {
                logger.info("starting import of {}", target);
                knapsackService.addImport(status);
                Packet<String> packet;
                while ((packet = session.read()) != null) {
                    String[] entry = packet.getName().split("/");
                    String index = entry.length > 0 ? entry[0] : null;
                    String type = entry.length > 1 ? entry[1] : null;
                    String id = entry.length > 2 ? entry[2] : null;
                    // re-map to new index/type
                    if (newIndex != null && !newIndex.equals("_all")) {
                        index = newIndex;
                    }
                    if (newType != null) {
                        type = newType;
                    }
                    if (entry.length == 2) {
                        if ("_settings".equals(entry[1])) {
                            String settings;
                            if (params.containsKey(index + "_settings")) {
                                InputStreamReader reader = new InputStreamReader(new FileInputStream(params.get(index + "_settings")), "UTF-8");
                                settings = Streams.copyToString(reader);
                                reader.close();
                            } else {
                                settings = packet.getPacket();
                            }
                            logger.info("index {}: got settings {}", index, settings);
                            indices.put(index, settings);
                            if (!createIndex(index)) {
                                throw new IOException("unable to create index '" + index + "' with settings " + settings);
                            }
                        }
                    } else if (entry.length == 3) {
                        String mapping;
                        if ("_mapping".equals(entry[2])) {
                            if (params.containsKey(index + "_" + type + "_mapping")) {
                                InputStreamReader reader = new InputStreamReader(new FileInputStream(params.get(index + "_" + type + "_mapping")), "UTF-8");
                                mapping = Streams.copyToString(reader);
                                reader.close();
                            } else {
                                mapping = packet.getPacket();
                            }
                            mappings.put(index + "/" + type, mapping);
                            if (!createMapping(index, type)) {
                                throw new IOException("unable to create index type '" + index + "/" + type + "' with mapping " + mapping);
                            }
                        } else {
                            // index document
                            op.index(index, type, id, packet.getPacket());
                        }
                    }
                }
                logger.info("import of {} completed", target);
                session.close();
                connection.close();

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {
                try {
                    op.flush();
                    knapsackService.removeImport(status);
                } catch (IOException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            }
        }

        private boolean createIndex(String index) {
            if ("_all".equals(index)) {
                logger.warn("not creating index, index name is _all");
                return true;
            }
            if (created.contains(index)) {
                logger.warn("index {} already created", index);
                return true;
            }
            String settings = indices.get(index);
            created.add(index);
            logger.info("creating index {} from import with settings {}", index, settings);
            CreateIndexRequest createIndexRequest = createIndexRequest(index);
            createIndexRequest.timeout(timeout);
            if (settings != null) {
                createIndexRequest.source(settings);
            }
            CreateIndexResponse response = client.admin().indices().create(createIndexRequest).actionGet();
            return response.isAcknowledged();
        }

        private boolean createMapping(String index, String type) throws IOException {
            if ("_all".equals(index)) {
                logger.warn("not creating mapping, index name is _all");
                return true;
            }
            if (!createIndex(index)) {
                throw new IOException("unable to create index " + index);
            }
            String desc = index + "/" + type;
            if (created.contains(desc)) {
                logger.warn("mapping {} already created", desc);
                return true;
            }
            String mapping = mappings.get(desc);
            created.add(desc);
            logger.info("creating mapping {} from import", desc);
            PutMappingRequest putMappingRequest = putMappingRequest(index);
            putMappingRequest.timeout(timeout);
            putMappingRequest.type(type);
            if (mapping != null) {
                putMappingRequest.source(mapping);
            }
            PutMappingResponse response = client.admin().indices().putMapping(putMappingRequest).actionGet();
            return response.isAcknowledged();
        }
    }

    private class RestKnapsackImportStatus implements RestHandler {

        @Override
        public void handleRequest(RestRequest request, RestChannel channel) {
            try {
                XContentBuilder builder = restContentBuilder(request);
                builder.startObject()
                        .field("imports")
                        .startArray();
                for (KnapsackStatus export : knapsackService.getImports()) {
                    export.toXContent(builder, EMPTY_PARAMS);
                }
                builder.endArray()
                        .endObject();
                channel.sendResponse(new XContentRestResponse(request, OK, builder));
            } catch (IOException ioe) {
                try {
                    channel.sendResponse(new XContentThrowableRestResponse(request, ioe));
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
