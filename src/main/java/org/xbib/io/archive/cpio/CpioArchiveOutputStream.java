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
package org.xbib.io.archive.cpio;

import org.xbib.io.CountingOutputStream;
import org.xbib.io.archive.ArchiveOutputStream;
import org.xbib.io.archive.ArchiveUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * CPIOArchiveOutputStream is a stream for writing CPIO streams. All formats of
 * CPIO are supported (old ASCII, old binary, new portable format and the new
 * portable format with CRC).
 * <p>
 * <p>
 * An entry can be written by creating an instance of CpioArchiveEntry and fill
 * it with the necessary values and put it into the CPIO stream. Afterwards
 * write the contents of the file into the CPIO stream. Either close the stream
 * by calling finish() or put a next entry into the cpio stream.
 * <p>
 * <code>
 * CpioArchiveOutputStream out = new CpioArchiveOutputStream(
 *         new FileOutputStream(new File("test.cpio")));
 * CpioArchiveEntry entry = new CpioArchiveEntry();
 * entry.setName("testfile");
 * String contents = &quot;12345&quot;;
 * entry.setFileSize(contents.length());
 * entry.setMode(CpioConstants.C_ISREG); // regular file
 * ... set other attributes, e.g. time, number of links
 * out.putArchiveEntry(entry);
 * out.write(testContents.getBytes());
 * out.close();
 * </code>
 * <p>
 * Note: This implementation should be compatible to cpio 2.5
 */
public class CpioArchiveOutputStream extends ArchiveOutputStream<CpioArchiveEntry> implements CpioConstants {

    private CpioArchiveEntry entry;

    private boolean closed = false;

    private boolean finished;

    private final short entryFormat;

    private final HashMap<String, CpioArchiveEntry> names = new HashMap<String, CpioArchiveEntry>();

    private long crc = 0;

    private long written;

    private final int blockSize;

    private long nextArtificalDeviceAndInode = 1;

    private final CountingOutputStream out;

    /**
     * Construct the cpio output stream. The format for this CPIO stream is the
     * "new" format
     *
     * @param out The cpio stream
     */
    public CpioArchiveOutputStream(OutputStream out) {
        this(out, FORMAT_NEW);
    }

    /**
     * Construct the cpio output stream with a specified format and a
     * blocksize of {@link CpioConstants#BLOCK_SIZE BLOCK_SIZE}.
     *
     * @param out    The cpio stream
     * @param format The format of the stream
     */
    public CpioArchiveOutputStream(OutputStream out, short format) {
        this(out, format, BLOCK_SIZE);
    }


    /**
     * Construct the cpio output stream with a specified format
     *
     * @param out       The cpio stream
     * @param format    The format of the stream
     * @param blockSize The block size of the archive.
     */
    public CpioArchiveOutputStream(OutputStream out,
                                   short format, int blockSize) {
        this.out = new CountingOutputStream(out);
        switch (format) {
            case FORMAT_NEW:
            case FORMAT_NEW_CRC:
            case FORMAT_OLD_ASCII:
            case FORMAT_OLD_BINARY:
                break;
            default:
                throw new IllegalArgumentException("Unknown format: " + format);

        }
        this.entryFormat = format;
        this.blockSize = blockSize;
    }

    /**
     * Check to make sure that this stream has not been closed
     *
     * @throws java.io.IOException if the stream is already closed
     */
    private void ensureOpen() throws IOException {
        if (this.closed) {
            throw new IOException("Stream closed");
        }
    }

    @Override
    public CpioArchiveEntry newArchiveEntry() {
        return new CpioArchiveEntry(FORMAT_NEW);
    }

    /**
     * Begins writing a new CPIO file entry and positions the stream to the
     * start of the entry data. Closes the current entry if still active. The
     * current time will be used if the entry has no set modification time and
     * the default header format will be used if no other format is specified in
     * the entry.
     *
     * @param entry the CPIO cpioEntry to be written
     * @throws java.io.IOException if an I/O error has occurred or if a CPIO file error has
     *                             occurred
     * @throws ClassCastException  if entry is not an instance of CpioArchiveEntry
     */
    @Override
    public void putArchiveEntry(CpioArchiveEntry entry) throws IOException {
        if (finished) {
            throw new IOException("stream has already been finished");
        }
        ensureOpen();
        if (this.entry != null) {
            closeArchiveEntry(); // close previous entry
        }
        if (entry.getTime() == -1) {
            entry.setTime(System.currentTimeMillis() / 1000);
        }
        if (entry.getFormat() != this.entryFormat) {
            throw new IOException("header format " + entry.getFormat() + " does not match existing format: " + this.entryFormat);
        }
        if (this.names.put(entry.getName(), entry) != null) {
            throw new IOException("duplicate entry: " + entry.getName());
        }
        writeHeader(entry);
        this.entry = entry;
        this.written = 0;
    }

    private void writeHeader(final CpioArchiveEntry e) throws IOException {
        switch (e.getFormat()) {
            case FORMAT_NEW:
                out.write(ArchiveUtils.toAsciiBytes(MAGIC_NEW));
                writeNewEntry(e);
                break;
            case FORMAT_NEW_CRC:
                out.write(ArchiveUtils.toAsciiBytes(MAGIC_NEW_CRC));
                writeNewEntry(e);
                break;
            case FORMAT_OLD_ASCII:
                out.write(ArchiveUtils.toAsciiBytes(MAGIC_OLD_ASCII));
                writeOldAsciiEntry(e);
                break;
            case FORMAT_OLD_BINARY:
                boolean swapHalfWord = true;
                writeBinaryLong(MAGIC_OLD_BINARY, 2, swapHalfWord);
                writeOldBinaryEntry(e, swapHalfWord);
                break;
        }
    }

    private void writeNewEntry(final CpioArchiveEntry entry) throws IOException {
        long inode = entry.getInode();
        long devMin = entry.getDeviceMin();
        if (CPIO_TRAILER.equals(entry.getName())) {
            inode = devMin = 0;
        } else {
            if (inode == 0 && devMin == 0) {
                inode = nextArtificalDeviceAndInode & 0xFFFFFFFF;
                devMin = (nextArtificalDeviceAndInode++ >> 32) & 0xFFFFFFFF;
            } else {
                nextArtificalDeviceAndInode =
                        Math.max(nextArtificalDeviceAndInode,
                                inode + 0x100000000L * devMin) + 1;
            }
        }
        writeAsciiLong(inode, 8, 16);
        writeAsciiLong(entry.getMode(), 8, 16);
        writeAsciiLong(entry.getUID(), 8, 16);
        writeAsciiLong(entry.getGID(), 8, 16);
        writeAsciiLong(entry.getNumberOfLinks(), 8, 16);
        writeAsciiLong(entry.getTime(), 8, 16);
        writeAsciiLong(entry.getEntrySize(), 8, 16);
        writeAsciiLong(entry.getDeviceMaj(), 8, 16);
        writeAsciiLong(devMin, 8, 16);
        writeAsciiLong(entry.getRemoteDeviceMaj(), 8, 16);
        writeAsciiLong(entry.getRemoteDeviceMin(), 8, 16);
        writeAsciiLong(entry.getName().length() + 1, 8, 16);
        writeAsciiLong(entry.getChksum(), 8, 16);
        writeCString(entry.getName());
        pad(entry.getHeaderPadCount());
    }

    private void writeOldAsciiEntry(final CpioArchiveEntry entry)
            throws IOException {
        long inode = entry.getInode();
        long device = entry.getDevice();
        if (CPIO_TRAILER.equals(entry.getName())) {
            inode = device = 0;
        } else {
            if (inode == 0 && device == 0) {
                inode = nextArtificalDeviceAndInode & 0777777;
                device = (nextArtificalDeviceAndInode++ >> 18) & 0777777;
            } else {
                nextArtificalDeviceAndInode =
                        Math.max(nextArtificalDeviceAndInode,
                                inode + 01000000 * device) + 1;
            }
        }
        writeAsciiLong(device, 6, 8);
        writeAsciiLong(inode, 6, 8);
        writeAsciiLong(entry.getMode(), 6, 8);
        writeAsciiLong(entry.getUID(), 6, 8);
        writeAsciiLong(entry.getGID(), 6, 8);
        writeAsciiLong(entry.getNumberOfLinks(), 6, 8);
        writeAsciiLong(entry.getRemoteDevice(), 6, 8);
        writeAsciiLong(entry.getTime(), 11, 8);
        writeAsciiLong(entry.getName().length() + 1, 6, 8);
        writeAsciiLong(entry.getEntrySize(), 11, 8);
        writeCString(entry.getName());
    }

    private void writeOldBinaryEntry(final CpioArchiveEntry entry,
                                     final boolean swapHalfWord) throws IOException {
        long inode = entry.getInode();
        long device = entry.getDevice();
        if (CPIO_TRAILER.equals(entry.getName())) {
            inode = device = 0;
        } else {
            if (inode == 0 && device == 0) {
                inode = nextArtificalDeviceAndInode & 0xFFFF;
                device = (nextArtificalDeviceAndInode++ >> 16) & 0xFFFF;
            } else {
                nextArtificalDeviceAndInode =
                        Math.max(nextArtificalDeviceAndInode,
                                inode + 0x10000 * device) + 1;
            }
        }

        writeBinaryLong(device, 2, swapHalfWord);
        writeBinaryLong(inode, 2, swapHalfWord);
        writeBinaryLong(entry.getMode(), 2, swapHalfWord);
        writeBinaryLong(entry.getUID(), 2, swapHalfWord);
        writeBinaryLong(entry.getGID(), 2, swapHalfWord);
        writeBinaryLong(entry.getNumberOfLinks(), 2, swapHalfWord);
        writeBinaryLong(entry.getRemoteDevice(), 2, swapHalfWord);
        writeBinaryLong(entry.getTime(), 4, swapHalfWord);
        writeBinaryLong(entry.getName().length() + 1, 2, swapHalfWord);
        writeBinaryLong(entry.getEntrySize(), 4, swapHalfWord);
        writeCString(entry.getName());
        pad(entry.getHeaderPadCount());
    }

    @Override
    public void closeArchiveEntry() throws IOException {
        if (finished) {
            throw new IOException("stream has already been finished");
        }
        ensureOpen();
        if (entry == null) {
            throw new IOException("trying to close non-existent entry");
        }
        if (this.entry.getEntrySize() != this.written) {
            throw new IOException("invalid entry size (expected "
                    + this.entry.getEntrySize() + " but got " + this.written + " bytes)");
        }
        pad(this.entry.getDataPadCount());
        if (this.entry.getFormat() == FORMAT_NEW_CRC && this.crc != this.entry.getChksum()) {
            throw new IOException("CRC Error");
        }
        this.entry = null;
        this.crc = 0;
        this.written = 0;
    }

    /**
     * Writes an array of bytes to the current CPIO entry data. This method will
     * block until all the bytes are written.
     *
     * @param b   the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes that are written
     * @throws java.io.IOException if an I/O error has occurred or if a CPIO file error has occurred
     */
    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        ensureOpen();
        getWatcher().updateBytesTransferred(len);
        if (off < 0 || len < 0 || off > b.length - len) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        if (this.entry == null) {
            throw new IOException("no current CPIO entry");
        }
        if (this.written + len > this.entry.getEntrySize()) {
            throw new IOException("attempt to write past end of STORED entry");
        }
        out.write(b, off, len);
        this.written += len;
        if (this.entry.getFormat() == FORMAT_NEW_CRC) {
            for (int pos = 0; pos < len; pos++) {
                this.crc += b[pos] & 0xFF;
            }
        }
    }

    /**
     * Finishes writing the contents of the CPIO output stream without closing
     * the underlying stream. Use this method when applying multiple filters in
     * succession to the same output stream.
     *
     * @throws java.io.IOException if an I/O exception has occurred or if a CPIO file error has
     *                             occurred
     */
    @Override
    public void finish() throws IOException {
        ensureOpen();
        if (finished) {
            throw new IOException("This archive has already been finished");
        }
        if (this.entry != null) {
            throw new IOException("This archive contains unclosed entries.");
        }
        this.entry = new CpioArchiveEntry(this.entryFormat);
        this.entry.setName(CPIO_TRAILER);
        this.entry.setNumberOfLinks(1);
        writeHeader(this.entry);
        closeArchiveEntry();

        int lengthOfLastBlock = (int) (out.getBytesWritten() % blockSize);
        if (lengthOfLastBlock != 0) {
            pad(blockSize - lengthOfLastBlock);
        }
        finished = true;
    }

    /**
     * Closes the CPIO output stream as well as the stream being filtered.
     *
     * @throws java.io.IOException if an I/O error has occurred or if a CPIO file error has
     *                             occurred
     */
    @Override
    public void close() throws IOException {
        if (!finished) {
            finish();
        }
        if (!this.closed) {
            out.close();
            this.closed = true;
        }
    }

    private void pad(int count) throws IOException {
        if (count > 0) {
            byte buff[] = new byte[count];
            out.write(buff);
        }
    }

    private void writeBinaryLong(final long number, final int length,
                                 final boolean swapHalfWord) throws IOException {
        byte tmp[] = CpioUtil.long2byteArray(number, length, swapHalfWord);
        out.write(tmp);
    }

    private void writeAsciiLong(final long number, final int length,
                                final int radix) throws IOException {
        StringBuilder tmp = new StringBuilder();
        String tmpStr;
        if (radix == 16) {
            tmp.append(Long.toHexString(number));
        } else if (radix == 8) {
            tmp.append(Long.toOctalString(number));
        } else {
            tmp.append(Long.toString(number));
        }

        if (tmp.length() <= length) {
            long insertLength = length - tmp.length();
            for (int pos = 0; pos < insertLength; pos++) {
                tmp.insert(0, "0");
            }
            tmpStr = tmp.toString();
        } else {
            tmpStr = tmp.substring(tmp.length() - length);
        }
        byte[] b = ArchiveUtils.toAsciiBytes(tmpStr);
        out.write(b);
    }

    /**
     * Writes an ASCII string to the stream followed by \0
     *
     * @param str the String to write
     * @throws java.io.IOException if the string couldn't be written
     */
    private void writeCString(final String str) throws IOException {
        byte[] b = ArchiveUtils.toAsciiBytes(str);
        out.write(b);
        out.write('\0');
    }

}
