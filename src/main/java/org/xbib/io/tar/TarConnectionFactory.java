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
package org.xbib.io.tar;

import java.io.IOException;
import java.net.URI;
import org.xbib.io.Connection;
import org.xbib.io.ConnectionFactory;

/**
 * Tar connection factory
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public final class TarConnectionFactory implements ConnectionFactory<TarSession> {

    /**
     * Get connection
     *
     * @param uri the connection URI
     *
     * @return a new connection
     *
     * @throws IOException if connection can not be established
     */
    @Override
    public Connection<TarSession> getConnection(final URI uri) throws IOException {
         TarConnection connection = new TarConnection();
         connection.setURI(uri);
         return connection;
    }

    /**
     * Check if scheme is provided
     *
     * @param scheme the scheme to be checked
     *
     * @return true if scheme is provided
     */
    @Override
    public boolean providesScheme(String scheme) {
        return scheme.startsWith("tar");
    }
}
