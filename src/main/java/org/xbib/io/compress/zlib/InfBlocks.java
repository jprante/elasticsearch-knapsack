
package org.xbib.io.compress.zlib;

final class InfBlocks {

    private final static int MANY = 1440;
    // And'ing with mask[n] masks the lower n bits
    private final static int[] inflate_mask = {
            0x00000000, 0x00000001, 0x00000003, 0x00000007, 0x0000000f,
            0x0000001f, 0x0000003f, 0x0000007f, 0x000000ff, 0x000001ff,
            0x000003ff, 0x000007ff, 0x00000fff, 0x00001fff, 0x00003fff,
            0x00007fff, 0x0000ffff
    };
    // Table for deflate from PKZIP's appnote.txt.
    private final static int[] border = { // Order of the bit length code lengths
            16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
    };
    private final static int Z_OK = 0;
    private final static int Z_STREAM_END = 1;
    private final static int Z_STREAM_ERROR = -2;
    private final static int Z_DATA_ERROR = -3;
    private final static int Z_BUF_ERROR = -5;
    private final static int TYPE = 0;  // get type bits (3, including end bit)
    private final static int LENS = 1;  // get lengths for stored
    private final static int STORED = 2;// processing stored block
    private final static int TABLE = 3; // get table lengths
    private final static int BTREE = 4; // get bit lengths tree for a dynamic block
    private final static int DTREE = 5; // get length, distance trees for a dynamic block
    private final static int CODES = 6; // processing fixed or dynamic block
    private final static int DRY = 7;   // output remaining window bytes
    private final static int DONE = 8;  // finished last block, done
    private final static int BAD = 9;   // ot a data error--stuck here
    private int mode;            // current inflate_block mode
    private int left;            // if STORED, bytes left to copy
    private int table;           // table lengths (14 bits)
    private int index;           // index into blens (or border)
    private int[] blens;         // bit lengths of codes
    private int[] bb = new int[1]; // bit length tree depth
    private int[] tb = new int[1]; // bit length decoding tree
    private InfCodes codes = new InfCodes();      // if CODES, current state
    private int last;            // true if this block is the last block
    // mode independent information
    protected int bitk;            // bits in bit buffer
    protected int bitb;            // bit buffer
    private int[] hufts;         // single malloc for tree space
    protected byte[] window;       // sliding window
    protected int end;             // one byte after sliding window
    protected int read;            // window read pointer
    protected int write;           // window write pointer
    private Object checkfn;      // check function
    private long check;          // check on output
    private InfTree inftree = new InfTree();

    InfBlocks(ZStream z, Object checkfn, int w) {
        hufts = new int[MANY * 3];
        window = new byte[w];
        end = w;
        this.checkfn = checkfn;
        mode = TYPE;
        reset(z, null);
    }

    protected void reset(ZStream z, long[] c) {
        if (c != null) {
            c[0] = check;
        }
        if (mode == CODES) {
            codes.free(z);
        }
        mode = TYPE;
        bitk = 0;
        bitb = 0;
        read = write = 0;
        if (checkfn != null) {
            z.adler = check = Adler32.adler32(0L, null, 0, 0);
        }
    }

    protected int proc(ZStream z, int r) {
        int t;              // temporary storage
        int b;              // bit buffer
        int k;              // bits in bit buffer
        int p;              // input data pointer
        int n;              // bytes available there
        int q;              // output window write pointer
        int m;              // bytes to end of window or read pointer

        // copy input/output information to locals (UPDATE macro restores)
        {
            p = z.nextinindex;
            n = z.availin;
            b = bitb;
            k = bitk;
        }
        {
            q = write;
            m = q < read ? read - q - 1 : end - q;
        }

        // process input based on current state
        while (true) {
            switch (mode) {
                case TYPE:
                    while (k < (3)) {
                        if (n != 0) {
                            r = Z_OK;
                        } else {
                            bitb = b;
                            bitk = k;
                            z.availin = n;
                            z.totalin += p - z.nextinindex;
                            z.nextinindex = p;
                            write = q;
                            return inflateFlush(z, r);
                        }
                        n--;
                        b |= (z.nextin[p++] & 0xff) << k;
                        k += 8;
                    }
                    t = b & 7;
                    last = t & 1;
                    switch (t >>> 1) {
                        case 0: // stored
                            b >>>= (3);
                            k -= (3);
                            t = k & 7; // go to byte boundary
                            b >>>= (t);
                            k -= (t);
                            mode = LENS; // get length of stored block
                            break;
                        case 1: // fixed
                            int[] bl = new int[1];
                            int[] bd = new int[1];
                            int[][] tl = new int[1][];
                            int[][] td = new int[1][];
                            InfTree.inflate_trees_fixed(bl, bd, tl, td, z);
                            codes.init(bl[0], bd[0], tl[0], 0, td[0], 0, z);
                            b >>>= (3);
                            k -= (3);
                            mode = CODES;
                            break;
                        case 2: // dynamic
                            b >>>= (3);
                            k -= (3);
                            mode = TABLE;
                            break;
                        case 3: // illegal
                            b >>>= (3);
                            k -= (3);
                            mode = BAD;
                            z.msg = "invalid block type";
                            r = Z_DATA_ERROR;
                            bitb = b;
                            bitk = k;
                            z.availin = n;
                            z.totalin += p - z.nextinindex;
                            z.nextinindex = p;
                            write = q;
                            return inflateFlush(z, r);
                    }
                    break;
                case LENS:
                    while (k < (32)) {
                        if (n != 0) {
                            r = Z_OK;
                        } else {
                            bitb = b;
                            bitk = k;
                            z.availin = n;
                            z.totalin += p - z.nextinindex;
                            z.nextinindex = p;
                            write = q;
                            return inflateFlush(z, r);
                        }
                        n--;
                        b |= (z.nextin[p++] & 0xff) << k;
                        k += 8;
                    }
                    if ((((~b) >>> 16) & 0xffff) != (b & 0xffff)) {
                        mode = BAD;
                        z.msg = "invalid stored block lengths";
                        r = Z_DATA_ERROR;

                        bitb = b;
                        bitk = k;
                        z.availin = n;
                        z.totalin += p - z.nextinindex;
                        z.nextinindex = p;
                        write = q;
                        return inflateFlush(z, r);
                    }
                    left = (b & 0xffff);
                    b = k = 0;                       // dump bits
                    mode = left != 0 ? STORED : (last != 0 ? DRY : TYPE);
                    break;
                case STORED:
                    if (n == 0) {
                        bitb = b;
                        bitk = k;
                        z.availin = n;
                        z.totalin += p - z.nextinindex;
                        z.nextinindex = p;
                        write = q;
                        return inflateFlush(z, r);
                    }
                    if (m == 0) {
                        if (q == end && read != 0) {
                            q = 0;
                            m = q < read ? read - q - 1 : end - q;
                        }
                        if (m == 0) {
                            write = q;
                            r = inflateFlush(z, r);
                            q = write;
                            m = q < read ? read - q - 1 : end - q;
                            if (q == end && read != 0) {
                                q = 0;
                                m = q < read ? read - q - 1 : end - q;
                            }
                            if (m == 0) {
                                bitb = b;
                                bitk = k;
                                z.availin = n;
                                z.totalin += p - z.nextinindex;
                                z.nextinindex = p;
                                write = q;
                                return inflateFlush(z, r);
                            }
                        }
                    }
                    r = Z_OK;
                    t = left;
                    if (t > n) {
                        t = n;
                    }
                    if (t > m) {
                        t = m;
                    }
                    System.arraycopy(z.nextin, p, window, q, t);
                    p += t;
                    n -= t;
                    q += t;
                    m -= t;
                    if ((left -= t) != 0) {
                        break;
                    }
                    mode = last != 0 ? DRY : TYPE;
                    break;
                case TABLE:
                    while (k < (14)) {
                        if (n != 0) {
                            r = Z_OK;
                        } else {
                            bitb = b;
                            bitk = k;
                            z.availin = n;
                            z.totalin += p - z.nextinindex;
                            z.nextinindex = p;
                            write = q;
                            return inflateFlush(z, r);
                        }
                        n--;
                        b |= (z.nextin[p++] & 0xff) << k;
                        k += 8;
                    }
                    table = t = (b & 0x3fff);
                    if ((t & 0x1f) > 29 || ((t >> 5) & 0x1f) > 29) {
                        mode = BAD;
                        z.msg = "too many length or distance symbols";
                        r = Z_DATA_ERROR;

                        bitb = b;
                        bitk = k;
                        z.availin = n;
                        z.totalin += p - z.nextinindex;
                        z.nextinindex = p;
                        write = q;
                        return inflateFlush(z, r);
                    }
                    t = 258 + (t & 0x1f) + ((t >> 5) & 0x1f);
                    if (blens == null || blens.length < t) {
                        blens = new int[t];
                    } else {
                        for (int i = 0; i < t; i++) {
                            blens[i] = 0;
                        }
                    }
                    b >>>= (14);
                    k -= (14);
                    index = 0;
                    mode = BTREE;
                    break;
                case BTREE:
                    while (index < 4 + (table >>> 10)) {
                        while (k < (3)) {
                            if (n != 0) {
                                r = Z_OK;
                            } else {
                                bitb = b;
                                bitk = k;
                                z.availin = n;
                                z.totalin += p - z.nextinindex;
                                z.nextinindex = p;
                                write = q;
                                return inflateFlush(z, r);
                            }
                            n--;
                            b |= (z.nextin[p++] & 0xff) << k;
                            k += 8;
                        }
                        blens[border[index++]] = b & 7;
                        b >>>= 3;
                        k -= 3;
                    }
                    while (index < 19) {
                        blens[border[index++]] = 0;
                    }
                    bb[0] = 7;
                    t = inftree.inflateTreesBits(blens, bb, tb, hufts, z);
                    if (t != Z_OK) {
                        r = t;
                        if (r == Z_DATA_ERROR) {
                            blens = null;
                            mode = BAD;
                        }
                        bitb = b;
                        bitk = k;
                        z.availin = n;
                        z.totalin += p - z.nextinindex;
                        z.nextinindex = p;
                        write = q;
                        return inflateFlush(z, r);
                    }
                    index = 0;
                    mode = DTREE;
                    break;
                case DTREE:
                    while (true) {
                        t = table;
                        if (!(index < 258 + (t & 0x1f) + ((t >> 5) & 0x1f))) {
                            break;
                        }
                        //int[] h;
                        int i, j, c;
                        t = bb[0];
                        while (k < (t)) {
                            if (n != 0) {
                                r = Z_OK;
                            } else {
                                bitb = b;
                                bitk = k;
                                z.availin = n;
                                z.totalin += p - z.nextinindex;
                                z.nextinindex = p;
                                write = q;
                                return inflateFlush(z, r);
                            }
                            n--;
                            b |= (z.nextin[p++] & 0xff) << k;
                            k += 8;
                        }
                        t = hufts[(tb[0] + (b & inflate_mask[t])) * 3 + 1];
                        c = hufts[(tb[0] + (b & inflate_mask[t])) * 3 + 2];
                        if (c < 16) {
                            b >>>= (t);
                            k -= (t);
                            blens[index++] = c;
                        } else { // c == 16..18
                            i = c == 18 ? 7 : c - 14;
                            j = c == 18 ? 11 : 3;
                            while (k < (t + i)) {
                                if (n != 0) {
                                    r = Z_OK;
                                } else {
                                    bitb = b;
                                    bitk = k;
                                    z.availin = n;
                                    z.totalin += p - z.nextinindex;
                                    z.nextinindex = p;
                                    write = q;
                                    return inflateFlush(z, r);
                                }
                                n--;
                                b |= (z.nextin[p++] & 0xff) << k;
                                k += 8;
                            }
                            b >>>= (t);
                            k -= (t);
                            j += (b & inflate_mask[i]);
                            b >>>= (i);
                            k -= (i);
                            i = index;
                            t = table;
                            if (i + j > 258 + (t & 0x1f) + ((t >> 5) & 0x1f) ||
                                    (c == 16 && i < 1)) {
                                blens = null;
                                mode = BAD;
                                z.msg = "invalid bit length repeat";
                                r = Z_DATA_ERROR;

                                bitb = b;
                                bitk = k;
                                z.availin = n;
                                z.totalin += p - z.nextinindex;
                                z.nextinindex = p;
                                write = q;
                                return inflateFlush(z, r);
                            }
                            c = c == 16 ? blens[i - 1] : 0;
                            do {
                                blens[i++] = c;
                            } while (--j != 0);
                            index = i;
                        }
                    }
                    tb[0] = -1;
                {
                    int[] bl = new int[1];
                    int[] bd = new int[1];
                    int[] tl = new int[1];
                    int[] td = new int[1];
                    bl[0] = 9;         // must be <= 9 for lookahead assumptions
                    bd[0] = 6;         // must be <= 9 for lookahead assumptions
                    t = table;
                    t = inftree.inflate_trees_dynamic(257 + (t & 0x1f),
                            1 + ((t >> 5) & 0x1f),
                            blens, bl, bd, tl, td, hufts, z);
                    if (t != Z_OK) {
                        if (t == Z_DATA_ERROR) {
                            blens = null;
                            mode = BAD;
                        }
                        r = t;
                        bitb = b;
                        bitk = k;
                        z.availin = n;
                        z.totalin += p - z.nextinindex;
                        z.nextinindex = p;
                        write = q;
                        return inflateFlush(z, r);
                    }
                    codes.init(bl[0], bd[0], hufts, tl[0], hufts, td[0], z);
                }
                mode = CODES;
                break;
                case CODES:
                    bitb = b;
                    bitk = k;
                    z.availin = n;
                    z.totalin += p - z.nextinindex;
                    z.nextinindex = p;
                    write = q;
                    if ((r = codes.proc(this, z, r)) != Z_STREAM_END) {
                        return inflateFlush(z, r);
                    }
                    r = Z_OK;
                    codes.free(z);
                    p = z.nextinindex;
                    n = z.availin;
                    b = bitb;
                    k = bitk;
                    q = write;
                    m = q < read ? read - q - 1 : end - q;
                    if (last == 0) {
                        mode = TYPE;
                        break;
                    }
                    mode = DRY;
                    break;
                case DRY:
                    write = q;
                    r = inflateFlush(z, r);
                    q = write;
                    m = q < read ? read - q - 1 : end - q;
                    if (read != write) {
                        bitb = b;
                        bitk = k;
                        z.availin = n;
                        z.totalin += p - z.nextinindex;
                        z.nextinindex = p;
                        write = q;
                        return inflateFlush(z, r);
                    }
                    mode = DONE;
                    break;
                case DONE:
                    r = Z_STREAM_END;
                    bitb = b;
                    bitk = k;
                    z.availin = n;
                    z.totalin += p - z.nextinindex;
                    z.nextinindex = p;
                    write = q;
                    return inflateFlush(z, r);
                case BAD:
                    r = Z_DATA_ERROR;
                    bitb = b;
                    bitk = k;
                    z.availin = n;
                    z.totalin += p - z.nextinindex;
                    z.nextinindex = p;
                    write = q;
                    return inflateFlush(z, r);
                default:
                    r = Z_STREAM_ERROR;
                    bitb = b;
                    bitk = k;
                    z.availin = n;
                    z.totalin += p - z.nextinindex;
                    z.nextinindex = p;
                    write = q;
                    return inflateFlush(z, r);
            }
        }
    }

    protected void free(ZStream z) {
        reset(z, null);
        window = null;
        hufts = null;
        //ZFREE(z, s);
    }

    protected void setDictionary(byte[] d, int start, int n) {
        System.arraycopy(d, start, window, 0, n);
        read = write = n;
    }

    // Returns true if inflate is currently at the end of a block generated
    // by Z_SYNC_FLUSH or Z_FULL_FLUSH.
    protected int syncPoint() {
        return mode == LENS ? 1 : 0;
    }

    // copy as much as possible from the sliding window to the output area
    protected int inflateFlush(ZStream z, int r) {
        int n;
        int p;
        int q;

        // local copies of source and destination pointers
        p = z.nextoutindex;
        q = read;

        // compute number of bytes to copy as far as end of window
        n = (q <= write ? write : end) - q;
        if (n > z.availout) {
            n = z.availout;
        }
        if (n != 0 && r == Z_BUF_ERROR) {
            r = Z_OK;
        }

        // update counters
        z.availout -= n;
        z.totalout += n;

        // update check information
        if (checkfn != null) {
            z.adler = check = Adler32.adler32(check, window, q, n);
        }

        // copy as far as end of window
        System.arraycopy(window, q, z.nextout, p, n);
        p += n;
        q += n;

        // see if more to copy at beginning of window
        if (q == end) {
            // wrap pointers
            q = 0;
            if (write == end) {
                write = 0;
            }

            // compute bytes to copy
            n = write - q;
            if (n > z.availout) {
                n = z.availout;
            }
            if (n != 0 && r == Z_BUF_ERROR) {
                r = Z_OK;
            }

            // update counters
            z.availout -= n;
            z.totalout += n;

            // update check information
            if (checkfn != null) {
                z.adler = check = Adler32.adler32(check, window, q, n);
            }

            // copy
            System.arraycopy(window, q, z.nextout, p, n);
            p += n;
            q += n;
        }

        // update pointers
        z.nextoutindex = p;
        read = q;

        // done
        return r;
    }
}
