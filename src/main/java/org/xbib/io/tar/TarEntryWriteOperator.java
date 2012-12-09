/*
 * Licensed to Jörg Prante and xbib under one or more contributor 
 * license agreements. See the NOTICE.txt file distributed with this work
 * for additional information regarding copyright ownership.
 *
 * Copyright (C) 2012 Jörg Prante and xbib
 * 
 * This program is free software; you can redistribute it and/or modify 
 * it under the terms of the GNU Affero General Public License as published 
 * by the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the 
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License 
 * along with this program; if not, see http://www.gnu.org/licenses 
 * or write to the Free Software Foundation, Inc., 51 Franklin Street, 
 * Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * The interactive user interfaces in modified source and object code 
 * versions of this program must display Appropriate Legal Notices, 
 * as required under Section 5 of the GNU Affero General Public License.
 * 
 * In accordance with Section 7(b) of the GNU Affero General Public 
 * License, these Appropriate Legal Notices must retain the display of the 
 * "Powered by xbib" logo. If the display of the logo is not reasonably 
 * feasible for technical reasons, the Appropriate Legal Notices must display
 * the words "Powered by xbib".
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
public class TarEntryWriteOperator {

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
