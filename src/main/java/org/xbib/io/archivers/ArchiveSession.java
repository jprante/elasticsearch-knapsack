
package org.xbib.io.archivers;

import org.xbib.io.ObjectPacket;
import org.xbib.io.Packet;
import org.xbib.io.Session;
import org.xbib.io.StreamCodecService;
import org.xbib.io.StreamUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Set;

import static org.xbib.io.archivers.ArchiveFactory.createArchiveEntry;
import static org.xbib.io.archivers.ArchiveFactory.createArchiveInputStream;
import static org.xbib.io.archivers.ArchiveFactory.createArchiveOutputStream;

/**
 * A basic archive session
 */
public abstract class ArchiveSession implements Session {

    private final static StreamCodecService codecFactory = StreamCodecService.getInstance();

    private boolean isOpen;

    private ArchiveInputStream in;

    private ArchiveOutputStream out;

    private URI uri;

    protected ArchiveSession() {
    }

    public ArchiveSession setURI(URI uri) {
        this.uri = uri;
        return this;
    }

    protected abstract String getSuffix();


    @Override
    public synchronized void open(Mode mode) throws IOException {
        if (isOpen) {
            return;
        }
        final String suffix = getSuffix();
        final String scheme = uri.getScheme();
        final String part = uri.getSchemeSpecificPart();
        switch (mode) {
            case READ: {
                FileInputStream fin;
                this.in = null;
                if (scheme.equals(suffix) || part.endsWith("." + suffix)) {
                    fin = createFileInputStream(uri, "." + suffix);
                    this.in = createArchiveInputStream(suffix, fin);
                } else {
                    Set<String> codecs = StreamCodecService.getCodecs();
                    for (String codec : codecs) {
                        if (scheme.equals(suffix + codec) || part.endsWith("." + suffix + "." + codec)) {
                            fin = createFileInputStream(uri, "." + suffix + "." + codec);
                            this.in = createArchiveInputStream(suffix, codecFactory.getCodec(codec).decode(fin));
                        }
                    }
                }
                this.isOpen = in != null;
                if (!isOpen) {
                    throw new FileNotFoundException("can't open for input, check existence or access rights: " + uri);
                }
                break;
            }
            case WRITE: {
                FileOutputStream fout;
                this.out = null;
                if (scheme.equals(suffix) || part.endsWith("." + suffix)) {
                    fout = createFileOutputStream(uri, "." + suffix);
                    this.out = createArchiveOutputStream(suffix, fout);
                } else {
                    Set<String> codecs = StreamCodecService.getCodecs();
                    for (String codec : codecs) {
                        if (scheme.equals(suffix + codec) || part.endsWith("." + suffix + "." + codec)) {
                            fout = createFileOutputStream(uri, "." + suffix + "." + codec);
                            this.out = createArchiveOutputStream(suffix, codecFactory.getCodec(codec).encode(fout));
                        }
                    }
                }
                this.isOpen = out != null;
                if (!isOpen) {
                    throw new FileNotFoundException("can't open for output, check existence or access rights: " + uri);
                }
                break;
            }
        }
    }

    @Override
    public Packet newPacket() {
        return new ObjectPacket();
    }

    @Override
    public synchronized Packet read() throws IOException {
        if (!isOpen()) {
            throw new IOException("not open");
        }
        if (in == null) {
            throw new IOException("no input stream found");
        }
        ArchiveEntry entry = in.getNextEntry();
        if (entry == null) {
            return null;
        }
        ObjectPacket packet = new ObjectPacket();
        String name = entry.getName();
        packet.name(name);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        StreamUtil.copy(in, bout);
        packet.packet(new String(bout.toByteArray()));
        return packet;
    }

    @Override
    public synchronized void write(Packet packet) throws IOException {
        if (!isOpen()) {
            throw new IOException("not open");
        }
        if (out == null) {
            throw new IOException("no output stream found");
        }
        if (packet == null || packet.toString() == null) {
            throw new IOException("no payload to write for entry");
        }
        byte[] buf = packet.toString().getBytes();
        if (buf.length > 0) {
            String name = packet.name();
            ArchiveEntry entry = createArchiveEntry(getSuffix());
            entry.setName(name);
            entry.setLastModified(new Date());
            entry.setEntrySize(buf.length);
            out.putArchiveEntry(entry);
            out.write(buf);
            out.closeArchiveEntry();
        }
    }

    /**
     * Close session
     */
    @Override
    public synchronized void close() throws IOException {
        if (!isOpen) {
            return;
        }
        if (out != null) {
            out.close();
            out = null;
        }
        if (in != null) {
            in.close();
            in = null;
        }
        this.isOpen = false;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    public static boolean canOpen(URI uri, String suffix, boolean withCodecs) {
        final String scheme = uri.getScheme();
        final String part = uri.getSchemeSpecificPart();
        if (scheme.equals(suffix) ||
                (scheme.equals("file") && part.endsWith("." + suffix.toLowerCase())) ||
                (scheme.equals("file") && part.endsWith("." + suffix.toUpperCase()))) {
            return true;
        }
        if (withCodecs) {
            Set<String> codecs = StreamCodecService.getCodecs();
            for (String codec : codecs) {
                String s = "." + suffix + "." + codec;
                if (part.endsWith(s) || part.endsWith(s.toLowerCase()) || part.endsWith(s.toUpperCase())) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Helper method for creating the FileInputStream
     *
     * @param uri    the URI
     * @param suffix the suffix
     * @return a FileInputStream
     * @throws java.io.IOException if existence or access rights do not suffice
     */
    private FileInputStream createFileInputStream(URI uri, String suffix) throws IOException {
        String part = uri.getSchemeSpecificPart();
        String s = part.endsWith(suffix) ? part : part + suffix;
        File f = new File(s);
        if (f.isFile() && f.canRead()) {
            return new FileInputStream(f);
        } else {
            throw new FileNotFoundException("can't open for input, check existence or access rights: " + s);
        }
    }

    /**
     * Helper method for creating the FileOutputStream. Creates the directory if
     * it does not exist.
     *
     * @param uri    the URI
     * @param suffix the suffix
     * @throws java.io.IOException
     */
    private FileOutputStream createFileOutputStream(URI uri, String suffix) throws IOException {
        String part = uri.getSchemeSpecificPart();
        String s = part.endsWith(suffix) ? part : part + suffix;
        File f = new File(s);
        if (!f.getAbsoluteFile().getParentFile().exists()
                && !f.getAbsoluteFile().getParentFile().mkdirs()) {
            throw new IOException("could not create directories to write: " + f);
        }
        if (!f.exists()) {
            return new FileOutputStream(f);
        } else {
            throw new IOException("file " + f.getAbsolutePath() + " already exists");
        }
    }

}
