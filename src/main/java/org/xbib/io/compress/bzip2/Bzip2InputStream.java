
package org.xbib.io.compress.bzip2;

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream that decompresses from the BZip2 format (without the file
 * header chars) to be read as any other stream.
 */
public class Bzip2InputStream extends InputStream implements Bzip2Constants {

    private void makeMaps() {
        final boolean[] inUse = this.data.inUse;
        final byte[] seqToUnseq = this.data.seqToUnseq;

        int nInUseShadow = 0;

        for (int i = 0; i < 256; i++) {
            if (inUse[i]) {
                seqToUnseq[nInUseShadow++] = (byte) i;
            }
        }

        this.nInUse = nInUseShadow;
    }

    /**
     * Index of the last char in the block, so the block size == last + 1.
     */
    private int last;
    /**
     * Index in zptr[] of original string after sorting.
     */
    private int origPtr;
    /**
     * always: in the range 0 .. 9.
     * The current block size is 100000 * this number.
     */
    private int blockSize100k;
    private boolean blockRandomised;
    private int bsBuff;
    private int bsLive;
    private final CRC crc = new CRC();
    private int nInUse;
    private InputStream in;
    private int currentChar = -1;
    private static final int EOF = 0;
    private static final int START_BLOCK_STATE = 1;
    private static final int RAND_PART_A_STATE = 2;
    private static final int RAND_PART_B_STATE = 3;
    private static final int RAND_PART_C_STATE = 4;
    private static final int NO_RAND_PART_A_STATE = 5;
    private static final int NO_RAND_PART_B_STATE = 6;
    private static final int NO_RAND_PART_C_STATE = 7;
    private int currentState = START_BLOCK_STATE;
    private int storedBlockCRC, storedCombinedCRC;
    private int computedBlockCRC, computedCombinedCRC;

    // Variables used by setup* methods exclusively
    private int setupcount;
    private int setupch2;
    private int setupchPrev;
    private int setupi2;
    private int setupj2;
    private int setuprNToGo;
    private int setuprTPos;
    private int setuptPos;
    private char setupz;
    /**
     * All memory intensive stuff.
     * This field is initialized by initBlock().
     */
    private Data data;

    /**
     * Constructs a new CBZip2InputStream which decompresses bytes read from
     * the specified stream.
     * <p/>
     * <p>Although BZip2 headers are marked with the magic
     * <tt>"Bz"</tt> this constructor expects the next byte in the
     * stream to be the first one after the magic.  Thus callers have
     * to skip the first two bytes. Otherwise this constructor will
     * throw an exception. </p>
     *
     * @throws java.io.IOException  if the stream content is malformed or an I/O error occurs.
     * @throws NullPointerException if <tt>in == null</tt>
     */
    public Bzip2InputStream(final InputStream in) throws IOException {
        super();
        this.in = in;
        init();
    }

    public Bzip2InputStream(final InputStream in, int bufsize) throws IOException {
        super();
        this.in = in;
        init();
    }

    @Override
    public final int read() throws IOException {
        if (this.in != null) {
            return read0();
        } else {
            throw new IOException("stream closed");
        }
    }

    @Override
    public final int read(final byte[] dest, final int offs, final int len)
            throws IOException {
        if (offs < 0) {
            throw new IndexOutOfBoundsException("offs(" + offs + ") < 0.");
        }
        if (len < 0) {
            throw new IndexOutOfBoundsException("len(" + len + ") < 0.");
        }
        if (offs + len > dest.length) {
            throw new IndexOutOfBoundsException("offs(" + offs + ") + len(" + len + ") > dest.length(" + dest.length + ").");
        }
        if (this.in == null) {
            throw new IOException("stream closed");
        }

        final int hi = offs + len;
        int destOffs = offs;
        for (int b; (destOffs < hi) && ((b = read0()) >= 0); ) {
            dest[destOffs++] = (byte) b;
        }

        return (destOffs == offs) ? -1 : (destOffs - offs);
    }

    private int read0() throws IOException {
        final int retChar = this.currentChar;

        switch (this.currentState) {
            case EOF:
                return -1;

            case START_BLOCK_STATE:
                throw new IllegalStateException();

            case RAND_PART_A_STATE:
                throw new IllegalStateException();

            case RAND_PART_B_STATE:
                setupRandPartB();
                break;

            case RAND_PART_C_STATE:
                setupRandPartC();
                break;

            case NO_RAND_PART_A_STATE:
                throw new IllegalStateException();

            case NO_RAND_PART_B_STATE:
                setupNoRandPartB();
                break;

            case NO_RAND_PART_C_STATE:
                setupNoRandPartC();
                break;

            default:
                throw new IllegalStateException();
        }

        return retChar;
    }

    private void init() throws IOException {
        if (null == in) {
            throw new IOException("no input stream");
        }
        if (in.available() == 0) {
            throw new IOException("empty input stream");
        }
        // skip "BZ" marker
        in.read();
        in.read();
        int magic2 = this.in.read();
        if (magic2 != 'h') {
            throw new IOException("stream is not bzip2: expected 'h'" + " as first byte but got '" + (char) magic2 + "'");
        }

        int blockSize = this.in.read();
        if ((blockSize < '1') || (blockSize > '9')) {
            throw new IOException("stream is not bzip2: illegal " + "blocksize " + (char) blockSize);
        }

        this.blockSize100k = blockSize - '0';

        initBlock();
        setupBlock();
    }

    private void initBlock() throws IOException {
        char magic0 = bsGetUByte();
        char magic1 = bsGetUByte();
        char magic2 = bsGetUByte();
        char magic3 = bsGetUByte();
        char magic4 = bsGetUByte();
        char magic5 = bsGetUByte();

        if (magic0 == 0x17 &&
                magic1 == 0x72 &&
                magic2 == 0x45 &&
                magic3 == 0x38 &&
                magic4 == 0x50 &&
                magic5 == 0x90) {
            complete(); // end of file
        } else if (magic0 != 0x31 || // '1'
                magic1 != 0x41 || // ')'
                magic2 != 0x59 || // 'Y'
                magic3 != 0x26 || // '&'
                magic4 != 0x53 || // 'S'
                magic5 != 0x59 // 'Y'
                ) {
            this.currentState = EOF;
            throw new IOException("bad block header");
        } else {
            this.storedBlockCRC = bsGetInt();
            this.blockRandomised = bsR(1) == 1;

            /**
             * Allocate data here instead in constructor, so we do not
             * allocate it if the input file is empty.
             */
            if (this.data == null) {
                this.data = new Data(this.blockSize100k);
            }

            // currBlockNo++;
            getAndMoveToFrontDecode();

            this.crc.initialiseCRC();
            this.currentState = START_BLOCK_STATE;
        }
    }

    private void endBlock() throws IOException {
        this.computedBlockCRC = this.crc.getFinalCRC();

        // A bad CRC is considered a fatal error.
        if (this.storedBlockCRC != this.computedBlockCRC) {
            // make next blocks readable without error
            // (repair feature, not yet documented, not tested)
            this.computedCombinedCRC = (this.storedCombinedCRC << 1) | (this.storedCombinedCRC >>> 31);
            this.computedCombinedCRC ^= this.storedBlockCRC;

            throw new IOException("CRC error");
        }

        this.computedCombinedCRC = (this.computedCombinedCRC << 1) | (this.computedCombinedCRC >>> 31);
        this.computedCombinedCRC ^= this.computedBlockCRC;
    }

    private void complete() throws IOException {
        this.storedCombinedCRC = bsGetInt();
        this.currentState = EOF;
        this.data = null;

        if (this.storedCombinedCRC != this.computedCombinedCRC) {
            throw new IOException("CRC error");
        }
    }

    @Override
    public final void close() throws IOException {
        InputStream inShadow = this.in;
        if (inShadow != null) {
            try {
                if (inShadow != System.in) {
                    inShadow.close();
                }
            } finally {
                this.data = null;
                this.in = null;
            }
        }
    }

    private int bsR(final int n) throws IOException {
        int bsLiveShadow = this.bsLive;
        int bsBuffShadow = this.bsBuff;

        if (bsLiveShadow < n) {
            final InputStream inShadow = this.in;
            do {
                int thech = inShadow.read();

                if (thech < 0) {
                    throw new IOException("unexpected end of stream");
                }

                bsBuffShadow = (bsBuffShadow << 8) | thech;
                bsLiveShadow += 8;
            } while (bsLiveShadow < n);

            this.bsBuff = bsBuffShadow;
        }

        this.bsLive = bsLiveShadow - n;
        return (bsBuffShadow >> (bsLiveShadow - n)) & ((1 << n) - 1);
    }

    private boolean bsGetBit() throws IOException {
        int bsLiveShadow = this.bsLive;
        int bsBuffShadow = this.bsBuff;

        if (bsLiveShadow < 1) {
            int thech = this.in.read();

            if (thech < 0) {
                throw new IOException("unexpected end of stream");
            }

            bsBuffShadow = (bsBuffShadow << 8) | thech;
            bsLiveShadow += 8;
            this.bsBuff = bsBuffShadow;
        }

        this.bsLive = bsLiveShadow - 1;
        return ((bsBuffShadow >> (bsLiveShadow - 1)) & 1) != 0;
    }

    private char bsGetUByte() throws IOException {
        return (char) bsR(8);
    }

    private int bsGetInt() throws IOException {
        return (((((bsR(8) << 8) | bsR(8)) << 8) | bsR(8)) << 8) | bsR(8);
    }

    /**
     * Called by createHuffmanDecodingTables() exclusively.
     */
    private static void hbCreateDecodeTables(final int[] limit,
                                             final int[] base,
                                             final int[] perm,
                                             final char[] length,
                                             final int minLen,
                                             final int maxLen,
                                             final int alphaSize) {
        for (int i = minLen, pp = 0; i <= maxLen; i++) {
            for (int j = 0; j < alphaSize; j++) {
                if (length[j] == i) {
                    perm[pp++] = j;
                }
            }
        }

        for (int i = MAX_CODE_LEN; --i > 0; ) {
            base[i] = 0;
            limit[i] = 0;
        }

        for (int i = 0; i < alphaSize; i++) {
            base[length[i] + 1]++;
        }

        for (int i = 1, b = base[0]; i < MAX_CODE_LEN; i++) {
            b += base[i];
            base[i] = b;
        }

        for (int i = minLen, vec = 0, b = base[i]; i <= maxLen; i++) {
            final int nb = base[i + 1];
            vec += nb - b;
            b = nb;
            limit[i] = vec - 1;
            vec <<= 1;
        }

        for (int i = minLen + 1; i <= maxLen; i++) {
            base[i] = ((limit[i - 1] + 1) << 1) - base[i];
        }
    }

    private void recvDecodingTables() throws IOException {
        final Data dataShadow = this.data;
        final boolean[] inUse = dataShadow.inUse;
        final byte[] pos = dataShadow.recvDecodingTables_pos;
        final byte[] selector = dataShadow.selector;
        final byte[] selectorMtf = dataShadow.selectorMtf;

        int inUse16 = 0;

        /* Receive the mapping table */
        for (int i = 0; i < 16; i++) {
            if (bsGetBit()) {
                inUse16 |= 1 << i;
            }
        }

        for (int i = 256; --i >= 0; ) {
            inUse[i] = false;
        }

        for (int i = 0; i < 16; i++) {
            if ((inUse16 & (1 << i)) != 0) {
                final int i16 = i << 4;
                for (int j = 0; j < 16; j++) {
                    if (bsGetBit()) {
                        inUse[i16 + j] = true;
                    }
                }
            }
        }

        makeMaps();
        final int alphaSize = this.nInUse + 2;

        /* Now the selectors */
        final int nGroups = bsR(3);
        final int nSelectors = bsR(15);

        for (int i = 0; i < nSelectors; i++) {
            int j = 0;
            while (bsGetBit()) {
                j++;
            }
            selectorMtf[i] = (byte) j;
        }

        /* Undo the MTF values for the selectors. */
        for (int v = nGroups; --v >= 0; ) {
            pos[v] = (byte) v;
        }

        for (int i = 0; i < nSelectors; i++) {
            int v = selectorMtf[i] & 0xff;
            final byte tmp = pos[v];
            while (v > 0) {
                // nearly all times v is zero, 4 in most other cases
                pos[v] = pos[v - 1];
                v--;
            }
            pos[0] = tmp;
            selector[i] = tmp;
        }

        final char[][] len = dataShadow.temp_charArray2d;

        /* Now the coding tables */
        for (int t = 0; t < nGroups; t++) {
            int curr = bsR(5);
            final char[] len_t = len[t];
            for (int i = 0; i < alphaSize; i++) {
                while (bsGetBit()) {
                    curr += bsGetBit() ? -1 : 1;
                }
                len_t[i] = (char) curr;
            }
        }

        // finally create the Huffman tables
        createHuffmanDecodingTables(alphaSize, nGroups);
    }

    /**
     * Called by recvDecodingTables() exclusively.
     */
    private void createHuffmanDecodingTables(final int alphaSize,
                                             final int nGroups) {
        final Data dataShadow = this.data;
        final char[][] len = dataShadow.temp_charArray2d;
        final int[] minLens = dataShadow.minLens;
        final int[][] limit = dataShadow.limit;
        final int[][] base = dataShadow.base;
        final int[][] perm = dataShadow.perm;

        for (int t = 0; t < nGroups; t++) {
            int minLen = 32;
            int maxLen = 0;
            final char[] len_t = len[t];
            for (int i = alphaSize; --i >= 0; ) {
                final char lent = len_t[i];
                if (lent > maxLen) {
                    maxLen = lent;
                }
                if (lent < minLen) {
                    minLen = lent;
                }
            }
            hbCreateDecodeTables(limit[t], base[t], perm[t], len[t], minLen,
                    maxLen, alphaSize);
            minLens[t] = minLen;
        }
    }

    private void getAndMoveToFrontDecode() throws IOException {
        this.origPtr = bsR(24);
        recvDecodingTables();

        final InputStream inShadow = this.in;
        final Data dataShadow = this.data;
        final byte[] ll8 = dataShadow.ll8;
        final int[] unzftab = dataShadow.unzftab;
        final byte[] selector = dataShadow.selector;
        final byte[] seqToUnseq = dataShadow.seqToUnseq;
        final char[] yy = dataShadow.getAndMoveToFrontDecodeyy;
        final int[] minLens = dataShadow.minLens;
        final int[][] limit = dataShadow.limit;
        final int[][] base = dataShadow.base;
        final int[][] perm = dataShadow.perm;
        final int limitLast = this.blockSize100k * 100000;

        /*
        Setting up the unzftab entries here is not strictly
        necessary, but it does save having to do it later
        in a separate pass, and so saves a block's worth of
        cache misses.
         */
        for (int i = 256; --i >= 0; ) {
            yy[i] = (char) i;
            unzftab[i] = 0;
        }

        int groupNo = 0;
        int groupPos = G_SIZE - 1;
        final int eob = this.nInUse + 1;
        int nextSym = getAndMoveToFrontDecode0(0);
        int bsBuffShadow = this.bsBuff;
        int bsLiveShadow = this.bsLive;
        int lastShadow = -1;
        int zt = selector[groupNo] & 0xff;
        int[] basezt = base[zt];
        int[] limitzt = limit[zt];
        int[] permzt = perm[zt];
        int minLenszt = minLens[zt];

        while (nextSym != eob) {
            if ((nextSym == RUNA) || (nextSym == RUNB)) {
                int s = -1;

                for (int n = 1; true; n <<= 1) {
                    if (nextSym == RUNA) {
                        s += n;
                    } else if (nextSym == RUNB) {
                        s += n << 1;
                    } else {
                        break;
                    }

                    if (groupPos == 0) {
                        groupPos = G_SIZE - 1;
                        zt = selector[++groupNo] & 0xff;
                        basezt = base[zt];
                        limitzt = limit[zt];
                        permzt = perm[zt];
                        minLenszt = minLens[zt];
                    } else {
                        groupPos--;
                    }

                    int zn = minLenszt;

                    // Inlined:
                    // int zvec = bsR(zn);
                    while (bsLiveShadow < zn) {
                        final int thech = inShadow.read();
                        if (thech >= 0) {
                            bsBuffShadow = (bsBuffShadow << 8) | thech;
                            bsLiveShadow += 8;
                            continue;
                        } else {
                            throw new IOException("unexpected end of stream");
                        }
                    }
                    int zvec = (bsBuffShadow >> (bsLiveShadow - zn)) & ((1 << zn) - 1);
                    bsLiveShadow -= zn;

                    while (zvec > limitzt[zn]) {
                        zn++;
                        while (bsLiveShadow < 1) {
                            final int thech = inShadow.read();
                            if (thech >= 0) {
                                bsBuffShadow = (bsBuffShadow << 8) | thech;
                                bsLiveShadow += 8;
                                continue;
                            } else {
                                throw new IOException("unexpected end of stream");
                            }
                        }
                        bsLiveShadow--;
                        zvec = (zvec << 1) | ((bsBuffShadow >> bsLiveShadow) & 1);
                    }
                    nextSym = permzt[zvec - basezt[zn]];
                }

                final byte ch = seqToUnseq[yy[0]];
                unzftab[ch & 0xff] += s + 1;

                while (s-- >= 0) {
                    ll8[++lastShadow] = ch;
                }

                if (lastShadow >= limitLast) {
                    throw new IOException("block overrun");
                }
            } else {
                if (++lastShadow >= limitLast) {
                    throw new IOException("block overrun");
                }

                final char tmp = yy[nextSym - 1];
                unzftab[seqToUnseq[tmp] & 0xff]++;
                ll8[lastShadow] = seqToUnseq[tmp];

                /*
                This loop is hammered during decompression,
                hence avoid native method call overhead of
                System.arraycopy for very small ranges to copy.
                 */
                if (nextSym <= 16) {
                    for (int j = nextSym - 1; j > 0; ) {
                        yy[j] = yy[--j];
                    }
                } else {
                    System.arraycopy(yy, 0, yy, 1, nextSym - 1);
                }

                yy[0] = tmp;

                if (groupPos == 0) {
                    groupPos = G_SIZE - 1;
                    zt = selector[++groupNo] & 0xff;
                    basezt = base[zt];
                    limitzt = limit[zt];
                    permzt = perm[zt];
                    minLenszt = minLens[zt];
                } else {
                    groupPos--;
                }

                int zn = minLenszt;

                // Inlined:
                // int zvec = bsR(zn);
                while (bsLiveShadow < zn) {
                    final int thech = inShadow.read();
                    if (thech >= 0) {
                        bsBuffShadow = (bsBuffShadow << 8) | thech;
                        bsLiveShadow += 8;
                        continue;
                    } else {
                        throw new IOException("unexpected end of stream");
                    }
                }
                int zvec = (bsBuffShadow >> (bsLiveShadow - zn)) & ((1 << zn) - 1);
                bsLiveShadow -= zn;

                while (zvec > limitzt[zn]) {
                    zn++;
                    while (bsLiveShadow < 1) {
                        final int thech = inShadow.read();
                        if (thech >= 0) {
                            bsBuffShadow = (bsBuffShadow << 8) | thech;
                            bsLiveShadow += 8;
                            continue;
                        } else {
                            throw new IOException("unexpected end of stream");
                        }
                    }
                    bsLiveShadow--;
                    zvec = (zvec << 1) | ((bsBuffShadow >> bsLiveShadow) & 1);
                }
                nextSym = permzt[zvec - basezt[zn]];
            }
        }

        this.last = lastShadow;
        this.bsLive = bsLiveShadow;
        this.bsBuff = bsBuffShadow;
    }

    private int getAndMoveToFrontDecode0(final int groupNo)
            throws IOException {
        final InputStream inShadow = this.in;
        final Data dataShadow = this.data;
        final int zt = dataShadow.selector[groupNo] & 0xff;
        final int[] limitzt = dataShadow.limit[zt];
        int zn = dataShadow.minLens[zt];
        int zvec = bsR(zn);
        int bsLiveShadow = this.bsLive;
        int bsBuffShadow = this.bsBuff;

        while (zvec > limitzt[zn]) {
            zn++;
            while (bsLiveShadow < 1) {
                final int thech = inShadow.read();

                if (thech >= 0) {
                    bsBuffShadow = (bsBuffShadow << 8) | thech;
                    bsLiveShadow += 8;
                    continue;
                } else {
                    throw new IOException("unexpected end of stream");
                }
            }
            bsLiveShadow--;
            zvec = (zvec << 1) | ((bsBuffShadow >> bsLiveShadow) & 1);
        }

        this.bsLive = bsLiveShadow;
        this.bsBuff = bsBuffShadow;

        return dataShadow.perm[zt][zvec - dataShadow.base[zt][zn]];
    }

    private void setupBlock() throws IOException {
        if (this.data == null) {
            return;
        }

        final int[] cftab = this.data.cftab;
        final int[] tt = this.data.initTT(this.last + 1);
        final byte[] ll8 = this.data.ll8;
        cftab[0] = 0;
        System.arraycopy(this.data.unzftab, 0, cftab, 1, 256);

        for (int i = 1, c = cftab[0]; i <= 256; i++) {
            c += cftab[i];
            cftab[i] = c;
        }

        for (int i = 0, lastShadow = this.last; i <= lastShadow; i++) {
            tt[cftab[ll8[i] & 0xff]++] = i;
        }

        if ((this.origPtr < 0) || (this.origPtr >= tt.length)) {
            throw new IOException("stream corrupted");
        }

        this.setuptPos = tt[this.origPtr];
        this.setupcount = 0;
        this.setupi2 = 0;
        this.setupch2 = 256;   /* not a char and not EOF */

        if (this.blockRandomised) {
            this.setuprNToGo = 0;
            this.setuprTPos = 0;
            setupRandPartA();
        } else {
            setupNoRandPartA();
        }
    }

    private void setupRandPartA() throws IOException {
        if (this.setupi2 <= this.last) {
            this.setupchPrev = this.setupch2;
            int setupch2Shadow = this.data.ll8[this.setuptPos] & 0xff;
            this.setuptPos = this.data.tt[this.setuptPos];
            if (this.setuprNToGo == 0) {
                this.setuprNToGo = NUMS[this.setuprTPos] - 1;
                if (++this.setuprTPos == 512) {
                    this.setuprTPos = 0;
                }
            } else {
                this.setuprNToGo--;
            }
            this.setupch2 = setupch2Shadow ^= (this.setuprNToGo == 1) ? 1 : 0;
            this.setupi2++;
            this.currentChar = setupch2Shadow;
            this.currentState = RAND_PART_B_STATE;
            this.crc.updateCRC(setupch2Shadow);
        } else {
            endBlock();
            initBlock();
            setupBlock();
        }
    }

    private void setupNoRandPartA() throws IOException {
        if (this.setupi2 <= this.last) {
            this.setupchPrev = this.setupch2;
            int setupch2Shadow = this.data.ll8[this.setuptPos] & 0xff;
            this.setupch2 = setupch2Shadow;
            this.setuptPos = this.data.tt[this.setuptPos];
            this.setupi2++;
            this.currentChar = setupch2Shadow;
            this.currentState = NO_RAND_PART_B_STATE;
            this.crc.updateCRC(setupch2Shadow);
        } else {
            this.currentState = NO_RAND_PART_A_STATE;
            endBlock();
            initBlock();
            setupBlock();
        }
    }

    private void setupRandPartB() throws IOException {
        if (this.setupch2 != this.setupchPrev) {
            this.currentState = RAND_PART_A_STATE;
            this.setupcount = 1;
            setupRandPartA();
        } else if (++this.setupcount >= 4) {
            this.setupz = (char) (this.data.ll8[this.setuptPos] & 0xff);
            this.setuptPos = this.data.tt[this.setuptPos];
            if (this.setuprNToGo == 0) {
                this.setuprNToGo = NUMS[this.setuprTPos] - 1;
                if (++this.setuprTPos == 512) {
                    this.setuprTPos = 0;
                }
            } else {
                this.setuprNToGo--;
            }
            this.setupj2 = 0;
            this.currentState = RAND_PART_C_STATE;
            if (this.setuprNToGo == 1) {
                this.setupz ^= 1;
            }
            setupRandPartC();
        } else {
            this.currentState = RAND_PART_A_STATE;
            setupRandPartA();
        }
    }

    private void setupRandPartC() throws IOException {
        if (this.setupj2 < this.setupz) {
            this.currentChar = this.setupch2;
            this.crc.updateCRC(this.setupch2);
            this.setupj2++;
        } else {
            this.currentState = RAND_PART_A_STATE;
            this.setupi2++;
            this.setupcount = 0;
            setupRandPartA();
        }
    }

    private void setupNoRandPartB() throws IOException {
        if (this.setupch2 != this.setupchPrev) {
            this.setupcount = 1;
            setupNoRandPartA();
        } else if (++this.setupcount >= 4) {
            this.setupz = (char) (this.data.ll8[this.setuptPos] & 0xff);
            this.setuptPos = this.data.tt[this.setuptPos];
            this.setupj2 = 0;
            setupNoRandPartC();
        } else {
            setupNoRandPartA();
        }
    }

    private void setupNoRandPartC() throws IOException {
        if (this.setupj2 < this.setupz) {
            int setupch2Shadow = this.setupch2;
            this.currentChar = setupch2Shadow;
            this.crc.updateCRC(setupch2Shadow);
            this.setupj2++;
            this.currentState = NO_RAND_PART_C_STATE;
        } else {
            this.setupi2++;
            this.setupcount = 0;
            setupNoRandPartA();
        }
    }

    private static final class Data extends Object {

        // (with blockSize 900k)
        private final boolean[] inUse = new boolean[256];                                   //      256 byte
        private final byte[] seqToUnseq = new byte[256];                                    //      256 byte
        private final byte[] selector = new byte[MAX_SELECTORS];                          //    18002 byte
        private final byte[] selectorMtf = new byte[MAX_SELECTORS];                          //    18002 byte
        /**
         * Freq table collected to save a pass over the data during
         * decompression.
         */
        private final int[] unzftab = new int[256];                                           //     1024 byte
        private final int[][] limit = new int[N_GROUPS][MAX_ALPHA_SIZE];                      //     6192 byte
        private final int[][] base = new int[N_GROUPS][MAX_ALPHA_SIZE];                      //     6192 byte
        private final int[][] perm = new int[N_GROUPS][MAX_ALPHA_SIZE];                      //     6192 byte
        private final int[] minLens = new int[N_GROUPS];                                      //       24 byte
        private final int[] cftab = new int[257];                                     //     1028 byte
        private final char[] getAndMoveToFrontDecodeyy = new char[256];                   //      512 byte
        private final char[][] temp_charArray2d = new char[N_GROUPS][MAX_ALPHA_SIZE];       //     3096 byte
        private final byte[] recvDecodingTables_pos = new byte[N_GROUPS];                     //        6 byte
        //---------------
        //    60798 byte
        private int[] tt;                                                                     //  3600000 byte
        private byte[] ll8;                                                                   //   900000 byte
        //---------------
        //  4560782 byte
        //===============

        Data(int blockSize100k) {
            super();

            this.ll8 = new byte[blockSize100k * BASE_BLOCK_SIZE];
        }

        /**
         * Initializes the {@link #tt} array.
         * <p/>
         * This method is called when the required length of the array
         * is known.  I don't initialize it at construction time to
         * avoid unneccessary memory allocation when compressing small
         * files.
         */
        int[] initTT(int length) {
            int[] ttShadow = this.tt;

            // tt.length should always be >= length, but theoretically
            // it can happen, if the compressor mixed small and large
            // blocks.  Normally only the last block will be smaller
            // than others.
            if ((ttShadow == null) || (ttShadow.length < length)) {
                this.tt = ttShadow = new int[length];
            }

            return ttShadow;
        }
    }
}
