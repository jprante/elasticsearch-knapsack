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
package org.xbib.elasticsearch.knapsack;

import com.google.common.collect.ImmutableList;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.recovery.RecoveryResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.indices.IndexAlreadyExistsException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.common.xcontent.XContentFactory.xContent;
import static org.elasticsearch.common.xcontent.XContentParser.Token.START_ARRAY;
import static org.elasticsearch.common.xcontent.XContentParser.Token.END_ARRAY;
import static org.elasticsearch.common.xcontent.XContentType.JSON;

public class KnapsackService extends AbstractLifecycleComponent<KnapsackService> {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackService.class.getSimpleName());

    public static final String INDEX_NAME = ".knapsack";

    private static final String MAPPING_NAME = "knapsack";

    private static final String EXPORT_NAME = "export";

    private static final String IMPORT_NAME = "import";

    private final Injector injector;

    private ExecutorService executor;

    private List<Future<?>> tasks;

    @Inject
    public KnapsackService(Settings settings, Injector injector) {
        super(settings);
        this.injector = injector;
    }

    @Override
    protected void doStart() throws ElasticsearchException {
        this.tasks = new ArrayList<>();
        this.executor = newExecutorService();
    }

    @Override
    protected void doStop() throws ElasticsearchException {
    }

    @Override
    protected void doClose() throws ElasticsearchException {
        int size = tasks.size();
        if (size > 0) {
            for (Future<?> f : tasks) {
                if (!f.isDone()) {
                    logger.info("aborting knapsack task {}", f);
                    boolean b = f.cancel(true);
                    if (!b) {
                        logger.error("knapsack task {} could not be cancelled", f);
                    }
                }
            }
            tasks.clear();
        }
        logger.info("knapsack shutdown...");
        executor.shutdown();
        try {
            this.executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new ElasticsearchException(e.getMessage());
        }
        if (!executor.isShutdown()) {
            logger.info("knapsack shutdown now");
            executor.shutdownNow();
            try {
                this.executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new ElasticsearchException(e.getMessage());
            }
        }
        logger.info("knapsack shutdown complete");
    }

    protected ExecutorService newExecutorService() {
        return Executors.newFixedThreadPool(4);
    }

    public List<KnapsackState> getImports() throws IOException {
        return get(IMPORT_NAME);
    }

    public void addImport(KnapsackState newImport) throws IOException {
        add(IMPORT_NAME, getImports(), newImport);
    }

    public void removeImport(KnapsackState targetImport) throws IOException {
        remove(IMPORT_NAME, getImports(), targetImport);
    }

    public List<KnapsackState> getExports() throws IOException {
        return get(EXPORT_NAME);
    }

    public void addExport(KnapsackState newExport) throws IOException {
        add(EXPORT_NAME, getExports(), newExport);
    }

    public void removeExport(KnapsackState targetExport) throws IOException {
        remove(EXPORT_NAME, getExports(), targetExport);
    }

    private void add(String name, List<KnapsackState> values, KnapsackState targetValue) throws IOException {
        logger.debug("add: {} -> {}", name, values);
        if (values == null) {
            values = Collections.emptyList();
        }
        put(name, generate(ImmutableList.<KnapsackState>builder()
                .addAll(values)
                .add(targetValue)
                .build()));
    }

    private void remove(String name, List<KnapsackState> values, KnapsackState targetValue) throws IOException {
        logger.debug("remove: {} -> {}", name, values);
        ImmutableList.Builder<KnapsackState> updatedValues = ImmutableList.builder();
        for (KnapsackState value : values) {
            if (!value.equals(targetValue)) {
                updatedValues.add(value);
            }
        }
        put(name, generate(updatedValues.build()));
    }

    private List<KnapsackState> get(String name) throws IOException {
        ImmutableList.Builder<KnapsackState> builder = ImmutableList.builder();
        try {
            logger.debug("get knapsack states: {}", name);
            final Client client = injector.getInstance(Client.class);
            createIndexIfNotExist(client);
            GetResponse getResponse = client.prepareGet(INDEX_NAME, MAPPING_NAME, name).execute().actionGet();
            if (!getResponse.isExists()) {
                return builder.build();
            }
            XContentParser parser = xContent(JSON).createParser(getResponse.getSourceAsBytes());
            while (parser.nextToken() != START_ARRAY) {
                // forward
            }
            while (parser.nextToken() != END_ARRAY) {
                KnapsackState state = new KnapsackState();
                builder.add(state.fromXContent(parser));
            }
            return builder.build();
        } catch (Throwable t) {
            logger.error("get settings failed", t);
            return null;
        }
    }

    private void put(final String name, final XContentBuilder builder) {
        try {
            logger.debug("put knapsack state: {} -> {}", name, builder.string());
            final Client client = injector.getInstance(Client.class);
            createIndexIfNotExist(client);
            client.prepareIndex(INDEX_NAME, MAPPING_NAME, name)
                    .setSource(builder)
                    .setRefresh(true)
                    .execute().actionGet();
        } catch (Throwable t) {
            logger.error("update settings failed", t);
        }
    }

    private void remove(final String name) {
        try {
            logger.debug("remove: {}", name);
            final Client client = injector.getInstance(Client.class);
            createIndexIfNotExist(client);
            client.prepareDelete(INDEX_NAME, MAPPING_NAME, name)
                    .setRefresh(true)
                    .execute().actionGet();
        } catch (Throwable t) {
            logger.error("remove failed", t);
        }
    }

    private static XContentBuilder generate(List<KnapsackState> values) throws IOException {
        XContentBuilder builder = jsonBuilder();
        builder.startObject();
        builder.startArray("array");
        for (KnapsackState value : values) {
            value.toXContent(builder, EMPTY_PARAMS);
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }

    private void createIndexIfNotExist(Client client) {
        try {
            client.admin().indices().prepareCreate(INDEX_NAME).execute().actionGet();
            RecoveryResponse response = client.admin().indices().prepareRecoveries(INDEX_NAME).execute().actionGet();
            int shards = response.getTotalShards();
            client.admin().cluster().prepareHealth(INDEX_NAME)
                    .setWaitForActiveShards(shards)
                    .setWaitForYellowStatus()
                    .execute().actionGet();
        } catch (IndexAlreadyExistsException e) {
            // ignore
        }
    }

    public void submit(Runnable runnable) {
        Iterator<Future<?>> it = tasks.iterator();
        while (it.hasNext()) {
            Future<?> f = it.next();
            if (f.isDone()) {
                it.remove();
            }
        }
        Future<?> f = executor.submit(runnable);
        tasks.add(f);
    }

    public void abort(boolean reset) {
        doClose();
        this.executor = newExecutorService();
        if (reset) {
            remove(EXPORT_NAME);
            remove(IMPORT_NAME);
        }
    }
}