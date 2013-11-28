
package org.xbib.elasticsearch.action;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.net.MalformedURLException;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequestBuilder;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.ImmutableSet;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.unit.TimeValue;
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
import org.elasticsearch.search.SearchHit;

import org.xbib.elasticsearch.knapsack.KnapsackService;
import org.xbib.elasticsearch.knapsack.KnapsackStatus;
import org.xbib.io.Connection;
import org.xbib.io.ConnectionFactory;
import org.xbib.io.ConnectionService;
import org.xbib.io.Packet;
import org.xbib.io.Session;
import org.xbib.io.StreamCodecService;
import org.xbib.elasticsearch.s3.S3;
import org.xbib.elasticsearch.s3.S3Factory;
import org.xbib.elasticsearch.s3.S3Service;

import static org.elasticsearch.common.collect.Maps.newHashMap;
import static org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.action.support.RestXContentBuilder.restContentBuilder;


public class RestExportAction extends BaseRestHandler {

    private final SettingsFilter settingsFilter;

    private final KnapsackService knapsackService;

    @Inject
    public RestExportAction(Settings settings, Client client,
            RestController controller, SettingsFilter settingsFilter, KnapsackService knapsackService) {
        super(settings, client);
        this.settingsFilter = settingsFilter;
        this.knapsackService = knapsackService;

        controller.registerHandler(POST, "/_export", this);
        controller.registerHandler(POST, "/{index}/_export", this);
        controller.registerHandler(POST, "/{index}/{type}/_export", this);
        controller.registerHandler(GET, "/_export/state",  new RestKnapsackExportStatus());

        controller.registerHandler(POST, "/_export/{s3}", this);
        controller.registerHandler(POST, "/{index}/_export/{s3}", this);
        controller.registerHandler(POST, "/{index}/{type}/_export/{s3}", this);
    }

    @Override
    public void handleRequest(final RestRequest request, RestChannel channel) {
        try {
            final String index = request.param("index");
            final String type = request.param("type");
            final String desc = (index != null ? index : "_all")
                    + (type != null ? "_" + type : "");
            XContentBuilder builder = restContentBuilder(request)
                    .startObject()
                    .field("ok", true)
                    .endObject();
            ClusterHealthResponse healthResponse =
                    client.admin().cluster().prepareHealth().setWaitForYellowStatus()
                            .setTimeout("30s").execute().actionGet(30000);
            if (healthResponse.isTimedOut()) {
                String msg = "cluster not healthy, cowardly refusing to continue with export";
                logger.error(msg);
                throw new IOException(msg);
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
            }

            ConnectionService service = ConnectionService.getInstance();
            ConnectionFactory factory = service.getConnectionFactory(scheme);
            final Connection<Session> connection = factory.getConnection(URI.create(scheme + ":" + target));
            final Session session = connection.createSession();
            session.open(Session.Mode.WRITE);
            channel.sendResponse(new XContentRestResponse(request, OK, builder));
            EsExecutors.daemonThreadFactory(settings, "Knapsack export [" + desc + "]")
                    .newThread(new ExportThread(request, connection, session, target, s3path, s3bucket)).start();
        } catch (Exception ex) {
            try {
                logger.error(ex.getMessage(), ex);
                channel.sendResponse(new XContentThrowableRestResponse(request, ex));
            } catch (Exception ex2) {
                logger.error(ex2.getMessage(), ex2);
            }
        }
    }

    private class ExportThread extends Thread {

        private final RestRequest request;

        private final Connection<Session> connection;

        private final Session session;

        private final String target;

        private final String[] indices;

        private final String[] types;

        private final String s3bucket;

        private final String s3path;

        public ExportThread(RestRequest request, Connection<Session> connection,
                            Session session, String target, String s3path, String s3bucket) {
            this.request = request;
            this.connection = connection;
            this.session = session;
            this.target = target;
            this.s3path = s3path;
            this.s3bucket = s3bucket;
            this.indices = Strings.splitStringByCommaToArray(request.param("index", "_all"));
            this.types = request.param("type") != null ?
                    Strings.splitStringByCommaToArray(request.param("type")) : null;
        }

        @Override
        public void run() {
            long millis = request.paramAsLong("millis", 30000L);
            int size = request.paramAsInt("size", 1000);
            final KnapsackStatus status = new KnapsackStatus(indices, types, target);
            try {
                logger.info("starting export to {}", target);
                knapsackService.addExport(status);
                // export settings & mappings
                for (String s : indices) {
                    Map<String, String> settings = getSettings(s);
                    for (String index : settings.keySet()) {
                        session.write(new ElasticPacket(index, "_settings", null, settings.get(index)));
                        Map<String, String> mappings = getMapping(index, types != null ? ImmutableSet.copyOf(types) : null);
                        for (String type : mappings.keySet()) {
                            session.write(new ElasticPacket(index, type, "_mapping", mappings.get(type)));
                        }
                    }
                }
                // export document _source fields
                SearchRequestBuilder searchRequest = client.prepareSearch()
                        .setSize(size)
                        .setSearchType(SearchType.SCAN)
                        .setScroll(TimeValue.timeValueMillis(millis));
                if (indices != null && !"_all".equals(indices[0])) {
                    searchRequest.setIndices(indices);
                }
                if (types != null) {
                    searchRequest.setTypes(types);
                }
                SearchResponse searchResponse = searchRequest.execute().actionGet();
                while (searchResponse.getScrollId() != null) {
                    searchResponse = client.prepareSearchScroll(searchResponse.getScrollId())
                            .setScroll(TimeValue.timeValueMillis(millis))
                            .execute().actionGet();
                    if (searchResponse.getHits().getHits().length == 0) {
                        break;
                    }
                    for (SearchHit hit : searchResponse.getHits()) {
                        ElasticPacket packet = new ElasticPacket(hit.getIndex(), hit.getType(), hit.getId(), hit.getSourceAsString());
                        session.write(packet);
                    }
                }
                session.close();
                connection.close();
                logger.info("export to {} completed", target);

                String s3 = request.param("s3", null);
                if(s3 != null){
                    S3Service s3service = S3Service.getInstance();
                    S3Factory s3Factory = s3service.getS3Factory();
                    S3 s3client = s3Factory.getS3(target, request.param("accesskey"), request.param("secretkey"));
                    s3client.writeToS3(s3bucket, s3path);
                    logger.info("transfer of {} to s3 completed", target);
                }

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {
                try {
                    knapsackService.removeExport(status);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    private Map<String, String> getSettings(String index) throws IOException {
        logger.info("getting settings for index {}", index);
        Map<String, String> settings = newHashMap();
        ClusterStateRequestBuilder request = client.admin().cluster().prepareState()
                .setFilterRoutingTable(true)
                .setFilterNodes(true);
        if (!"_all".equals(index)) {
            request.setFilterIndices(index);
        }
        ClusterStateResponse response = request.execute().actionGet();
        MetaData metaData = response.getState().metaData();
        if (!metaData.getIndices().isEmpty()) {
            for (IndexMetaData indexMetaData : metaData) {
                final XContentBuilder builder = jsonBuilder();
                builder.startObject();
                for (Map.Entry<String, String> entry :
                        settingsFilter.filterSettings(indexMetaData.getSettings()).getAsMap().entrySet()) {
                    builder.field(entry.getKey(), entry.getValue());
                }
                builder.endObject();
                settings.put(indexMetaData.getIndex(), builder.string());
            }
        }
        return settings;
    }

    private Map<String, String> getMapping(String index, Set<String> types) throws IOException {
        logger.info("getting mapping for index {} and types {}", index, types);
        Map<String, String> mappings = newHashMap();
        ClusterStateResponse response = client.admin().cluster().prepareState()
                .setFilterRoutingTable(true)
                .setFilterNodes(true)
                .setFilterIndices(index)
                .execute().actionGet();
        MetaData metaData = response.getState().getMetaData();
        if (!metaData.getIndices().isEmpty()) {
            IndexMetaData indexMetaData = metaData.iterator().next();
            for (MappingMetaData mappingMd : indexMetaData.getMappings().values()) {
                if (types == null || types.isEmpty() || types.contains(mappingMd.type())) {
                    final XContentBuilder builder = jsonBuilder();
                    builder.startObject();
                    builder.field(mappingMd.type());
                    builder.map(mappingMd.sourceAsMap());
                    builder.endObject();
                    mappings.put(mappingMd.type(), builder.string());
                }
            }
        }
        return mappings;
    }

    private class ElasticPacket implements Packet<String> {

        String name;
        String packet;

        ElasticPacket(String index, String type, String id, String packet) {
            this.name = index + "/" + type + (id != null ? "/" + id : "");
            this.packet = packet;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getPacket() {
            return packet;
        }

        @Override
        public String toString() {
            return packet;
        }
    }

    private class RestKnapsackExportStatus implements RestHandler {

        @Override
        public void handleRequest(RestRequest request, RestChannel channel) {
            try {
                XContentBuilder builder = restContentBuilder(request);
                builder.startObject()
                        .field("exports")
                        .startArray();
                for (KnapsackStatus export : knapsackService.getExports()) {
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
