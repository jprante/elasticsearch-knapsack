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
package org.elasticsearch.plugin.knapsack.io;

import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * The Connection factory service
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public final class ConnectionService<F extends ConnectionFactory> {

    private final static ConnectionService instance = new ConnectionService();

    private ConnectionService() {
    }

    public static ConnectionService getInstance() {
        return instance;
    }

    public synchronized F getConnectionFactory(String scheme)
            throws IOException {
        if (scheme == null) {
            throw new IllegalArgumentException("no connection scheme given");
        }
        ConnectionFactory factory;
        ServiceLoader<ConnectionFactory> loader = ServiceLoader.load(ConnectionFactory.class);
        Iterator<ConnectionFactory> it = loader.iterator();
        while (it.hasNext()) {
            factory = it.next();
            if (scheme != null && factory.providesScheme(scheme)) {
                return (F) factory;
            }
        }
        throw new ServiceConfigurationError("no connection factory found for scheme " + scheme);
    }
}
