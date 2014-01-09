
package org.xbib.io.compress.zlib;

public final class Inflate {

    // preset dictionary flag in zlib header
    private static final int PRESET_DICT = 0x20;
    private static final int Z_FINISH = 4;
    private static final int Z_DEFLATED = 8;
    private static final int Z_OK = 0;
    private static final int Z_STREAM_END = 1;
    private static final int Z_NEED_DICT = 2;
    private static final int Z_STREAM_ERROR = -2;
    private static final int Z_DATA_ERROR = -3;
    private static final int Z_BUF_ERROR = -5;
    private static final int METHOD = 0;   // waiting for method byte
    private static final int FLAG = 1;     // waiting for flag byte
    private static final int DICT4 = 2;    // four dictionary check bytes to go
    private static final int DICT3 = 3;    // three dictionary check bytes to go
    private static final int DICT2 = 4;    // two dictionary check bytes to go
    private static final int DICT1 = 5;    // one dictionary check byte to go
    private static final int DICT0 = 6;    // waiting for inflateSetDictionary
    private static final int BLOCKS = 7;   // decompressing blocks
    private static final int CHECK4 = 8;   // four check bytes to go
    private static final int CHECK3 = 9;   // three check bytes to go
    private static final int CHECK2 = 10;  // two check bytes to go
    private static final int CHECK1 = 11;  // one check byte to go
    private static final int DONE = 12;    // finished check, done
    private static final int BAD = 13;     // got an error--stay here
    private int mode;                            // current inflate mode
    // mode dependent information
    private int method;        // if FLAGS, method byte
    // if CHECK, check values to compare
    private long[] was = new long[1]; // computed check value
    private long need;               // stream check value
    // if BAD, inflateSync's marker bytes count
    private int marker;
    // mode independent information
    private int nowrap;          // flag for no wrapper
    private int wbits;            // log2(window size)  (8..15, defaults to 15)
    private InfBlocks blocks;     // current inflate_blocks state

    protected int inflateReset(ZStream z) {
        if (z == null || z.istate == null) {
            return Z_STREAM_ERROR;
        }

        z.totalin = z.totalout = 0;
        z.msg = null;
        z.istate.mode = z.istate.nowrap != 0 ? BLOCKS : METHOD;
        z.istate.blocks.reset(z, null);
        return Z_OK;
    }

    protected int inflateEnd(ZStream z) {
        if (blocks != null) {
            blocks.free(z);
        }
        blocks = null;
        //    ZFREE(z, z->state);
        return Z_OK;
    }

    protected int inflateInit(ZStream z, int w) {
        z.msg = null;
        blocks = null;

        // handle undocumented nowrap option (no zlib header or check)
        nowrap = 0;
        if (w < 0) {
            w = -w;
            nowrap = 1;
        }

        // set window size
        if (w < 8 || w > 15) {
            inflateEnd(z);
            return Z_STREAM_ERROR;
        }
        wbits = w;

        z.istate.blocks = new InfBlocks(z,
                z.istate.nowrap != 0 ? null : this,
                1 << w);

        // reset state
        inflateReset(z);
        return Z_OK;
    }

    protected int inflate(ZStream z, int f) {
        int r;
        int b;

        if (z == null || z.istate == null || z.nextin == null) {
            return Z_STREAM_ERROR;
        }
        f = f == Z_FINISH ? Z_BUF_ERROR : Z_OK;
        r = Z_BUF_ERROR;
        while (true) {
            switch (z.istate.mode) {
                case METHOD:
                    if (z.availin == 0) {
                        return r;
                    }
                    r = f;
                    z.availin--;
                    z.totalin++;
                    if (((z.istate.method = z.nextin[z.nextinindex++]) & 0xf) != Z_DEFLATED) {
                        z.istate.mode = BAD;
                        z.msg = "unknown compression method";
                        z.istate.marker = 5;       // can't try inflateSync
                        break;
                    }
                    if ((z.istate.method >> 4) + 8 > z.istate.wbits) {
                        z.istate.mode = BAD;
                        z.msg = "invalid window size";
                        z.istate.marker = 5;       // can't try inflateSync
                        break;
                    }
                    z.istate.mode = FLAG;
                    break;
                case FLAG:
                    if (z.availin == 0) {
                        return r;
                    }
                    r = f;
                    z.availin--;
                    z.totalin++;
                    b = (z.nextin[z.nextinindex++]) & 0xff;
                    if ((((z.istate.method << 8) + b) % 31) != 0) {
                        z.istate.mode = BAD;
                        z.msg = "incorrect header check";
                        z.istate.marker = 5;       // can't try inflateSync
                        break;
                    }

                    if ((b & PRESET_DICT) == 0) {
                        z.istate.mode = BLOCKS;
                        break;
                    }
                    z.istate.mode = DICT4;
                    break;
                case DICT4:
                    if (z.availin == 0) {
                        return r;
                    }
                    r = f;
                    z.availin--;
                    z.totalin++;
                    z.istate.need = ((z.nextin[z.nextinindex++] & 0xff) << 24) & 0xff000000L;
                    z.istate.mode = DICT3;
                    break;
                case DICT3:
                    if (z.availin == 0) {
                        return r;
                    }
                    r = f;
                    z.availin--;
                    z.totalin++;
                    z.istate.need += ((z.nextin[z.nextinindex++] & 0xff) << 16) & 0xff0000L;
                    z.istate.mode = DICT2;
                    break;
                case DICT2:
                    if (z.availin == 0) {
                        return r;
                    }
                    r = f;
                    z.availin--;
                    z.totalin++;
                    z.istate.need += ((z.nextin[z.nextinindex++] & 0xff) << 8) & 0xff00L;
                    z.istate.mode = DICT1;
                    break;
                case DICT1:
                    if (z.availin == 0) {
                        return r;
                    }
                    r = f;
                    z.availin--;
                    z.totalin++;
                    z.istate.need += (z.nextin[z.nextinindex++] & 0xffL);
                    z.adler = z.istate.need;
                    z.istate.mode = DICT0;
                    return Z_NEED_DICT;
                case DICT0:
                    z.istate.mode = BAD;
                    z.msg = "need dictionary";
                    z.istate.marker = 0;       // can try inflateSync
                    return Z_STREAM_ERROR;
                case BLOCKS:
                    r = z.istate.blocks.proc(z, r);
                    if (r == Z_DATA_ERROR) {
                        z.istate.mode = BAD;
                        z.istate.marker = 0;     // can try inflateSync
                        break;
                    }
                    if (r == Z_OK) {
                        r = f;
                    }
                    if (r != Z_STREAM_END) {
                        return r;
                    }
                    r = f;
                    z.istate.blocks.reset(z, z.istate.was);
                    if (z.istate.nowrap != 0) {
                        z.istate.mode = DONE;
                        break;
                    }
                    z.istate.mode = CHECK4;
                    break;
                case CHECK4:
                    if (z.availin == 0) {
                        return r;
                    }
                    r = f;
                    z.availin--;
                    z.totalin++;
                    z.istate.need = ((z.nextin[z.nextinindex++] & 0xff) << 24) & 0xff000000L;
                    z.istate.mode = CHECK3;
                    break;
                case CHECK3:
                    if (z.availin == 0) {
                        return r;
                    }
                    r = f;
                    z.availin--;
                    z.totalin++;
                    z.istate.need += ((z.nextin[z.nextinindex++] & 0xff) << 16) & 0xff0000L;
                    z.istate.mode = CHECK2;
                    break;
                case CHECK2:
                    if (z.availin == 0) {
                        return r;
                    }
                    r = f;
                    z.availin--;
                    z.totalin++;
                    z.istate.need += ((z.nextin[z.nextinindex++] & 0xff) << 8) & 0xff00L;
                    z.istate.mode = CHECK1;
                    break;
                case CHECK1:
                    if (z.availin == 0) {
                        return r;
                    }
                    r = f;
                    z.availin--;
                    z.totalin++;
                    z.istate.need += (z.nextin[z.nextinindex++] & 0xffL);
                    if (((int) (z.istate.was[0])) != ((int) (z.istate.need))) {
                        z.istate.mode = BAD;
                        z.msg = "incorrect data check";
                        z.istate.marker = 5;       // can't try inflateSync
                        break;
                    }
                    z.istate.mode = DONE;
                    break;
                case DONE:
                    return Z_STREAM_END;
                case BAD:
                    return Z_DATA_ERROR;
                default:
                    return Z_STREAM_ERROR;
            }
        }
    }

    protected int inflateSetDictionary(ZStream z, byte[] dictionary, int dictLength) {
        int index = 0;
        int length = dictLength;
        if (z == null || z.istate == null || z.istate.mode != DICT0) {
            return Z_STREAM_ERROR;
        }

        if (Adler32.adler32(1L, dictionary, 0, dictLength) != z.adler) {
            return Z_DATA_ERROR;
        }

        z.adler = Adler32.adler32(0, null, 0, 0);

        if (length >= (1 << z.istate.wbits)) {
            length = (1 << z.istate.wbits) - 1;
            index = dictLength - length;
        }
        z.istate.blocks.setDictionary(dictionary, index, length);
        z.istate.mode = BLOCKS;
        return Z_OK;
    }

    private static byte[] mark = {(byte) 0, (byte) 0, (byte) 0xff, (byte) 0xff};

    protected int inflateSync(ZStream z) {
        int n;       // number of bytes to look at
        int p;       // pointer to bytes
        int m;       // number of marker bytes found in a row
        long r, w;   // temporaries to save total_in and total_out

        // set up
        if (z == null || z.istate == null) {
            return Z_STREAM_ERROR;
        }
        if (z.istate.mode != BAD) {
            z.istate.mode = BAD;
            z.istate.marker = 0;
        }
        if ((n = z.availin) == 0) {
            return Z_BUF_ERROR;
        }
        p = z.nextinindex;
        m = z.istate.marker;

        // search
        while (n != 0 && m < 4) {
            if (z.nextin[p] == mark[m]) {
                m++;
            } else if (z.nextin[p] != 0) {
                m = 0;
            } else {
                m = 4 - m;
            }
            p++;
            n--;
        }

        // restore
        z.totalin += p - z.nextinindex;
        z.nextinindex = p;
        z.availin = n;
        z.istate.marker = m;

        // return no joy or set up to restart on a new block
        if (m != 4) {
            return Z_DATA_ERROR;
        }
        r = z.totalin;
        w = z.totalout;
        inflateReset(z);
        z.totalin = r;
        z.totalout = w;
        z.istate.mode = BLOCKS;
        return Z_OK;
    }

}
