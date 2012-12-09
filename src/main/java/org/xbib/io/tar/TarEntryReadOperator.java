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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.xbib.io.ObjectPacket;
import org.xbib.io.Packet;

/**
 *  TAR entry read operation
 *
 *  @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public class TarEntryReadOperator {

    private final String platformEncoding = System.getProperty("file.encoding");;
    
    private String encoding;
    
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
            throw new IOException("no input stream");
        }
        if (encoding == null) {
        	this.encoding = platformEncoding;
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
