
package org.xbib.io.compress.lzf;

import org.xbib.io.compress.BufferRecycler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Decorator {@link java.io.InputStream} implementation used for reading compressed data
 * and uncompressing it on the fly, such that reads return uncompressed data.
 * Its direct counterpart is {@link LZFOutputStream}; but there is also
 * {@link LZFCompressingInputStream} which does reverse of this class.
 */
public class LZFInputStream extends InputStream {

    /**
     * Underlying decoder in use.
     */
    protected final ChunkDecoder _decoder;
    /**
     * Object that handles details of buffer recycling
     */
    protected final BufferRecycler _recycler;
    /**
     * stream to be decompressed
     */
    protected final InputStream _inputStream;
    /**
     * Flag that indicates if we have already called 'inputStream.close()' (to
     * avoid calling it multiple times)
     */
    protected boolean _inputStreamClosed;
    /**
     * Flag that indicates whether we force full reads (reading of as many bytes
     * as requested), or 'optimal' reads (up to as many as available, but at
     * least one). Default is false, meaning that 'optimal' read is used.
     */
    protected boolean _cfgFullReads = false;
    /**
     * the current buffer of compressed bytes (from which to decode)
     */
    protected byte[] _inputBuffer;
    /**
     * the buffer of uncompressed bytes from which content is read
     */
    protected byte[] _decodedBytes;
    /**
     * The current position (next char to output) in the uncompressed bytes
     * buffer.
     */
    protected int _bufferPosition = 0;
    /**
     * Length of the current uncompressed bytes buffer
     */
    protected int _bufferLength = 0;
    /**
     * Number of bytes read from the underlying {@link #_inputStream}
     */
    protected int _readCount = 0;

    /*
     // Construction
     */
    public LZFInputStream(final InputStream inputStream) throws IOException {
        this(inputStream, false);
    }

    /**
     * @param in        Underlying input stream to use
     * @param fullReads Whether {@link #read(byte[])} should try to read exactly
     *                  as many bytes as requested (true); or just however many happen to be
     *                  available (false)
     */
    public LZFInputStream(final InputStream in, boolean fullReads) throws IOException {
        this(in, fullReads, ChunkDecoderFactory.optimalInstance());
    }

    public LZFInputStream(final InputStream in, boolean fullReads, ChunkDecoder decoder)
            throws IOException {
        super();
        _decoder = decoder;
        _recycler = BufferRecycler.instance();
        _inputStream = in;
        _inputStreamClosed = false;
        _cfgFullReads = fullReads;

        _inputBuffer = _recycler.allocInputBuffer(LZFChunk.MAX_CHUNK_LEN);
        _decodedBytes = _recycler.allocDecodeBuffer(LZFChunk.MAX_CHUNK_LEN);
    }

    /**
     * Method that can be used define whether reads should be "full" or
     * "optimal": former means that full compressed blocks are read right away
     * as needed, optimal that only smaller chunks are read at a time, more
     * being read as needed.
     */
    public void setUseFullReads(boolean b) {
        _cfgFullReads = b;
    }

    /*
     * InputStream impl
     */

    /**
     * Method is overridden to report number of bytes that can now be read from
     * decoded data buffer, without reading bytes from the underlying stream.
     * Never throws an exception; returns number of bytes available without
     * further reads from underlying source; -1 if stream has been closed, or 0
     * if an actual read (and possible blocking) is needed to find out.
     */
    @Override
    public int available() {
        // if closed, return -1;
        if (_inputStreamClosed) {
            return -1;
        }
        int left = (_bufferLength - _bufferPosition);
        return (left <= 0) ? 0 : left;
    }

    @Override
    public int read() throws IOException {
        if (!readyBuffer()) {
            return -1;
        }
        return _decodedBytes[_bufferPosition++] & 255;
    }

    @Override
    public int read(final byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public int read(final byte[] buffer, int offset, int length) throws IOException {
        if (length < 1) {
            return 0;
        }
        if (!readyBuffer()) {
            return -1;
        }
        // First let's read however much data we happen to have...
        int chunkLength = Math.min(_bufferLength - _bufferPosition, length);
        System.arraycopy(_decodedBytes, _bufferPosition, buffer, offset, chunkLength);
        _bufferPosition += chunkLength;

        if (chunkLength == length || !_cfgFullReads) {
            return chunkLength;
        }
        // Need more data, then
        int totalRead = chunkLength;
        do {
            offset += chunkLength;
            if (!readyBuffer()) {
                break;
            }
            chunkLength = Math.min(_bufferLength - _bufferPosition, (length - totalRead));
            System.arraycopy(_decodedBytes, _bufferPosition, buffer, offset, chunkLength);
            _bufferPosition += chunkLength;
            totalRead += chunkLength;
        } while (totalRead < length);

        return totalRead;
    }

    @Override
    public void close() throws IOException {
        _bufferPosition = _bufferLength = 0;
        byte[] buf = _inputBuffer;
        if (buf != null) {
            _inputBuffer = null;
            _recycler.releaseInputBuffer(buf);
        }
        buf = _decodedBytes;
        if (buf != null) {
            _decodedBytes = null;
            _recycler.releaseDecodeBuffer(buf);
        }
        if (!_inputStreamClosed) {
            _inputStreamClosed = true;
            _inputStream.close();
        }
    }

    /**
     * Overridden to just skip at most a single chunk at a time
     */
    @Override
    public long skip(long n) throws IOException {
        if (_inputStreamClosed) {
            return -1;
        }
        int left = (_bufferLength - _bufferPosition);
        // if none left, must read more:
        if (left <= 0) {
            // otherwise must read more to skip...
            int b = read();
            if (b < 0) { // EOF
                return -1;
            }
            // push it back to get accurate skip count
            --_bufferPosition;
            left = (_bufferLength - _bufferPosition);
        }
        // either way, just skip whatever we have decoded
        if (left > n) {
            left = (int) n;
        }
        _bufferPosition += left;
        return left;
    }

    /**
     * Method that can be used to find underlying {@link java.io.InputStream} that we
     * read from to get LZF encoded data to decode. Will never return null;
     * although underlying stream may be closed (if this stream has been
     * closed).
     */
    public InputStream getUnderlyingInputStream() {
        return _inputStream;
    }

    /**
     * Method that can be called to discard any already buffered input, read
     * from input source. Specialized method that only makes sense if the
     * underlying {@link java.io.InputStream} can be repositioned reliably.
     */
    public void discardBuffered() {
        _bufferPosition = _bufferLength = 0;
    }

    /**
     * Convenience method that will read and uncompress all data available, and
     * write it using given {@link java.io.OutputStream}. This avoids having to make an
     * intermediate copy of uncompressed data which would be needed when doing
     * the same manually.
     *
     * @param out OutputStream to use for writing content
     * @return Number of bytes written (uncompressed)
     */
    public int readAndWrite(OutputStream out) throws IOException {
        int total = 0;

        while (readyBuffer()) {
            int avail = _bufferLength - _bufferPosition;
            out.write(_decodedBytes, _bufferPosition, avail);
            _bufferPosition += avail; // to ensure it looks like we consumed it all
            total += avail;
        }
        return total;
    }

    /**
     * Fill the uncompressed bytes buffer by reading the underlying inputStream.
     *
     * @throws java.io.IOException
     */
    protected boolean readyBuffer() throws IOException {
        if (_bufferPosition < _bufferLength) {
            return true;
        }
        if (_inputStreamClosed) {
            return false;
        }
        _bufferLength = _decoder.decodeChunk(_inputStream, _inputBuffer, _decodedBytes);
        if (_bufferLength < 0) {
            return false;
        }
        _bufferPosition = 0;
        return (_bufferPosition < _bufferLength);
    }
}
