
package org.xbib.io.commons;

import org.xbib.io.Connection;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;


public class TarConnection implements Connection<TarSession> {

    private List<TarSession> sessions = new ArrayList<TarSession>();
    private URI uri;

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
        session.setName(uri.getSchemeSpecificPart());
        session.setScheme(uri.getScheme());
        sessions.add(session);
        return session;
    }

    @Override
    public void close() throws IOException {
        for (TarSession session : sessions) {
            session.close();
        }
    }
}
