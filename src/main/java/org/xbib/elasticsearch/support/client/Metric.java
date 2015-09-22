package org.xbib.elasticsearch.support.client;

import org.elasticsearch.common.metrics.CounterMetric;
import org.elasticsearch.common.metrics.MeanMetric;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Metric {

    private final Set<String> indexNames = new HashSet<String>();
    private final Map<String, Long> startBulkRefreshIntervals = new HashMap<String, Long>();
    private final Map<String, Long> stopBulkRefreshIntervals = new HashMap<String, Long>();
    private final MeanMetric totalIngest = new MeanMetric();
    private final CounterMetric totalIngestSizeInBytes = new CounterMetric();
    private final CounterMetric currentIngest = new CounterMetric();
    private final CounterMetric currentIngestNumDocs = new CounterMetric();
    private final CounterMetric submitted = new CounterMetric();
    private final CounterMetric succeeded = new CounterMetric();
    private final CounterMetric failed = new CounterMetric();
    private long started;

    public MeanMetric getTotalIngest() {
        return totalIngest;
    }

    public CounterMetric getTotalIngestSizeInBytes() {
        return totalIngestSizeInBytes;
    }

    public CounterMetric getCurrentIngest() {
        return currentIngest;
    }

    public CounterMetric getCurrentIngestNumDocs() {
        return currentIngestNumDocs;
    }

    public CounterMetric getSubmitted() {
        return submitted;
    }

    public CounterMetric getSucceeded() {
        return succeeded;
    }

    public CounterMetric getFailed() {
        return failed;
    }

    public Metric start() {
        this.started = System.nanoTime();
        return this;
    }

    public long elapsed() {
        return System.nanoTime() - started;
    }

    public Metric setupBulk(String indexName, long startRefreshInterval, long stopRefreshInterval) {
        synchronized (indexNames) {
            indexNames.add(indexName);
            startBulkRefreshIntervals.put(indexName, startRefreshInterval);
            stopBulkRefreshIntervals.put(indexName, stopRefreshInterval);
        }
        return this;
    }

    public boolean isBulk(String indexName) {
        return indexNames.contains(indexName);
    }

    public Metric removeBulk(String indexName) {
        synchronized (indexNames) {
            indexNames.remove(indexName);
        }
        return this;
    }

    public Set<String> indices() {
        return indexNames;
    }

    public Map<String, Long> getStartBulkRefreshIntervals() {
        return startBulkRefreshIntervals;
    }

    public Map<String, Long> getStopBulkRefreshIntervals() {
        return stopBulkRefreshIntervals;
    }

}
