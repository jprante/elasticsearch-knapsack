
package org.xbib.io.archivers.cpio;

import org.xbib.io.Connection;
import org.xbib.io.ConnectionFactory;
import org.xbib.io.archivers.ArchiveSession;

import java.io.IOException;
import java.net.URI;

/**
 * Cpio connection factory
 */
public final class CpioConnectionFactory implements ConnectionFactory<CpioSession> {

    @Override
    public String getName() {
        return "cpio";
    }

    /**
     * Get connection
     *
     * @param uri the connection URI
     * @return a new connection
     * @throws java.io.IOException if connection can not be established
     */
    @Override
    public Connection<CpioSession> getConnection(URI uri) throws IOException {
        CpioConnection connection = new CpioConnection();
        connection.setURI(uri);
        return connection;
    }

    @Override
    public boolean canOpen(URI uri) {
        return ArchiveSession.canOpen(uri, getName(), true);
    }
}
