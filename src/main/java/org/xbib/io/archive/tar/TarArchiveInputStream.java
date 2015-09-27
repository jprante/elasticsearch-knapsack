package org.xbib.io.archive.tar;

import org.xbib.io.archive.ArchiveInputStream;
import org.xbib.io.archive.entry.ArchiveEntryEncoding;
import org.xbib.io.archive.entry.ArchiveEntryEncodingHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class TarArchiveInputStream extends ArchiveInputStream<TarArchiveInputEntry> implements TarConstants {

    private final ArchiveEntryEncoding encoding;

    private final InputStream inStream;

    private final int blockSize;

    private final int recordSize;

    private final int recsPerBlock;

    private final byte[] blockBuffer;

    private byte[] readBuf;

    private boolean hasHitEOF;

    private long entrySize;

    private long entryOffset;

    private TarArchiveInputEntry entry;

    private int currRecIdx;

    /**
     * Constructor for TarInputStream.
     *
     * @param is the input stream to use
     */
    public TarArchiveInputStream(InputStream is) {
        this.encoding = ArchiveEntryEncodingHelper.getEncoding(null);
        this.readBuf = null;
        this.hasHitEOF = false;
        this.inStream = is;
        this.blockSize = DEFAULT_BLOCK_SIZE;
        this.recordSize = DEFAULT_RECORD_SIZE;
        this.recsPerBlock = this.blockSize / this.recordSize;
        this.blockBuffer = new byte[this.blockSize];
        this.currRecIdx = this.recsPerBlock;
    }

    /**
     * Closes this stream
     *
     * @throws IOException on error
     */
    @Override
    public void close() throws IOException {
        if (inStream != null) {
            if (inStream != System.in) {
                inStream.close();
            }
        }
    }

    /**
     * Get the record size
     *
     * @return the record size.
     */
    public int getRecordSize() {
        return recordSize;
    }

    /**
     * Get the available data that can be read from the current
     * entry in the archive. This does not indicate how much data
     * is left in the entire archive, only in the current entry.
     * This value is determined from the entry's size header field
     * and the amount of data already read from the current entry.
     * Integer.MAX_VALUE is returen in case more than Integer.MAX_VALUE
     * bytes are left in the current entry in the archive.
     *
     * @return The number of available bytes for the current entry.
     * @throws IOException
     */
    @Override
    public int available() throws IOException {
        if (entrySize - entryOffset > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) (entrySize - entryOffset);
    }

    /**
     * Skip bytes in the input buffer. This skips bytes in the
     * current entry's data, not the entire archive, and will
     * stop at the end of the current entry's data if the number
     * to skip extends beyond that point.
     *
     * @param numToSkip The number of bytes to skip.
     * @return the number actually skipped
     * @throws IOException on error
     */
    @Override
    public long skip(long numToSkip) throws IOException {
        // REVIEW
        // This is horribly inefficient, but it ensures that we
        // properly skip over bytes
        //
        byte[] skipBuf = new byte[1024];
        long skip = numToSkip;
        while (skip > 0) {
            int realSkip = (int) (skip > skipBuf.length ? skipBuf.length : skip);
            int numRead = read(skipBuf, 0, realSkip);
            if (numRead == -1) {
                break;
            }
            skip -= numRead;
        }
        return (numToSkip - skip);
    }

    /**
     * Since we do not support marking just yet, we do nothing.
     */
    @Override
    public void reset() {
    }

    /**
     * Get the next entry in this tar archive. This will skip
     * over any remaining data in the current entry, if there
     * is one, and place the input stream at the header of the
     * next entry, and read the header and instantiate a new
     * TarEntry from the header bytes and return that entry.
     * If there are no more entries in the archive, null will
     * be returned to indicate that the end of the archive has
     * been reached.
     *
     * @return The next TarEntry in the archive, or null.
     * @throws IOException on error
     */
    public synchronized TarArchiveInputEntry getNextTarEntry() throws IOException {
        if (hasHitEOF) {
            return null;
        }
        if (entry != null) {
            long numToSkip = entrySize - entryOffset;
            while (numToSkip > 0) {
                long skipped = skip(numToSkip);
                if (skipped <= 0) {
                    throw new RuntimeException("failed to skip current tar entry");
                }
                numToSkip -= skipped;
            }
            readBuf = null;
        }
        byte[] headerBuf = getRecord();
        if (hasHitEOF) {
            entry = null;
            return null;
        }
        try {
            this.entry = new TarArchiveInputEntry(headerBuf, encoding);
            this.entryOffset = 0;
            this.entrySize = this.entry.getEntrySize();
        } catch (IllegalArgumentException e) {
            throw new IOException("error detected parsing the header", e);
        }
        if (entry.isGNULongNameEntry()) {
            StringBuilder longName = new StringBuilder();
            byte[] buf = new byte[SMALL_BUFFER_SIZE];
            int length;
            while ((length = read(buf)) >= 0) {
                longName.append(new String(buf, 0, length));
            }
            getNextEntry();
            if (entry == null) {
                return null;
            }
            if (longName.length() > 0 && longName.charAt(longName.length() - 1) == 0) {
                longName.deleteCharAt(longName.length() - 1);
            }
            entry.setName(longName.toString());
        }
        if (entry.isPaxHeader()) {
            paxHeaders();
        }
        return entry;
    }

    /**
     * Get the next record in this tar archive. This will skip
     * over any remaining data in the current entry, if there
     * is one, and place the input stream at the header of the
     * next entry.
     * If there are no more entries in the archive, null will
     * be returned to indicate that the end of the archive has
     * been reached.
     *
     * @return The next header in the archive, or null.
     * @throws IOException on error
     */
    private byte[] getRecord() throws IOException {
        if (hasHitEOF) {
            return null;
        }
        byte[] headerBuf = readRecord();
        if (headerBuf == null) {
            hasHitEOF = true;
        } else if (isEOFRecord(headerBuf)) {
            hasHitEOF = true;
        }
        return hasHitEOF ? null : headerBuf;
    }

    /**
     * Read a record from the input stream and return the data.
     *
     * @return The record data.
     * @throws IOException on error
     */
    private byte[] readRecord() throws IOException {
        if (currRecIdx >= recsPerBlock && !readBlock()) {
            return null;
        }
        byte[] result = new byte[recordSize];
        System.arraycopy(blockBuffer, (currRecIdx * recordSize), result, 0, recordSize);
        currRecIdx++;
        return result;
    }

    private boolean readBlock() throws IOException {
        currRecIdx = 0;
        int offset = 0;
        int bytesNeeded = blockSize;
        while (bytesNeeded > 0) {
            long numBytes = inStream.read(blockBuffer, offset, bytesNeeded);
            if (numBytes == -1) {
                if (offset == 0) {
                    return false;
                }
                Arrays.fill(blockBuffer, offset, offset + bytesNeeded, (byte) 0);
                break;
            }
            offset += numBytes;
            bytesNeeded -= numBytes;
        }
        return true;
    }

    /**
     * Determine if an archive record indicate End of Archive. End of
     * archive is indicated by a record that consists entirely of null bytes.
     *
     * @param record The record data to check.
     * @return true if the record data is an End of Archive
     */
    private boolean isEOFRecord(byte[] record) {
        for (int i = 0, sz = getRecordSize(); i < sz; ++i) {
            if (record[i] != 0) {
                return false;
            }
        }
        return true;
    }


    private void paxHeaders() throws IOException {
        Map<String, String> headers = parsePaxHeaders(this);
        getNextEntry(); // Get the actual file entry
        applyPaxHeadersToCurrentEntry(headers);
    }

    private Map<String, String> parsePaxHeaders(InputStream i) throws IOException {
        Map<String, String> headers = new HashMap<String, String>();
        // Format is "length keyword=value\n";
        while (true) { // get length
            int ch;
            int len = 0;
            int read = 0;
            while ((ch = i.read()) != -1) {
                read++;
                if (ch == ' ') { // End of length string
                    // Get keyword
                    ByteArrayOutputStream coll = new ByteArrayOutputStream();
                    while ((ch = i.read()) != -1) {
                        read++;
                        if (ch == '=') { // end of keyword
                            String keyword = coll.toString("UTF-8");
                            // Get rest of entry
                            byte[] rest = new byte[len - read];
                            int got = i.read(rest);
                            if (got != len - read) {
                                throw new IOException("Failed to read "
                                        + "Paxheader. Expected "
                                        + (len - read)
                                        + " bytes, read "
                                        + got);
                            }
                            // Drop trailing NL
                            String value = new String(rest, 0,
                                    len - read - 1, Charset.forName("UTF-8"));
                            headers.put(keyword, value);
                            break;
                        }
                        coll.write((byte) ch);
                    }
                    break; // Processed single header
                }
                len *= 10;
                len += ch - '0';
            }
            if (ch == -1) { // EOF
                break;
            }
        }
        return headers;
    }

    private void applyPaxHeadersToCurrentEntry(Map<String, String> headers) {
        /*
         * The following headers are defined for Pax.
         * atime, ctime, charset: cannot use these without changing TarArchiveEntry fields
         * mtime
         * comment
         * gid, gname
         * linkpath
         * size
         * uid,uname
         * SCHILY.devminor, SCHILY.devmajor: don't have setters/getters for those
         */
        for (Entry<String, String> ent : headers.entrySet()) {
            String key = ent.getKey();
            String val = ent.getValue();
            if ("path".equals(key)) {
                entry.setName(val);
            } else if ("linkpath".equals(key)) {
                entry.setLinkName(val);
            } else if ("gid".equals(key)) {
                entry.setGroupId(Integer.parseInt(val));
            } else if ("gname".equals(key)) {
                entry.setGroupName(val);
            } else if ("uid".equals(key)) {
                entry.setUserId(Integer.parseInt(val));
            } else if ("uname".equals(key)) {
                entry.setUserName(val);
            } else if ("size".equals(key)) {
                entry.setEntrySize(Long.parseLong(val));
            } else if ("mtime".equals(key)) {
                long mtime = (long) (Double.parseDouble(val) * 1000);
                entry.setLastModified(new Date(mtime));
            } else if ("SCHILY.devminor".equals(key)) {
                entry.setDevMinor(Integer.parseInt(val));
            } else if ("SCHILY.devmajor".equals(key)) {
                entry.setDevMajor(Integer.parseInt(val));
            }
        }
    }

    @Override
    public TarArchiveInputEntry getNextEntry() throws IOException {
        return getNextTarEntry();
    }

    /**
     * Reads bytes from the current tar archive entry.
     * This method is aware of the boundaries of the current
     * entry in the archive and will deal with them as if they
     * were this stream's start and EOF.
     *
     * @param buf       The buffer into which to place bytes read.
     * @param offset    The offset at which to place bytes read.
     * @param numToRead The number of bytes to read.
     * @return The number of bytes read, or -1 at EOF
     * @throws IOException on error
     */
    @Override
    public int read(byte[] buf, int offset, int numToRead) throws IOException {
        int totalRead = 0;
        if (entryOffset >= entrySize) {
            return -1;
        }
        if ((numToRead + entryOffset) > entrySize) {
            numToRead = (int) (entrySize - entryOffset);
        }
        if (readBuf != null) {
            int sz = (numToRead > readBuf.length) ? readBuf.length : numToRead;
            System.arraycopy(readBuf, 0, buf, offset, sz);
            if (sz >= readBuf.length) {
                readBuf = null;
            } else {
                int newLen = readBuf.length - sz;
                byte[] newBuf = new byte[newLen];
                System.arraycopy(readBuf, sz, newBuf, 0, newLen);
                readBuf = newBuf;
            }
            totalRead += sz;
            numToRead -= sz;
            offset += sz;
        }
        while (numToRead > 0) {
            byte[] rec = readRecord();
            if (rec == null) {
                throw new IOException("unexpected EOF with " + numToRead + " bytes unread");
            }
            int sz = numToRead;
            int recLen = rec.length;
            if (recLen > sz) {
                System.arraycopy(rec, 0, buf, offset, sz);
                readBuf = new byte[recLen - sz];
                System.arraycopy(rec, sz, readBuf, 0, recLen - sz);
            } else {
                sz = recLen;
                System.arraycopy(rec, 0, buf, offset, recLen);
            }
            totalRead += sz;
            numToRead -= sz;
            offset += sz;
        }
        entryOffset += totalRead;
        return totalRead;
    }

}
