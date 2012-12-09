/*
 * Copyright 2002,2004 The Apache Software Foundation.
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
package org.xbib.io.tar;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;


/**
 * The TarInputStream reads a UNIX tar archive as an InputStream. methods
 * are provided to position at each successive entry in the archive, and the
 * read each entry as a normal input stream using read().
 *
 * @author <a href="mailto:time@ice.com">Timothy Gerard Endres</a>
 * @author <a href="mailto:stefano@apache.org">Stefano Mazzocchi</a>
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 *
 * @see TarInputStream
 * @see TarEntry
 */
public class TarInputStream extends FilterInputStream {

    /** the tar buffer */
    private TarBuffer buffer;

    /** the current tar entry */
    private TarEntry currEntry;

    /** one buffer */
    private byte[] oneBuf;

    /** read buffer */
    private byte[] readBuf;

    /** eof flag */
    private boolean hasHitEOF;

    /** entry offset */
    private int entryOffset;

    /** entry size */
    private int entrySize;

    /**
     * Construct a TarInputStream using specified input
     * stream and default block and record sizes.
     *
     * @param input stream to create TarInputStream from
     * @see TarBuffer#DEFAULT_BLOCKSIZE
     * @see TarBuffer#DEFAULT_RECORDSIZE
     */
    public TarInputStream(InputStream input) {
        this(input, TarBuffer.DEFAULT_BLOCKSIZE, TarBuffer.DEFAULT_RECORDSIZE);
    }

    /**
     * Construct a TarInputStream using specified input
     * stream, block size and default record sizes.
     *
     * @param input stream to create TarInputStream from
     * @param blockSize the block size to use
     * @see TarBuffer#DEFAULT_RECORDSIZE
     */
    public TarInputStream(InputStream input, int blockSize) {
        this(input, blockSize, TarBuffer.DEFAULT_RECORDSIZE);
    }

    /**
         * Construct a TarInputStream using specified input
         * stream, block size and record sizes.
         *
         * @param input stream to create TarInputStream from
         * @param blockSize the block size to use
         * @param recordSize the record size to use
         */
    public TarInputStream(InputStream input, int blockSize, int recordSize) {
        super(input);
        buffer = new TarBuffer(input, blockSize, recordSize);
        oneBuf = new byte[1];
    }

    /**
     * Get the available data that can be read from the current entry
     * in the archive. This does not indicate how much data is left in the
     * entire archive, only in the current entry. This value is determined
     * from the entry's size header field and the amount of data already read
     * from the current entry.
     *
     * @return The number of available bytes for the current entry.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    @Override
    public int available() throws IOException {
        return entrySize - entryOffset;
    }

    /**
     * Closes this stream. Calls the TarBuffer's close() method.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    public void close() throws IOException {
        buffer.close();
    }

    /**
     * Copies the contents of the current tar archive entry directly
     * into an output stream.
     *
     * @param output The OutputStream into which to write the entry's data.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    public void copyEntryContents(final OutputStream output) throws IOException {
    	copyEntryContents(output, 32768);
    }
    
    public void copyEntryContents(final OutputStream output, int bufsize) throws IOException {
        final byte[] buf = new byte[bufsize];
        while (true) {
            final int numRead = read(buf, 0, buf.length);
            if (numRead == -1) {
                break;
            }
            output.write(buf, 0, numRead);
        }
    }

    public void copyEntryContents(final Writer writer, String encoding) throws IOException {
        final byte[] buf = new byte[getRecordSize()];
    	read(buf, 0 , getRecordSize());
    	writer.write(new String(buf, encoding));
    }
    
    /**
     * Get the next entry in this tar archive. This will skip over any
     * remaining data in the current entry, if there is one, and place the
     * input stream at the header of the next entry, and read the header and
     * instantiate a new TarEntry from the header bytes and return that entry.
     * If there are no more entries in the archive, null will be returned to
     * indicate that the end of the archive has been reached.
     *
     * @return The next TarEntry in the archive, or null.
     *
     * @exception IOException Description of Exception
     */
    public synchronized TarEntry getNextEntry() throws IOException {
        if (hasHitEOF) {
            return null;
        }

        if (currEntry != null) {
            final int numToSkip = entrySize - entryOffset;

            if (numToSkip > 0) {
                skip(numToSkip);
            }

            readBuf = null;
        }

        final byte[] headerBuf = buffer.readRecord();

        if (headerBuf == null) {
            hasHitEOF = true;
        } else if (buffer.isEOFRecord(headerBuf)) {
            hasHitEOF = true;
        }

        if (hasHitEOF) {
            currEntry = null;
        } else {
            currEntry = new TarEntry(headerBuf);

            entryOffset = 0;

            // REVIEW How do we resolve this discrepancy?!
            entrySize = (int) currEntry.getSize();
        }

        if ((null != currEntry) && currEntry.isGNULongNameEntry()) {
            // read in the name
            final StringBuffer longName = new StringBuffer();
            final byte[] buf = new byte[256];
            int length = 0;

            while ((length = read(buf)) >= 0) {
                final String str = new String(buf, 0, length);
                longName.append(str);
            }

            getNextEntry();

            // remove trailing null terminator
            if ((longName.length() > 0) && (longName.charAt(longName.length() - 1) == 0)) {
                longName.deleteCharAt(longName.length() - 1);
            }

            currEntry.setName(longName.toString());
        }

        return currEntry;
    }

    /**
     * Get the record size being used by this stream's TarBuffer.
     *
     * @return The TarBuffer record size.
     */
    public int getRecordSize() {
        return buffer.getRecordSize();
    }

    /**
     * Since we do not support marking just yet, we do nothing.
     *
     * @param markLimit The limit to mark.
     */
    public void mark(int markLimit) {
    }

    /**
     * Since we do not support marking just yet, we return false.
     *
     * @return False.
     */
    public boolean markSupported() {
        return false;
    }

    /**
     * Reads a byte from the current tar archive entry. This method
     * simply calls read( byte[], int, int ).
     *
     * @return The byte read, or -1 at EOF.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    public int read() throws IOException {
        final int num = read(oneBuf, 0, 1);

        if (num == -1) {
            return num;
        } else {
            return (int) oneBuf[0];
        }
    }

    /**
     * Reads bytes from the current tar archive entry. This method
     * simply calls read( byte[], int, int ).
     *
     * @param buffer The buffer into which to place bytes read.
     *
     * @return The number of bytes read, or -1 at EOF.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    public int read(final byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    /**
     * Reads bytes from the current tar archive entry. This method is
     * aware of the boundaries of the current entry in the archive and will
     * deal with them as if they were this stream's start and EOF.
     *
     * @param buffer The buffer into which to place bytes read.
     * @param offset The offset at which to place bytes read.
     * @param count The number of bytes to read.
     *
     * @return The number of bytes read, or -1 at EOF.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    public synchronized int read(final byte[] buffer, final int offset, final int count) throws IOException {
        int position = offset;
        int numToRead = count;
        int totalRead = 0;

        if (entryOffset >= entrySize) {
            return -1;
        }
        if ((numToRead + entryOffset) > entrySize) {
            numToRead = (entrySize - entryOffset);
        }
        if (readBuf != null) {
            final int size = (numToRead > readBuf.length) ? readBuf.length : numToRead;
            System.arraycopy(readBuf, 0, buffer, position, size);
            if (size >= readBuf.length) {
                readBuf = null;
            } else {
                final int newLength = readBuf.length - size;
                final byte[] newBuffer = new byte[newLength];
                System.arraycopy(readBuf, size, newBuffer, 0, newLength);
                readBuf = newBuffer;
            }
            totalRead += size;
            numToRead -= size;
            position += size;
        }
        while (numToRead > 0) {
            final byte[] rec = this.buffer.readRecord();
            if (null == rec) {
                // Unexpected EOF!
                final String message = "unexpected EOF with " + numToRead + " bytes unread";
                throw new IOException(message);
            }
            int size = numToRead;
            final int recordLength = rec.length;
            if (recordLength > size) {
                System.arraycopy(rec, 0, buffer, position, size);
                readBuf = new byte[recordLength - size];
                System.arraycopy(rec, size, readBuf, 0, recordLength - size);
            } else {
                size = recordLength;
                System.arraycopy(rec, 0, buffer, position, recordLength);
            }
            totalRead += size;
            numToRead -= size;
            position += size;
        }
        entryOffset += totalRead;
        return totalRead;
    }

    /**
     * Since we do not support marking just yet, we do nothing.
     */
    public void reset() {
    }

    /**
     * Skip bytes in the input buffer. This skips bytes in the current
     * entry's data, not the entire archive, and will stop at the end of the
     * current entry's data if the number to skip extends beyond that point.
     *
     * @param numToSkip The number of bytes to skip.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    public synchronized void skip(final int numToSkip) throws IOException {
        // REVIEW
        // This is horribly inefficient, but it ensures that we
        // properly skip over bytes via the TarBuffer...
        //
        final byte[] skipBuf = new byte[8 * 1024];
        int num = numToSkip;

        while (num > 0) {
            final int count = (num > skipBuf.length) ? skipBuf.length : num;
            final int numRead = read(skipBuf, 0, count);

            if (numRead == -1) {
                break;
            }

            num -= numRead;
        }
    }
}
