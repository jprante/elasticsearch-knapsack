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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.xbib.io.ObjectPacket;
import org.xbib.io.Packet;

/**
 * TAR entry read operation
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
class TarEntryReadOperator {

    private final String platformEncoding = System.getProperty("file.encoding");    
    
    private String encoding = platformEncoding;

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getEncoding() {
        return encoding;
    }

    public synchronized Packet read(TarSession session) throws IOException {
        if (!session.isOpen()) {
            throw new IOException("not open");
        }
        if (session.getInputStream() == null) {
            throw new IOException("no tar input stream");
        }
        TarEntry entry = session.getInputStream().getNextEntry();
        if (entry == null) {
            return null;
        }
        ObjectPacket packet = new ObjectPacket();
        String name = entry.getName();
        packet.setName(name);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        session.getInputStream().copyEntryContents(bout);
        packet.setPacket(new String(bout.toByteArray(), encoding));
        return packet;
    }
}
