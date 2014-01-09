
package org.xbib.io.archivers.tar;

import org.xbib.io.Connection;

import java.io.IOException;
import java.net.URI;

/**
 * Tar connection
 */
public class TarConnection implements Connection<TarSession> {

    private URI uri;

    protected TarConnection() {
    }

    @Override
    public TarConnection setURI(URI uri) {
        this.uri = uri;
        return this;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public TarSession createSession() throws IOException {
        TarSession session = new TarSession();
        session.setURI(uri);
        return session;
    }

}
