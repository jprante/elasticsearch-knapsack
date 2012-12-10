/*
Copyright (c) 2000,2001,2002,2003 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright 
notice, this list of conditions and the following disclaimer in 
the documentation and/or other materials provided with the distribution.

3. The names of the authors may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 * This program is based on zlib-1.1.3, so all credit should go authors
 * Jean-loup Gailly(jloup@gzip.org) and Mark Adler(madler@alumni.caltech.edu)
 * and contributors of zlib.
 */
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

    final public int inflateInit() {
        return inflateInit(DEF_WBITS);
    }

    final public int inflateInit(boolean nowrap) {
        return inflateInit(DEF_WBITS, nowrap);
    }

    final public int inflateInit(int w) {
        return inflateInit(w, false);
    }

    final public int inflateInit(int w, boolean nowrap) {
        istate = new Inflate();
        return istate.inflateInit(this, nowrap ? -w : w);
    }

    final public int inflate(int f) {
        if (istate == null) {
            return Z_STREAM_ERROR;
        }
        return istate.inflate(this, f);
    }

    final public int inflateEnd() {
        if (istate == null) {
            return Z_STREAM_ERROR;
        }
        int ret = istate.inflateEnd(this);
        istate = null;
        return ret;
    }

    final public int inflateSync() {
        if (istate == null) {
            return Z_STREAM_ERROR;
        }
        return istate.inflateSync(this);
    }

    final public int inflateSetDictionary(byte[] dictionary, int dictLength) {
        if (istate == null) {
            return Z_STREAM_ERROR;
        }
        return istate.inflateSetDictionary(this, dictionary, dictLength);
    }

    final public int deflateInit(int level) {
        return deflateInit(level, MAX_WBITS);
    }

    final public int deflateInit(int level, boolean nowrap) {
        return deflateInit(level, MAX_WBITS, nowrap);
    }

    final public int deflateInit(int level, int bits) {
        return deflateInit(level, bits, false);
    }

    final public int deflateInit(int level, int bits, boolean nowrap) {
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
