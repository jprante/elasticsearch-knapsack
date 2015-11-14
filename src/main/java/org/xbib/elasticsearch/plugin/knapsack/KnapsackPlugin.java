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
package org.xbib.elasticsearch.plugin.knapsack;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.cluster.ClusterModule;
import org.elasticsearch.cluster.settings.Validator;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestModule;
import org.xbib.elasticsearch.action.knapsack.abort.KnapsackAbortAction;
import org.xbib.elasticsearch.action.knapsack.abort.TransportKnapsackAbortAction;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportAction;
import org.xbib.elasticsearch.action.knapsack.exp.TransportKnapsackExportAction;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportAction;
import org.xbib.elasticsearch.action.knapsack.imp.TransportKnapsackImportAction;
import org.xbib.elasticsearch.action.knapsack.pull.KnapsackPullAction;
import org.xbib.elasticsearch.action.knapsack.pull.TransportKnapsackPullAction;
import org.xbib.elasticsearch.action.knapsack.push.KnapsackPushAction;
import org.xbib.elasticsearch.action.knapsack.push.TransportKnapsackPushAction;
import org.xbib.elasticsearch.action.knapsack.state.KnapsackStateAction;
import org.xbib.elasticsearch.action.knapsack.state.TransportKnapsackStateAction;
import org.xbib.elasticsearch.knapsack.KnapsackModule;
import org.xbib.elasticsearch.knapsack.KnapsackService;
import org.xbib.elasticsearch.rest.action.knapsack.abort.RestKnapsackAbortAction;
import org.xbib.elasticsearch.rest.action.knapsack.exp.RestKnapsackExportAction;
import org.xbib.elasticsearch.rest.action.knapsack.imp.RestKnapsackImportAction;
import org.xbib.elasticsearch.rest.action.knapsack.pull.RestKnapsackPullAction;
import org.xbib.elasticsearch.rest.action.knapsack.push.RestKnapsackPushAction;
import org.xbib.elasticsearch.rest.action.knapsack.state.RestKnapsackStateAction;

import java.util.ArrayList;
import java.util.Collection;

public class KnapsackPlugin extends Plugin {

    private final static String ENABLED = "plugins.knapsack.enabled";

    private final Settings settings;

    @Inject
    public KnapsackPlugin(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String name() {
        return "knapsack";
    }

    @Override
    public String description() {
        return "Knapsack import/export plugin for Elasticsearch";
    }

    public void onModule(ActionModule module) {
        if (settings.getAsBoolean(ENABLED, true)) {
            module.registerAction(KnapsackExportAction.INSTANCE, TransportKnapsackExportAction.class);
            module.registerAction(KnapsackPushAction.INSTANCE, TransportKnapsackPushAction.class);
            module.registerAction(KnapsackImportAction.INSTANCE, TransportKnapsackImportAction.class);
            module.registerAction(KnapsackPullAction.INSTANCE, TransportKnapsackPullAction.class);
            module.registerAction(KnapsackStateAction.INSTANCE, TransportKnapsackStateAction.class);
            module.registerAction(KnapsackAbortAction.INSTANCE, TransportKnapsackAbortAction.class);
        }
    }

    public void onModule(RestModule module) {
        if (settings.getAsBoolean(ENABLED, true)) {
            module.addRestAction(RestKnapsackExportAction.class);
            module.addRestAction(RestKnapsackImportAction.class);
            module.addRestAction(RestKnapsackPushAction.class);
            module.addRestAction(RestKnapsackPullAction.class);
            module.addRestAction(RestKnapsackStateAction.class);
            module.addRestAction(RestKnapsackAbortAction.class);
        }
    }

    /*public void onModule(ClusterModule module) {
        if ("node".equals(settings.get("client.type")) && settings.getAsBoolean(ENABLED, true)) {
            module.registerClusterDynamicSetting(KnapsackService.EXPORT_STATE_SETTING_NAME, Validator.EMPTY);
            module.registerClusterDynamicSetting(KnapsackService.IMPORT_STATE_SETTING_NAME, Validator.EMPTY);
        }
    }*/

    @Override
    public Collection<Module> nodeModules() {
        Collection<Module> modules = new ArrayList<>();
        if ("node".equals(settings.get("client.type")) && settings.getAsBoolean(ENABLED, true)) {
            modules.add(new KnapsackModule());
        }
        return modules;
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> nodeServices() {
        Collection<Class<? extends LifecycleComponent>> services = new ArrayList<>();
        if ("node".equals(settings.get("client.type")) && settings.getAsBoolean(ENABLED, true)) {
            services.add(KnapsackService.class);
        }
        return services;
    }

}
