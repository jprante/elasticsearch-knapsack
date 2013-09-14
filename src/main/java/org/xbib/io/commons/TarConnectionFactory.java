
package org.xbib.io.commons;

import org.xbib.io.Connection;
import org.xbib.io.ConnectionFactory;

import java.io.IOException;
import java.net.URI;


public final class TarConnectionFactory implements ConnectionFactory<TarSession> {

    /**
     * Get connection
     *
     * @param uri the connection URI
     *
     * @return a new connection
     *
     * @throws java.io.IOException if connection can not be established
     */
    @Override
    public Connection<TarSession> getConnection(final URI uri) throws IOException {
         TarConnection connection = new TarConnection();
         connection.setURI(uri);
         return connection;
    }

    /**
     * Check if scheme is provided
     *
     * @param scheme the scheme to be checked
     *
     * @return true if scheme is provided
     */
    @Override
    public boolean providesScheme(String scheme) {
        return scheme.startsWith("tar");
    }
}
