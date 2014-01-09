
package org.xbib.io.compress.zlib;

public class ZStream {

    private final static int MAX_WBITS = 15;        // 32K LZ77 window

    private final static int DEF_WBITS = MAX_WBITS;

    private final static int Z_STREAM_ERROR = -2;

    protected byte[] nextin;     // next input byte

    protected int nextinindex;

    protected int availin;       // number of bytes available at next_in

    protected long totalin;      // total nb of input bytes read so far

    protected byte[] nextout;    // next output byte should be put there

    protected int nextoutindex;

    protected int availout;      // remaining free space at next_out

    protected long totalout;     // total nb of bytes output so far

    protected String msg;

    protected Deflate dstate;

    protected Inflate istate;

    protected int dataType; // best guess about the data type: ascii or binary

    protected long adler;

    public int inflateInit() {
        return inflateInit(DEF_WBITS);
    }

    public int inflateInit(boolean nowrap) {
        return inflateInit(DEF_WBITS, nowrap);
    }

    public int inflateInit(int w) {
        return inflateInit(w, false);
    }

    public int inflateInit(int w, boolean nowrap) {
        istate = new Inflate();
        return istate.inflateInit(this, nowrap ? -w : w);
    }

    public int inflate(int f) {
        if (istate == null) {
            return Z_STREAM_ERROR;
        }
        return istate.inflate(this, f);
    }

    public int inflateEnd() {
        if (istate == null) {
            return Z_STREAM_ERROR;
        }
        int ret = istate.inflateEnd(this);
        istate = null;
        return ret;
    }

    public int inflateSync() {
        if (istate == null) {
            return Z_STREAM_ERROR;
        }
        return istate.inflateSync(this);
    }

    public int inflateSetDictionary(byte[] dictionary, int dictLength) {
        if (istate == null) {
            return Z_STREAM_ERROR;
        }
        return istate.inflateSetDictionary(this, dictionary, dictLength);
    }

    public int deflateInit(int level) {
        return deflateInit(level, MAX_WBITS);
    }

    public int deflateInit(int level, boolean nowrap) {
        return deflateInit(level, MAX_WBITS, nowrap);
    }

    public int deflateInit(int level, int bits) {
        return deflateInit(level, bits, false);
    }

    public int deflateInit(int level, int bits, boolean nowrap) {
        dstate = new Deflate();
        return dstate.deflateInit(this, level, nowrap ? -bits : bits);
    }

    final public int deflate(int flush) {
        if (dstate == null) {
            return Z_STREAM_ERROR;
        }
        return dstate.deflate(this, flush);
    }

    final public int deflateEnd() {
        if (dstate == null) {
            return Z_STREAM_ERROR;
        }
        int ret = dstate.deflateEnd();
        dstate = null;
        return ret;
    }

    final public int deflateParams(int level, int strategy) {
        if (dstate == null) {
            return Z_STREAM_ERROR;
        }
        return dstate.deflateParams(this, level, strategy);
    }

    final public int deflateSetDictionary(byte[] dictionary, int dictLength) {
        if (dstate == null) {
            return Z_STREAM_ERROR;
        }
        return dstate.deflateSetDictionary(this, dictionary, dictLength);
    }

    // Flush as much pending output as possible. All deflate() output goes
    // through this function so some applications may wish to modify it
    // to avoid allocating a large strm->next_out buffer and copying into it.
    // (See also read_buf()).
    protected void flushPending() {
        int len = dstate.getPending();

        if (len > availout) {
            len = availout;
        }
        if (len == 0) {
            return;
        }

        System.arraycopy(dstate.getPendingBuf(), dstate.getPendingOut(),
                nextout, nextoutindex, len);

        nextoutindex += len;
        dstate.setPendingOut(dstate.getPendingOut() + len);
        totalout += len;
        availout -= len;
        dstate.setPending(dstate.getPending() - len);
        if (dstate.getPending() == 0) {
            dstate.setPendingOut(0);
        }
    }

    // Read a new buffer from the current input stream, update the adler32
    // and total number of bytes read.  All deflate() input goes through
    // this function so some applications may wish to modify it to avoid
    // allocating a large strm->next_in buffer and copying from it.
    // (See also flush_pending()).
    protected int readBuf(byte[] buf, int start, int size) {
        int len = availin;

        if (len > size) {
            len = size;
        }
        if (len == 0) {
            return 0;
        }

        availin -= len;

        if (dstate.getNoHeader() == 0) {
            adler = Adler32.adler32(adler, nextin, nextinindex, len);
        }
        System.arraycopy(nextin, nextinindex, buf, start, len);
        nextinindex += len;
        totalin += len;
        return len;
    }

    public void free() {
        nextin = null;
        nextout = null;
        msg = null;
    }
}
