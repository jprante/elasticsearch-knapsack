
package org.xbib.io.archivers.s3;

import org.xbib.io.Connection;
import org.xbib.io.ConnectionFactory;

import java.io.IOException;
import java.net.URI;

public class S3ConnectionFactory implements ConnectionFactory<S3Session> {

    @Override
    public String getName() {
        return "s3";
    }

    @Override
    public boolean canOpen(URI uri) {
        return uri != null && getName().equals(uri.getScheme());
    }

    @Override
    public Connection<S3Session> getConnection(URI uri) throws IOException {
        S3Connection connection = new S3Connection();
        connection.setURI(uri);
        return connection;
    }
}
