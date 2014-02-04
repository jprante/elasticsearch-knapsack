
package org.xbib.classloader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public abstract class AbstractResourceHandle implements ResourceHandle {

    public byte[] getBytes() throws IOException {
        InputStream in = getInputStream();
        try {
            return load(in);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    public Manifest getManifest() throws IOException {
        return null;
    }

    public Certificate[] getCertificates() {
        return null;
    }

    public Attributes getAttributes() throws IOException {
        Manifest m = getManifest();
        if (m == null) {
            return null;
        }

        String entry = getUrl().getFile();
        return m.getAttributes(entry);
    }

    public void close() {
    }

    public String toString() {
        return "[" + getName() + ": " + getUrl() + "; code source: " + getCodeSourceUrl() + "]";
    }

    static byte[] load(InputStream inputStream) throws IOException {
        try {
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int count = inputStream.read(buffer); count >= 0; count = inputStream.read(buffer)) {
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
