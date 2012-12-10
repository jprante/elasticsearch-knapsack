package org.xbib.io.compress;

import java.io.*;

/**
 * Simple wrapper or wrapper around {@link Uncompressor}, to help
 * with inter-operability.
 */
public class UncompressorOutputStream extends OutputStream
{
    protected final Uncompressor _uncompressor;

    private byte[] _singleByte = null;
    
    public UncompressorOutputStream(Uncompressor uncomp)
    {
        _uncompressor = uncomp;
    }

    /**
     * Call to this method will result in call to
     * {@link Uncompressor#complete()}, which is idempotent
     * (i.e. can be called multiple times without ill effects).
     */
    @Override
    public void close() throws IOException {
        _uncompressor.complete();
    }

    @Override
    public void flush() { }

    @Override
    public void write(byte[] b) throws IOException {
        _uncompressor.feedCompressedData(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        _uncompressor.feedCompressedData(b, off, len);
    }

    @Override
    public void write(int b)  throws IOException
    {
        if (_singleByte == null) {
            _singleByte = new byte[1];
        }
        _singleByte[0] = (byte) b;
        _uncompressor.feedCompressedData(_singleByte, 0, 1);
    }
    
}
