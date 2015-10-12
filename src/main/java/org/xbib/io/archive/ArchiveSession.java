/*
 * Copyright (C) 2014 Jörg Prante
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xbib.io.archive;

import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.xbib.io.BytesProgressWatcher;
import org.xbib.io.Session;
import org.xbib.io.StringPacket;
import org.xbib.io.compress.CompressCodecService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A basic archive session
 */
public abstract class ArchiveSession<I extends ArchiveInputStream, O extends ArchiveOutputStream> implements Session<StringPacket> {

    private final static ESLogger logger = ESLoggerFactory.getLogger(ArchiveSession.class.getSimpleName());

    private final static CompressCodecService codecService = CompressCodecService.getInstance();

    private final static ArchiveService archiveService = ArchiveService.getInstance();

    private boolean isOpen;

    private EnumSet<Mode> mode;

    private File file;

    private Path path;

    private I in;

    private O out;

    private BytesProgressWatcher watcher;

    private long packetCounter;

    private AtomicLong archiveCounter = new AtomicLong();

    protected ArchiveSession(BytesProgressWatcher watcher) {
        this.watcher = watcher;
        this.packetCounter = 0L;
    }

    public BytesProgressWatcher getWatcher() {
        if (watcher == null) {
            watcher = new BytesProgressWatcher(0L);
        }
        return watcher;
    }

    public long getPacketCounter() {
        return packetCounter;
    }

    protected abstract String getName();

    @Override
    public synchronized void open(EnumSet<Mode> mode, Path path) throws IOException {
        if (isOpen) {
            return;
        }
        this.mode = mode;
        this.path = path;
        this.file = path.toFile();
        if (mode.contains(Mode.READ)) {
            this.in = createArchiveInputStream();
            this.isOpen = this.in != null;
            if (!isOpen) {
                throw new FileNotFoundException("can't open for input, check existence or access rights: " + file.getAbsolutePath());
            }
        } else if (mode.contains(Mode.WRITE)) {
            this.out = createArchiveOutputStream(false);
            this.isOpen = this.out != null;
            if (!isOpen) {
                throw new FileNotFoundException("can't open for output, check existence or access rights: " + file.getAbsolutePath());
            }
        } else if (mode.contains(Mode.OVERWRITE)) {
            this.out = createArchiveOutputStream(true);
            this.isOpen = this.out != null;
            if (!isOpen) {
                throw new FileNotFoundException("can't open for output, check existence or access rights: " + file.getAbsolutePath());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private I createArchiveInputStream() throws IOException {
        I archiveIn;
        FileInputStream in;
        if (file.isFile() && file.canRead()) {
            in = new FileInputStream(file);
        } else {
            throw new FileNotFoundException("can't open for input, check existence or access rights: " + path);
        }
        String pathStr = path.toString();
        Set<String> streamCodecs = CompressCodecService.getCodecs();
        for (String codec : streamCodecs) {
            if (pathStr.endsWith("." + codec)) {
                archiveIn = (I) archiveService.getCodec(getName()).createArchiveInputStream(codecService.getCodec(codec).decode(in));
                archiveIn.setWatcher(watcher);
                return archiveIn;
            }
        }
        archiveIn = (I) archiveService.getCodec(getName()).createArchiveInputStream(in);
        archiveIn.setWatcher(watcher);
        return archiveIn;
    }

    @SuppressWarnings("unchecked")
    private O createArchiveOutputStream(boolean overwrite) throws IOException {
        O archiveOut;
        FileOutputStream out;
        if (!file.exists() || file.length() == 0 || overwrite) {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                throw new IOException("can not create directory: " + file.getParent());
            }
            out = new FileOutputStream(file);
        } else {
            throw new FileNotFoundException("can't open for output, check existence or access rights: " + file.getAbsolutePath());
        }
        String pathStr = path.toString();
        Set<String> streamCodecs = CompressCodecService.getCodecs();
        for (String codec : streamCodecs) {
            if (pathStr.endsWith("." + codec)) {
                archiveOut = (O) archiveService.getCodec(getName()).createArchiveOutputStream(codecService.getCodec(codec).encode(out));
                archiveOut.setWatcher(watcher);
                return archiveOut;
            }
        }
        archiveOut = (O) archiveService.getCodec(getName()).createArchiveOutputStream(out);
        archiveOut.setWatcher(watcher);
        return archiveOut;
    }

    @Override
    public StringPacket newPacket() {
        return new StringPacket();
    }

    @Override
    public synchronized StringPacket read() throws IOException {
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
        StringPacket packet = newPacket();
        String name = entry.getName();
        packet.meta("name", name);
        ArchiveUtils.decodeArchiveEntryName(packet, name);
        int size = (int) entry.getEntrySize();
        if (size >= 0) {
            byte[] b = new byte[size]; // naive but fast, heap may explode
            int num = in.read(b, 0, size); // fill byte array from stream
            packet.payload(new String(b, "UTF-8"));
        } else {
            // slow copy, unknown size (zip deflate method)
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            Streams.copy(in, b);
            packet.payload(new String(b.toByteArray(), "UTF-8"));
        }
        packetCounter++;
        return packet;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized void write(StringPacket packet) throws IOException {
        if (!isOpen()) {
            throw new IOException("not open");
        }
        if (out == null) {
            throw new IOException("no output stream found");
        }
        if (packet == null || packet.payload() == null) {
            throw new IOException("no payload to write for entry");
        }
        byte[] buf = packet.payload().getBytes("UTF-8");
        String name = ArchiveUtils.encodeArchiveEntryName(packet);
        ArchiveEntry entry = out.newArchiveEntry();
        entry.setName(name);
        entry.setLastModified(new Date());
        entry.setEntrySize(buf.length);
        out.putArchiveEntry(entry);
        out.write(buf);
        out.closeArchiveEntry();
        packetCounter++;
        if (watcher.getBytesToTransfer() != 0 && watcher.getBytesTransferred() > watcher.getBytesToTransfer()) {
            logger.debug("bytes watcher: transferred = {}, rate {}",
                    watcher.getBytesTransferred(), watcher.getRecentByteRatePerSecond());
            switchToNextArchive();
            watcher.resetWatcher();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!isOpen) {
            return;
        }
        if (out != null) {
            out.close();
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

    /**
     * Switches to next archive stream if a certain byte limit was set
     *
     * @throws IOException
     */
    private void switchToNextArchive() throws IOException {
        close();
        String filename = file.getName();
        String prefix = Long.toString(archiveCounter.get()) + ".";
        if (filename.startsWith(prefix)) {
            filename = filename.substring(prefix.length());
        }
        filename = archiveCounter.incrementAndGet() + "." + filename;
        this.file = new File(file.getParent() + File.separator + filename);
        this.path = file.toPath();
        open(mode, path);
    }


}