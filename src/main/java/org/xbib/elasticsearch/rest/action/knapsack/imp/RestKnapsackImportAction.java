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
package org.xbib.elasticsearch.rest.action.knapsack.imp;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.support.RestToXContentListener;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportAction;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportRequest;
import org.xbib.elasticsearch.action.knapsack.imp.KnapsackImportResponse;
import org.xbib.elasticsearch.knapsack.KnapsackHelper;
import org.xbib.elasticsearch.knapsack.KnapsackParameter;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import static org.elasticsearch.rest.RestRequest.Method.POST;

/**
 * The REST knapsack import action opens an archive and transfers the content into Elasticsearch
 */
public class RestKnapsackImportAction extends BaseRestHandler implements KnapsackParameter {

    private final static ESLogger logger = ESLoggerFactory.getLogger(RestKnapsackImportAction.class.getSimpleName());

    @Inject
    public RestKnapsackImportAction(Settings settings, Client client, RestController controller) {
        super(settings, controller, client);

        controller.registerHandler(POST, "/_import", this);
        controller.registerHandler(POST, "/{index}/_import", this);
        controller.registerHandler(POST, "/{index}/{type}/_import", this);
    }

    @Override
    public void handleRequest(final RestRequest request, RestChannel channel, Client client) {
        try {
            final String index = request.param(INDEX_PARAM, "_all");
            final String type = request.param(TYPE_PARAM);
            String archivePathString = request.param(PATH_PARAM);
            if (archivePathString == null) {
                String dataPath = settings.get(KnapsackParameter.KNAPSACK_PATH, settings.get(KnapsackParameter.KNAPSACK_DEFAULT_PATH, "."));
                archivePathString = dataPath + File.separator + index + (type != null ? "_" + type : "") + ".tar.gz";
            }
            final Path archivePath = new File(archivePathString).toPath();
            KnapsackImportRequest importRequest = new KnapsackImportRequest()
                    .setIndex(index)
                    .setType(type)
                    .setArchivePath(archivePath)
                    .setTimeout(request.paramAsTime(TIMEOUT_PARAM, TimeValue.timeValueSeconds(30L)))
                    .setMaxActionsPerBulkRequest(request.paramAsInt(MAX_BULK_ACTIONS_PER_REQUEST_PARAM, 1000))
                    .setMaxBulkConcurrency(request.paramAsInt(MAX_BULK_CONCURRENCY_PARAM,
                            Runtime.getRuntime().availableProcessors() * 2))
                    .withMetadata(request.paramAsBoolean(WITH_METADATA_PARAM, true))
                    .setIndexTypeNames(KnapsackHelper.toMap(request.param(MAP_PARAM), logger));
            // add user-defined settings and mappings
            for (Map.Entry<String, String> e : request.params().entrySet()) {
                if (e.getKey().endsWith("_settings")) {
                    importRequest.addIndexSettings(e.getKey(), e.getValue());
                } else if (e.getKey().endsWith("_mapping")) {
                    importRequest.addIndexTypeMapping(e.getKey(), e.getValue());
                }
            }
            client.admin().indices().execute(KnapsackImportAction.INSTANCE, importRequest,
                    new RestToXContentListener<KnapsackImportResponse>(channel));
        } catch (Throwable ex) {
            try {
                logger.error(ex.getMessage(), ex);
                channel.sendResponse(new BytesRestResponse(channel, ex));
            } catch (Exception ex2) {
                logger.error(ex2.getMessage(), ex2);
            }
        }
    }

}
