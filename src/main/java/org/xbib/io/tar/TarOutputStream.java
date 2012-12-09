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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The TarOutputStream writes a UNIX tar archive as an OutputStream.
 * Methods are provided to put entries, and then write their contents by
 * writing to this stream using write().
 *
 * @author Timothy Gerard Endres <a href="mailto:time@ice.com">time@ice.com</a>
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 *
 * @see TarInputStream
 * @see TarEntry
 */
public class TarOutputStream extends FilterOutputStream {

    /**
     * Flag to indicate that an error should be generated if an attempt
     * is made to write an entry that exceeds the 100 char POSIX limit.
     */
    public static final int LONGFILE_ERROR = 0;
    /**
     * Flag to indicate that entry name should be truncated if an
     * attempt is made to write an entry that exceeds the 100 char POSIX
     * limit.
     */
    public static final int LONGFILE_TRUNCATE = 1;
    /**
     * Flag to indicate that entry name should be formatted according
     * to GNU tar extension if an attempt is made to write an entry that
     * exceeds the 100 char POSIX limit. Note that this makes the jar
     * unreadable by non-GNU tar commands.
     */
    public static final int LONGFILE_GNU = 2;
    private TarBuffer buffer;
    private byte[] assemBuf;
    private byte[] oneBuf;
    private byte[] recordBuf;
    private int assemLen;
    private int longFileMode = LONGFILE_ERROR;
    private long currBytes;
    private long currSize;
    private boolean closed = false;

    /**
     * Construct a TarOutputStream using specified input
     * stream and default block and record sizes.
     *
     * @param output stream to create TarOutputStream from
     * @see TarBuffer#DEFAULT_BLOCKSIZE
     * @see TarBuffer#DEFAULT_RECORDSIZE
     */
    public TarOutputStream(OutputStream output) {
        this(output, TarBuffer.DEFAULT_BLOCKSIZE, TarBuffer.DEFAULT_RECORDSIZE);
    }

    /**
     * Construct a TarOutputStream using specified input
     * stream, block size and default record sizes.
     *
     * @param output stream to create TarOutputStream from
     * @param blockSize the block size
     * @see TarBuffer#DEFAULT_RECORDSIZE
     */
    public TarOutputStream(OutputStream output, final int blockSize) {
        this(output, blockSize, TarBuffer.DEFAULT_RECORDSIZE);
    }

    /**
     * Construct a TarOutputStream using specified input
     * stream, block size and record sizes.
     *
     * @param output stream to create TarOutputStream from
     * @param blockSize the block size
     * @param recordSize the record size
     */
    public TarOutputStream(OutputStream output, int blockSize, int recordSize) {
        super(output);

        this.buffer = new TarBuffer(output, blockSize, recordSize);
        this.assemLen = 0;
        this.assemBuf = new byte[recordSize];
        this.recordBuf = new byte[recordSize];
        this.oneBuf = new byte[1];
    }

    /**
     * Ends the TAR archive and closes the underlying OutputStream.
     * This means that finish() is called followed by calling the TarBuffer's
     * close().
     *
     * @exception IOException when an IO error causes operation to fail
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            this.finish();
            this.buffer.close();
            out.close();
            super.close();
            closed = true;
        }
    }

    /**
     * Close an entry. This method MUST be called for all file entries
     * that contain data. The reason is that we must buffer data written to
     * the stream in order to satisfy the buffer's record based writes. Thus,
     * there may be data fragments still being assembled that must be written
     * to the output stream before this entry is closed and the next entry
     * written.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    public void closeEntry() throws IOException {
        if (this.assemLen > 0) {
            for (int i = this.assemLen; i < this.assemBuf.length; ++i) {
                this.assemBuf[i] = 0;
            }

            this.buffer.writeRecord(this.assemBuf);

            this.currBytes += this.assemLen;
            this.assemLen = 0;
        }

        if (this.currBytes < this.currSize) {
            throw new IOException("entry closed at '" + currBytes
                    + "' before the '" + currSize + "' bytes specified in the header were written");
        }
    }

    /**
     * Copies the contents of the specified stream into current tar
     * archive entry.
     *
     * @param input The InputStream from which to read entries data
     *
     * @exception IOException when an IO error causes operation to fail
     */
    public void copyEntryContents(final InputStream input) throws IOException {
        final byte[] buf = new byte[32 * 1024];

        while (true) {
            final int numRead = input.read(buf, 0, buf.length);

            if (numRead == -1) {
                break;
            }

            write(buf, 0, numRead);
        }
    }

    /**
     * Ends the TAR archive without closing the underlying
     * OutputStream. The result is that the EOF record of nulls is written.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    public void finish() throws IOException {
        writeEOFRecord();
        writeEOFRecord();
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
     * Put an entry on the output stream. This writes the entry's
     * header record and positions the output stream for writing the contents
     * of the entry. Once this method is called, the stream is ready for calls
     * to write() to write the entry's contents. Once the contents are
     * written, closeEntry() <B>MUST</B> be called to ensure that all buffered
     * data is completely written to the output stream.
     *
     * @param entry The TarEntry to be written to the archive.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    public void putNextEntry(TarEntry entry) throws IOException {
        if (entry.getName().length() >= TarEntry.NAMELEN) {
            if (longFileMode == LONGFILE_GNU) {
                // create a TarEntry for the LongLink, the contents
                // of which are the entry's name
                TarEntry longLinkEntry =
                        new TarEntry(TarConstants.GNU_LONGLINK, TarConstants.LF_GNUTYPE_LONGNAME);
                longLinkEntry.setSize(entry.getName().length() + 1);
                putNextEntry(longLinkEntry);
                write(entry.getName().getBytes());
                write(0);
                closeEntry();
            } else if (longFileMode != LONGFILE_TRUNCATE) {
                throw new IOException("file name '" + entry.getName()
                        + "' is too long ( > " + TarEntry.NAMELEN + " bytes)");
            }
        }

        entry.writeEntryHeader(recordBuf);
        buffer.writeRecord(recordBuf);

        currBytes = 0;

        if (entry.isDirectory()) {
            this.currSize = 0;
        } else {
            this.currSize = (int) entry.getSize();
        }
    }

    /**
     * Set the mode used to work with entrys exceeding 100 chars (and
     * thus break the POSIX standard). Must be one of the LONGFILE_ constants.
     *
     * @param longFileMode the mode
     *
     * @throws IllegalArgumentException
     */
    public void setLongFileMode(final int longFileMode) {
        if ((LONGFILE_ERROR != longFileMode) && (LONGFILE_GNU != longFileMode) && (LONGFILE_TRUNCATE != longFileMode)) {
            throw new IllegalArgumentException("longFileMode");
        }
        this.longFileMode = longFileMode;
    }

    /**
     * Writes a byte to the current tar archive entry. This method
     * simply calls read( byte[], int, int ).
     *
     * @param data The byte written.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    @Override
    public void write(final int data) throws IOException {
        oneBuf[0] = (byte) data;

        write(oneBuf, 0, 1);
    }

    /**
     * Writes bytes to the current tar archive entry. This method
     * simply calls write( byte[], int, int ).
     *
     * @param buffer The buffer to write to the archive.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    @Override
    public void write(final byte[] buffer) throws IOException {
        write(buffer, 0, buffer.length);
    }

    /**
     * Writes bytes to the current tar archive entry. This method is
     * aware of the current entry and will throw an exception if you attempt
     * to write bytes past the length specified for the current entry. The
     * method is also (painfully) aware of the record buffering required by
     * TarBuffer, and manages buffers that are not a multiple of recordsize in
     * length, including assembling records from small buffers.
     *
     * @param buf The buffer to write to the archive.
     * @param offset The offset in the buffer from which to get bytes.
     * @param numToWrite The number of bytes to write.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    @Override
    public void write(byte[] buf, int offset, int numToWrite) throws IOException {

        if ((this.currBytes + numToWrite) > this.currSize) {
            throw new IOException("request to write '" + numToWrite
                    + "' bytes exceeds size in header of '" + this.currSize + "' bytes");
        }
        //
        // We have to deal with assembly!!!
        // The programmer can be writing little 32 byte chunks for all
        // we know, and we must assemble complete records for writing.
        // REVIEW Maybe this should be in TarBuffer? Could that help to
        // eliminate some of the buffer copying.
        //
        if (this.assemLen > 0) {
            if (this.assemLen + numToWrite >= this.recordBuf.length) {
                int length = this.recordBuf.length - assemLen;

                System.arraycopy(this.assemBuf, 0, this.recordBuf, 0, this.assemLen);
                System.arraycopy(buf, offset, this.recordBuf, this.assemLen, length);
                buffer.writeRecord(this.recordBuf);

                this.currBytes += this.recordBuf.length;
                offset += length;
                numToWrite -= length;
                this.assemLen = 0;
            } else {
                System.arraycopy(buf, offset, this.assemBuf, this.assemLen, numToWrite);

                offset += numToWrite;
                this.assemLen += numToWrite;
                numToWrite -= numToWrite;
            }
        }

        //
        // When we get here we have EITHER:
        // o An empty "assemble" buffer.
        // o No bytes to write (numToWrite == 0)
        //
        while (numToWrite > 0) {
            if (numToWrite < recordBuf.length) {
                System.arraycopy(buf, offset, this.assemBuf, this.assemLen, numToWrite);
                assemLen += numToWrite;
                break;
            }

            this.buffer.writeRecord(buf, offset);

            long num = this.recordBuf.length;

            this.currBytes += num;
            numToWrite -= num;
            offset += num;
        }
    }

    /**
     * Write an EOF (end of archive) record to the tar archive. An EOF
     * record consists of a record of all zeros.
     *
     * @exception IOException when an IO error causes operation to fail
     */
    private void writeEOFRecord() throws IOException {
        for (int i = 0; i < this.recordBuf.length; ++i) {
            this.recordBuf[i] = 0;
        }
        this.buffer.writeRecord(this.recordBuf);
    }
}
