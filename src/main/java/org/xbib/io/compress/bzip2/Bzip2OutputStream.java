
package org.xbib.io.compress.bzip2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * BZip2OutputSream
 * <p/>
 * Encapsulates any outputstream to write a bzip2-ed stream to it.
 */
public class Bzip2OutputStream extends OutputStream {

    private ByteArrayOutputStream buffer;
    private BZip2Deflate deflater;
    private OutputStream os;
    private int bufferMax;

    /**
     * Wraps an OutputStream in a BZip2OutputStream.
     * <p/>
     * Any bytes written to this stream will be compressed using the bzip2 algorithm on the fly
     *
     * @param os OutputStream to encapsulate
     */
    public Bzip2OutputStream(OutputStream os) throws IOException {
        this(os, 8192);
    }

    /**
     * Wraps an OutputStream in a BZip2OutputStream.
     * <p/>
     * Any bytes written to this stream will be compressed using the bzip2 algorithm on the fly
     *
     * @param os OutputStream to encapsulate
     */
    public Bzip2OutputStream(OutputStream os, int bufferMax) throws IOException {
        super();
        this.bufferMax = bufferMax;
        this.buffer = new ByteArrayOutputStream(bufferMax);
        this.deflater = new BZip2Deflate(1, bufferMax, true);
        this.os = os;
    }

    /**
     * Write integer to OutputStream
     *
     * @param b integer to write to stream
     */
    @Override
    public void write(int b) throws IOException {
        if (buffer.size() > bufferMax) {
            flush();
        }
        if (deflater.setInput(b) > 0) {
            deflate();
        }
    }

    /**
     * Write byte array OutputStream
     *
     * @param b byte array to write
     */
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Write part of a byte array OutputStream
     *
     * @param b   byte array to write
     * @param off index in byte array to start output at
     * @param len number of bytes to write
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (buffer.size() > bufferMax) {
            flush();
        }
        if (deflater.setInput(b, off, len) > 0) {
            deflate();
        }
    }

    /**
     * Close OutputStream
     */
    @Override
    public void close() throws IOException {
        deflater.finish();
        while (deflate() > 0) {
            flush();
        }
        os.close();
    }

    /**
     * Flush OutputStream
     */
    @Override
    public void flush() throws IOException {
        if (buffer.size() > 0) {
            os.write(buffer.toByteArray());
            buffer.reset();
            os.flush();
        }
    }

    /**
     * reads add empties the BZip2Deflate buffer
     *
     * @return the number of bytes read into the local output buffer.
     */
    private int deflate() {
        byte[] b = new byte[bufferMax];
        int c;
        c = deflater.deflate(b);
        if (c > 0) {
            buffer.write(b, 0, c);
        }
        return c;
    }
}
