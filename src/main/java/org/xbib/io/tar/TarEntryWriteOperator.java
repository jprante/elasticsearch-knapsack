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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import org.xbib.io.Packet;

/**
 * TAR write entry operation
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
class TarEntryWriteOperator {

    private AtomicLong counter = new AtomicLong();
    private static final NumberFormat nf = DecimalFormat.getIntegerInstance();

    static {
        nf.setGroupingUsed(false);
        nf.setMinimumIntegerDigits(12);
    }

    public synchronized void write(TarSession session, Packet packet) throws IOException {
        if (packet == null || packet.toString() == null) {
            throw new IOException("no packet to write");
        }
        byte[] buf = packet.toString().getBytes();
        if (buf.length > 0) {
            String name = createEntryName(packet.getName(), packet.getNumber());
            TarEntry entry = new TarEntry(name);
            entry.setModTime(new Date());
            entry.setSize(buf.length);
            session.getOutputStream().putNextEntry(entry);
            session.getOutputStream().write(buf);
            session.getOutputStream().closeEntry();
        }
    }

    private String createEntryName(String name, String number) {
        StringBuilder sb = new StringBuilder();
        if (name != null && name.length() > 0) {
            sb.append(name);
        }
        if (number != null) {
            // distribute numbered entries over 12-digit directories (10.000 per directory)
            String d = nf.format(counter.incrementAndGet());
            sb.append("/").append(d.substring(0, 4))
                    .append("/").append(d.substring(4, 8))
                    .append("/").append(d.substring(8, 12))
                    .append("/").append(number);
        }
        return sb.toString();
    }
}
