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

import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.collect.ImmutableList.Builder;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.common.collect.Lists.newArrayList;
import static org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS;
import static org.elasticsearch.common.xcontent.XContentFactory.xContent;
import static org.elasticsearch.common.xcontent.XContentParser.Token.END_ARRAY;
import static org.elasticsearch.common.xcontent.XContentType.JSON;

public class KnapsackService extends AbstractLifecycleComponent<KnapsackService> {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackService.class.getSimpleName());

    public static final String EXPORT_STATE_SETTING_NAME = "plugin.knapsack.export.state";

    public static final String IMPORT_STATE_SETTING_NAME = "plugin.knapsack.import.state";

    private final ClusterService clusterService;

    private ExecutorService executor;

    private List<Future<?>> tasks;

    @Inject
    public KnapsackService(Settings settings, ClusterService clusterService) {
        super(settings);
        this.clusterService = clusterService;
        this.tasks = newArrayList();
        this.executor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void doStart() throws ElasticSearchException {
    }

    @Override
    protected void doStop() throws ElasticSearchException {
        this.executor.shutdownNow();
        try {
            this.executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new ElasticSearchException(e.getMessage());
        }
    }

    @Override
    protected void doClose() throws ElasticSearchException {
    }

    public List<KnapsackState> getImports(Client client) throws IOException {
        return get(IMPORT_STATE_SETTING_NAME);
    }

    public void addImport(Client client, KnapsackState newImport) throws IOException {
        add(client, IMPORT_STATE_SETTING_NAME, getImports(client), newImport);
    }

    public void removeImport(Client client, KnapsackState targetImport) throws IOException {
        remove(client, IMPORT_STATE_SETTING_NAME, getImports(client), targetImport);
    }

    public void updateImport(Client client, KnapsackState targetImport) throws IOException {
        update(client, IMPORT_STATE_SETTING_NAME, getImports(client), targetImport);
    }

    public List<KnapsackState> getExports(Client client) throws IOException {
        return get(EXPORT_STATE_SETTING_NAME);
    }

    public void addExport(Client client, KnapsackState newExport) throws IOException {
        add(client, EXPORT_STATE_SETTING_NAME, getExports(client), newExport);
    }

    public void removeExport(Client client, KnapsackState targetExport) throws IOException {
        remove(client, EXPORT_STATE_SETTING_NAME, getExports(client), targetExport);
    }

    public void updateExport(Client client, KnapsackState targetExport) throws IOException {
        update(client, EXPORT_STATE_SETTING_NAME, getExports(client), targetExport);
    }

    private List<KnapsackState> get(String name) throws IOException {
        return parseStates(getClusterSetting(name));
    }

    private void add(Client client, String name, List<KnapsackState> values, KnapsackState targetValue) throws IOException {
        logger.info("add: {} -> {}", name, values);
        updateClusterSettings(client, name, generateSetting(ImmutableList.<KnapsackState>builder()
                .addAll(values).add(targetValue).build()));
    }

    private void remove(Client client, String name, List<KnapsackState> values, KnapsackState targetValue) throws IOException {
        logger.info("remove: {} -> {}", name, values);
        Builder<KnapsackState> updatedValues = ImmutableList.builder();
        for (KnapsackState value : values) {
            if (!value.equals(targetValue)) {
                updatedValues.add(value);
            }
        }
        updateClusterSettings(client, name, generateSetting(updatedValues.build()));
    }

    private void update(Client client, String name, List<KnapsackState> values, KnapsackState targetValue) throws IOException {
        Builder<KnapsackState> updatedValues = ImmutableList.builder();
        for (KnapsackState value : values) {
            if (value.equals(targetValue)) {
                updatedValues.add(targetValue);
            } else {
                updatedValues.add(value);
            }
        }
        updateClusterSettings(client, name, generateSetting(updatedValues.build()));
    }

    private String getClusterSetting(String name) {
        return getClusterSettings().get(name, "[]");
    }

    private Settings getClusterSettings() {
        return clusterService.state().getMetaData().transientSettings();
    }

    private void updateClusterSettings(Client client, String name, String value) {
        logger.info("update cluster settings: {} -> {}", name, value);
        client.admin().cluster().prepareUpdateSettings()
                .setTransientSettings(ImmutableSettings.settingsBuilder()
                        .put(getClusterSettings())
                        .put(name, value)
                        .build())
                .execute()
                .actionGet();
    }

    private static List<KnapsackState> parseStates(String value) throws IOException {
        XContentParser parser = xContent(JSON).createParser(value);
        Builder<KnapsackState> builder = ImmutableList.builder();
        parser.nextToken();
        while (parser.nextToken() != END_ARRAY) {
            KnapsackState state = new KnapsackState();
            builder.add(state.fromXContent(parser));
        }
        return builder.build();
    }

    private static String generateSetting(List<KnapsackState> values) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startArray();
        for (KnapsackState value : values) {
            value.toXContent(builder, EMPTY_PARAMS);
        }
        builder.endArray();
        return builder.string();
    }

    public void submit(Runnable runnable) {
        Future<?> f = executor.submit(runnable);
        tasks.add(f);
    }

    public int abort() {
        int size = tasks.size();
        logger.info("aborting {} tasks", size);
        for (Future<?> f : tasks) {
            boolean b = f.cancel(true);
            if (!b) {
                logger.error("task {} could not be cancelled", f);
            }
        }
        tasks.clear();
        logger.info("shutdown");
        executor.shutdown();
        try {
            this.executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new ElasticSearchException(e.getMessage());
        }
        logger.info("shutdown now");
        List<Runnable> runnables = executor.shutdownNow();
        try {
            this.executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new ElasticSearchException(e.getMessage());
        }
        logger.info("task queue size was {}, setting new executor", runnables.size());
        this.executor = Executors.newSingleThreadExecutor();
        return size;
    }

}