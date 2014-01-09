
package org.xbib.io;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.WeakHashMap;

/**
 * The Connection service
 */
public final class ConnectionService<S extends Session> {

    private final Map<String, ConnectionFactory<S>> factories = new WeakHashMap<String, ConnectionFactory<S>>();

    private final static ConnectionService instance = new ConnectionService();

    private ConnectionService() {
        ServiceLoader<ConnectionFactory> loader = ServiceLoader.load(ConnectionFactory.class);
        for (ConnectionFactory factory : loader) {
            if (!factories.containsKey(factory.getName())) {
                factories.put(factory.getName(), factory);
            }
        }
    }

    public static <S extends Session> ConnectionService<S> getInstance() {
        return instance;
    }

    public ConnectionFactory<S> getConnectionFactory(String name)
            throws IOException {
        if (factories.containsKey(name)) {
            return factories.get(name);
        }
        throw new ServiceConfigurationError("no connection factory found for scheme " + name);
    }

    public ConnectionFactory<S> getConnectionFactory(URI uri) {
        for (ConnectionFactory<S> factory : factories.values()) {
            if (factory.canOpen(uri)) {
                return factory;
            }
        }
        throw new ServiceConfigurationError("no connection factory found for " + uri);
    }
}
