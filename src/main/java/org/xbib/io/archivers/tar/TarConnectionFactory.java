
package org.xbib.io.archivers.tar;

import org.xbib.io.Connection;
import org.xbib.io.ConnectionFactory;
import org.xbib.io.archivers.ArchiveSession;

import java.io.IOException;
import java.net.URI;

/**
 * Tar connection factory
 */
public final class TarConnectionFactory implements ConnectionFactory<TarSession> {

    @Override
    public String getName() {
        return "tar";
    }

    /**
     * Get connection
     *
     * @param uri the connection URI
     * @return a new connection
     * @throws java.io.IOException if connection can not be established
     */
    @Override
    public Connection<TarSession> getConnection(URI uri) throws IOException {
        TarConnection connection = new TarConnection();
        connection.setURI(uri);
        return connection;
    }

    @Override
    public boolean canOpen(URI uri) {
        return ArchiveSession.canOpen(uri, getName(), true);
    }
}
