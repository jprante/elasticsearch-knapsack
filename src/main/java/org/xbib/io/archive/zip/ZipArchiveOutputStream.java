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
package org.xbib.io.archive.zip;

import org.xbib.io.archive.ArchiveOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipArchiveOutputStream extends ArchiveOutputStream<ZipArchiveEntry> {

    private final ZipOutputStream out;

    private ZipEntry zipEntry;

    private List<ByteBuffer> bytes;

    private CRC32 crc;

    private boolean finished;

    private boolean closed;

    public ZipArchiveOutputStream(OutputStream out) {
        this.out = new ZipOutputStream(out);
        this.bytes = new LinkedList<ByteBuffer>();
        this.crc = new CRC32();
    }

    @Override
    public ZipArchiveEntry newArchiveEntry() {
        return new ZipArchiveEntry();
    }

    @Override
    public void putArchiveEntry(ZipArchiveEntry entry) throws IOException {
        zipEntry = entry.getEntry();
    }

    @Override
    public void closeArchiveEntry() throws IOException {
        zipEntry.setCrc(crc.getValue());
        out.putNextEntry(zipEntry);
        WritableByteChannel channel = Channels.newChannel(out);
        for (ByteBuffer buffer : bytes) {
            channel.write(buffer);
        }
        bytes.clear();
        out.closeEntry();
    }

    @Override
    public void finish() throws IOException {
        out.finish();
        finished = true;
    }

    @Override
    public void close() throws IOException {
        if (!finished) {
            finish();
        }
        if (!closed) {
            out.close();
            closed = true;
        }
    }

    @Override
    public void write(byte[] b, int offset, int length) throws IOException {
        getWatcher().updateBytesTransferred(length);
        crc.update(b, offset, length);
        bytes.add(ByteBuffer.wrap(b, offset, length));
    }

}
