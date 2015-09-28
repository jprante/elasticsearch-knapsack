
package org.xbib.io.archive;

import org.xbib.io.BytesProgressWatcher;
import org.xbib.io.compress.CompressCodecService;

import java.nio.file.Path;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;

public class ArchiveService {

    private final static Map<String, ArchiveCodec> archiveCodecs = new WeakHashMap<String, ArchiveCodec>();

    private final static Set<String> codecs = CompressCodecService.getCodecs();

    private final static ArchiveService instance = new ArchiveService();

    private ArchiveService() {
        ServiceLoader<ArchiveCodec> loader = ServiceLoader.load(ArchiveCodec.class, getClass().getClassLoader());
        for (ArchiveCodec codec : loader) {
            if (!archiveCodecs.containsKey(codec.getName())) {
                archiveCodecs.put(codec.getName(), codec);
            }
        }
    }

    public static ArchiveService getInstance() {
        return instance;
    }

    public ArchiveCodec getCodec(String name) {
        if (archiveCodecs.containsKey(name)) {
            return archiveCodecs.get(name);
        }
        throw new IllegalArgumentException("archive codec for " + name + " not found in " + archiveCodecs);
    }

    public static Set<String> getCodecs() {
        return archiveCodecs.keySet();
    }

    @SuppressWarnings("unchecked")
    public static <I extends ArchiveInputStream, O extends ArchiveOutputStream> ArchiveSession<I, O> newSession(Path path, BytesProgressWatcher watcher) {
        for (String archiverName : getCodecs()) {
            if (canOpen(archiverName, path)) {
                return archiveCodecs.get(archiverName).newSession(watcher);
            }
        }
        throw new IllegalArgumentException("no archive session implementation found for path " + path);
    }

    private static boolean canOpen(String suffix, Path path) {
        String pathStr = path.toString();
        if (pathStr.endsWith("." + suffix.toLowerCase()) || pathStr.endsWith("." + suffix.toUpperCase())) {
            return true;
        }

        for (String codec : codecs) {
            String s = "." + suffix + "." + codec;
            if (pathStr.endsWith(s) || pathStr.endsWith(s.toLowerCase()) || pathStr.endsWith(s.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

}
