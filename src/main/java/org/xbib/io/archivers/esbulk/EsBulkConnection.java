
package org.xbib.io.archivers.esbulk;

import org.xbib.io.Connection;

import java.io.IOException;
import java.net.URI;

public class EsBulkConnection implements Connection<EsBulkSession> {

    private URI uri;

    protected EsBulkConnection() {
    }

    @Override
    public EsBulkConnection setURI(URI uri) {
        this.uri = uri;
        return this;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public EsBulkSession createSession() throws IOException {
        EsBulkSession session = new EsBulkSession();
        session.setURI(uri);
        return session;
    }

}
