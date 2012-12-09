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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The TarBuffer class implements the tar archive concept of a buffered
 * input stream. This concept goes back to the days of blocked tape drives and
 * special io devices. In the Java universe, the only real function that this
 * class performs is to ensure that files have the correct "block" size, or
 * other tars will complain.<p>You should never have a need to access this
 * class directly. TarBuffers are created by Tar IO Streams.</p>
 *
 * @author <a href="mailto:time@ice.com">Timothy Gerard Endres</a>
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public class TarBuffer {

    /** the default record size */
    public static final int DEFAULT_RECORDSIZE = 512;
    /** the default block size */
    public static final int DEFAULT_BLOCKSIZE = DEFAULT_RECORDSIZE * 20;
    private InputStream input;
    private OutputStream output;
    private byte[] blockbuffer;
    private int blocksize;
    private int currblkidx;
    private int currrecidx;
    private int recordsize;
    private int recsperblock;

    public TarBuffer(final InputStream input) {
        this(input, TarBuffer.DEFAULT_BLOCKSIZE);
    }

    public TarBuffer(final InputStream input, final int blockSize) {
        this(input, blockSize, TarBuffer.DEFAULT_RECORDSIZE);
    }

    public TarBuffer(InputStream input, int blockSize, int recordSize) {
        this.input = input;
        initialize(blockSize, recordSize);
    }

    public TarBuffer(OutputStream output) {
        this(output, TarBuffer.DEFAULT_BLOCKSIZE);
    }

    public TarBuffer(OutputStream output, int blockSize) {
        this(output, blockSize, TarBuffer.DEFAULT_RECORDSIZE);
    }

    public TarBuffer(OutputStream output, int blockSize, int recordSize) {
        this.output = output;
        initialize(blockSize, recordSize);
    }

    public void close() throws IOException {
        if (null != output) {
            flushBlock();

            if ((output != System.out) && (output != System.err)) {
                output.close();
                output = null;
            }
        } else if ((input != null) && (input != System.in)) {
            input.close();
            input = null;
        }
    }

    private void flushBlock() throws IOException {
        if (currrecidx > 0) {
            if (output == null) {
                throw new IOException("no output buffer");
            }
            output.write(blockbuffer, 0, blocksize);
            output.flush();
            currrecidx = 0;
            currblkidx++;
        }
    }

    public int getBlockSize() {
        return blocksize;
    }

    public int getCurrentBlockNum() {
        return currblkidx;
    }

    public int getCurrentRecordNum() {
        return currrecidx - 1;
    }

    public int getRecordSize() {
        return recordsize;
    }

    private void initialize(int blocksize, int recordsize) {
        this.blocksize = blocksize;
        this.recordsize = recordsize;
        this.recsperblock = (blocksize / recordsize);
        this.blockbuffer = new byte[blocksize];

        if (input != null) {
            this.currblkidx = -1;
            this.currrecidx = recsperblock;
        } else {
            currblkidx = 0;
            this.currrecidx = 0;
        }
    }

    /**
     * Determine if an archive record indicate End of Archive. End of
     * archive is indicated by a record that consists entirely of null bytes.
     *
     * @param record The record data to check.
     *
     * @return The EOFRecord value
     */
    public boolean isEOFRecord(final byte[] record) {
        final int size = getRecordSize();

        for (int i = 0; i < size; ++i) {
            if (record[i] != 0) {
                return false;
            }
        }

        return true;
    }

    private boolean readBlock() throws IOException {
        if (input == null) {
            throw new IOException("reading from an output buffer");
        }
        this.currrecidx = 0;
        int offset = 0;
        int bytesNeeded = blocksize;
        while (bytesNeeded > 0) {
            final long numBytes = input.read(blockbuffer, offset, bytesNeeded);
            //
            // NOTE
            // We have fit EOF, and the block is not full!
            //
            // This is a broken archive. It does not follow the standard
            // blocking algorithm. However, because we are generous, and
            // it requires little effort, we will simply ignore the error
            // and continue as if the entire block were read. This does
            // not appear to break anything upstream. We used to return
            // false in this case.
            //
            // Thanks to 'Yohann.Roussel@alcatel.fr' for this fix.
            //
            if (numBytes == -1) {
                break;
            }
            offset += numBytes;
            bytesNeeded -= numBytes;
        }
        currblkidx++;
        return true;
    }
    
    /**
     * Read a record from the input stream and return the data.
     *
     * @return The record data.
     *
     * @exception IOException Description of Exception
     */
    public byte[] readRecord() throws IOException {
        if (null == input) {
            throw new IOException("reading from an output buffer");
        }
        if ((currrecidx >= recsperblock) && (!readBlock())) {
            throw new IOException("illegal read");
        }
        final byte[] result = new byte[recordsize];
        System.arraycopy(blockbuffer, (currrecidx * recordsize), result, 0, recordsize);
        currrecidx++;
        return result;
    }

    /**
     * Skip over a record on the input stream.
     *
     * @throws IOException
     */
    public void skipRecord() throws IOException {
        if (null == input) {
            final String message = "reading (via skip) from an output buffer";
            throw new IOException(message);
        }

        if ((currrecidx >= recsperblock) && (!readBlock())) {
            return; // UNDONE
        }

        currrecidx++;
    }

    /**
     * Write an archive record to the archive.
     *
     * @param record The record data to write to the archive.
     *
     * @throws IOException
     */
    public void writeRecord(byte[] record) throws IOException {
        if (output == null) {
            throw new IOException("writing to an input buffer");
        }

        if (record.length != recordsize) {
            throw new IOException("record to write has length '"
                    + record.length + "' which is not the record size of '" + recordsize + "'");
        }

        if (currrecidx >= recsperblock) {
            if (output == null) {
                throw new IOException("writing to an input buffer");
            }
            output.write(blockbuffer, 0, blocksize);
            output.flush();
            currrecidx = 0;
            currblkidx++;
        }

        System.arraycopy(record, 0, blockbuffer, (currrecidx * recordsize), recordsize);

        this.currrecidx++;
    }

    /**
     * Write an archive record to the archive, where the record may be
     * inside of a larger array buffer. The buffer must be "offset plus record
     * size" long.
     *
     * @param buffer The buffer containing the record data to write.
     * @param offset The offset of the record data within buf.
     *
     * @throws IOException
     */
    public void writeRecord(byte[] buffer, int offset) throws IOException {
        if ((offset + recordsize) > buffer.length) {
            throw new IOException("record has length '" + buffer.length
                    + "' with offset '" + offset + "' which is less than the record size of '" + recordsize + "'");
        }

        if (currrecidx >= recsperblock) {
            if (output == null) {
                throw new IOException("writing to an input buffer");
            }
            output.write(blockbuffer, 0, blocksize);
            output.flush();
            this.currrecidx = 0;
            this.currblkidx++;
        }

        System.arraycopy(buffer, offset, blockbuffer, (currrecidx * recordsize), recordsize);

        this.currrecidx++;
    }
}
