
package org.xbib.io;

import java.io.IOException;
import java.io.Serializable;

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
