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
