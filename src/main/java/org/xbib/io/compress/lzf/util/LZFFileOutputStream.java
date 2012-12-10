package org.xbib.io.compress.lzf.util;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.xbib.io.compress.BufferRecycler;
import org.xbib.io.compress.lzf.ChunkEncoder;
import org.xbib.io.compress.lzf.LZFChunk;

/**
 * Helper class that allows use of LZF compression even if a library requires
 * use of {@link FileOutputStream}. <p> Note that use of this class is not
 * recommended unless you absolutely must use a {@link FileOutputStream}
 * instance; otherwise basic {@link LZFOutputStream} (which uses aggregation for
 * underlying streams) is more appropriate <p> Implementation note: much of the
 * code is just copied from {@link LZFOutputStream}, so care must be taken to
 * keep implementations in sync if there are fixes.
 *
 */
public class LZFFileOutputStream extends FileOutputStream {

    private static final int OUTPUT_BUFFER_SIZE = LZFChunk.MAX_CHUNK_LEN;
    private final ChunkEncoder _encoder;
    private final BufferRecycler _recycler;
    protected byte[] _outputBuffer;
    protected int _position = 0;
    /**
     * Configuration setting that governs whether basic 'flush()' should first
     * complete a block or not. <p> Default value is 'true'
     *
     * @since 0.8
     */
    protected boolean _cfgFinishBlockOnFlush = true;
    /**
     * Flag that indicates if we have already called '_outputStream.close()' (to
     * avoid calling it multiple times)
     */
    protected boolean _outputStreamClosed;
    /**
     * Wrapper object we use to allow decoder to write directly to the stream,
     * without ending in infinite loop...
     */
    private final Wrapper _wrapper;

    /*
     // Construction, configuration
     */
    public LZFFileOutputStream(File file) throws FileNotFoundException {
        super(file);
        _encoder = new ChunkEncoder(OUTPUT_BUFFER_SIZE);
        _recycler = BufferRecycler.instance();
        _outputBuffer = _recycler.allocOutputBuffer(OUTPUT_BUFFER_SIZE);
        _wrapper = new Wrapper();
    }

    public LZFFileOutputStream(File file, boolean append) throws FileNotFoundException {
        super(file, append);
        _encoder = new ChunkEncoder(OUTPUT_BUFFER_SIZE);
        _recycler = BufferRecycler.instance();
        _outputBuffer = _recycler.allocOutputBuffer(OUTPUT_BUFFER_SIZE);
        _wrapper = new Wrapper();
    }

    public LZFFileOutputStream(FileDescriptor fdObj) {
        super(fdObj);
        _encoder = new ChunkEncoder(OUTPUT_BUFFER_SIZE);
        _recycler = BufferRecycler.instance();
        _outputBuffer = _recycler.allocOutputBuffer(OUTPUT_BUFFER_SIZE);
        _wrapper = new Wrapper();
    }

    public LZFFileOutputStream(String name) throws FileNotFoundException {
        super(name);
        _encoder = new ChunkEncoder(OUTPUT_BUFFER_SIZE);
        _recycler = BufferRecycler.instance();
        _outputBuffer = _recycler.allocOutputBuffer(OUTPUT_BUFFER_SIZE);
        _wrapper = new Wrapper();
    }

    public LZFFileOutputStream(String name, boolean append) throws FileNotFoundException {
        super(name, append);
        _encoder = new ChunkEncoder(OUTPUT_BUFFER_SIZE);
        _recycler = BufferRecycler.instance();
        _outputBuffer = _recycler.allocOutputBuffer(OUTPUT_BUFFER_SIZE);
        _wrapper = new Wrapper();
    }

    /**
     * Method for defining whether call to {@link #flush} will also complete
     * current block (similar to calling {@link #finishBlock()}) or not.
     */
    public LZFFileOutputStream setFinishBlockOnFlush(boolean b) {
        _cfgFinishBlockOnFlush = b;
        return this;
    }

    /*
     // FileOutputStream overrides
     */
    @Override
    public void close() throws IOException {
        if (!_outputStreamClosed) {
            if (_position > 0) {
                writeCompressedBlock();
            }
            super.flush();
            _encoder.close();
            byte[] buf = _outputBuffer;
            if (buf != null) {
                _outputBuffer = null;
                _recycler.releaseOutputBuffer(buf);
            }
            _outputStreamClosed = true;
            super.close();
        }
    }

    @Override
    public void flush() throws IOException {
        checkNotClosed();
        if (_cfgFinishBlockOnFlush && _position > 0) {
            writeCompressedBlock();
        }
        super.flush();
    }

    // fine as is: don't override
    // public FileChannel getChannel();
    // final, can't override:
    // public FileDescriptor getFD();
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        checkNotClosed();

        final int BUFFER_LEN = _outputBuffer.length;

        // simple case first: buffering only (for trivially short writes)
        int free = BUFFER_LEN - _position;
        if (free >= length) {
            System.arraycopy(buffer, offset, _outputBuffer, _position, length);
            _position += length;
            return;
        }
        // otherwise, copy whatever we can, flush
        System.arraycopy(buffer, offset, _outputBuffer, _position, free);
        offset += free;
        length -= free;
        _position += free;
        writeCompressedBlock();

        // then write intermediate full block, if any, without copying:
        while (length >= BUFFER_LEN) {
            _encoder.encodeAndWriteChunk(buffer, offset, BUFFER_LEN, _wrapper);
            offset += BUFFER_LEN;
            length -= BUFFER_LEN;
        }

        // and finally, copy leftovers in buffer, if any
        if (length > 0) {
            System.arraycopy(buffer, offset, _outputBuffer, 0, length);
        }
        _position = length;
    }

    @Override
    public void write(int b) throws IOException {
        checkNotClosed();
        if (_position >= _outputBuffer.length) {
            writeCompressedBlock();
        }
        _outputBuffer[_position++] = (byte) b;
    }

    /*
     // Additional public methods
     */
    /**
     * Accessor for checking whether call to "flush()" will first finish the
     * current block or not
     */
    public boolean getFinishBlockOnFlush() {
        return _cfgFinishBlockOnFlush;
    }

    /**
     * Method that can be used to force completion of the current block, which
     * means that all buffered data will be compressed into an LZF block. This
     * typically results in lower compression ratio as larger blocks compress
     * better; but may be necessary for network connections to ensure timely
     * sending of data.
     */
    public LZFFileOutputStream finishBlock() throws IOException {
        checkNotClosed();
        if (_position > 0) {
            writeCompressedBlock();
        }
        return this;
    }

    /*
     ///////////////////////////////////////////////////////////////////////
     // Internal methods
     ///////////////////////////////////////////////////////////////////////
     */
    /**
     * Compress and write the current block to the OutputStream
     */
    protected void writeCompressedBlock() throws IOException {
        int left = _position;
        _position = 0;
        int offset = 0;

        do {
            int chunkLen = Math.min(LZFChunk.MAX_CHUNK_LEN, left);
            _encoder.encodeAndWriteChunk(_outputBuffer, offset, chunkLen, _wrapper);
            offset += chunkLen;
            left -= chunkLen;
        } while (left > 0);
    }

    protected void rawWrite(byte[] buffer, int offset, int length) throws IOException {
        super.write(buffer, offset, length);
    }

    protected void checkNotClosed() throws IOException {
        if (_outputStreamClosed) {
            throw new IOException(getClass().getName() + " already closed");
        }
    }

    /*
     // Helper class(es)
     */
    /**
     * This simple wrapper is needed to re-route read calls so that they will
     * use "raw" writes
     */
    private final class Wrapper extends OutputStream {

        @Override
        public void write(int arg0) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            rawWrite(buffer, offset, length);
        }
    }
}
