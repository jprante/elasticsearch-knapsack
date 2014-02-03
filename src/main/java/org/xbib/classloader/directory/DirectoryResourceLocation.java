
package org.xbib.classloader.directory;

import org.xbib.classloader.ResourceHandle;
import org.xbib.classloader.url.AbstractURLResourceLocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.jar.Manifest;

public class DirectoryResourceLocation extends AbstractURLResourceLocation {

    private final File baseDir;

    private boolean manifestLoaded = false;

    private Manifest manifest;

    public DirectoryResourceLocation(File baseDir) throws MalformedURLException {
        super(baseDir.toURI().toURL());
        this.baseDir = baseDir;
    }

    @Override
    public ResourceHandle getResourceHandle(String resourceName) {
        File file = new File(baseDir, resourceName);
        if (!file.exists() || !isLocal(file)) {
            return null;
        }
        try {
            return new DirectoryResourceHandle(resourceName, file, baseDir, getManifestSafe());
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private boolean isLocal(File file) {
        try {
            String base = baseDir.getCanonicalPath();
            String relative = file.getCanonicalPath();
            return (relative.startsWith(base));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Manifest getManifest() throws IOException {
        if (!manifestLoaded) {
            File manifestFile = new File(baseDir, "META-INF/MANIFEST.MF");
            if (manifestFile.isFile() && manifestFile.canRead()) {
                FileInputStream in = null;
                try {
                    in = new FileInputStream(manifestFile);
                    manifest = new Manifest(in);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            manifestLoaded = true;
        }
        return manifest;
    }

    private Manifest getManifestSafe() {
        Manifest m = null;
        try {
            m = getManifest();
        } catch (IOException e) {
            // ignore
        }
        return m;
    }
}
