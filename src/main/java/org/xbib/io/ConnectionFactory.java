
package org.xbib.io;

import java.io.IOException;
import java.net.URI;

public interface ConnectionFactory<S extends Session> {

    /**
     * Creates a new connection
     *
     * @param uri the URI for the connection
     *
     * @return the connection
     *
     * @throws IOException if the connection can not be established
     */
    Connection<S> getConnection(URI uri) throws IOException;

    /**
     * Checks if this connection factory can provide this URI scheme.
     *
     * @param scheme the URI scheme to check
     *
     * @return true if the scheme can be provided, otherwise false
     */
    boolean providesScheme(String scheme);

}
