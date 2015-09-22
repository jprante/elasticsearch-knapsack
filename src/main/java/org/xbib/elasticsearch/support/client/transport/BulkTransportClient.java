package org.xbib.elasticsearch.support.client.transport;

import com.google.common.collect.ImmutableSet;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.xbib.elasticsearch.support.client.BaseIngestTransportClient;
import org.xbib.elasticsearch.support.client.BulkProcessorHelper;
import org.xbib.elasticsearch.support.client.ClientHelper;
import org.xbib.elasticsearch.support.client.Ingest;
import org.xbib.elasticsearch.support.client.Metric;

import java.io.IOException;
import java.util.Map;

/**
 * Transport client using the BulkProcessor of Elasticsearch
 */
public class BulkTransportClient extends BaseIngestTransportClient implements Ingest {

    private final static ESLogger logger = ESLoggerFactory.getLogger(BulkTransportClient.class.getName());

    private int maxActionsPerRequest = DEFAULT_MAX_ACTIONS_PER_REQUEST;

    private int maxConcurrentRequests = DEFAULT_MAX_CONCURRENT_REQUESTS;

    private ByteSizeValue maxVolumePerRequest = DEFAULT_MAX_VOLUME_PER_REQUEST;

    private TimeValue flushInterval = DEFAULT_FLUSH_INTERVAL;

    private BulkProcessor bulkProcessor;

    private Throwable throwable;

    private boolean closed = false;

    @Override
    public BulkTransportClient maxActionsPerRequest(int maxActionsPerRequest) {
        this.maxActionsPerRequest = maxActionsPerRequest;
        return this;
    }

    @Override
    public BulkTransportClient maxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
        return this;
    }

    @Override
    public BulkTransportClient maxVolumePerRequest(ByteSizeValue maxVolumePerRequest) {
        this.maxVolumePerRequest = maxVolumePerRequest;
        return this;
    }

    @Override
    public BulkTransportClient flushIngestInterval(TimeValue flushInterval) {
        this.flushInterval = flushInterval;
        return this;
    }

    @Override
    public BulkTransportClient init(ElasticsearchClient client) throws IOException {
        return this.init(findSettings());
    }

    @Override
    public BulkTransportClient init(Map<String, String> settings) throws IOException {
        return this.init(Settings.settingsBuilder().put(settings).build());
    }

    @Override
    public BulkTransportClient init(Settings settings) throws IOException {
        super.init(settings);
        resetSettings();
        BulkProcessor.Listener listener = new BulkProcessor.Listener() {
            @Override
            public void beforeBulk(long executionId, BulkRequest request) {
                metric.getCurrentIngest().inc();
                long l = metric.getCurrentIngest().count();
                int n = request.numberOfActions();
                metric.getSubmitted().inc(n);
                metric.getCurrentIngestNumDocs().inc(n);
                metric.getTotalIngestSizeInBytes().inc(request.estimatedSizeInBytes());
                logger.debug("before bulk [{}] [actions={}] [bytes={}] [concurrent requests={}]",
                        executionId,
                        request.numberOfActions(),
                        request.estimatedSizeInBytes(),
                        l);
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
                metric.getCurrentIngest().dec();
                long l = metric.getCurrentIngest().count();
                metric.getSucceeded().inc(response.getItems().length);
                metric.getTotalIngest().inc(response.getTookInMillis());
                int n = 0;
                for (BulkItemResponse itemResponse : response.getItems()) {
                    if (itemResponse.isFailed()) {
                        n++;
                        metric.getSucceeded().dec(1);
                        metric.getFailed().inc(1);
                    }
                }
                logger.debug("after bulk [{}] [succeeded={}] [failed={}] [{}ms] [concurrent requests={}]",
                        executionId,
                        metric.getSucceeded().count(),
                        metric.getFailed().count(),
                        response.getTook().millis(),
                        l);
                if (n > 0) {
                    logger.error("bulk [{}] failed with {} failed items, failure message = {}",
                            executionId, n, response.buildFailureMessage());
                } else {
                    metric.getCurrentIngestNumDocs().dec(response.getItems().length);
                }
            }

            @Override
            public void afterBulk(long executionId, BulkRequest requst, Throwable failure) {
                metric.getCurrentIngest().dec();
                throwable = failure;
                closed = true;
                logger.error("bulk [" + executionId + "] error", failure);
            }
        };
        BulkProcessor.Builder builder = BulkProcessor.builder(client, listener)
                .setBulkActions(maxActionsPerRequest)
                .setConcurrentRequests(maxConcurrentRequests)
                .setFlushInterval(flushInterval);
        if (maxVolumePerRequest != null) {
            builder.setBulkSize(maxVolumePerRequest);
        }
        this.bulkProcessor = builder.build();
        this.closed = false;
        return this;
    }

    @Override
    public ElasticsearchClient client() {
        return client;
    }

    @Override
    public Metric getMetric() {
        return metric;
    }

    @Override
    public BulkTransportClient setMetric(Metric metric) {
        this.metric = metric;
        return this;
    }

    @Override
    public BulkTransportClient newIndex(String index) {
        if (closed) {
            throw new ElasticsearchException("client is closed");
        }
        super.newIndex(index);
        return this;
    }

    @Override
    public BulkTransportClient newIndex(String index, Settings settings, Map<String, String> mappings) {
        if (closed) {
            throw new ElasticsearchException("client is closed");
        }
        super.newIndex(index, settings, mappings);
        return this;
    }

    @Override
    public BulkTransportClient deleteIndex(String index) {
        if (closed) {
            throw new ElasticsearchException("client is closed");
        }
        super.deleteIndex(index);
        return this;
    }

    @Override
    public BulkTransportClient startBulk(String index, long startRefreshInterval, long stopRefreshIterval) throws IOException {
        super.startBulk(index, startRefreshInterval, stopRefreshIterval);
        return this;
    }

    @Override
    public BulkTransportClient stopBulk(String index) throws IOException {
        super.stopBulk(index);
        return this;
    }

    @Override
    public BulkTransportClient flushIndex(String index) {
        ClientHelper.flushIndex(client, index);
        return this;
    }

    @Override
    public BulkTransportClient refreshIndex(String index) {
        ClientHelper.refreshIndex(client, index);
        return this;
    }

    @Override
    public BulkTransportClient index(String index, String type, String id, String source) {
        if (closed) {
            throw new ElasticsearchException("client is closed");
        }
        try {
            metric.getCurrentIngest().inc();
            bulkProcessor.add(new IndexRequest(index).type(type).id(id).create(false).source(source));
        } catch (Exception e) {
            throwable = e;
            closed = true;
            logger.error("bulk add of index request failed: " + e.getMessage(), e);
        } finally {
            metric.getCurrentIngest().dec();
        }
        return this;
    }

    @Override
    public BulkTransportClient bulkIndex(IndexRequest indexRequest) {
        if (closed) {
            throw new ElasticsearchException("client is closed");
        }
        try {
            metric.getCurrentIngest().inc();
            bulkProcessor.add(indexRequest);
        } catch (Exception e) {
            throwable = e;
            closed = true;
            logger.error("bulk add of index request failed: " + e.getMessage(), e);
        } finally {
            metric.getCurrentIngest().dec();
        }
        return this;
    }

    @Override
    public BulkTransportClient delete(String index, String type, String id) {
        if (closed) {
            throw new ElasticsearchException("client is closed");
        }
        try {
            metric.getCurrentIngest().inc();
            bulkProcessor.add(new DeleteRequest(index).type(type).id(id));
        } catch (Exception e) {
            throwable = e;
            closed = true;
            logger.error("bulk add of delete request failed: " + e.getMessage(), e);
        } finally {
            metric.getCurrentIngest().dec();
        }
        return this;
    }

    @Override
    public BulkTransportClient bulkDelete(DeleteRequest deleteRequest) {
        if (closed) {
            throw new ElasticsearchException("client is closed");
        }
        try {
            metric.getCurrentIngest().inc();
            bulkProcessor.add(deleteRequest);
        } catch (Exception e) {
            throwable = e;
            closed = true;
            logger.error("bulk add of delete request failed: " + e.getMessage(), e);
        } finally {
            metric.getCurrentIngest().dec();
        }
        return this;
    }


    @Override
    public synchronized BulkTransportClient flushIngest() {
        if (closed) {
            throw new ElasticsearchException("client is closed");
        }
        if (client == null) {
            logger.warn("no client");
            return this;
        }
        logger.debug("flushing bulk processor");
        BulkProcessorHelper.flush(bulkProcessor);
        return this;
    }

    @Override
    public synchronized BulkTransportClient waitForResponses(TimeValue maxWaitTime) throws InterruptedException {
        if (closed) {
            throw new ElasticsearchException("client is closed");
        }
        if (client == null) {
            logger.warn("no client");
            return this;
        }
        BulkProcessorHelper.waitFor(bulkProcessor, maxWaitTime);
        return this;
    }

    @Override
    public BulkTransportClient waitForCluster(ClusterHealthStatus status, TimeValue timeValue) throws IOException {
        ClientHelper.waitForCluster(client, status, timeValue);
        return this;
    }

    @Override
    public int waitForRecovery(String index) throws IOException {
        return ClientHelper.waitForRecovery(client, index);
    }

    @Override
    public int updateReplicaLevel(String index, int level) throws IOException {
        return ClientHelper.updateReplicaLevel(client, index, level);
    }

    @Override
    public synchronized void shutdown() {
        if (closed) {
            super.shutdown();
            throw new ElasticsearchException("client is closed");
        }
        if (client == null) {
            logger.warn("no client");
            return;
        }
        try {
            if (bulkProcessor != null) {
                logger.debug("closing bulk processor...");
                bulkProcessor.close();
            }
            if (metric != null && metric.indices() != null && !metric.indices().isEmpty()) {
                logger.debug("stopping bulk mode for indices {}...", metric.indices());
                for (String index : ImmutableSet.copyOf(metric.indices())) {
                    stopBulk(index);
                }
            }
            logger.debug("shutting down...");
            super.shutdown();
            logger.debug("shutting down completed");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public boolean hasThrowable() {
        return throwable != null;
    }

    @Override
    public Throwable getThrowable() {
        return throwable;
    }
}
