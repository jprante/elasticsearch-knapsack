
package org.xbib.io.archivers.zip;

import org.xbib.io.Connection;
import org.xbib.io.ConnectionFactory;
import org.xbib.io.archivers.ArchiveSession;

import java.io.IOException;
import java.net.URI;

/**
 * Zip connection factory
 */
public final class ZipConnectionFactory implements ConnectionFactory<ZipSession> {

    @Override
    public String getName() {
        return "zip";
    }

    /**
     * Get connection
     *
     * @param uri the connection URI
     * @return a new connection
     * @throws java.io.IOException if connection can not be established
     */
    @Override
    public Connection<ZipSession> getConnection(URI uri) throws IOException {
        ZipConnection connection = new ZipConnection();
        connection.setURI(uri);
        return connection;
    }

    @Override
    public boolean canOpen(URI uri) {
        return ArchiveSession.canOpen(uri, getName(), false);
    }
}
