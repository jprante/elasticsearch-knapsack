
package org.xbib.io.archivers.cpio;

import org.xbib.io.Connection;

import java.io.IOException;
import java.net.URI;

/**
 * Cpio connection
 */
public class CpioConnection implements Connection<CpioSession> {

    private URI uri;

    protected CpioConnection() {
    }

    @Override
    public CpioConnection setURI(URI uri) {
        this.uri = uri;
        return this;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public CpioSession createSession() throws IOException {
        CpioSession session = new CpioSession();
        session.setURI(uri);
        return session;
    }

}
