
package org.xbib.elasticsearch.action;

import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequestBuilder;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchOperationThreading;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
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
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.XContentRestResponse;
import org.elasticsearch.rest.XContentThrowableRestResponse;
import org.elasticsearch.rest.action.support.RestActions;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;

import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.xbib.elasticsearch.plugin.knapsack.KnapsackHelper;
import org.xbib.elasticsearch.plugin.knapsack.KnapsackPacket;
import org.xbib.elasticsearch.plugin.knapsack.KnapsackStatus;
import org.xbib.io.ObjectPacket;
import org.xbib.elasticsearch.support.client.bulk.BulkClient;
import org.xbib.io.Connection;
import org.xbib.io.ConnectionFactory;
import org.xbib.io.ConnectionService;
import org.xbib.io.Session;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.client.Requests.createIndexRequest;
import static org.elasticsearch.common.collect.Maps.newHashMap;
import static org.elasticsearch.common.collect.Sets.newHashSet;
import static org.elasticsearch.common.unit.TimeValue.parseTimeValue;
import static org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.RestStatus.BAD_REQUEST;
import static org.elasticsearch.rest.action.support.RestXContentBuilder.restContentBuilder;

/**
 * The knapsack export action performs a scan/scroll action over a user defined query
 * and stores the result in an archive or transfers it to an ES cluster
 */
public class RestExportAction extends BaseRestHandler {

    private final ClusterName clusterName;

    private final Environment environment;

    private final KnapsackHelper knapsackHelper;

    private final SettingsFilter settingsFilter;

    @Inject
    public RestExportAction(Settings settings,
                            Environment environment,
                            Client client,
                            RestController controller,
                            SettingsFilter settingsFilter,
                            ClusterService clusterService,
                            ClusterName clusterName) {
        super(settings, client);
        this.environment = environment;
        this.settingsFilter = settingsFilter;
        this.clusterName = clusterName;
        this.knapsackHelper = new KnapsackHelper(client, clusterService);

        controller.registerHandler(POST, "/_export", this);
        controller.registerHandler(POST, "/{index}/_export", this);
        controller.registerHandler(POST, "/{index}/{type}/_export", this);

        controller.registerHandler(POST, "/_export/{mode}", this);
        controller.registerHandler(POST, "/{index}/_export/{mode}", this);
        controller.registerHandler(POST, "/{index}/{type}/_export/{mode}", this);

        controller.registerHandler(GET, "/_export/state", new RestKnapsackExportState());
    }

    @Override
    public void handleRequest(final RestRequest request, RestChannel channel) {
        try {
            final XContentBuilder builder = restContentBuilder(request);
            final boolean copy = "copy".equals(request.param("mode"));
            if (copy) {
                final URI destination = KnapsackHelper.getAddress(client, clusterName, request.param("host"), request.paramAsInt("port", 0),
                        request.param("cluster"));
                EsExecutors.daemonThreadFactory(settings, "knapsack-export-[" + destination + "]")
                        .newThread(new ExportThread(request, destination))
                        .start();
                builder.startObject()
                        .field("running", true)
                        .field("mode", request.param("mode") != null ? request.param("mode") : "export")
                        .field("path", destination)
                        .endObject();
            } else {
                final String index = request.param("index", "_all");
                final String type = request.param("type");
                final String defaultSpec = index + (type != null ? "_" + type : "") + ".tar.gz";
                final String path = request.param("path", defaultSpec);
                URI uri = URI.create("file:" + path);
                ConnectionService<Session<KnapsackPacket>> service = ConnectionService.getInstance();
                ConnectionFactory<Session<KnapsackPacket>> factory = service.getConnectionFactory(uri);
                final Connection<Session<KnapsackPacket>> connection = factory.getConnection(uri);
                final Session<KnapsackPacket> session = connection.createSession();
                session.open(Session.Mode.WRITE);
                if (session.isOpen()) {
                    EsExecutors.daemonThreadFactory(settings, "knapsack-export-[" + connection.getURI() + "]")
                            .newThread(new ExportThread(request, connection, session))
                            .start();
                    builder.startObject()
                            .field("running", true)
                            .field("mode", request.param("mode") != null ? request.param("mode") : "export")
                            .field("archive", factory.getName())
                            .field("path", connection.getURI())
                            .endObject();
                } else {
                    builder.startObject()
                            .field("running", false)
                            .endObject();
                    channel.sendResponse(new XContentRestResponse(request, BAD_REQUEST, builder));
                    return;
                }
            }
            channel.sendResponse(new XContentRestResponse(request, OK, builder));
        } catch (Throwable ex) {
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

        private BulkClient bulkClient;

        private Connection<Session<KnapsackPacket>> connection;

        private Session<KnapsackPacket> session;

        private TimeValue timeout;

        private URI destination;

        private Map<String,Object> map;

        ExportThread(RestRequest request, URI destination) {
            this.request = request;
            this.destination = destination;
        }

        ExportThread(RestRequest request, Connection<Session<KnapsackPacket>> connection, Session<KnapsackPacket> session) {
            this.request = request;
            this.connection = connection;
            this.session = session;
        }

        @Override
        public void run() {
            this.bulkClient = new BulkClient();
            logger.info("params = {}", request.params());
            this.timeout = request.paramAsTime("timeout", TimeValue.timeValueSeconds(30L));
            final int maxActionsPerBulkRequest = request.paramAsInt("maxActionsPerBulkRequest", 1000);
            final int maxBulkConcurrency = request.paramAsInt("maxBulkConcurrency",
                    Runtime.getRuntime().availableProcessors() * 2);
            final boolean skipIndexCreation = request.paramAsBoolean("skipIndexCreation", false);
            map = toMap(request.param("map"));
            logger.info("index/type map = {}", map);
            String path = connection != null ? connection.getURI().getSchemeSpecificPart() : null;
            boolean copy = "copy".equals(request.param("mode"));
            boolean s3 = "s3".equals(request.param("mode"));
            final KnapsackStatus status = new KnapsackStatus()
                    .setTimestamp(new Date())
                    .setMap(map)
                    .setPath(path)
                    .setURI(destination)
                    .setCopy(copy)
                    .setS3(s3);
            try {
                logger.info("starting export: {}", status);
                knapsackHelper.addExport(status);
                Map<String,Set<String>> indices = newHashMap();
                for (String s : Strings.commaDelimitedListToSet(request.param("index", "_all"))) {
                    indices.put(s, Strings.commaDelimitedListToSet(request.param("type")));
                }
                if (copy) {
                    Settings settings = KnapsackHelper.clientSettings(environment, destination);
                    bulkClient.flushInterval(TimeValue.timeValueSeconds(5))
                            .maxActionsPerBulkRequest(maxActionsPerBulkRequest)
                            .maxConcurrentBulkRequests(maxBulkConcurrency)
                            .newClient(destination, settings);
                    logger.info("waiting for healthy cluster...");
                    bulkClient.waitForCluster(ClusterHealthStatus.YELLOW, timeout);
                    logger.info("... cluster is ready");
                }
                if (!skipIndexCreation) {
                    if (map != null) {
                        for (String spec : map.keySet()) {
                            if (spec == null) {
                                continue;
                            }
                            String[] s = spec.split("/");
                            String index = s[0];
                            String type = s.length > 1 ? s[1] : null;
                            if (!"_all".equals(index)) {
                                Set<String> types = indices.get(index);
                                if (types == null) {
                                    types = newHashSet();
                                }
                                if (type != null) {
                                    types.add(type);
                                }
                                indices.put(index, types);
                            }
                        }
                    }
                    // get settings for all indices
                    logger.info("getting settings for indices {}", indices.keySet());
                    Set<String> settingsIndices = newHashSet(indices.keySet());
                    settingsIndices.remove("_all");
                    Map<String, String> settings = getSettings(settingsIndices.toArray(new String[settingsIndices.size()]));
                    logger.info("found indices: {}", settings.keySet());
                    // we resolved the specs in indices to the real indices in the settings
                    // get mapping per index, create index
                    for (String index : settings.keySet()) {
                        CreateIndexRequest createIndexRequest = createIndexRequest(mapIndex(index));
                        if (!copy) {
                            session.write(new KnapsackPacket(mapIndex(index),
                                    "_settings",
                                    null,
                                    null,
                                    settings.get(index)));
                        }
                        Set<String> types = indices.get(index);
                        createIndexRequest.settings(settings.get(index));
                        logger.info("getting mappings for index {} and types {}", index, types);
                        Map<String, String> mappings = getMapping(index, types != null ? ImmutableSet.copyOf(types) : null);
                        logger.info("found mappings: {}", mappings.keySet());
                        for (String type : mappings.keySet()) {
                            if (!copy) {
                                 session.write(new KnapsackPacket(mapIndex(index),
                                         mapType(index, type),
                                         "_mapping",
                                         null,
                                         mappings.get(type)));
                            }
                            logger.info("adding mapping: {}", mapType(index, type));
                            createIndexRequest.mapping(mapType(index, type), mappings.get(type));
                        }
                        if (copy) {
                            // create index
                            logger.info("creating index: {}", mapIndex(index));
                            bulkClient.client().admin().indices().create(createIndexRequest).actionGet();
                            logger.info("index created: {}", mapIndex(index));
                        }
                    }
                }
                SearchRequest searchRequest;
                // override size = 10 default
                request.params().put("size", request.param("maxActionsPerBulkRequest", "1000"));
                searchRequest = parseSearchRequest(request);
                searchRequest.listenerThreaded(false);
                SearchOperationThreading operationThreading =
                        SearchOperationThreading.fromString(request.param("operation_threading"), null);
                if (operationThreading != null) {
                    if (operationThreading == SearchOperationThreading.NO_THREADS) {
                        operationThreading = SearchOperationThreading.SINGLE_THREAD;
                    }
                    searchRequest.operationThreading(operationThreading);
                }
                for (String index : indices.keySet()) {
                    searchRequest.searchType(SearchType.SCAN).scroll(timeout);
                    if (!"_all".equals(index)) {
                        searchRequest.indices(index);
                    }
                    Set<String> types = indices.get(index);
                    if (types != null) {
                        searchRequest.types(types.toArray(new String[types.size()]));
                    }
                    // use local node client
                    SearchResponse searchResponse = client.search(searchRequest).actionGet();
                    long total = 0L;
                    while (searchResponse.getScrollId() != null) {
                        searchResponse = client.prepareSearchScroll(searchResponse.getScrollId())
                                .setScroll(timeout)
                                .execute()
                                .actionGet();
                        long hits = searchResponse.getHits().getHits().length;
                        if (hits == 0) {
                            break;
                        }
                        total += hits;
                        if (logger.isDebugEnabled()) {
                            logger.debug("total={} hits={} took={}", total, hits, searchResponse.getTookInMillis());
                        }
                        for (SearchHit hit : searchResponse.getHits()) {
                            if (copy) {
                                IndexRequest indexRequest = new IndexRequest(mapIndex(hit.getIndex()),
                                        mapType(hit.getIndex(), hit.getType()), hit.getId());
                                for (String f : hit.getFields().keySet()) {
                                    if (f.equals("_parent")) {
                                        indexRequest.parent(hit.getFields().get(f).getValue().toString());
                                    } else if (f.equals("_routing")) {
                                        indexRequest.routing(hit.getFields().get(f).getValue().toString());
                                    } else if (f.equals("_timestamp")) {
                                        indexRequest.timestamp(hit.getFields().get(f).getValue().toString());
                                    } else if (f.equals("_version")) {
                                        indexRequest.versionType(VersionType.EXTERNAL)
                                                .version(Long.parseLong(hit.getFields().get(f).getValue().toString()));
                                    } else if (f.equals("_source")) {
                                        indexRequest.source(hit.getSourceAsString());
                                    } else {
                                        indexRequest.source(f, hit.getFields().get(f).getValue().toString());
                                    }
                                }
                                if (!hit.getFields().keySet().contains("_source")) {
                                    indexRequest.source(hit.getSourceAsString());
                                }
                                bulkClient.index(indexRequest);
                            } else {
                                for (String f : hit.getFields().keySet()) {
                                    session.write(new KnapsackPacket(mapIndex(hit.getIndex()),
                                        mapType(hit.getIndex(), hit.getType()),
                                        hit.getId(), f, hit.getFields().get(f).getValue().toString()));
                                }
                                if (!hit.getFields().keySet().contains("_source")) {
                                    session.write(new KnapsackPacket(mapIndex(hit.getIndex()),
                                        mapType(hit.getIndex(), hit.getType()),
                                        hit.getId(), "_source", hit.getSourceAsString()));
                                }
                            }
                        }
                    }
                }
                logger.info("end of export: {}", status);
                if (!copy) {
                    session.close();
                }
                if (copy) {
                    bulkClient.refresh().shutdown();
                    bulkClient = null;
                }
                if (s3 && request.param("uri") != null) {
                    // s3://auth:host, s3://host
                    URI s3URI = URI.create(request.param("uri")
                            + "?bucketName=" + request.param("bucketName")
                            + "&key=" + request.param("key"));
                    logger.info("trying to transfer file {} to s3 {}", path, s3URI);
                    ConnectionService<Session<ObjectPacket>> service = ConnectionService.getInstance();
                    ConnectionFactory<Session<ObjectPacket>> factory = service.getConnectionFactory(s3URI);
                    final Connection<Session<ObjectPacket>> connection = factory.getConnection(s3URI);
                    final Session<ObjectPacket> session = connection.createSession();
                    session.open(Session.Mode.WRITE);
                    session.write(new ObjectPacket().name(path));
                    session.close();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {
                try {
                    knapsackHelper.removeExport(status);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }

        private Map<String, Object> toMap(String s) {
            if (s == null) {
                logger.warn("no map defined");
                return newHashMap();
            }
            try {
                XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(s);
                return parser.mapAndClose();
            } catch (IOException e) {
                logger.warn("parse error while reading map", e);
                return newHashMap();
            }
        }

        private String mapIndex(String index) {
            return map.containsKey(index) ? map.get(index).toString() : index;
        }

        private String mapType(String index, String type) {
            String s = index + "/" + type;
            return map.containsKey(s) ? map.get(s).toString() : type;
        }
    }

    private Map<String, String> getSettings(String... index) throws IOException {
        Map<String, String> settings = newHashMap();
        ClusterStateRequestBuilder request = client.admin().cluster().prepareState()
                .setFilterRoutingTable(true)
                .setFilterNodes(true)
                .setFilterIndices(index);
        ClusterStateResponse response = request.execute().actionGet();
        MetaData metaData = response.getState().metaData();
        if (!metaData.getIndices().isEmpty()) {
            // filter out the settings from the metadata
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
        Map<String, String> mappings = newHashMap();
        ClusterStateRequestBuilder request = client.admin().cluster().prepareState()
                .setFilterRoutingTable(true)
                .setFilterNodes(true);
        if (!"_all".equals(index)) {
            request.setFilterIndices(index);
        }
        ClusterStateResponse response = request.execute().actionGet();
        MetaData metaData = response.getState().getMetaData();
        if (!metaData.getIndices().isEmpty()) {
            for (IndexMetaData indexMetaData : metaData) {
                for (Map.Entry<String,MappingMetaData> c : indexMetaData.getMappings().entrySet()) {
                    MappingMetaData mappingMetaData = c.getValue();
                    if (types == null || types.isEmpty() || types.contains(mappingMetaData.type())) {
                        final XContentBuilder builder = jsonBuilder();
                        builder.startObject();
                        builder.field(mappingMetaData.type());
                        builder.map(mappingMetaData.sourceAsMap());
                        builder.endObject();
                        mappings.put(mappingMetaData.type(), builder.string());
                    }
                }
            }
        }
        return mappings;
    }

    private class RestKnapsackExportState implements RestHandler {

        @Override
        public void handleRequest(RestRequest request, RestChannel channel) {
            try {
                XContentBuilder builder = restContentBuilder(request);
                builder.startObject()
                        .field("exports")
                        .startArray();
                for (KnapsackStatus export : knapsackHelper.getExports()) {
                    export.toXContent(builder, EMPTY_PARAMS);
                }
                builder.endArray()
                        .endObject();
                channel.sendResponse(new XContentRestResponse(request, OK, builder));
            } catch (IOException ioe) {
                try {
                    channel.sendResponse(new XContentThrowableRestResponse(request, ioe));
                } catch (IOException e) {
                    logger.error("unable to send response to client");
                }
            }
        }
    }

    // copy/paste from RestSearchAction - fixed in later versions

    private SearchRequest parseSearchRequest(RestRequest request) {
        String[] indices = RestActions.splitIndices(request.param("index"));
        SearchRequest searchRequest = new SearchRequest(indices);
        // get the content, and put it in the body
        if (request.hasContent()) {
            searchRequest.source(request.content().toBytes());
        } else {
            String source = request.param("source");
            if (source != null) {
                searchRequest.source(source);
            }
        }
        // add extra source based on the request parameters
        searchRequest.extraSource(parseSearchSource(request));

        searchRequest.searchType(request.param("search_type"));

        String scroll = request.param("scroll");
        if (scroll != null) {
            searchRequest.scroll(new Scroll(parseTimeValue(scroll, null)));
        }

        searchRequest.types(RestActions.splitTypes(request.param("type")));
        searchRequest.queryHint(request.param("query_hint"));
        searchRequest.routing(request.param("routing"));
        searchRequest.preference(request.param("preference"));

        return searchRequest;
    }

    private SearchSourceBuilder parseSearchSource(RestRequest request) {
        SearchSourceBuilder searchSourceBuilder = null;
        String queryString = request.param("q");
        if (queryString != null) {
            QueryStringQueryBuilder queryBuilder = QueryBuilders.queryString(queryString);
            queryBuilder.defaultField(request.param("df"));
            queryBuilder.analyzer(request.param("analyzer"));
            queryBuilder.analyzeWildcard(request.paramAsBoolean("analyze_wildcard", false));
            queryBuilder.lowercaseExpandedTerms(request.paramAsBoolean("lowercase_expanded_terms", true));
            queryBuilder.lenient(request.paramAsBooleanOptional("lenient", null));
            String defaultOperator = request.param("default_operator");
            if (defaultOperator != null) {
                if ("OR".equals(defaultOperator)) {
                    queryBuilder.defaultOperator(QueryStringQueryBuilder.Operator.OR);
                } else if ("AND".equals(defaultOperator)) {
                    queryBuilder.defaultOperator(QueryStringQueryBuilder.Operator.AND);
                } else {
                    throw new ElasticSearchIllegalArgumentException("Unsupported defaultOperator [" + defaultOperator + "], can either be [OR] or [AND]");
                }
            }
            searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(queryBuilder);
        }

        int from = request.paramAsInt("from", -1);
        if (from != -1) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.from(from);
        }
        int size = request.paramAsInt("size", -1);
        if (size != -1) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.size(size);
        }

        if (request.hasParam("explain")) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.explain(request.paramAsBooleanOptional("explain", null));
        }
        if (request.hasParam("version")) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.version(request.paramAsBooleanOptional("version", null));
        }
        if (request.hasParam("timeout")) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.timeout(request.paramAsTime("timeout", null));
        }

        String sField = request.param("fields");
        if (sField != null) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            if (!Strings.hasText(sField)) {
                searchSourceBuilder.noFields();
            } else {
                String[] sFields = Strings.splitStringByCommaToArray(sField);
                if (sFields != null) {
                    for (String field : sFields) {
                        searchSourceBuilder.field(field);
                    }
                }
            }
        }

        String sSorts = request.param("sort");
        if (sSorts != null) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            String[] sorts = Strings.splitStringByCommaToArray(sSorts);
            for (String sort : sorts) {
                int delimiter = sort.lastIndexOf(":");
                if (delimiter != -1) {
                    String sortField = sort.substring(0, delimiter);
                    String reverse = sort.substring(delimiter + 1);
                    if ("asc".equals(reverse)) {
                        searchSourceBuilder.sort(sortField, SortOrder.ASC);
                    } else if ("desc".equals(reverse)) {
                        searchSourceBuilder.sort(sortField, SortOrder.DESC);
                    }
                } else {
                    searchSourceBuilder.sort(sort);
                }
            }
        }

        String sIndicesBoost = request.param("indices_boost");
        if (sIndicesBoost != null) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            String[] indicesBoost = Strings.splitStringByCommaToArray(sIndicesBoost);
            for (String indexBoost : indicesBoost) {
                int divisor = indexBoost.indexOf(',');
                if (divisor == -1) {
                    throw new ElasticSearchIllegalArgumentException("Illegal index boost [" + indexBoost + "], no ','");
                }
                String indexName = indexBoost.substring(0, divisor);
                String sBoost = indexBoost.substring(divisor + 1);
                try {
                    searchSourceBuilder.indexBoost(indexName, Float.parseFloat(sBoost));
                } catch (NumberFormatException e) {
                    throw new ElasticSearchIllegalArgumentException("Illegal index boost [" + indexBoost + "], boost not a float number");
                }
            }
        }

        String sStats = request.param("stats");
        if (sStats != null) {
            if (searchSourceBuilder == null) {
                searchSourceBuilder = new SearchSourceBuilder();
            }
            searchSourceBuilder.stats(Strings.splitStringByCommaToArray(sStats));
        }

        return searchSourceBuilder;
    }
}
