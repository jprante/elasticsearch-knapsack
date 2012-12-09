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
package org.elasticsearch.plugin.knapsack.io.tar;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.plugin.knapsack.io.Connection;

/**
 * A TAR connection
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public class TarConnection implements Connection<TarSession> {

    private List<TarSession> sessions = new ArrayList<TarSession>();
    private URI uri;

    @Override
    public TarConnection setURI(URI uri) {
        this.uri = uri;
        return this;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public TarSession createSession() throws IOException {
        TarSession session = new TarSession();
        session.setName(uri.getSchemeSpecificPart());
        session.setScheme(uri.getScheme());
        sessions.add(session);
        return session;
    }

    @Override
    public void close() throws IOException {
        for (TarSession session : sessions) {
            session.close();
        }
    }
}
