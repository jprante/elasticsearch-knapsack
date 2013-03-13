/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tar Session
 *
 * @author <a href="mailto:joergprante@gmail.com">J&ouml;rg Prante</a>
 */
public class TarSession implements Session {

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
                    String s = part + ".tar.gz";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarArchiveInputStream(codecFactory.getCodec("gz").decode(fin));
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                } else if (scheme.startsWith("tarbz2")) {
                    String s = part + ".tar.bz2";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarArchiveInputStream(codecFactory.getCodec("bz2").decode(fin));
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                } else if (scheme.startsWith("tarxz")) {
                    String s = part + ".tar.xz";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarArchiveInputStream(codecFactory.getCodec("xz").decode(fin));
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                } else {
                    String s = part + ".tar";
                    File f = new File(s);
                    if (f.isFile() && f.canRead()) {
                        this.fin = new FileInputStream(f);
                        this.in = new TarArchiveInputStream(fin);
                        this.isOpen = true;
                    } else {
                        throw new FileNotFoundException("check existence or access rights: " + s);
                    }
                }
                break;
            case WRITE:
                if (scheme.equals("targz")) {
                    createFileOutputStream(".tar.gz");
                    this.out = new TarArchiveOutputStream(codecFactory.getCodec("gz").encode(fout));
                    out.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                } else if (scheme.equals("tarbz2")) {
                    createFileOutputStream(".tar.bz2");
                    this.out = new TarArchiveOutputStream(codecFactory.getCodec("bz2").encode(fout));
                    out.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                } else if (scheme.equals("tarxz")) {
                    createFileOutputStream(".tar.xz");
                    this.out = new TarArchiveOutputStream(codecFactory.getCodec("xz").encode(fout));
                    out.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
                    this.isOpen = true;
                } else {
                    createFileOutputStream(".tar");
                    this.out = new TarArchiveOutputStream(fout);
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
        //return readOp.read(this);
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
        packet.setPacket(new String(bout.toByteArray()));
        return packet;
    }

    @Override
    public void write(Packet packet) throws IOException {
        //writeOp.write(this, packet);
        if (packet == null || packet.toString() == null) {
            throw new IOException("no packet to write");
        }
        byte[] buf = packet.toString().getBytes();
        if (buf.length > 0) {
            String name = createEntryName(packet.getName(), packet.getNumber());
            TarArchiveEntry entry = new TarArchiveEntry(name);
            entry.setModTime(new Date());
            entry.setSize(buf.length);
            //getOutputStream().putNextEntry(entry);
            getOutputStream().putArchiveEntry(entry);
            getOutputStream().write(buf);
            //getOutputStream().closeEntry();
            getOutputStream().closeArchiveEntry();
        }
    }

    private AtomicLong counter = new AtomicLong();
    private static final NumberFormat nf = DecimalFormat.getIntegerInstance();

    static {
        nf.setGroupingUsed(false);
        nf.setMinimumIntegerDigits(12);
    }


    private String createEntryName(String name, String number) {
        StringBuilder sb = new StringBuilder();
        if (name != null && name.length() > 0) {
            sb.append(name);
        }
        if (number != null) {
            // distribute numbered entries over 12-digit directories (10.000 per directory)
            String d = nf.format(counter.incrementAndGet());
            sb.append("/").append(d.substring(0, 4))
                    .append("/").append(d.substring(4, 8))
                    .append("/").append(d.substring(8, 12))
                    .append("/").append(number);
        }
        return sb.toString();
    }

    @Override
    public Packet newPacket() {
        return new ObjectPacket();
    }
}
