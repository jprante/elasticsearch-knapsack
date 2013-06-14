/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.xbib.io;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.logging.ESLogger;

/**
 * Send bulk data to Elasticsearch
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public class BulkOperation {

    private String index;

    private String type;

    private String id;

    private Client client;

    private ESLogger logger;

    private int bulkSize = 100;

    private int maxActiveRequests = 30;

    private long millisBeforeContinue = 60000L;

    private int totalTimeouts;

    private static final int MAX_TOTAL_TIMEOUTS = 10;

    private static final AtomicInteger activeBulks = new AtomicInteger(0);

    private static final AtomicLong counter = new AtomicLong(0);

    private ThreadLocal<BulkRequestBuilder> currentBulk = new ThreadLocal();

    public BulkOperation(Client client, ESLogger logger) {
        this.client = client;
        this.logger = logger;
        this.id = null;
        this.totalTimeouts = 0;
    }
    
    public BulkOperation setIndex(String index) {
        this.index = index;
        return this;
    }
    
    public String getIndex() {
        return index;
    }
    
    public BulkOperation setType(String type) {
        this.type = type;
        return this;
    }
    
    public String getType() {
        return type;
    }

    public BulkOperation setId(String id) {
        this.id = id;
        return this;
    }
    
    public String getId() {
        return id;
    }

    public long getCount() {
        return counter.longValue();
    }
    
    public BulkOperation setBulkSize(int bulkSize) {
        this.bulkSize = bulkSize;
        return this;
    }

    public BulkOperation setMaxActiveRequests(int maxActiveRequests) {
        this.maxActiveRequests = maxActiveRequests;
        return this;
    }

    public BulkOperation setMillisBeforeContinue(long millis) {
        this.millisBeforeContinue = millis;
        return this;
    }

    public void create(String index, String type, String id, String source) {
        if (!isNullOrEmpty(index)) {
            setIndex(index);
        }
        if (!isNullOrEmpty(type)) {
            setType(type);
        }
        if (!isNullOrEmpty(id)) {
           setId(id);
        }
        if (id == null) {
            return; // skip if ID is null
        }
        if (currentBulk.get() == null) {
            currentBulk.set(client.prepareBulk());
        }
        IndexRequest request = Requests.indexRequest(getIndex()).type(getType()).id(getId()).create(true).source(source);
        currentBulk.get().add(request);
        if (currentBulk.get().numberOfActions() >= bulkSize) {
            processBulk();
        }
    }    
    
    public void index(String index, String type, String id, String source) {
        if (!isNullOrEmpty(index)) {
            setIndex(index);
        }
        if (!isNullOrEmpty(type)) {
            setType(type);
        }
        if (!isNullOrEmpty(id)) {
           setId(id);
        }
        if (id == null) {
            return; // skip if ID is null
        }
        if (currentBulk.get() == null) {
            currentBulk.set(client.prepareBulk());
        }
        IndexRequest request = Requests.indexRequest(getIndex()).type(getType()).id(getId()).source(source);
        currentBulk.get().add(request);
        if (currentBulk.get().numberOfActions() >= bulkSize) {
            processBulk();
        }
    }
    
    public void delete(String index, String type, String id) {
        if (!isNullOrEmpty(index)) {
            setIndex(index);
        }
        if (!isNullOrEmpty(type)) {
            setType(type);
        }
        if (!isNullOrEmpty(id)) {
           setId(id);
        }
        if (id == null) {
            return; // skip
        }
        if (currentBulk.get() == null) {
            currentBulk.set(client.prepareBulk());
        }
        currentBulk.get().add(Requests.deleteRequest(getIndex()).type(getType()).id(getId()));
        if (currentBulk.get().numberOfActions() >= bulkSize) {
            processBulk();
        }        
    }
    
    public void flush() throws IOException {
        if (totalTimeouts > MAX_TOTAL_TIMEOUTS) {
            // waiting some minutes is much too long, do not wait any longer            
            throw new IOException("total timeouts exceeded limit of + " + MAX_TOTAL_TIMEOUTS + ", aborting");
        }
        if (currentBulk.get() != null && currentBulk.get().numberOfActions() > 0) {
            processBulk();
        }
        // wait for all outstanding bulk requests
        while (activeBulks.intValue() > 0) {
            logger.info("waiting for {} active bulk requests", activeBulks);
            synchronized (activeBulks) {
                try {
                    activeBulks.wait(millisBeforeContinue);
                } catch (InterruptedException e) {
                    logger.warn("timeout while waiting, continuing after {} ms", millisBeforeContinue);
                    totalTimeouts++;
                }
            }
        }
    }

    private void processBulk() {
        while (activeBulks.intValue() >= maxActiveRequests) {
            logger.info("waiting for {} active bulk requests", activeBulks);
            synchronized (activeBulks) {
                try {
                    activeBulks.wait(millisBeforeContinue);
                } catch (InterruptedException e) {
                    logger.warn("timeout while waiting, continuing after {} ms", millisBeforeContinue);
                    totalTimeouts++;
                }
            }
        }
        final int currentOnGoingBulks = activeBulks.incrementAndGet();
        final int numberOfActions = currentBulk.get().numberOfActions();
        logger.info("submitting new bulk request ({} docs, {} requests currently active)", numberOfActions, currentOnGoingBulks);
        try {
            currentBulk.get().execute(new ActionListener<BulkResponse>() {

                @Override
                public void onResponse(BulkResponse bulkResponse) {
                    if (bulkResponse.hasFailures()) {
                        logger.error("bulk request has failures: {}", bulkResponse.buildFailureMessage());
                    } else {
                        final long totalActions = counter.addAndGet(numberOfActions);
                        logger.info("bulk request success ({} millis, {} docs, total of {} docs)",
                                bulkResponse.getTookInMillis(), numberOfActions, totalActions);
                    }
                    activeBulks.decrementAndGet();
                    synchronized (activeBulks) {
                        activeBulks.notifyAll();
                    }
                }

                @Override
                public void onFailure(Throwable e) {
                    logger.error("bulk request failed", e);
                }
            });
        } catch (Exception e) {
            logger.error("unhandled exception, failed to execute bulk request", e);
        } finally {
            currentBulk.set(client.prepareBulk());
        }
    }

    private boolean isNullOrEmpty(String s) {
        return s == null || s.length() == 0;
    }
    
}
