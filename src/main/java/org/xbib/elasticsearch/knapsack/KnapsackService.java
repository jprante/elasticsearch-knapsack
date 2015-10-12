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
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateUpdateTask;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.common.xcontent.ToXContent.EMPTY_PARAMS;
import static org.elasticsearch.common.xcontent.XContentFactory.xContent;
import static org.elasticsearch.common.xcontent.XContentParser.Token.END_ARRAY;
import static org.elasticsearch.common.xcontent.XContentType.JSON;

public class KnapsackService extends AbstractLifecycleComponent<KnapsackService> {

    private final static ESLogger logger = ESLoggerFactory.getLogger(KnapsackService.class.getSimpleName());

    public static final String EXPORT_STATE_SETTING_NAME = "plugin.knapsack.export.state";

    public static final String IMPORT_STATE_SETTING_NAME = "plugin.knapsack.import.state";

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
        return get(IMPORT_STATE_SETTING_NAME);
    }

    public void addImport(KnapsackState newImport) throws IOException {
        add(IMPORT_STATE_SETTING_NAME, getImports(), newImport);
    }

    public void removeImport(KnapsackState targetImport) throws IOException {
        remove(IMPORT_STATE_SETTING_NAME, getImports(), targetImport);
    }

    public void updateImport(KnapsackState targetImport) throws IOException {
        update(IMPORT_STATE_SETTING_NAME, getImports(), targetImport);
    }

    public List<KnapsackState> getExports() throws IOException {
        return get(EXPORT_STATE_SETTING_NAME);
    }

    public void addExport(KnapsackState newExport) throws IOException {
        add(EXPORT_STATE_SETTING_NAME, getExports(), newExport);
    }

    public void removeExport(KnapsackState targetExport) throws IOException {
        remove(EXPORT_STATE_SETTING_NAME, getExports(), targetExport);
    }

    public void updateExport(KnapsackState targetExport) throws IOException {
        update(EXPORT_STATE_SETTING_NAME, getExports(), targetExport);
    }

    private List<KnapsackState> get(String name) throws IOException {
        return parseStates(getClusterSetting(name));
    }

    private void add(String name, List<KnapsackState> values, KnapsackState targetValue) throws IOException {
        logger.debug("add: {} -> {}", name, values);
        updateClusterSettings(name, generateSetting(ImmutableList.<KnapsackState>builder()
                .addAll(values).add(targetValue).build()));
    }

    private void remove(String name, List<KnapsackState> values, KnapsackState targetValue) throws IOException {
        logger.debug("remove: {} -> {}", name, values);
        ImmutableList.Builder<KnapsackState> updatedValues = ImmutableList.builder();
        for (KnapsackState value : values) {
            if (!value.equals(targetValue)) {
                updatedValues.add(value);
            }
        }
        updateClusterSettings(name, generateSetting(updatedValues.build()));
    }

    private void update(String name, List<KnapsackState> values, KnapsackState targetValue) throws IOException {
        ImmutableList.Builder<KnapsackState> updatedValues = ImmutableList.builder();
        for (KnapsackState value : values) {
            if (value.equals(targetValue)) {
                updatedValues.add(targetValue);
            } else {
                updatedValues.add(value);
            }
        }
        updateClusterSettings(name, generateSetting(updatedValues.build()));
    }

    private String getClusterSetting(String name) {
        return getClusterSettings().get(name, "[]");
    }

    private Settings getClusterSettings() {
        ClusterService clusterService = injector.getInstance(ClusterService.class);
        return clusterService.state().getMetaData().transientSettings();
    }

    private void removeClusterSettings(final String name) {
        updateClusterSettings(name, null);
    }

    private void updateClusterSettings(final String name, final String value) {
        logger.debug("update cluster settings: {} -> {}", name, value);
        final ClusterService clusterService = injector.getInstance(ClusterService.class);
        try {
            clusterService.submitStateUpdateTask("knapsack", new ClusterStateUpdateTask() {
                @Override
                public ClusterState execute(ClusterState currentState) throws Exception {
                    Settings oldSettings = clusterService.state().getMetaData().transientSettings();
                    MetaData.Builder mdBuilder = MetaData.builder(currentState.metaData());
                    if (value != null) {
                        Settings newSettings = Settings.settingsBuilder()
                                .put(oldSettings)
                                .put(name, value)
                                .build();
                        mdBuilder.transientSettings(newSettings);
                        logger.debug("cluster transient settings update done: {} -> {}",
                                oldSettings.getAsMap(), newSettings.getAsMap());
                    } else {
                        Settings.Builder newSettings = Settings.settingsBuilder()
                                .put(oldSettings);
                        newSettings.remove(name);
                        mdBuilder.transientSettings(newSettings.build());
                        logger.debug("cluster transient settings update done: {} -> {}",
                                oldSettings.getAsMap(), newSettings.build());
                    }
                    return ClusterState.builder(currentState).metaData(mdBuilder).build();
                }

                @Override
                public void onFailure(String source, Throwable t) {
                    logger.error("submitStateUpdateTask failure", t);
                }
            });
        } catch (Throwable t) {
            logger.error("submitStateUpdateTask failure", t);
        }
    }

    private static List<KnapsackState> parseStates(String value) throws IOException {
        XContentParser parser = xContent(JSON).createParser(value);
        ImmutableList.Builder<KnapsackState> builder = ImmutableList.builder();
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
            removeClusterSettings("import");
            removeClusterSettings("export");
        }
    }
}