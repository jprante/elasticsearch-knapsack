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

public interface KnapsackParameter {

    /**
     * The indices used for knapsack
     */
    String INDEX_PARAM = "index";

    /**
     * The type used for knapsack
     */
    String TYPE_PARAM = "type";

    /**
     * The archive file path parameter name for the knapsack archive
     */
    String PATH_PARAM = "archivepath";

    /**
     * The parameter for configuring archive location
     */
    String KNAPSACK_PATH = "path.knapsack";

    /**
     * The default archives are read.written into this path
     */
    String KNAPSACK_DEFAULT_PATH = "path.logs";

    /**
     * the Elasticsearch host for knapsack push/pull
     */
    String HOST_PARAM = "host";

    /**
     * The knapsack port
     */
    String PORT_PARAM = "port";

    /**
     * The Elasticsearch cluster name for knapsack push/pull
     */
    String CLUSTER_PARAM = "cluster";

    /**
     * Should knapsack sniff for nodes in the Elasticsearch cluster
     */
    String SNIFF_PARAM = "sniff";

    /**
     * Timeout for connections that Knapsack uses
     */
    String TIMEOUT_PARAM = "timeout";

    /**
     * Is knapsack allowed to overwrite archive files or not
     */
    String OVERWRITE_PARAM = "overwrite";

    /**
     * Bulk indexing setting, maximum actions per bulk request
     */
    String MAX_BULK_ACTIONS_PER_REQUEST_PARAM = "max_bulk_actions_per_request";

    /**
     * Bulk indexing setting, maximum concurrecny for bulk requests
     */
    String MAX_BULK_CONCURRENCY_PARAM = "max_bulk_concurrency";

    /**
     * A map for renaming indices/types
     */
    String MAP_PARAM = "map";

    /**
     * Should knapsack consider index metadata (mappings, aliases) or not
     */
    String WITH_METADATA_PARAM = "with_metadata";

    String BYTES_PARAM = "bytes";

    String WITH_ALIASES = "with_aliases";

}
