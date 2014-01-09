
package org.xbib.io.archivers.tar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * The TarBuffer class implements the tar archive concept
 * of a buffered input stream. This concept goes back to the
 * days of blocked tape drives and special io devices. In the
 * Java universe, the only real function that this class
 * performs is to ensure that files have the correct "block"
 * size, or other tars will complain.
 * <p/>
 * You should never have a need to access this class directly.
 * TarBuffers are created by Tar IO Streams.
 */

class TarBuffer {

    /**
     * Default record size
     */
    public static final int DEFAULT_RCDSIZE = (512);

    /**
     * Default block size
     */
    public static final int DEFAULT_BLKSIZE = (DEFAULT_RCDSIZE * 20);

    // TODO make these final? (would need to change close() method)
    private InputStream inStream;
    private OutputStream outStream;
    private final int blockSize;
    private final int recordSize;
    private final int recsPerBlock;
    private final byte[] blockBuffer;

    private int currBlkIdx;
    private int currRecIdx;

    /**
     * Constructor for a TarBuffer on an input stream.
     *
     * @param inStream the input stream to use
     */
    public TarBuffer(InputStream inStream) {
        this(inStream, TarBuffer.DEFAULT_BLKSIZE);
    }

    /**
     * Constructor for a TarBuffer on an input stream.
     *
     * @param inStream  the input stream to use
     * @param blockSize the block size to use
     */
    public TarBuffer(InputStream inStream, int blockSize) {
        this(inStream, blockSize, TarBuffer.DEFAULT_RCDSIZE);
    }

    /**
     * Constructor for a TarBuffer on an input stream.
     *
     * @param inStream   the input stream to use
     * @param blockSize  the block size to use
     * @param recordSize the record size to use
     */
    public TarBuffer(InputStream inStream, int blockSize, int recordSize) {
        this(inStream, null, blockSize, recordSize);
    }

    /**
     * Constructor for a TarBuffer on an output stream.
     *
     * @param outStream the output stream to use
     */
    public TarBuffer(OutputStream outStream) {
        this(outStream, TarBuffer.DEFAULT_BLKSIZE);
    }

    /**
     * Constructor for a TarBuffer on an output stream.
     *
     * @param outStream the output stream to use
     * @param blockSize the block size to use
     */
    public TarBuffer(OutputStream outStream, int blockSize) {
        this(outStream, blockSize, TarBuffer.DEFAULT_RCDSIZE);
    }

    /**
     * Constructor for a TarBuffer on an output stream.
     *
     * @param outStream  the output stream to use
     * @param blockSize  the block size to use
     * @param recordSize the record size to use
     */
    public TarBuffer(OutputStream outStream, int blockSize, int recordSize) {
        this(null, outStream, blockSize, recordSize);
    }

    /**
     * Private constructor to perform common setup.
     */
    private TarBuffer(InputStream inStream, OutputStream outStream, int blockSize, int recordSize) {
        this.inStream = inStream;
        this.outStream = outStream;
        this.blockSize = blockSize;
        this.recordSize = recordSize;
        this.recsPerBlock = (this.blockSize / this.recordSize);
        this.blockBuffer = new byte[this.blockSize];

        if (this.inStream != null) {
            this.currBlkIdx = -1;
            this.currRecIdx = this.recsPerBlock;
        } else {
            this.currBlkIdx = 0;
            this.currRecIdx = 0;
        }
    }

    /**
     * Get the TAR Buffer's block size. Blocks consist of multiple records.
     *
     * @return the block size
     */
    public int getBlockSize() {
        return this.blockSize;
    }

    /**
     * Get the TAR Buffer's record size.
     *
     * @return the record size
     */
    public int getRecordSize() {
        return this.recordSize;
    }

    /**
     * Determine if an archive record indicate End of Archive. End of
     * archive is indicated by a record that consists entirely of null bytes.
     *
     * @param record The record data to check.
     * @return true if the record data is an End of Archive
     */
    public boolean isEOFRecord(byte[] record) {
        for (int i = 0, sz = getRecordSize(); i < sz; ++i) {
            if (record[i] != 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Skip over a record on the input stream.
     *
     * @throws java.io.IOException on error
     */
    public void skipRecord() throws IOException {
        if (inStream == null) {
            throw new IOException("reading (via skip) from an output buffer");
        }

        if (currRecIdx >= recsPerBlock && !readBlock()) {
            return;    // UNDONE
        }

        currRecIdx++;
    }

    /**
     * Read a record from the input stream and return the data.
     *
     * @return The record data.
     * @throws java.io.IOException on error
     */
    public byte[] readRecord() throws IOException {
        if (inStream == null) {
            if (outStream == null) {
                throw new IOException("input buffer is closed");
            }
            throw new IOException("reading from an output buffer");
        }

        if (currRecIdx >= recsPerBlock && !readBlock()) {
            return null;
        }

        byte[] result = new byte[recordSize];

        System.arraycopy(blockBuffer,
                (currRecIdx * recordSize), result, 0,
                recordSize);

        currRecIdx++;

        return result;
    }

    /**
     * @return false if End-Of-File, else true
     */
    private boolean readBlock() throws IOException {
        if (inStream == null) {
            throw new IOException("reading from an output buffer");
        }

        currRecIdx = 0;

        int offset = 0;
        int bytesNeeded = blockSize;

        while (bytesNeeded > 0) {
            long numBytes = inStream.read(blockBuffer, offset,
                    bytesNeeded);
            if (numBytes == -1) {
                if (offset == 0) {
                    return false;
                }
                Arrays.fill(blockBuffer, offset, offset + bytesNeeded, (byte) 0);
                break;
            }

            offset += numBytes;
            bytesNeeded -= numBytes;

            if (numBytes != blockSize) {
                // TODO: Incomplete Read occured - throw exception?
            }
        }

        currBlkIdx++;

        return true;
    }

    /**
     * Get the current block number, zero based.
     *
     * @return The current zero based block number.
     */
    public int getCurrentBlockNum() {
        return currBlkIdx;
    }

    /**
     * Get the current record number, within the current block, zero based.
     * Thus, current offset = (currentBlockNum * recsPerBlk) + currentRecNum.
     *
     * @return The current zero based record number.
     */
    public int getCurrentRecordNum() {
        return currRecIdx - 1;
    }

    /**
     * Write an archive record to the archive.
     *
     * @param record The record data to write to the archive.
     * @throws java.io.IOException on error
     */
    public void writeRecord(byte[] record) throws IOException {
        if (outStream == null) {
            if (inStream == null) {
                throw new IOException("Output buffer is closed");
            }
            throw new IOException("writing to an input buffer");
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

        System.arraycopy(record, 0, blockBuffer,
                (currRecIdx * recordSize),
                recordSize);

        currRecIdx++;
    }

    /**
     * Write an archive record to the archive, where the record may be
     * inside of a larger array buffer. The buffer must be "offset plus
     * record size" long.
     *
     * @param buf    The buffer containing the record data to write.
     * @param offset The offset of the record data within buf.
     * @throws java.io.IOException on error
     */
    public void writeRecord(byte[] buf, int offset) throws IOException {
        if (outStream == null) {
            if (inStream == null) {
                throw new IOException("Output buffer is closed");
            }
            throw new IOException("writing to an input buffer");
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

        System.arraycopy(buf, offset, blockBuffer,
                (currRecIdx * recordSize),
                recordSize);

        currRecIdx++;
    }

    /**
     * Write a TarBuffer block to the archive.
     */
    private void writeBlock() throws IOException {
        if (outStream == null) {
            throw new IOException("writing to an input buffer");
        }

        outStream.write(blockBuffer, 0, blockSize);
        outStream.flush();

        currRecIdx = 0;
        currBlkIdx++;
        Arrays.fill(blockBuffer, (byte) 0);
    }

    /**
     * Flush the current data block if it has any data in it.
     */
    void flushBlock() throws IOException {
        if (outStream == null) {
            throw new IOException("writing to an input buffer");
        }

        if (currRecIdx > 0) {
            writeBlock();
        }
    }

    /**
     * Close the TarBuffer. If this is an output buffer, also flush the
     * current block before closing.
     *
     * @throws java.io.IOException on error
     */
    public void close() throws IOException {
        if (outStream != null) {
            flushBlock();

            if (outStream != System.out
                    && outStream != System.err) {
                outStream.close();

                outStream = null;
            }
        } else if (inStream != null) {
            if (inStream != System.in) {
                inStream.close();
            }
            inStream = null;
        }
    }
}
