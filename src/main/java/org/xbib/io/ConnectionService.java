
package org.xbib.io;

import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;


public final class ConnectionService<F extends ConnectionFactory> {

    private final static ConnectionService instance = new ConnectionService();

    private ConnectionService() {
    }

    public static ConnectionService getInstance() {
        return instance;
    }

    public synchronized F getConnectionFactory(String scheme)
            throws IOException {
        if (scheme == null) {
            throw new IllegalArgumentException("no connection scheme given");
        }
        ConnectionFactory factory;
        ServiceLoader<ConnectionFactory> loader = ServiceLoader.load(ConnectionFactory.class);
        Iterator<ConnectionFactory> it = loader.iterator();
        while (it.hasNext()) {
            factory = it.next();
            if (scheme != null && factory.providesScheme(scheme)) {
                return (F) factory;
            }
        }
        throw new ServiceConfigurationError("no connection factory found for scheme " + scheme);
    }
}
