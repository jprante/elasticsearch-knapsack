
package org.xbib.io.commons;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.xbib.io.ObjectPacket;
import org.xbib.io.Packet;
import org.xbib.io.Session;
import org.xbib.io.StreamCodecService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

public class TarSession implements Session {

    private static final String CHARSET_NAME = "UTF-8";
    private final StreamCodecService codecFactory = StreamCodecService.getInstance();
    private boolean isOpen;
    private FileInputStream fin;
    private FileOutputStream fout;
    private TarArchiveInputStream in;
    private TarArchiveOutputStream out;
    private String scheme;
    private String part;

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public void setName(String name) {
        this.part = name;
    }

    public String getName() {
        return part;
    }

    @Override
    public synchronized void open(Mode mode) throws IOException {
        if (isOpen) {
            return;
        }
        switch (mode) {
            case READ:
                if (scheme.startsWith("targz")) {
                    String s = (part.endsWith("tar.gz")) ? part : part + ".tar.gz";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarArchiveInputStream(codecFactory.getCodec("gz").decode(fin), "UTF-8");
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                } else if (scheme.startsWith("tarbz2")) {
                    String s = (part.endsWith("tar.bz2")) ? part : part + ".tar.bz2";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarArchiveInputStream(codecFactory.getCodec("bz2").decode(fin), "UTF-8");
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                } else if (scheme.startsWith("tarxz")) {
                    String s = (part.endsWith(".tar.xz")) ? part : part + ".tar.xz";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarArchiveInputStream(codecFactory.getCodec("xz").decode(fin), "UTF-8");
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                } else {
                    String s = (part.endsWith(".tar")) ? part : part + ".tar";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarArchiveInputStream(fin, "UTF-8");
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                }
                break;
            case WRITE:
                if (scheme.equals("targz")) {
                    createFileOutputStream(".tar.gz");
                    this.out = new TarArchiveOutputStream(codecFactory.getCodec("gz").encode(fout), "UTF-8");
                    out.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                } else if (scheme.equals("tarbz2")) {
                    createFileOutputStream(".tar.bz2");
                    this.out = new TarArchiveOutputStream(codecFactory.getCodec("bz2").encode(fout), "UTF-8");
                    out.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                } else if (scheme.equals("tarxz")) {
                    createFileOutputStream(".tar.xz");
                    this.out = new TarArchiveOutputStream(codecFactory.getCodec("xz").encode(fout), "UTF-8");
                    out.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                } else {
                    createFileOutputStream(".tar");
                    this.out = new TarArchiveOutputStream(fout, "UTF-8");
                    out.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                }
                break;
        }
    }

    /**
     * Close Tar session
     */
    @Override
    public synchronized void close() throws IOException {
        if (!isOpen) {
            return;
        }
        if (out != null) {
            out.flush();
            out.close();
            out = null;
        }
        if (in != null) {
            in.close();
        }
        this.isOpen = false;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    TarArchiveInputStream getInputStream() {
        return in;
    }

    TarArchiveOutputStream getOutputStream() {
        return out;
    }

    /**
     * Helper method for creating the FileOutputStream. Creates the directory if
     * it doesnot exist.
     *
     * @param suffix
     * @throws java.io.IOException
     */
    private synchronized void createFileOutputStream(String suffix) throws IOException {
        File f = new File(part + (part.endsWith(suffix) ? "" : suffix));
        if (!f.getAbsoluteFile().getParentFile().exists()
                && !f.getAbsoluteFile().getParentFile().mkdirs()) {
            throw new RuntimeException(
                    "Could not create directories to store file: " + f);
        }
        if (f.exists()) {
            throw new IOException("file " + f.getAbsolutePath() + " already exists");
        }
        this.fout = new FileOutputStream(f);
    }

    @Override
    public Packet read() throws IOException {
        if (!isOpen()) {
            throw new IOException("not open");
        }
        if (getInputStream() == null) {
            throw new IOException("no tar input stream");
        }
        TarArchiveEntry entry = getInputStream().getNextTarEntry();
        if (entry == null) {
            return null;
        }
        ObjectPacket packet = new ObjectPacket();
        String name = entry.getName();
        packet.setName(name);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        IOUtils.copy(getInputStream(), bout);
        packet.setPacket(new String(bout.toByteArray() , CHARSET_NAME));
        return packet;
    }

    @Override
    public void write(Packet packet) throws IOException {
        if (packet == null || packet.toString() == null) {
            throw new IOException("no packet to write");
        }
        byte[] buf = packet.toString().getBytes(CHARSET_NAME);
        if (buf.length > 0) {
            String name = createEntryName(packet.getName());
            TarArchiveEntry entry = new TarArchiveEntry(name);
            entry.setModTime(new Date());
            entry.setSize(buf.length);
            getOutputStream().putArchiveEntry(entry);
            getOutputStream().write(buf);
            getOutputStream().closeArchiveEntry();
        }
    }

    private AtomicLong counter = new AtomicLong();
    private static final NumberFormat nf = DecimalFormat.getIntegerInstance();

    static {
        nf.setGroupingUsed(false);
        nf.setMinimumIntegerDigits(12);
    }


    private String createEntryName(String name) {
        StringBuilder sb = new StringBuilder();
        if (name != null && name.length() > 0) {
            // normalize name if it contains relative "." or ".."
            try {
                URI n = new URI("file:" + name).normalize();
                sb.append(n.getSchemeSpecificPart());
            } catch (URISyntaxException e) {
                sb.append(name);
            }
        }
        return sb.toString();
    }

    @Override
    public Packet newPacket() {
        return new ObjectPacket();
    }
}
