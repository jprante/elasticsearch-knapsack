
package org.xbib.io;

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

        READ, WRITE, READ_WRITE, APPEND, DEFERRED_WRITE, OVERWRITE, URI_ENCODED, NONE;
    }

    /**
     * Open valve with a given input/output mode
     *
     * @throws java.io.IOException if valve can not be opened
     */
    void open(EnumSet<Mode> mode, Path path) throws IOException;

    P newPacket();

    P read() throws IOException;

    void write(P packet) throws IOException;

    /**
     * Close valve
     *
     * @throws java.io.IOException if valve can not be closed
     */
    void close() throws IOException;

    /**
     * Checks if this session has been successfully opened.
     *
     * @return true if the session is open
     */
    boolean isOpen();
}
