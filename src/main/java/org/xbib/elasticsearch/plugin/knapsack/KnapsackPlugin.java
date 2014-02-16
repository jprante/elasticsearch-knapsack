
package org.xbib.elasticsearch.plugin.knapsack;

import org.elasticsearch.cluster.settings.ClusterDynamicSettingsModule;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.rest.RestModule;
import org.xbib.elasticsearch.action.RestExportAction;
import org.xbib.elasticsearch.action.RestImportAction;

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

    public void onModule(RestModule module) {
        module.addRestAction(RestExportAction.class);
        module.addRestAction(RestImportAction.class);
    }

    public void onModule(ClusterDynamicSettingsModule module) {
        module.addDynamicSetting(KnapsackHelper.EXPORT_STATE_SETTING_NAME);
        module.addDynamicSetting(KnapsackHelper.IMPORT_STATE_SETTING_NAME);
    }

}
