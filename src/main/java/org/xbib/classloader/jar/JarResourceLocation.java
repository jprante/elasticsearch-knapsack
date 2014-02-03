
package org.xbib.classloader.jar;

import org.xbib.classloader.AbstractResourceHandle;
import org.xbib.classloader.ResourceHandle;
import org.xbib.classloader.url.AbstractURLResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipException;

public class JarResourceLocation extends AbstractURLResourceLocation {

    private JarFile jarFile;

    private byte content[];

    public JarResourceLocation(URL codeSource, File cacheFile) throws IOException {
        super(codeSource);
        try {
            jarFile = new JarFile(cacheFile);
        } catch (ZipException ze) {
            // We get this exception on windows when the
            // path to the jar file gets too long (Bug ID: 6374379)
            InputStream is = null;
            try {
                is = new FileInputStream(cacheFile);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[2048];
                int bytesRead = -1;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                this.content = baos.toByteArray();
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }
    }

    public ResourceHandle getResourceHandle(String resourceName) {
        if (jarFile != null) {
            JarEntry jarEntry = jarFile.getJarEntry(resourceName);
            if (jarEntry != null) {
                try {
                    URL url = new URL(getCodeSource(), resourceName);
                    return new JarResourceHandle(jarFile, jarEntry, getCodeSource());
                } catch (MalformedURLException e) {
                    // ignore
                }
            }
        } else {
            try {
                final JarInputStream is = new JarInputStream(new ByteArrayInputStream(this.content));
                JarEntry jarEntry = null;
                while ((jarEntry = is.getNextJarEntry()) != null) {
                    if (jarEntry.getName().equals(resourceName)) {
                        try {
                            URL url = new URL(getCodeSource(), resourceName);
                            return new JarEntryResourceHandle(jarEntry, is);
                        } catch (MalformedURLException e) {
                            // skip
                        }
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return null;
    }

    public Manifest getManifest() throws IOException {
        if (jarFile != null) {
            return jarFile.getManifest();
        } else {
            try {
                JarInputStream is = new JarInputStream(new ByteArrayInputStream(this.content));
                return is.getManifest();
            } catch (IOException e) {
            }
        }
        return null;
    }

    @Override
    public void close() {
        if (jarFile != null) {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private class JarEntryResourceHandle extends AbstractResourceHandle {

        private final JarEntry jarEntry2;
        private final JarInputStream is;

        public JarEntryResourceHandle(JarEntry jarEntry2, JarInputStream is) {
            this.jarEntry2 = jarEntry2;
            this.is = is;
        }

        public String getName() {
            return jarEntry2.getName();
        }

        public URL getUrl() {
            try {
                return new URL("jar", "", -1, getCodeSource() + "!/" + jarEntry2.getName());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean isDirectory() {
            return jarEntry2.isDirectory();
        }

        public URL getCodeSourceUrl() {
            return getCodeSource();
        }

        public InputStream getInputStream() throws IOException {
            return is;
        }

        public int getContentLength() {
            return (int) jarEntry2.getSize();
        }
    }
}
