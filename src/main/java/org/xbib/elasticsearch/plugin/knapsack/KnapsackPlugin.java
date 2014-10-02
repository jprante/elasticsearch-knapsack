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
import org.elasticsearch.cluster.settings.ClusterDynamicSettingsModule;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.rest.RestModule;
import org.xbib.elasticsearch.action.knapsack.abort.KnapsackAbortAction;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportAction;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportAction;
import org.xbib.elasticsearch.action.knapsack.pull.KnapsackPullAction;
import org.xbib.elasticsearch.action.knapsack.pull.TransportKnapsackPullAction;
import org.xbib.elasticsearch.action.knapsack.push.KnapsackPushAction;
import org.xbib.elasticsearch.action.knapsack.push.TransportKnapsackPushAction;
import org.xbib.elasticsearch.action.knapsack.state.KnapsackStateAction;
import org.xbib.elasticsearch.action.knapsack.abort.TransportKnapsackAbortAction;
import org.xbib.elasticsearch.action.knapsack.exp.TransportKnapsackExportAction;
import org.xbib.elasticsearch.action.knapsack.imp.TransportKnapsackImportAction;
import org.xbib.elasticsearch.action.knapsack.state.TransportKnapsackStateAction;
import org.xbib.elasticsearch.knapsack.KnapsackModule;
import org.xbib.elasticsearch.knapsack.KnapsackService;
import org.xbib.elasticsearch.rest.action.knapsack.abort.RestKnapsackAbortAction;
import org.xbib.elasticsearch.rest.action.knapsack.exp.RestKnapsackExportAction;
import org.xbib.elasticsearch.rest.action.knapsack.imp.RestKnapsackImportAction;
import org.xbib.elasticsearch.rest.action.knapsack.pull.RestKnapsackPullAction;
import org.xbib.elasticsearch.rest.action.knapsack.push.RestKnapsackPushAction;
import org.xbib.elasticsearch.rest.action.knapsack.state.RestKnapsackStateAction;

import java.util.Collection;

import static org.elasticsearch.common.collect.Lists.newArrayList;

public class KnapsackPlugin extends AbstractPlugin {

    @Override
    public String name() {
        return "knapsack-" +
                Build.getInstance().getVersion() + "-" +
                Build.getInstance().getShortHash();
    }

    @Override
    public String description() {
        return "Knapsack plugin for import/export";
    }

    public void onModule(ActionModule module) {
        module.registerAction(KnapsackExportAction.INSTANCE, TransportKnapsackExportAction.class);
        module.registerAction(KnapsackPushAction.INSTANCE, TransportKnapsackPushAction.class);
        module.registerAction(KnapsackImportAction.INSTANCE, TransportKnapsackImportAction.class);
        module.registerAction(KnapsackPullAction.INSTANCE, TransportKnapsackPullAction.class);
        module.registerAction(KnapsackStateAction.INSTANCE, TransportKnapsackStateAction.class);
        module.registerAction(KnapsackAbortAction.INSTANCE, TransportKnapsackAbortAction.class);
    }

    public void onModule(RestModule module) {
        module.addRestAction(RestKnapsackExportAction.class);
        module.addRestAction(RestKnapsackImportAction.class);
        module.addRestAction(RestKnapsackPushAction.class);
        module.addRestAction(RestKnapsackPullAction.class);
        module.addRestAction(RestKnapsackStateAction.class);
        module.addRestAction(RestKnapsackAbortAction.class);
    }

    public void onModule(ClusterDynamicSettingsModule module) {
        module.addDynamicSettings(KnapsackService.EXPORT_STATE_SETTING_NAME);
        module.addDynamicSettings(KnapsackService.IMPORT_STATE_SETTING_NAME);
    }

    @Override
    public Collection<Class<? extends Module>> modules() {
        Collection<Class<? extends Module>> modules = newArrayList();
        modules.add(KnapsackModule.class);
        return modules;
    }

}
