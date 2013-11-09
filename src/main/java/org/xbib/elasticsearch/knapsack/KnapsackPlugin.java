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
package org.xbib.elasticsearch.knapsack;

import org.elasticsearch.cluster.settings.ClusterDynamicSettingsModule;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.rest.RestModule;
import org.xbib.elasticsearch.action.RestExportAction;
import org.xbib.elasticsearch.action.RestImportAction;

public class KnapsackPlugin extends AbstractPlugin {

    @Override
    public String name() {
        return "knapsack";
    }

    @Override
    public String description() {
        return "Knapsack plugin";
    }  
  
    public void onModule(RestModule module) {
        module.addRestAction(RestExportAction.class);
        module.addRestAction(RestImportAction.class);
    }

    public void onModule(ClusterDynamicSettingsModule module) {
        module.addDynamicSettings(KnapsackService.EXPORT_STATE_SETTING_NAME);
        module.addDynamicSettings(KnapsackService.IMPORT_STATE_SETTING_NAME);
    }

}
