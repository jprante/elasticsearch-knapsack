
package org.xbib.io.compress.zlib;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ZInputStream extends FilterInputStream {

    protected ZStream z = new ZStream();
    protected int flush = ZConstants.Z_NO_FLUSH;
    protected byte[] buf;
    protected byte[] buf1 = new byte[1];
    protected boolean compress;
    private int bufsize;

    public ZInputStream(InputStream in) {
        this(in, false);
    }

    public ZInputStream(InputStream in, boolean nowrap) {
        super(in);
        this.bufsize = 512;
        this.buf = new byte[bufsize];
        z.inflateInit(nowrap);
        compress = false;
        z.nextin = buf;
        z.nextinindex = 0;
        z.availin = 0;
    }

    public ZInputStream(InputStream in, int bufsize) {
        super(in);
        this.bufsize = bufsize;
        this.buf = new byte[bufsize];
        z.inflateInit(false);
        compress = false;
        z.nextin = buf;
        z.nextinindex = 0;
        z.availin = 0;
    }

    public void level(int level) {
        z.deflateInit(level);
    }

    @Override
    public int read() throws IOException {
        if (read(buf1, 0, 1) == -1) {
            return (-1);
        }
        return (buf1[0] & 0xFF);
    }

    private boolean nomoreinput = false;

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return (0);
        }
        int err;
        z.nextout = b;
        z.nextoutindex = off;
        z.availout = len;
        do {
            if ((z.availin == 0) && (!nomoreinput)) { // if buffer is empty and more input is avaiable, refill it
                z.nextinindex = 0;
                z.availin = in.read(buf, 0, bufsize);//(bufsize<z.avail_out ? bufsize : z.avail_out));
                if (z.availin == -1) {
                    z.availin = 0;
                    nomoreinput = true;
                }
            }
            if (compress) {
                err = z.deflate(flush);
            } else {
                err = z.inflate(flush);
            }
            if (nomoreinput && (err == ZConstants.Z_BUF_ERROR)) {
                return (-1);
            }
            if (err != ZConstants.Z_OK && err != ZConstants.Z_STREAM_END) {
                throw new IOException((compress ? "de" : "in") + "flating: " + z.msg);
            }
            if ((nomoreinput || err == ZConstants.Z_STREAM_END) && (z.availout == len)) {
                return (-1);
            }
        } while (z.availout == len && err == ZConstants.Z_OK);
        //System.err.print("("+(len-z.avail_out)+")");
        return (len - z.availout);
    }

    public long skip(long n) throws IOException {
        int len = 512;
        if (n < len) {
            len = (int) n;
        }
        byte[] tmp = new byte[len];
        return ((long) read(tmp));
    }

    public int getFlushMode() {
        return (flush);
    }

    public void setFlushMode(int flush) {
        this.flush = flush;
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

    public void close() throws IOException {
        in.close();
    }
}
