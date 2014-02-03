
package org.xbib.classloader.url;

import org.xbib.classloader.ResourceLocation;

import java.net.URL;

public abstract class AbstractURLResourceLocation implements ResourceLocation {

    private final URL codeSource;

    public AbstractURLResourceLocation(URL codeSource) {
        this.codeSource = codeSource;
    }

    public final URL getCodeSource() {
        return codeSource;
    }

    public void close() {
    }

    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractURLResourceLocation that = (AbstractURLResourceLocation) o;
        return codeSource.equals(that.codeSource);
    }

    public final int hashCode() {
        return codeSource.hashCode();
    }

    public final String toString() {
        return "[" + getClass().getName() + ": " + codeSource + "]";
    }
}
