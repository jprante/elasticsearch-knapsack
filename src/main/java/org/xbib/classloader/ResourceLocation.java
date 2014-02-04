
package org.xbib.classloader;

import java.io.IOException;
import java.net.URL;
import java.util.jar.Manifest;

/**
 * This is a location which is searched by
 */
public interface ResourceLocation {

    URL getCodeSource();

    ResourceHandle getResourceHandle(String resourceName);

    Manifest getManifest() throws IOException;

    void close();
}
