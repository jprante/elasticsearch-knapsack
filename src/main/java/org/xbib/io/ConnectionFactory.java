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
package org.xbib.io;

import java.io.IOException;
import java.net.URI;

/**
 *  A connection factory interface
 *
 *  @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public interface ConnectionFactory<S extends Session> {

    /**
     * Creates a new connection
     *
     * @param uri the URI for the connection
     *
     * @return the connection
     *
     * @throws IOException if the connection can not be established
     */
    Connection<S> getConnection(URI uri) throws IOException;

    /**
     * Checks if this connection factory can provide this URI scheme.
     *
     * @param scheme the URI scheme to check
     *
     * @return true if the scheme can be provided, otherwise false
     */
    boolean providesScheme(String scheme);

}
