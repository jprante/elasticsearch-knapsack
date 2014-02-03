
package org.xbib.classloader;

import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class ResourceEnumeration implements Enumeration<URL> {

    private Iterator iterator;

    private final String resourceName;

    private URL next;

    public ResourceEnumeration(Collection resourceLocations, String resourceName) {
        this.iterator = resourceLocations.iterator();
        this.resourceName = resourceName;
    }

    public boolean hasMoreElements() {
        fetchNext();
        return (next != null);
    }

    public URL nextElement() {
        fetchNext();
        // save next into a local variable and clear the next field
        URL next = this.next;
        this.next = null;
        // if we didn't have a next throw an exception
        if (next == null) {
            throw new NoSuchElementException();
        }
        return next;
    }

    private void fetchNext() {
        if (iterator == null) {
            return;
        }
        if (next != null) {
            return;
        }
        try {
            while (iterator.hasNext()) {
                ResourceLocation resourceLocation = (ResourceLocation) iterator.next();
                ResourceHandle resourceHandle = resourceLocation.getResourceHandle(resourceName);
                if (resourceHandle != null) {
                    next = resourceHandle.getUrl();
                    return;
                }
            }
            // no more elements
            // clear the iterator so it can be GCed
            iterator = null;
        } catch (IllegalStateException e) {
            // Jar file was closed... this means the resource finder was destroyed
            // clear the iterator so it can be GCed
            iterator = null;
            throw e;
        }
    }
}
