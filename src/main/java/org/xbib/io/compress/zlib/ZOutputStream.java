
package org.xbib.io.compress.zlib;

import java.io.IOException;
import java.io.OutputStream;

public class ZOutputStream extends OutputStream {

    protected ZStream z = new ZStream();

    protected int bufsize;

    protected int flush = ZConstants.Z_NO_FLUSH;

    protected byte[] buf;

    protected byte[] buf1 = new byte[1];

    protected boolean compress;

    protected OutputStream out;

    public ZOutputStream(OutputStream out) {
        super();
        this.out = out;
        this.bufsize = 512;
        this.buf = new byte[bufsize];
        z.inflateInit();
        compress = false;
    }

    public ZOutputStream(OutputStream out, int bufsize) {
        this(out, ZConstants.Z_DEFAULT_COMPRESSION, false);
        this.bufsize = bufsize;
        this.buf = new byte[bufsize];
    }

    public ZOutputStream(OutputStream out, int level, boolean nowrap) {
        super();
        this.out = out;
        this.bufsize = 512;
        this.buf = new byte[bufsize];
        z.deflateInit(level, nowrap);
        compress = true;
    }

    public void write(int b) throws IOException {
        buf1[0] = (byte) b;
        write(buf1, 0, 1);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        if (len == 0) {
            return;
        }
        int err;
        z.nextin = b;
        z.nextinindex = off;
        z.availin = len;
        do {
            z.nextout = buf;
            z.nextoutindex = 0;
            z.availout = bufsize;
            if (compress) {
                err = z.deflate(flush);
            } else {
                err = z.inflate(flush);
            }
            if (err != ZConstants.Z_OK) {
                throw new IOException((compress ? "de" : "in") + "flating: " + z.msg);
            }
            out.write(buf, 0, bufsize - z.availout);
        } while (z.availin > 0 || z.availout == 0);
    }

    public int getFlushMode() {
        return (flush);
    }

    public void setFlushMode(int flush) {
        this.flush = flush;
    }

    public void finish() throws IOException {
        int err;
        do {
            z.nextout = buf;
            z.nextoutindex = 0;
            z.availout = bufsize;
            if (compress) {
                err = z.deflate(ZConstants.Z_FINISH);
            } else {
                err = z.inflate(ZConstants.Z_FINISH);
            }
            if (err != ZConstants.Z_STREAM_END && err != ZConstants.Z_OK) {
                throw new IOException((compress ? "de" : "in") + "flating: " + z.msg);
            }
            if (bufsize - z.availout > 0) {
                out.write(buf, 0, bufsize - z.availout);
            }
        } while (z.availin > 0 || z.availout == 0);
        flush();
    }

    public void end() {
        if (z == null) {
            return;
        }
        if (compress) {
            z.deflateEnd();
        } else {
            z.inflateEnd();
        }
        z.free();
        z = null;
    }

    @Override
    public void close() throws IOException {
        try {
            try {
                finish();
            } catch (IOException ignored) {
            }
        } finally {
            end();
            out.close();
            out = null;
        }
    }

    /**
     * Returns the total number of bytes input so far.
     */
    public long getTotalIn() {
        return z.totalin;
    }

    /**
     * Returns the total number of bytes output so far.
     */
    public long getTotalOut() {
        return z.totalout;
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }
}
