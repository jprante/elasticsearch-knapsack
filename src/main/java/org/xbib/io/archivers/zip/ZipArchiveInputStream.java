
package org.xbib.io.archivers.zip;

import org.xbib.io.archivers.ArchiveEntry;
import org.xbib.io.archivers.ArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import static org.xbib.io.archivers.zip.ZipConstants.DWORD;
import static org.xbib.io.archivers.zip.ZipConstants.SHORT;
import static org.xbib.io.archivers.zip.ZipConstants.WORD;
import static org.xbib.io.archivers.zip.ZipConstants.ZIP64_MAGIC;

/**
 * Implements an input stream that can read Zip archives.
 * <p/>
 * <p>Note that {@link ZipArchiveEntry#getSize()} may return -1 if the
 * DEFLATE algorithm is used, as the size information is not available
 * from the header.</p>
 * <p/>
 * <p>The {@link ZipFile} class is preferred when reading from files.</p>
 * <p/>
 * <p>This code transparently supports Zip64
 * extensions and thus individual entries and archives larger than 4
 * GB or with more than 65536 entries.</p>
 *
 * @see ZipFile
 */
public class ZipArchiveInputStream extends ArchiveInputStream {

    /**
     * The zip encoding to use for filenames and the file comment.
     */
    private final ZipEncoding zipEncoding;

    /**
     * Whether to look for and use Unicode extra fields.
     */
    private final boolean useUnicodeExtraFields;

    /**
     * Wrapped stream, will always be a PushbackInputStream.
     */
    private final InputStream in;

    /**
     * Inflater used for all deflated entries.
     */
    private final Inflater inf = new Inflater(true);

    /**
     * Calculates checkusms for all entries.
     */
    private final CRC32 crc = new CRC32();

    /**
     * Buffer used to read from the wrapped stream.
     */
    private final Buffer buf = new Buffer();
    /**
     * The entry that is currently being read.
     */
    private CurrentEntry current = null;
    /**
     * Whether the stream has been closed.
     */
    private boolean closed = false;
    /**
     * Whether the stream has reached the central directory - and thus
     * found all entries.
     */
    private boolean hitCentralDirectory = false;
    /**
     * When reading a stored entry that uses the data descriptor this
     * stream has to read the full entry and caches it.  This is the
     * cache.
     */
    private ByteArrayInputStream lastStoredEntry = null;

    /**
     * Whether the stream will try to read STORED entries that use a
     * data descriptor.
     */
    private boolean allowStoredEntriesWithDataDescriptor = false;

    private static final int LFH_LEN = 30;
    /*
      local file header signature     4 bytes  (0x04034b50)
      version needed to extract       2 bytes
      general purpose bit flag        2 bytes
      compression method              2 bytes
      last mod file time              2 bytes
      last mod file date              2 bytes
      crc-32                          4 bytes
      compressed size                 4 bytes
      uncompressed size               4 bytes
      file name length                2 bytes
      extra field length              2 bytes
    */

    private static final long TWO_EXP_32 = ZIP64_MAGIC + 1;

    public ZipArchiveInputStream(InputStream inputStream) {
        this(inputStream, ZipEncodingHelper.UTF8, true);
    }

    /**
     * @param encoding              the encoding to use for file names, use null
     *                              for the platform's default encoding
     * @param useUnicodeExtraFields whether to use InfoZIP Unicode
     *                              Extra Fields (if present) to set the file names.
     */
    public ZipArchiveInputStream(InputStream inputStream,
                                 String encoding,
                                 boolean useUnicodeExtraFields) {
        this(inputStream, encoding, useUnicodeExtraFields, false);
    }

    /**
     * @param encoding                             the encoding to use for file names, use null
     *                                             for the platform's default encoding
     * @param useUnicodeExtraFields                whether to use InfoZIP Unicode
     *                                             Extra Fields (if present) to set the file names.
     * @param allowStoredEntriesWithDataDescriptor whether the stream
     *                                             will try to read STORED entries that use a data descriptor
     */
    public ZipArchiveInputStream(InputStream inputStream,
                                 String encoding,
                                 boolean useUnicodeExtraFields,
                                 boolean allowStoredEntriesWithDataDescriptor) {
        zipEncoding = ZipEncodingHelper.getZipEncoding(encoding);
        this.useUnicodeExtraFields = useUnicodeExtraFields;
        in = new PushbackInputStream(inputStream, buf.buf.length);
        this.allowStoredEntriesWithDataDescriptor =
                allowStoredEntriesWithDataDescriptor;
    }

    public ZipArchiveEntry getNextZipEntry() throws IOException {
        if (closed) {
            throw new IOException("stream is closed");
        }
        if (hitCentralDirectory) {
            return null;
        }
        if (current != null) {
            closeEntry();
        }
        byte[] lfh = new byte[LFH_LEN];
        try {
            readFully(lfh);
        } catch (EOFException e) {
            return null;
        }
        ZipLong sig = new ZipLong(lfh);
        if (sig.equals(ZipLong.CFH_SIG)) {
            hitCentralDirectory = true;
            return null;
        }
        if (!sig.equals(ZipLong.LFH_SIG)) {
            return null;
        }

        int off = WORD;
        current = new CurrentEntry();

        int versionMadeBy = ZipShort.getValue(lfh, off);
        off += SHORT;
        current.entry.setPlatform((versionMadeBy >> ZipFile.BYTE_SHIFT)
                & ZipFile.NIBLET_MASK);

        final GeneralPurposeBit gpFlag = GeneralPurposeBit.parse(lfh, off);
        final boolean hasUTF8Flag = gpFlag.usesUTF8ForNames();
        final ZipEncoding entryEncoding =
                hasUTF8Flag ? ZipEncodingHelper.UTF8_ZIP_ENCODING : zipEncoding;
        current.hasDataDescriptor = gpFlag.usesDataDescriptor();
        current.entry.setGeneralPurposeBit(gpFlag);

        off += SHORT;

        current.entry.setMethod(ZipShort.getValue(lfh, off));
        off += SHORT;

        long time = ZipUtil.dosToJavaTime(ZipLong.getValue(lfh, off));
        current.entry.setTime(time);
        off += WORD;

        ZipLong size = null, cSize = null;
        if (!current.hasDataDescriptor) {
            current.entry.setCrc(ZipLong.getValue(lfh, off));
            off += WORD;

            cSize = new ZipLong(lfh, off);
            off += WORD;

            size = new ZipLong(lfh, off);
            off += WORD;
        } else {
            off += 3 * WORD;
        }

        int fileNameLen = ZipShort.getValue(lfh, off);

        off += SHORT;

        int extraLen = ZipShort.getValue(lfh, off);
        off += SHORT;

        byte[] fileName = new byte[fileNameLen];
        readFully(fileName);
        current.entry.setName(entryEncoding.decode(fileName), fileName);

        byte[] extraData = new byte[extraLen];
        readFully(extraData);
        current.entry.setExtra(extraData);

        if (!hasUTF8Flag && useUnicodeExtraFields) {
            ZipUtil.setNameAndCommentFromExtraFields(current.entry, fileName,
                    null);
        }

        processZip64Extra(size, cSize);
        return current.entry;
    }

    /**
     * Records whether a Zip64 extra is present and sets the size
     * information from it if sizes are 0xFFFFFFFF and the entry
     * doesn't use a data descriptor.
     */
    private void processZip64Extra(ZipLong size, ZipLong cSize) {
        Zip64ExtendedInformationExtraField z64 =
                (Zip64ExtendedInformationExtraField)
                        current.entry.getExtraField(Zip64ExtendedInformationExtraField
                                .HEADER_ID);
        current.usesZip64 = z64 != null;
        if (!current.hasDataDescriptor) {
            if (current.usesZip64 && (cSize.equals(ZipLong.ZIP64_MAGIC)
                    || size.equals(ZipLong.ZIP64_MAGIC))
                    ) {
                current.entry.setCompressedSize(z64.getCompressedSize() // z64 cannot be null here
                        .getLongValue());
                current.entry.setSize(z64.getSize().getLongValue());
            } else {
                current.entry.setCompressedSize(cSize.getValue());
                current.entry.setSize(size.getValue());
            }
        }
    }

    @Override
    public ArchiveEntry getNextEntry() throws IOException {
        return getNextZipEntry();
    }

    @Override
    public int read(byte[] buffer, int start, int length) throws IOException {
        if (closed) {
            throw new IOException("stream is closed");
        }
        if (inf.finished() || current == null) {
            return -1;
        }

        // avoid int overflow, check null buffer
        if (start <= buffer.length && length >= 0 && start >= 0
                && buffer.length - start >= length) {
            ZipUtil.checkRequestedFeatures(current.entry);
            if (!supportsDataDescriptorFor(current.entry)) {
                throw new UnsupportedZipFeatureException(UnsupportedZipFeatureException
                        .Feature
                        .DATA_DESCRIPTOR,
                        current.entry);
            }

            if (current.entry.getMethod() == ZipArchiveOutputStream.STORED) {
                return readStored(buffer, start, length);
            }
            return readDeflated(buffer, start, length);
        }
        throw new ArrayIndexOutOfBoundsException();
    }

    /**
     * Implementation of read for STORED entries.
     */
    private int readStored(byte[] buffer, int start, int length)
            throws IOException {

        if (current.hasDataDescriptor) {
            if (lastStoredEntry == null) {
                readStoredEntry();
            }
            return lastStoredEntry.read(buffer, start, length);
        }

        long csize = current.entry.getSize();
        if (current.bytesRead >= csize) {
            return -1;
        }

        if (buf.offsetInBuffer >= buf.lengthOfLastRead) {
            buf.offsetInBuffer = 0;
            if ((buf.lengthOfLastRead = in.read(buf.buf)) == -1) {
                return -1;
            }
            count(buf.lengthOfLastRead);
            current.bytesReadFromStream += buf.lengthOfLastRead;
        }

        int toRead = length > buf.lengthOfLastRead
                ? buf.lengthOfLastRead - buf.offsetInBuffer
                : length;
        if ((csize - current.bytesRead) < toRead) {
            // if it is smaller than toRead then it fits into an int
            toRead = (int) (csize - current.bytesRead);
        }
        System.arraycopy(buf.buf, buf.offsetInBuffer, buffer, start, toRead);
        buf.offsetInBuffer += toRead;
        current.bytesRead += toRead;
        crc.update(buffer, start, toRead);
        return toRead;
    }

    /**
     * Implementation of read for DEFLATED entries.
     */
    private int readDeflated(byte[] buffer, int start, int length)
            throws IOException {
        if (inf.needsInput()) {
            fill();
            if (buf.lengthOfLastRead > 0) {
                current.bytesReadFromStream += buf.lengthOfLastRead;
            }
        }
        int read;
        try {
            read = inf.inflate(buffer, start, length);
        } catch (DataFormatException e) {
            throw new ZipException(e.getMessage());
        }
        if (read == 0) {
            if (inf.finished()) {
                return -1;
            } else if (buf.lengthOfLastRead == -1) {
                throw new IOException("Truncated ZIP file");
            }
        }
        crc.update(buffer, start, read);
        return read;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            in.close();
            inf.end();
        }
    }

    /**
     * Skips over and discards value bytes of data from this input
     * stream.
     * <p/>
     * <p>This implementation may end up skipping over some smaller
     * number of bytes, possibly 0, if and only if it reaches the end
     * of the underlying stream.</p>
     * <p/>
     * <p>The actual number of bytes skipped is returned.</p>
     *
     * @param value the number of bytes to be skipped.
     * @return the actual number of bytes skipped.
     * @throws java.io.IOException      - if an I/O error occurs.
     * @throws IllegalArgumentException - if value is negative.
     */
    @Override
    public long skip(long value) throws IOException {
        if (value >= 0) {
            long skipped = 0;
            byte[] b = new byte[1024];
            while (skipped < value) {
                long rem = value - skipped;
                int x = read(b, 0, (int) (b.length > rem ? rem : b.length));
                if (x == -1) {
                    return skipped;
                }
                skipped += x;
            }
            return skipped;
        }
        throw new IllegalArgumentException();
    }

    /**
     * Checks if the signature matches what is expected for a zip file.
     * Does not currently handle self-extracting zips which may have arbitrary
     * leading content.
     *
     * @param signature the bytes to check
     * @param length    the number of bytes to check
     * @return true, if this stream is a zip archive stream, false otherwise
     */
    public static boolean matches(byte[] signature, int length) {
        if (length < ZipArchiveOutputStream.LFH_SIG.length) {
            return false;
        }

        return checksig(signature, ZipArchiveOutputStream.LFH_SIG) // normal file
                || checksig(signature, ZipArchiveOutputStream.EOCD_SIG); // empty zip
    }

    private static boolean checksig(byte[] signature, byte[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (signature[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Closes the current ZIP archive entry and positions the underlying
     * stream to the beginning of the next entry. All per-entry variables
     * and data structures are cleared.
     * <p/>
     * If the compressed size of this entry is included in the entry header,
     * then any outstanding bytes are simply skipped from the underlying
     * stream without uncompressing them. This allows an entry to be safely
     * closed even if the compression method is unsupported.
     * <p/>
     * In case we don't know the compressed size of this entry or have
     * already buffered too much data from the underlying stream to support
     * uncompression, then the uncompression process is completed and the
     * end position of the stream is adjusted based on the result of that
     * process.
     *
     * @throws java.io.IOException if an error occurs
     */
    private void closeEntry() throws IOException {
        if (closed) {
            throw new IOException("The stream is closed");
        }
        if (current == null) {
            return;
        }

        // Ensure all entry bytes are read
        if (current.bytesReadFromStream <= current.entry.getCompressedSize()
                && !current.hasDataDescriptor) {
            drainCurrentEntryData();
        } else {
            skip(Long.MAX_VALUE);

            long inB =
                    current.entry.getMethod() == ZipArchiveOutputStream.DEFLATED
                            ? getBytesInflated() : current.bytesRead;

            // this is at most a single read() operation and can't
            // exceed the range of int
            int diff = (int) (current.bytesReadFromStream - inB);

            // Pushback any required bytes
            if (diff > 0) {
                pushback(buf.buf, buf.lengthOfLastRead - diff, diff);
            }
        }

        if (lastStoredEntry == null && current.hasDataDescriptor) {
            readDataDescriptor();
        }

        inf.reset();
        buf.reset();
        crc.reset();
        current = null;
        lastStoredEntry = null;
    }

    /**
     * Read all data of the current entry from the underlying stream
     * that hasn't been read, yet.
     */
    private void drainCurrentEntryData() throws IOException {
        long remaining = current.entry.getCompressedSize()
                - current.bytesReadFromStream;
        while (remaining > 0) {
            long n = in.read(buf.buf, 0, (int) Math.min(buf.buf.length,
                    remaining));
            if (n < 0) {
                throw new EOFException(
                        "Truncated ZIP entry: " + current.entry.getName());
            } else {
                count(n);
                remaining -= n;
            }
        }
    }

    /**
     * Get the number of bytes Inflater has actually processed.
     * <p/>
     * <p>for Java &lt; Java7 the getBytes* methods in
     * Inflater/Deflater seem to return unsigned ints rather than
     * longs that start over with 0 at 2^32.</p>
     * <p/>
     * <p>The stream knows how many bytes it has read, but not how
     * many the Inflater actually consumed - it should be between the
     * total number of bytes read for the entry and the total number
     * minus the last read operation.  Here we just try to make the
     * value close enough to the bytes we've read by assuming the
     * number of bytes consumed must be smaller than (or equal to) the
     * number of bytes read but not smaller by more than 2^32.</p>
     */
    private long getBytesInflated() {
        long inB = inf.getBytesRead();
        if (current.bytesReadFromStream >= TWO_EXP_32) {
            while (inB + TWO_EXP_32 <= current.bytesReadFromStream) {
                inB += TWO_EXP_32;
            }
        }
        return inB;
    }

    private void fill() throws IOException {
        if (closed) {
            throw new IOException("The stream is closed");
        }
        if ((buf.lengthOfLastRead = in.read(buf.buf)) > 0) {
            count(buf.lengthOfLastRead);
            inf.setInput(buf.buf, 0, buf.lengthOfLastRead);
        }
    }

    private void readFully(byte[] b) throws IOException {
        int count = 0, x = 0;
        while (count != b.length) {
            count += x = in.read(b, count, b.length - count);
            if (x == -1) {
                throw new EOFException();
            }
            count(x);
        }
    }

    private void readDataDescriptor() throws IOException {
        byte[] b = new byte[WORD];
        readFully(b);
        ZipLong val = new ZipLong(b);
        if (ZipLong.DD_SIG.equals(val)) {
            // data descriptor with signature, skip sig
            readFully(b);
            val = new ZipLong(b);
        }
        current.entry.setCrc(val.getValue());

        // if there is a ZIP64 extra field, sizes are eight bytes
        // each, otherwise four bytes each.  Unfortunately some
        // implementations - namely Java7 - use eight bytes without
        // using a ZIP64 extra field -
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7073588

        // just read 16 bytes and check whether bytes nine to twelve
        // look like one of the signatures of what could follow a data
        // descriptor (ignoring archive decryption headers for now).
        // If so, push back eight bytes and assume sizes are four
        // bytes, otherwise sizes are eight bytes each.
        b = new byte[2 * DWORD];
        readFully(b);
        ZipLong potentialSig = new ZipLong(b, DWORD);
        if (potentialSig.equals(ZipLong.CFH_SIG)
                || potentialSig.equals(ZipLong.LFH_SIG)) {
            pushback(b, DWORD, DWORD);
            current.entry.setCompressedSize(ZipLong.getValue(b));
            current.entry.setSize(ZipLong.getValue(b, WORD));
        } else {
            current.entry
                    .setCompressedSize(ZipEightByteInteger.getLongValue(b));
            current.entry.setSize(ZipEightByteInteger.getLongValue(b, DWORD));
        }
    }

    /**
     * Whether this entry requires a data descriptor this library can work with.
     *
     * @return true if allowStoredEntriesWithDataDescriptor is true,
     * the entry doesn't require any data descriptor or the method is
     * DEFLATED.
     */
    private boolean supportsDataDescriptorFor(ZipArchiveEntry entry) {
        return allowStoredEntriesWithDataDescriptor ||
                !entry.getGeneralPurposeBit().usesDataDescriptor()
                || entry.getMethod() == ZipEntry.DEFLATED;
    }

    /**
     * Caches a stored entry that uses the data descriptor.
     * <p/>
     * <ul>
     * <li>Reads a stored entry until the signature of a local file
     * header, central directory header or data descriptor has been
     * found.</li>
     * <li>Stores all entry data in lastStoredEntry.</p>
     * <li>Rewinds the stream to position at the data
     * descriptor.</li>
     * <li>reads the data descriptor</li>
     * </ul>
     * <p/>
     * <p>After calling this method the entry should know its size,
     * the entry's data is cached and the stream is positioned at the
     * next local file or central directory header.</p>
     */
    private void readStoredEntry() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int off = 0;
        boolean done = false;

        // length of DD without signature
        int ddLen = current.usesZip64 ? WORD + 2 * DWORD : 3 * WORD;

        while (!done) {
            int r = in.read(buf.buf, off,
                    ZipArchiveOutputStream.BUFFER_SIZE - off);
            if (r <= 0) {
                // read the whole archive without ever finding a
                // central directory
                throw new IOException("Truncated ZIP file");
            }
            if (r + off < 4) {
                // buf is too small to check for a signature, loop
                off += r;
                continue;
            }

            done = bufferContainsSignature(bos, off, r, ddLen);
            if (!done) {
                off = cacheBytesRead(bos, off, r, ddLen);
            }
        }

        byte[] b = bos.toByteArray();
        lastStoredEntry = new ByteArrayInputStream(b);
    }

    private static final byte[] LFH = ZipLong.LFH_SIG.getBytes();
    private static final byte[] CFH = ZipLong.CFH_SIG.getBytes();
    private static final byte[] DD = ZipLong.DD_SIG.getBytes();

    /**
     * Checks whether the current buffer contains the signature of a
     * &quot;data decsriptor&quot;, &quot;local file header&quot; or
     * &quot;central directory entry&quot;.
     * <p/>
     * <p>If it contains such a signature, reads the data descriptor
     * and positions the stream right after the data descriptor.</p>
     */
    private boolean bufferContainsSignature(ByteArrayOutputStream bos,
                                            int offset, int lastRead,
                                            int expectedDDLen)
            throws IOException {
        boolean done = false;
        int readTooMuch = 0;
        for (int i = 0; !done && i < lastRead - 4; i++) {
            if (buf.buf[i] == LFH[0] && buf.buf[i + 1] == LFH[1]) {
                if ((buf.buf[i + 2] == LFH[2] && buf.buf[i + 3] == LFH[3])
                        || (buf.buf[i] == CFH[2] && buf.buf[i + 3] == CFH[3])) {
                    // found a LFH or CFH:
                    readTooMuch = offset + lastRead - i - expectedDDLen;
                    done = true;
                } else if (buf.buf[i + 2] == DD[2] && buf.buf[i + 3] == DD[3]) {
                    // found DD:
                    readTooMuch = offset + lastRead - i;
                    done = true;
                }
                if (done) {
                    // * push back bytes read in excess as well as the data
                    //   descriptor
                    // * copy the remaining bytes to cache
                    // * read data descriptor
                    pushback(buf.buf, offset + lastRead - readTooMuch,
                            readTooMuch);
                    bos.write(buf.buf, 0, i);
                    readDataDescriptor();
                }
            }
        }
        return done;
    }

    /**
     * If the last read bytes could hold a data descriptor and an
     * incomplete signature then save the last bytes to the front of
     * the buffer and cache everything in front of the potential data
     * descriptor into the given ByteArrayOutputStream.
     * <p/>
     * <p>Data descriptor plus incomplete signature (3 bytes in the
     * worst case) can be 20 bytes max.</p>
     */
    private int cacheBytesRead(ByteArrayOutputStream bos, int offset,
                               int lastRead, int expecteDDLen) {
        final int cacheable = offset + lastRead - expecteDDLen - 3;
        if (cacheable > 0) {
            bos.write(buf.buf, 0, cacheable);
            System.arraycopy(buf.buf, cacheable, buf.buf, 0,
                    expecteDDLen + 3);
            offset = expecteDDLen + 3;
        } else {
            offset += lastRead;
        }
        return offset;
    }

    private void pushback(byte[] buf, int offset, int length)
            throws IOException {
        ((PushbackInputStream) in).unread(buf, offset, length);
        pushedBackBytes(length);
    }

    /**
     * Structure collecting information for the entry that is
     * currently being read.
     */
    private static final class CurrentEntry {
        /**
         * Current ZIP entry.
         */
        private final ZipArchiveEntry entry = new ZipArchiveEntry();
        /**
         * Does the entry use a data descriptor?
         */
        private boolean hasDataDescriptor;
        /**
         * Does the entry have a ZIP64 extended information extra field.
         */
        private boolean usesZip64;
        /**
         * Number of bytes of entry content read by the client if the
         * entry is STORED.
         */
        private long bytesRead;
        /**
         * Number of bytes of entry content read so from the stream.
         * <p/>
         * <p>This may be more than the actual entry's length as some
         * stuff gets buffered up and needs to be pushed back when the
         * end of the entry has been reached.</p>
         */
        private long bytesReadFromStream;
    }

    /**
     * Contains a temporary buffer used to read from the wrapped
     * stream together with some information needed for internal
     * housekeeping.
     */
    private static final class Buffer {
        /**
         * Buffer used as temporary buffer when reading from the stream.
         */
        private final byte[] buf = new byte[ZipArchiveOutputStream.BUFFER_SIZE];
        /**
         * {@link #buf buf} may contain data the client hasnt read, yet,
         * this is the first byte that hasn't been read so far.
         */
        private int offsetInBuffer = 0;
        /**
         * Number of bytes read from the wrapped stream into {@link #buf
         * buf} with the last read operation.
         */
        private int lengthOfLastRead = 0;

        /**
         * Reset internal housekeeping.
         */
        private void reset() {
            offsetInBuffer = lengthOfLastRead = 0;
        }
    }
}
