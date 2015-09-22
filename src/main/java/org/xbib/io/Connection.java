
package org.xbib.io;

import java.io.IOException;
import java.net.URI;

/**
 * A Connection is an access to a resource via a scheme or a protocol.
 * Each connection can serve multiple sessions in parallel.
 */
public interface Connection<S extends Session> {

    /**
     * Set URI of this connection
     *
     * @param uri
     */
    Connection setURI(URI uri);

    /**
     * Get URI of this connection
     */
    URI getURI();

    /**
     * Close connection and close all sessions
     */
    void close() throws IOException;

    /**
     * Create a new session on this connection
     *
     * @return the session
     * @throws IOException if the session can not be created
     */
    S createSession() throws IOException;

}
