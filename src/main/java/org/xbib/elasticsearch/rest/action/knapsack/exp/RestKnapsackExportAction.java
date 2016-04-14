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
package org.xbib.elasticsearch.rest.action.knapsack.exp;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.search.RestSearchAction;
import org.elasticsearch.rest.action.support.RestToXContentListener;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportAction;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportRequest;
import org.xbib.elasticsearch.action.knapsack.exp.KnapsackExportResponse;
import org.xbib.elasticsearch.knapsack.KnapsackHelper;
import org.xbib.elasticsearch.knapsack.KnapsackParameter;

import java.io.File;
import java.nio.file.Path;

import static org.elasticsearch.rest.RestRequest.Method.POST;

/**
 * The REST knapsack export action performs a scan/scroll action over a user defined query
 * and stores the result in an archive file
 */
public class RestKnapsackExportAction extends BaseRestHandler implements KnapsackParameter {

    private final static ESLogger logger = ESLoggerFactory.getLogger(RestKnapsackExportAction.class.getSimpleName());

    @Inject
    public RestKnapsackExportAction(Settings settings, Client client, RestController controller) {
        super(settings, controller, client);

        controller.registerHandler(POST, "/_export", this);
        controller.registerHandler(POST, "/{index}/_export", this);
        controller.registerHandler(POST, "/{index}/{type}/_export", this);
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel, final Client client) {
        try {
            final String index = request.param(INDEX_PARAM, "_all");
            final String type = request.param(TYPE_PARAM);
            String archivePathString = request.param(PATH_PARAM);
            if (archivePathString == null) {
                String dataPath = settings.get(KnapsackParameter.KNAPSACK_PATH, settings.get(KnapsackParameter.KNAPSACK_DEFAULT_PATH, "."));
                archivePathString = dataPath + File.separator + index + (type != null ? "_" + type : "") + ".tar.gz";
            }
            final Path archivePath = new File(archivePathString).toPath();
            KnapsackExportRequest exportRequest = new KnapsackExportRequest()
                    .setIndex(index)
                    .setType(type)
                    .setArchivePath(archivePath)
                    .setOverwriteAllowed(request.paramAsBoolean(OVERWRITE_PARAM, false))
                    .withMetadata(request.paramAsBoolean(WITH_METADATA_PARAM, true))
                    .withAliases(request.paramAsBoolean(WITH_ALIASES, true))
                    .setIndexTypeNames(KnapsackHelper.toMap(request.param(MAP_PARAM), logger))
                    .setSearchRequest(toSearchRequest(request))
                    .setBytesToTransfer(request.paramAsSize(BYTES_PARAM, ByteSizeValue.parseBytesSizeValue("0", "")));
            client.admin().indices().execute(KnapsackExportAction.INSTANCE, exportRequest,
                    new RestToXContentListener<KnapsackExportResponse>(channel));
        } catch (Throwable ex) {
            try {
                logger.error(ex.getMessage(), ex);
                channel.sendResponse(new BytesRestResponse(channel, ex));
            } catch (Exception ex2) {
                logger.error(ex2.getMessage(), ex2);
            }
        }
    }

    private SearchRequest toSearchRequest(RestRequest request) {
        // override search action "size" (default = 10) by bulk request size. The size is per shard!
        request.params().put("size", request.param(MAX_BULK_ACTIONS_PER_REQUEST_PARAM, "1000"));
        SearchRequest searchRequest = new SearchRequest();
        RestSearchAction.parseSearchRequest(searchRequest, request, parseFieldMatcher, null);
        return searchRequest;
    }

}
