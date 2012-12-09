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
package org.xbib.io;

import java.io.IOException;
import java.io.Serializable;

/**
 * The Session interface is a serializable object for being opened, receive
 * operations, and being closed. Sessions must be opened before the first
 * operation and closed after the last operation.
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public interface Session<P extends Packet> extends Serializable {

    enum Mode {

        READ, WRITE, READ_WRITE, APPEND, DEFERRED_WRITE, DRY, CONTROL, DELETE;
    }

    /**
     * Open valve with a given input/output mode
     *
     * @throws IOException if valve can not be opened
     */
    void open(Mode mode) throws IOException;

    P newPacket();
    
    P read() throws IOException;

    void write(P packet) throws IOException;

    /**
     * Close valve
     *
     * @throws IOException if valve can not be closed
     */
    void close() throws IOException;

    /**
     * Checks if this session has been successfully opened.
     *
     * @return true if the session is open
     */
    boolean isOpen();
}
