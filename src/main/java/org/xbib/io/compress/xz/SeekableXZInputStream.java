/*
 * SeekableXZInputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package org.xbib.io.compress.xz;

import java.util.Arrays;
import java.util.ArrayList;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.EOFException;
import org.xbib.io.compress.xz.common.DecoderUtil;
import org.xbib.io.compress.xz.common.StreamFlags;
import org.xbib.io.compress.xz.check.Check;
import org.xbib.io.compress.xz.index.IndexDecoder;
import org.xbib.io.compress.xz.index.BlockInfo;

/**
 * Decompresses a .xz file in random access mode.
 * This supports decompressing concatenated .xz files.
 * <p>
 * Each .xz file consist of one or more Streams. Each Stream consist of zero
 * or more Blocks. Each Stream contains an Index of Streams' Blocks.
 * The Indexes from all Streams are loaded in RAM by a constructor of this
 * class. A typical .xz file has only one Stream, and parsing its Index will
 * need only three or four seeks.
 * <p>
 * To make random access possible, the data in a .xz file must be splitted
 * into multiple Blocks of reasonable size. Decompression can only start at
 * a Block boundary. When seeking to an uncompressed offset that is not at
 * a Block boundary, decompression starts at the beginning of the Block and
 * throws away data until the target offset is reached. Thus, smaller Blocks
 * mean faster seeks to arbitrary uncompressed offsets. On the other hand,
 * smaller Blocks mean worse compression. So one has to make a compromise
 * between random access speed and compression ratio.
 * <p>
 * Implementation note: This class uses linear search to locate the correct
 * Stream from the data structures in RAM. It was the simplest to implement
 * and should be fine as long as there aren't too many Streams. The correct
 * Block inside a Stream is located using binary search and thus is fast
 * even with a huge number of Blocks.
 *
 * <h4>Memory usage</h4>
 * <p>
 * The amount of memory needed for the Indexes is taken into account when
 * checking the memory usage limit. Each Stream is calculated to need at
 * least 1&nbsp;KiB of memory and each Block 16 bytes of memory, rounded up
 * to the next kibibyte. So unless the file has a huge number of Streams or
 * Blocks, these don't take significant amount of memory.
 *
 * <h4>Creating random-accessible .xz files</h4>
 * <p>
 * When using {@link XZOutputStream}, a new Block can be started by calling
 * its {@link XZOutputStream#endBlock() endBlock} method. If you know
 * that the decompressor will need to seek only to certain offsets, it can
 * be a good idea to start a new Block at (some of) these offsets (and
 * perhaps only at these offsets to get better compression ratio).
 * <p>
 * liblzma in XZ Utils supports starting a new Block with
 * <code>LZMA_FULL_FLUSH</code>. XZ Utils 5.1.1alpha added threaded
 * compression which creates multi-Block .xz files. XZ Utils 5.1.1alpha
 * also added the option <code>--block-size=SIZE</code> to the xz command
 * line tool.
 *
 * @see SeekableFileInputStream
 * @see XZInputStream
 * @see XZOutputStream
 */
public class SeekableXZInputStream extends SeekableInputStream {
    /**
     * The input stream containing XZ compressed data.
     */
    private SeekableInputStream in;

    /**
     * Memory usage limit after the memory usage of the IndexDecoders have
     * been substracted.
     */
    private final int memoryLimit;

    /**
     * Memory usage of the IndexDecoders.
     * <code>memoryLimit + indexMemoryUsage</code> equals the original
     * memory usage limit that was passed to the constructor.
     */
    private int indexMemoryUsage = 0;

    /**
     * List of IndexDecoders, one for each Stream in the file.
     */
    private final ArrayList streams = new ArrayList();

    /**
     * IndexDecoder from which the current Block is being decoded.
     * The constructor leaves this to point the IndexDecoder of
     * the first Stream.
     */
    private IndexDecoder index;

    /**
     * Bitmask of all Check IDs seen.
     */
    private int checkTypes = 0;

    /**
     * Integrity Check in the current XZ Stream. The constructor leaves
     * this to point to the Check of the first Stream.
     */
    private Check check;

    /**
     * Decoder of the current XZ Block, if any.
     */
    private BlockInputStream blockDecoder = null;

    /**
     * Compressed size of the file (all Streams).
     */
    private long uncompressedSize = 0;

    /**
     * Uncompressed size of the largest XZ Block in the file.
     */
    private long largestBlockSize = 0;

    /**
     * Current uncompressed position.
     */
    private long curPos = 0;

    /**
     * Target position for seeking.
     */
    private long seekPos;

    /**
     * True when <code>seek(long)</code> has been called but the actual
     * seeking hasn't been done yet.
     */
    private boolean seekNeeded = false;

    /**
     * True when end of the file was reached. This can be cleared by
     * calling <code>seek(long)</code>.
     */
    private boolean endReached = false;

    /**
     * Pending exception from an earlier error.
     */
    private IOException exception = null;

    /**
     * Creates a new seekable XZ decompressor without a memory usage limit.
     *
     * @param       in          seekable input stream containing one or more
     *                          XZ Streams; the whole input stream is used
     *
     * @throws      XZFormatException
     *                          input is not in the XZ format
     *
     * @throws      CorruptedInputException
     *                          XZ data is corrupt or truncated
     *
     * @throws      UnsupportedOptionsException
     *                          XZ headers seem valid but they specify
     *                          options not supported by this implementation
     *
     * @throws      EOFException
     *                          less than 6 bytes of input was available
     *                          from <code>in</code>, or (unlikely) the size
     *                          of the underlying stream got smaller while
     *                          this was reading from it
     *
     * @throws      IOException may be thrown by <code>in</code>
     */
    public SeekableXZInputStream(SeekableInputStream in)
            throws IOException {
        this(in, -1);
    }

    /**
     * Creates a new seekable XZ decomporessor with an optional
     * memory usage limit.
     *
     * @param       in          seekable input stream containing one or more
     *                          XZ Streams; the whole input stream is used
     *
     * @param       memoryLimit memory usage limit in kibibytes (KiB)
     *                          or <code>-1</code> to impose no
     *                          memory usage limit
     *
     * @throws      XZFormatException
     *                          input is not in the XZ format
     *
     * @throws      CorruptedInputException
     *                          XZ data is corrupt or truncated
     *
     * @throws      UnsupportedOptionsException
     *                          XZ headers seem valid but they specify
     *                          options not supported by this implementation
     *
     * @throws      MemoryLimitException
     *                          decoded XZ Indexes would need more memory
     *                          than allowed by the memory usage limit
     *
     * @throws      EOFException
     *                          less than 6 bytes of input was available
     *                          from <code>in</code>, or (unlikely) the size
     *                          of the underlying stream got smaller while
     *                          this was reading from it
     *
     * @throws      IOException may be thrown by <code>in</code>
     */
    public SeekableXZInputStream(SeekableInputStream in, int memoryLimit)
            throws IOException {
        this.in = in;
        DataInputStream inData = new DataInputStream(in);

        // Check the magic bytes in the beginning of the file.
        {
            in.seek(0);
            byte[] buf = new byte[XZ.HEADER_MAGIC.length];
            inData.readFully(buf);
            if (!Arrays.equals(buf, XZ.HEADER_MAGIC))
                throw new XZFormatException();
        }

        // Get the file size and verify that it is a multiple of 4 bytes.
        long pos = in.length();
        if ((pos & 3) != 0)
            throw new CorruptedInputException(
                    "XZ file size is not a multiple of 4 bytes");

        // Parse the headers starting from the end of the file.
        byte[] buf = new byte[DecoderUtil.STREAM_HEADER_SIZE];
        long streamPadding = 0;

        while (pos > 0) {
            if (pos < DecoderUtil.STREAM_HEADER_SIZE)
                throw new CorruptedInputException();

            // Read the potential Stream Footer.
            in.seek(pos - DecoderUtil.STREAM_HEADER_SIZE);
            inData.readFully(buf);

            // Skip Stream Padding four bytes at a time.
            // Skipping more at once would be faster,
            // but usually there isn't much Stream Padding.
            if (buf[8] == 0x00 && buf[9] == 0x00 && buf[10] == 0x00
                    && buf[11] == 0x00) {
                streamPadding += 4;
                pos -= 4;
                continue;
            }

            // It's not Stream Padding. Update pos.
            pos -= DecoderUtil.STREAM_HEADER_SIZE;

            // Decode the Stream Footer and check if Backward Size
            // looks reasonable.
            StreamFlags streamFooter = DecoderUtil.decodeStreamFooter(buf);
            if (streamFooter.backwardSize >= pos)
                throw new CorruptedInputException(
                        "Backward Size in XZ Stream Footer is too big");

            // Check that the Check ID is supported. Store it in case this
            // is the first Stream in the file.
            check = Check.getInstance(streamFooter.checkType);

            // Remember which Check IDs have been seen.
            checkTypes |= 1 << streamFooter.checkType;

            // Seek to the beginning of the Index.
            in.seek(pos - streamFooter.backwardSize);

            // Decode the Index field.
            try {
                index = new IndexDecoder(in, streamFooter, streamPadding,
                                         memoryLimit);
            } catch (MemoryLimitException e) {
                // IndexDecoder doesn't know how much memory we had
                // already needed so we need to recreate the exception.
                assert memoryLimit >= 0;
                throw new MemoryLimitException(
                        e.getMemoryNeeded() + indexMemoryUsage,
                        memoryLimit + indexMemoryUsage);
            }

            // Update the memory usage and limit counters.
            indexMemoryUsage += index.getMemoryUsage();
            if (memoryLimit >= 0) {
                memoryLimit -= index.getMemoryUsage();
                assert memoryLimit >= 0;
            }

            // Remember the uncompressed size of the largest Block.
            if (largestBlockSize < index.getLargestBlockSize())
                largestBlockSize = index.getLargestBlockSize();

            // Calculate the offset to the beginning of this XZ Stream and
            // check that it looks sane.
            long off = index.getStreamSize() - DecoderUtil.STREAM_HEADER_SIZE;
            if (pos < off)
                throw new CorruptedInputException("XZ Index indicates "
                        + "too big compressed size for the XZ Stream");

            // Seek to the beginning of this Stream.
            pos -= off;
            in.seek(pos);

            // Decode the Stream Header.
            inData.readFully(buf);
            StreamFlags streamHeader = DecoderUtil.decodeStreamHeader(buf);

            // Verify that the Stream Header matches the Stream Footer.
            if (!DecoderUtil.areStreamFlagsEqual(streamHeader, streamFooter))
                throw new CorruptedInputException(
                        "XZ Stream Footer does not match Stream Header");

            // Update the total uncompressed size of the file and check that
            // it doesn't overflow.
            uncompressedSize += index.getUncompressedSize();
            if (uncompressedSize < 0)
                throw new UnsupportedOptionsException("XZ file is too big");

            // Add this Stream to the list of Streams.
            streams.add(index);

            // Reset to be ready to parse the next Stream.
            streamPadding = 0;
        }

        assert pos == 0;

        // Save it now that indexMemoryUsage has been substracted from it.
        this.memoryLimit = memoryLimit;
    }

    /**
     * Gets the types of integrity checks used in the .xz file.
     * Multiple checks are possible only if there are multiple
     * concatenated XZ Streams.
     * <p>
     * The returned value has a bit set for every check type that is present.
     * For example, if CRC64 and SHA-256 were used, the return value is
     * <code>(1&nbsp;&lt;&lt;&nbsp;XZ.CHECK_CRC64)
     * | (1&nbsp;&lt;&lt;&nbsp;XZ.CHECK_SHA256)</code>.
     */
    public int getCheckTypes() {
        return checkTypes;
    }

    /**
     * Gets the amount of memory in kibibytes (KiB) used by
     * the data structures needed to locate the XZ Blocks.
     * This is usually useless information but since it is calculated
     * for memory usage limit anyway, it is nice to make it available to too.
     */
    public int getIndexMemoryUsage() {
        return indexMemoryUsage;
    }

    /**
     * Gets the uncompressed size of the largest XZ Block in bytes.
     * This can be useful if you want to check that the file doesn't
     * have huge XZ Blocks which could make seeking to arbitrary offsets
     * very slow. Note that huge Blocks don't automatically mean that
     * seeking would be slow, for example, seeking to the beginning of
     * any Block is always fast.
     */
    public long getLargestBlockSize() {
        return largestBlockSize;
    }

    /**
     * Decompresses the next byte from this input stream.
     *
     * @return      the next decompressed byte, or <code>-1</code>
     *              to indicate the end of the compressed stream
     *
     * @throws      CorruptedInputException
     * @throws      UnsupportedOptionsException
     * @throws      MemoryLimitException
     *
     * @throws      XZIOException if the stream has been closed
     *
     * @throws      IOException may be thrown by <code>in</code>
     */
    public int read() throws IOException {
        byte[] buf = new byte[1];
        return read(buf, 0, 1) == -1 ? -1 : (buf[0] & 0xFF);
    }

    /**
     * Decompresses into an array of bytes.
     * <p>
     * If <code>len</code> is zero, no bytes are read and <code>0</code>
     * is returned. Otherwise this will try to decompress <code>len</code>
     * bytes of uncompressed data. Less than <code>len</code> bytes may
     * be read only in the following situations:
     * <ul>
     *   <li>The end of the compressed data was reached successfully.</li>
     *   <li>An error is detected after at least one but less <code>len</code>
     *       bytes have already been successfully decompressed.
     *       The next call with non-zero <code>len</code> will immediately
     *       throw the pending exception.</li>
     *   <li>An exception is thrown.</li>
     * </ul>
     *
     * @param       buf         target buffer for uncompressed data
     * @param       off         start offset in <code>buf</code>
     * @param       len         maximum number of uncompressed bytes to read
     *
     * @return      number of bytes read, or <code>-1</code> to indicate
     *              the end of the compressed stream
     *
     * @throws      CorruptedInputException
     * @throws      UnsupportedOptionsException
     * @throws      MemoryLimitException
     *
     * @throws      XZIOException if the stream has been closed
     *
     * @throws      IOException may be thrown by <code>in</code>
     */
    public int read(byte[] buf, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len < 0 || off + len > buf.length)
            throw new IndexOutOfBoundsException();

        if (len == 0)
            return 0;

        if (in == null)
            throw new XZIOException("Stream closed");

        if (exception != null)
            throw exception;

        int size = 0;

        try {
            if (seekNeeded)
                seek();

            if (endReached)
                return -1;

            while (len > 0) {
                if (blockDecoder == null) {
                    seek();
                    if (endReached)
                        break;
                }

                int ret = blockDecoder.read(buf, off, len);

                if (ret > 0) {
                    curPos += ret;
                    size += ret;
                    off += ret;
                    len -= ret;
                } else if (ret == -1) {
                    blockDecoder = null;
                }
            }
        } catch (IOException e) {
            // We know that the file isn't simply truncated because we could
            // parse the Indexes in the constructor. So convert EOFException
            // to CorruptedInputException.
            if (e instanceof EOFException)
                e = new CorruptedInputException();

            exception = e;
            if (size == 0)
                throw e;
        }

        return size;
    }

    /**
     * Returns the number of uncompressed bytes that can be read
     * without blocking. The value is returned with an assumption
     * that the compressed input data will be valid. If the compressed
     * data is corrupt, <code>CorruptedInputException</code> may get
     * thrown before the number of bytes claimed to be available have
     * been read from this input stream.
     *
     * @return      the number of uncompressed bytes that can be read
     *              without blocking
     */
    public int available() throws IOException {
        if (in == null)
            throw new XZIOException("Stream closed");

        if (exception != null)
            throw exception;

        if (endReached || seekNeeded || blockDecoder == null)
            return 0;

        return blockDecoder.available();
    }

    /**
     * Closes the stream and calls <code>in.close()</code>.
     * If the stream was already closed, this does nothing.
     *
     * @throws  IOException if thrown by <code>in.close()</code>
     */
    public void close() throws IOException {
        if (in != null) {
            try {
                in.close();
            } finally {
                in = null;
            }
        }
    }

    /**
     * Gets the uncompressed size of this input stream. If there are multiple
     * XZ Streams, the total uncompressed size of all XZ Streams is returned.
     */
    public long length() {
        return uncompressedSize;
    }

    /**
     * Gets the uncompressed position in this input stream.
     *
     * @throws      XZIOException if the stream has been closed
     */
    public long position() throws IOException {
        if (in == null)
            throw new XZIOException("Stream closed");

        return seekNeeded ? seekPos : curPos;
    }

    /**
     * Seeks to the specified absolute uncompressed position in the stream.
     * This only stores the new position, so this function itself is always
     * very fast. The actual seek is done when <code>read</code> is called
     * to read at least one byte.
     * <p>
     * Seeking past the end of the stream is possible. In that case
     * <code>read</code> will return <code>-1</code> to indicate
     * the end of the stream.
     *
     * @param       pos         new uncompressed read position
     *
     * @throws      XZIOException
     *                          if <code>pos</code> is negative, or
     *                          if stream has been closed
     */
    public void seek(long pos) throws IOException {
        if (in == null)
            throw new XZIOException("Stream closed");

        if (pos < 0)
            throw new XZIOException("Negative seek position: " + pos);

        seekPos = pos;
        seekNeeded = true;
    }

    /**
     * Does the actual seeking. This is also called when <code>read</code>
     * needs a new Block to decode.
     */
    private void seek() throws IOException {
        // If seek(long) wasn't called, we simply need to get the next Block
        // from the same Stream. If there are no more Blocks in this Stream,
        // then we behave as if seek(long) had been called.
        if (!seekNeeded) {
            if (index.hasNext()) {
                BlockInfo info = index.getNext();
                initBlockDecoder(info);
                return;
            }

            seekPos = curPos;
        }

        seekNeeded = false;

        // Check if we are seeking to or past the end of the file.
        if (seekPos >= uncompressedSize) {
            curPos = seekPos;
            blockDecoder = null;
            endReached = true;
            return;
        }

        endReached = false;

        // Locate the Stream that contains the uncompressed target position.
        int i = streams.size();
        assert i >= 1;

        long uncompressedSum = 0;
        long compressedSum = 0;

        while (true) {
            index = (IndexDecoder)streams.get(--i);
            if (uncompressedSum + index.getUncompressedSize() > seekPos)
                break;

            uncompressedSum += index.getUncompressedSize();
            compressedSum += index.getStreamAndPaddingSize();
            assert (compressedSum & 3) == 0;
        }

        // Locate the Block from the Stream that contains
        // the uncompressed target position.
        BlockInfo info = index.locate(seekPos - uncompressedSum);
        assert (info.compressedOffset & 3) == 0 : info.compressedOffset;

        // Adjust the Stream-specific offsets to file offsets.
        info.compressedOffset += compressedSum;
        info.uncompressedOffset += uncompressedSum;
        assert seekPos >= info.uncompressedOffset;
        assert seekPos < info.uncompressedOffset + info.uncompressedSize;

        // Seek in the underlying stream and create a new Block decoder
        // only if really needed. We can skip it if the current position
        // is already in the correct Block and the target position hasn't
        // been decompressed yet.
        //
        // NOTE: If curPos points to the beginning of this Block, it's
        // because it was left there after decompressing an earlier Block.
        // In that case, decoding of the current Block hasn't been started
        // yet. (Decoding of a Block won't be started until at least one
        // byte will also be read from it.)
        if (!(curPos > info.uncompressedOffset && curPos <= seekPos)) {
            // Seek to the beginning of the Block.
            in.seek(info.compressedOffset);

            // Since it is possible that this Block is from a different
            // Stream than the previous Block, initialize a new Check.
            check = Check.getInstance(info.streamFlags.checkType);

            // Create a new Block decoder.
            initBlockDecoder(info);
            curPos = info.uncompressedOffset;
        }

        // If the target wasn't at a Block boundary, decompress and throw
        // away data to reach the target position.
        if (seekPos > curPos) {
            // NOTE: The "if" below is there just in case. In this situation,
            // blockDecoder.skip will always skip the requested amount
            // or throw an exception.
            long skipAmount = seekPos - curPos;
            if (blockDecoder.skip(skipAmount) != skipAmount)
                throw new CorruptedInputException();
        }

        curPos = seekPos;
    }

    /**
     * Initializes a new BlockInputStream. This is a helper function for
     * <code>seek()</code>.
     */
    private void initBlockDecoder(BlockInfo info) throws IOException {
        try {
            // Set it to null first so that GC can collect it if memory
            // runs tight when initializing a new BlockInputStream.
            blockDecoder = null;
            blockDecoder = new BlockInputStream(in, check, memoryLimit,
                                                info.unpaddedSize,
                                                info.uncompressedSize);
        } catch (MemoryLimitException e) {
            // BlockInputStream doesn't know how much memory we had
            // already needed so we need to recreate the exception.
            assert memoryLimit >= 0;
            throw new MemoryLimitException(
                    e.getMemoryNeeded() + indexMemoryUsage,
                    memoryLimit + indexMemoryUsage);
        } catch (IndexIndicatorException e) {
            // It cannot be Index so the file must be corrupt.
            throw new CorruptedInputException();
        }
    }
}
