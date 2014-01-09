package org.xbib.io.archivers.zip;

import org.xbib.io.Connection;

import java.io.IOException;
import java.net.URI;

public class ZipConnection implements Connection<ZipSession> {

    private URI uri;

    protected ZipConnection() {
    }

    @Override
    public ZipConnection setURI(URI uri) {
        this.uri = uri;
        return this;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public ZipSession createSession() throws IOException {
        ZipSession session = new ZipSession();
        session.setURI(uri);
        return session;
    }

}
