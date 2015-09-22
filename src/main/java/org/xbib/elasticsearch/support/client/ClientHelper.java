package org.xbib.elasticsearch.support.client;

import org.elasticsearch.ElasticsearchTimeoutException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthAction;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.cluster.state.ClusterStateAction;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequestBuilder;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.admin.indices.flush.FlushAction;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingAction;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.recovery.RecoveryAction;
import org.elasticsearch.action.admin.indices.recovery.RecoveryRequest;
import org.elasticsearch.action.admin.indices.recovery.RecoveryResponse;
import org.elasticsearch.action.admin.indices.refresh.RefreshAction;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsAction;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;

import java.io.IOException;
import java.util.Map;

public class ClientHelper {

    public static void updateIndexSetting(ElasticsearchClient client, String index, String key, Object value) throws IOException {
        if (client == null) {
            throw new IOException("no client");
        }
        if (index == null) {
            throw new IOException("no index name given");
        }
        if (key == null) {
            throw new IOException("no key given");
        }
        if (value == null) {
            throw new IOException("no value given");
        }
        Settings.Builder settingsBuilder = Settings.settingsBuilder();
        settingsBuilder.put(key, value.toString());
        UpdateSettingsRequest updateSettingsRequest = new UpdateSettingsRequest(index)
                .settings(settingsBuilder);
        client.execute(UpdateSettingsAction.INSTANCE, updateSettingsRequest).actionGet();
    }

    public static void waitForRecovery(ElasticsearchClient client) throws IOException {
        RecoveryResponse response = client.execute(RecoveryAction.INSTANCE, new RecoveryRequest()).actionGet();
    }

    public static int waitForRecovery(ElasticsearchClient client, String index) throws IOException {
        if (index == null) {
            throw new IOException("unable to waitfor recovery, index not set");
        }
        RecoveryResponse response = client.execute(RecoveryAction.INSTANCE, new RecoveryRequest(index)).actionGet();
        int shards = response.getTotalShards();
        client.execute(ClusterHealthAction.INSTANCE, new ClusterHealthRequest(index).waitForActiveShards(shards)).actionGet();
        return shards;
    }

    public static void waitForCluster(ElasticsearchClient client, ClusterHealthStatus status, TimeValue timeout) throws IOException {
        try {
            ClusterHealthResponse healthResponse =
                    client.execute(ClusterHealthAction.INSTANCE, new ClusterHealthRequest().waitForStatus(status).timeout(timeout)).actionGet();
            if (healthResponse != null && healthResponse.isTimedOut()) {
                throw new IOException("cluster state is " + healthResponse.getStatus().name()
                        + " and not " + status.name()
                        + ", from here on, everything will fail!");
            }
        } catch (ElasticsearchTimeoutException e) {
            throw new IOException("timeout, cluster does not respond to health request, cowardly refusing to continue with operations");
        }
    }

    public static String clusterName(ElasticsearchClient client) {
        try {
            ClusterStateRequestBuilder clusterStateRequestBuilder =
                    new ClusterStateRequestBuilder(client, ClusterStateAction.INSTANCE).all();
            ClusterStateResponse clusterStateResponse = clusterStateRequestBuilder.execute().actionGet();
            String name = clusterStateResponse.getClusterName().value();
            int nodeCount = clusterStateResponse.getState().getNodes().size();
            return name + " (" + nodeCount + " nodes connected)";
        } catch (ElasticsearchTimeoutException e) {
            return "TIMEOUT";
        } catch (NoNodeAvailableException e) {
            return "DISCONNECTED";
        } catch (Throwable t) {
            return "[" + t.getMessage() + "]";
        }
    }

    public static String healthColor(ElasticsearchClient client) {
        try {
            ClusterHealthResponse healthResponse =
                    client.execute(ClusterHealthAction.INSTANCE, new ClusterHealthRequest().timeout(TimeValue.timeValueSeconds(30))).actionGet();
            ClusterHealthStatus status = healthResponse.getStatus();
            return status.name();
        } catch (ElasticsearchTimeoutException e) {
            return "TIMEOUT";
        } catch (NoNodeAvailableException e) {
            return "DISCONNECTED";
        } catch (Throwable t) {
            return "[" + t.getMessage() + "]";
        }
    }

    public static int updateReplicaLevel(ElasticsearchClient client, String index, int level) throws IOException {
        waitForCluster(client, ClusterHealthStatus.YELLOW, TimeValue.timeValueSeconds(30));
        updateIndexSetting(client, index, "number_of_replicas", level);
        return waitForRecovery(client, index);
    }

    public static void flushIndex(ElasticsearchClient client, String index) {
        if (client != null && index != null) {
            client.execute(FlushAction.INSTANCE, new FlushRequest(index)).actionGet();
        }
    }

    public static void refreshIndex(ElasticsearchClient client, String index) {
        if (client != null && index != null) {
            client.execute(RefreshAction.INSTANCE, new RefreshRequest(index)).actionGet();
        }
    }

    public static void putMapping(ElasticsearchClient client, ConfigHelper configHelper, String index) {
        if (!configHelper.mappings().isEmpty()) {
            for (Map.Entry<String, String> me : configHelper.mappings().entrySet()) {
                client.execute(PutMappingAction.INSTANCE,
                        new PutMappingRequest(index).type(me.getKey()).source(me.getValue())).actionGet();
            }
        }
    }

}
