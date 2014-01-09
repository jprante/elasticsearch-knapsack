
package org.xbib.io.archivers.s3;

import org.xbib.io.Connection;

import java.io.IOException;
import java.net.URI;

public class S3Connection implements Connection<S3Session> {

    private URI uri;

    protected S3Connection() {
    }

    @Override
    public S3Connection setURI(URI uri) {
        this.uri = uri;
        return this;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public S3Session createSession() throws IOException {
        S3Session session = new S3Session();
        session.setURI(uri);
        return session;
    }

}
