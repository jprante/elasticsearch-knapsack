package org.xbib.io.archive.tar;

import org.xbib.io.archive.ArchiveOutputStream;
import org.xbib.io.archive.entry.ArchiveEntryEncoding;
import org.xbib.io.archive.entry.ArchiveEntryEncodingHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The TarOutputStream writes a UNIX tar archive as an output stream
 */
public class TarArchiveOutputStream extends ArchiveOutputStream<TarArchiveOutputEntry> implements TarConstants {

    private static final ArchiveEntryEncoding ASCII = ArchiveEntryEncodingHelper.getEncoding("ASCII");

    private final ArchiveEntryEncoding encoding;

    private final OutputStream outStream;

    private final int blockSize;

    private final int recordSize;

    private final int recsPerBlock;

    private final byte[] blockBuffer;

    private final byte[] recordBuf;

    private final byte[] assemBuf;

    private int currRecIdx;

    private long currSize;

    private String currName;

    private long currBytes;

    private int assemLen;

    private int longFileMode = LONGFILE_GNU;

    private int bigNumberMode = BIGNUMBER_ERROR;

    private boolean closed = false;

    /**
     * Indicates if putArchiveEntry has been called without closeArchiveEntry
     */
    private boolean haveUnclosedEntry = false;

    /**
     * indicates if this archive is finished
     */
    private boolean finished = false;

    private boolean addPaxHeadersForNonAsciiNames = false;

    /**
     * Constructor for TarInputStream.
     *
     * @param os the output stream to use
     */
    public TarArchiveOutputStream(OutputStream os) {
        this(os, DEFAULT_BLOCK_SIZE, DEFAULT_RECORD_SIZE);
    }

    /**
     * Constructor for TarInputStream.
     *
     * @param os       the output stream to use
     * @param encoding name of the encoding to use for file names
     */
    public TarArchiveOutputStream(OutputStream os, String encoding) {
        this(os, DEFAULT_BLOCK_SIZE, DEFAULT_RECORD_SIZE, encoding);
    }

    /**
     * Constructor for TarInputStream.
     *
     * @param os        the output stream to use
     * @param blockSize the block size to use
     */
    public TarArchiveOutputStream(OutputStream os, int blockSize) {
        this(os, blockSize, DEFAULT_RECORD_SIZE);
    }

    /**
     * Constructor for TarInputStream.
     *
     * @param os        the output stream to use
     * @param blockSize the block size to use
     * @param encoding  name of the encoding to use for file names
     */
    public TarArchiveOutputStream(OutputStream os, int blockSize, String encoding) {
        this(os, blockSize, DEFAULT_RECORD_SIZE, encoding);
    }

    /**
     * Constructor for TarInputStream.
     *
     * @param os         the output stream to use
     * @param blockSize  the block size to use
     * @param recordSize the record size to use
     */
    public TarArchiveOutputStream(OutputStream os, int blockSize, int recordSize) {
        this(os, blockSize, recordSize, null);
    }

    /**
     * Constructor for TarInputStream.
     *
     * @param os         the output stream to use
     * @param blockSize  the block size to use
     * @param recordSize the record size to use
     * @param encoding   name of the encoding to use for file names
     */
    public TarArchiveOutputStream(OutputStream os, int blockSize, int recordSize, String encoding) {
        this.encoding = ArchiveEntryEncodingHelper.getEncoding(encoding);
        this.assemLen = 0;
        this.assemBuf = new byte[recordSize];
        this.recordBuf = new byte[recordSize];
        this.outStream = os;
        this.blockSize = blockSize;
        this.recordSize = recordSize;
        this.recsPerBlock = (this.blockSize / this.recordSize);
        this.blockBuffer = new byte[this.blockSize];
        this.currRecIdx = 0;
    }

    /**
     * Set the long file mode.
     * This can be LONGFILE_ERROR(0), LONGFILE_TRUNCATE(1) or LONGFILE_GNU(2).
     * This specifies the treatment of long file names (names &gt;= NAMELEN).
     * Default is LONGFILE_GNU.
     *
     * @param longFileMode the mode to use
     */
    public void setLongFileMode(int longFileMode) {
        this.longFileMode = longFileMode;
    }

    /**
     * Set the big number mode.
     * This can be BIGNUMBER_ERROR(0), BIGNUMBER_POSIX(1) or BIGNUMBER_STAR(2).
     * This specifies the treatment of big files (sizes &gt; MAXSIZE) and other numeric values to big to fit into a traditional tar header.
     * Default is BIGNUMBER_ERROR.
     *
     * @param bigNumberMode the mode to use
     */
    public void setBigNumberMode(int bigNumberMode) {
        this.bigNumberMode = bigNumberMode;
    }

    /**
     * Whether to add a PAX extension header for non-ASCII file names.
     */
    public void setAddPaxHeadersForNonAsciiNames(boolean b) {
        addPaxHeadersForNonAsciiNames = b;
    }

    /**
     * Ends the TAR archive without closing the underlying OutputStream.
     * An archive consists of a series of file entries terminated by an
     * end-of-archive entry, which consists of two 512 blocks of zero bytes.
     * POSIX.1 requires two EOF records, like some other implementations.
     *
     * @throws IOException on error
     */
    @Override
    public void finish() throws IOException {
        if (finished) {
            throw new IOException("This archive has already been finished");
        }

        if (haveUnclosedEntry) {
            throw new IOException("This archives contains unclosed entries.");
        }
        writeEOFRecord();
        writeEOFRecord();
        flushBlock();
        finished = true;
    }

    /**
     * Closes the underlying OutputStream.
     *
     * @throws IOException on error
     */
    @Override
    public void close() throws IOException {
        if (!finished) {
            finish();
        }
        if (!closed) {
            if (outStream != null) {
                flushBlock();
                if (outStream != System.out
                        && outStream != System.err) {
                    outStream.close();
                }
            }
            closed = true;
        }
    }

    private void flushBlock() throws IOException {
        if (outStream == null) {
            throw new IOException("writing to an input buffer");
        }
        if (currRecIdx > 0) {
            writeBlock();
        }
    }

    private void writeBlock() throws IOException {
        if (outStream == null) {
            throw new IOException("writing to an input buffer");
        }
        outStream.write(blockBuffer, 0, blockSize);
        outStream.flush();
        currRecIdx = 0;
        Arrays.fill(blockBuffer, (byte) 0);
    }


    /**
     * Get the record size being used by this stream's TarBuffer.
     *
     * @return The TarBuffer record size.
     */
    public int getRecordSize() {
        return recordSize;
    }

    @Override
    public TarArchiveOutputEntry newArchiveEntry() {
        return new TarArchiveOutputEntry();
    }

    /**
     * Put an entry on the output stream. This writes the entry's
     * header record and positions the output stream for writing
     * the contents of the entry. Once this method is called, the
     * stream is ready for calls to write() to write the entry's
     * contents. Once the contents are written, closeArchiveEntry()
     * <B>MUST</B> be called to ensure that all buffered data
     * is completely written to the output stream.
     *
     * @param archiveEntry The TarEntry to be written to the archive.
     * @throws IOException on error
     * @throws ClassCastException  if archiveEntry is not an instance of TarArchiveEntry
     */
    @Override
    public void putArchiveEntry(TarArchiveOutputEntry archiveEntry) throws IOException {
        if (finished) {
            throw new IOException("Stream has already been finished");
        }
        Map<String, String> paxHeaders = new HashMap<String, String>();
        final String entryName = archiveEntry.getName();
        final byte[] nameBytes = encoding.encode(entryName).array();
        boolean paxHeaderContainsPath = false;
        if (nameBytes.length >= NAMELEN) {
            if (longFileMode == LONGFILE_POSIX) {
                paxHeaders.put("path", entryName);
                paxHeaderContainsPath = true;
            } else if (longFileMode == LONGFILE_GNU) {
                // create a TarEntry for the LongLink, the contents
                // of which are the entry's name
                TarArchiveOutputEntry longLinkEntry = new TarArchiveOutputEntry(GNU_LONGLINK, LF_GNUTYPE_LONGNAME);
                longLinkEntry.setEntrySize(nameBytes.length + 1); // +1 for NUL
                putArchiveEntry(longLinkEntry);
                write(nameBytes);
                write(0); // NUL terminator
                closeArchiveEntry();
            } else if (longFileMode != LONGFILE_TRUNCATE) {
                throw new RuntimeException("file name '" + entryName
                        + "' is too long ( > "
                        + NAMELEN + " bytes)");
            }
        }
        if (bigNumberMode == BIGNUMBER_POSIX) {
            addPaxHeadersForBigNumbers(paxHeaders, archiveEntry);
        } else if (bigNumberMode != BIGNUMBER_STAR) {
            failForBigNumbers(archiveEntry);
        }
        if (addPaxHeadersForNonAsciiNames && !paxHeaderContainsPath
                && !ASCII.canEncode(entryName)) {
            paxHeaders.put("path", entryName);
        }
        if (addPaxHeadersForNonAsciiNames
                && (archiveEntry.isLink() || archiveEntry.isSymbolicLink())
                && !ASCII.canEncode(archiveEntry.getLinkName())) {
            paxHeaders.put("linkpath", archiveEntry.getLinkName());
        }
        if (paxHeaders.size() > 0) {
            writePaxHeaders(entryName, paxHeaders);
        }
        archiveEntry.writeEntryHeader(recordBuf, encoding, bigNumberMode == BIGNUMBER_STAR);
        writeRecord(recordBuf);
        currBytes = 0;
        if (archiveEntry.isDirectory()) {
            currSize = 0;
        } else {
            currSize = archiveEntry.getEntrySize();
        }
        currName = entryName;
        haveUnclosedEntry = true;
    }

    /**
     * Close an entry. This method MUST be called for all file
     * entries that contain data. The reason is that we must
     * buffer data written to the stream in order to satisfy
     * the buffer's record based writes. Thus, there may be
     * data fragments still being assembled that must be written
     * to the output stream before this entry is closed and the
     * next entry written.
     *
     * @throws IOException on error
     */
    @Override
    public void closeArchiveEntry() throws IOException {
        if (finished) {
            throw new IOException("stream has already been finished");
        }
        if (!haveUnclosedEntry) {
            throw new IOException("no current entry to close");
        }
        if (assemLen > 0) {
            for (int i = assemLen; i < assemBuf.length; ++i) {
                assemBuf[i] = 0;
            }

            writeRecord(assemBuf);

            currBytes += assemLen;
            assemLen = 0;
        }

        if (currBytes < currSize) {
            throw new IOException("entry '" + currName + "' closed at '"
                    + currBytes
                    + "' before the '" + currSize
                    + "' bytes specified in the header were written");
        }
        haveUnclosedEntry = false;
    }

    /**
     * Writes bytes to the current tar archive entry. This method
     * is aware of the current entry and will throw an exception if
     * you attempt to write bytes past the length specified for the
     * current entry. The method is also (painfully) aware of the
     * record buffering required by TarBuffer, and manages buffers
     * that are not a multiple of recordsize in length, including
     * assembling records from small buffers.
     *
     * @param wBuf       The buffer to write to the archive.
     * @param wOffset    The offset in the buffer from which to get bytes.
     * @param numToWrite The number of bytes to write.
     * @throws IOException on error
     */
    @Override
    public void write(byte[] wBuf, int wOffset, int numToWrite) throws IOException {
        if ((currBytes + numToWrite) > currSize) {
            throw new IOException("request to write '" + numToWrite
                    + "' bytes exceeds size in header of '"
                    + currSize + "' bytes for entry '"
                    + currName + "'");

            //
            // We have to deal with assembly!!!
            // The programmer can be writing little 32 byte chunks for all
            // we know, and we must assemble complete records for writing.
            // REVIEW Maybe this should be in TarBuffer? Could that help to
            // eliminate some of the buffer copying.
            //
        }

        if (assemLen > 0) {
            if ((assemLen + numToWrite) >= recordBuf.length) {
                int aLen = recordBuf.length - assemLen;

                System.arraycopy(assemBuf, 0, recordBuf, 0,
                        assemLen);
                System.arraycopy(wBuf, wOffset, recordBuf,
                        assemLen, aLen);
                writeRecord(recordBuf);

                currBytes += recordBuf.length;
                wOffset += aLen;
                numToWrite -= aLen;
                assemLen = 0;
            } else {
                System.arraycopy(wBuf, wOffset, assemBuf, assemLen,
                        numToWrite);

                wOffset += numToWrite;
                assemLen += numToWrite;
                numToWrite = 0;
            }
        }

        //
        // When we get here we have EITHER:
        // o An empty "assemble" buffer.
        // o No bytes to write (numToWrite == 0)
        //
        while (numToWrite > 0) {
            if (numToWrite < recordBuf.length) {
                System.arraycopy(wBuf, wOffset, assemBuf, assemLen,
                        numToWrite);

                assemLen += numToWrite;

                break;
            }

            writeRecord(wBuf, wOffset);

            int num = recordBuf.length;

            currBytes += num;
            numToWrite -= num;
            wOffset += num;
        }
    }

    /**
     * Writes a PAX extended header with the given map as contents.
     */
    void writePaxHeaders(String entryName, Map<String, String> headers) throws IOException {
        String name = "./PaxHeaders.X/" + stripTo7Bits(entryName);
        if (name.length() >= NAMELEN) {
            name = name.substring(0, NAMELEN - 1);
        }
        TarArchiveOutputEntry pex = new TarArchiveOutputEntry(name, LF_PAX_EXTENDED_HEADER_LC);
        StringWriter w = new StringWriter();
        for (Map.Entry<String, String> h : headers.entrySet()) {
            String key = h.getKey();
            String value = h.getValue();
            int len = key.length() + value.length()
                    + 3 /* blank, equals and newline */
                    + 2 /* guess 9 < actual length < 100 */;
            String line = len + " " + key + "=" + value + "\n";
            int actualLength = line.getBytes(Charset.forName("UTF-8")).length;
            while (len != actualLength) {
                // Adjust for cases where length < 10 or > 100
                // or where UTF-8 encoding isn't a single octet
                // per character.
                // Must be in loop as size may go from 99 to 100 in
                // first pass so we'd need a second.
                len = actualLength;
                line = len + " " + key + "=" + value + "\n";
                actualLength = line.getBytes(Charset.forName("UTF-8")).length;
            }
            w.write(line);
        }
        byte[] data = w.toString().getBytes(Charset.forName("UTF-8"));
        pex.setEntrySize(data.length);
        putArchiveEntry(pex);
        write(data);
        closeArchiveEntry();
    }

    private String stripTo7Bits(String name) {
        final int length = name.length();
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char stripped = (char) (name.charAt(i) & 0x7F);
            if (stripped != 0) { // would be read as Trailing null
                result.append(stripped);
            }
        }
        return result.toString();
    }

    /**
     * Write an EOF (end of archive) record to the tar archive.
     * An EOF record consists of a record of all zeros.
     */
    private void writeEOFRecord() throws IOException {
        for (int i = 0; i < recordBuf.length; ++i) {
            recordBuf[i] = 0;
        }

        writeRecord(recordBuf);
    }

    @Override
    public void flush() throws IOException {
        outStream.flush();
    }

    private void addPaxHeadersForBigNumbers(Map<String, String> paxHeaders,
                                            TarArchiveOutputEntry entry) {
        addPaxHeaderForBigNumber(paxHeaders, "size", entry.getEntrySize(), MAXSIZE);
        addPaxHeaderForBigNumber(paxHeaders, "gid", entry.getGroupId(), MAXID);
        addPaxHeaderForBigNumber(paxHeaders, "mtime", entry.getLastModified().getTime() / 1000, MAXSIZE);
        addPaxHeaderForBigNumber(paxHeaders, "uid", entry.getUserId(), MAXID);
        // star extensions by J\u00f6rg Schilling
        addPaxHeaderForBigNumber(paxHeaders, "SCHILY.devmajor", entry.getDevMajor(), MAXID);
        addPaxHeaderForBigNumber(paxHeaders, "SCHILY.devminor", entry.getDevMinor(), MAXID);
        // there is no PAX header for file mode
        failForBigNumber("mode", entry.getMode(), MAXID);
    }

    private void addPaxHeaderForBigNumber(Map<String, String> paxHeaders,
                                          String header, long value,
                                          long maxValue) {
        if (value < 0 || value > maxValue) {
            paxHeaders.put(header, String.valueOf(value));
        }
    }

    private void failForBigNumbers(TarArchiveOutputEntry entry) {
        failForBigNumber("entry size", entry.getEntrySize(), MAXSIZE);
        failForBigNumber("group id", entry.getGroupId(), MAXID);
        failForBigNumber("last modification time", entry.getLastModified().getTime() / 1000, MAXSIZE);
        failForBigNumber("user id", entry.getUserId(), MAXID);
        failForBigNumber("mode", entry.getMode(), MAXID);
        failForBigNumber("major device number", entry.getDevMajor(), MAXID);
        failForBigNumber("minor device number", entry.getDevMinor(), MAXID);
    }

    private void failForBigNumber(String field, long value, long maxValue) {
        if (value < 0 || value > maxValue) {
            throw new RuntimeException(field + " '" + value
                    + "' is too big ( > "
                    + maxValue + " )");
        }
    }

    private void writeRecord(byte[] record) throws IOException {
        if (outStream == null) {
            throw new IOException("Output buffer is closed");
        }
        if (record.length != recordSize) {
            throw new IOException("record to write has length '"
                    + record.length
                    + "' which is not the record size of '"
                    + recordSize + "'");
        }
        if (currRecIdx >= recsPerBlock) {
            writeBlock();
        }
        System.arraycopy(record, 0, blockBuffer, (currRecIdx * recordSize), recordSize);
        currRecIdx++;
    }

    private void writeRecord(byte[] buf, int offset) throws IOException {
        if (outStream == null) {
            throw new IOException("Output buffer is closed");
        }
        if ((offset + recordSize) > buf.length) {
            throw new IOException("record has length '" + buf.length
                    + "' with offset '" + offset
                    + "' which is less than the record size of '"
                    + recordSize + "'");
        }
        if (currRecIdx >= recsPerBlock) {
            writeBlock();
        }
        System.arraycopy(buf, offset, blockBuffer, (currRecIdx * recordSize), recordSize);
        currRecIdx++;
    }
}
