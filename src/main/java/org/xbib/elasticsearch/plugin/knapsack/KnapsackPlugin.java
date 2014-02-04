
package org.xbib.elasticsearch.plugin.knapsack;

import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.rest.RestModule;
import org.xbib.elasticsearch.action.RestExportAction;
import org.xbib.elasticsearch.action.RestImportAction;

public class KnapsackPlugin extends AbstractPlugin {

    @Override
    public String name() {
        return "knapsack-" + Build.getInstance().getVersion();
    }

    @Override
    public String description() {
        return "Knapsack plugin for import/export";
    }

    public void onModule(RestModule module) {
        module.addRestAction(RestExportAction.class);
        module.addRestAction(RestImportAction.class);
        MetaData.addDynamicSettings(KnapsackHelper.EXPORT_STATE_SETTING_NAME);
        MetaData.addDynamicSettings(KnapsackHelper.IMPORT_STATE_SETTING_NAME);
    }

}
