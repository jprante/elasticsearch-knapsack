/*
 * Copyright (C) 2014 Jörg Prante
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xbib.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;

/**
 * The Session interface is used for being opened, receive
 * operations, and being closed. Sessions must be opened before the first
 * operation and closed after the last operation.
 */
public interface Session<P extends Packet> {

    enum Mode {

        READ,
        WRITE,
        OVERWRITE,
        URI_ENCODED,
        NONE
    }

    String getName();

    void open(EnumSet<Mode> mode, Path path, File file) throws IOException;

    /**
     * Close session
     *
     * @throws java.io.IOException if valve can not be closed
     */
    void close() throws IOException;

    P newPacket();

    P read() throws IOException;

    void write(P packet) throws IOException;

    /**
     * Checks if this session has been successfully opened.
     *
     * @return true if the session is open
     */
    boolean isOpen();

    long getPacketCounter();
}
