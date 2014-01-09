
package org.xbib.io.compress.bzip2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * An input stream that decompresses from the BZip2 format (without the file
 * header chars) to be read as any other stream.
 */
public class BZip2Inflate implements Bzip2Constants {

    private ByteArrayOutputStream inputBuffer;
    private boolean readHeader = false;

    /*
    index of the last char in the block, so
    the block size == last + 1.
     */
    private int last;

    /*
    index in zptr[] of original string after sorting.
     */
    private int origPtr;

    /*
    always: in the range 0 .. 9.
    The current block size is 100000 * this number.
     */
    private int blockSize100k;
    private boolean blockRandomised;
    private int bsBuff;
    private int bsLive;
    private CRC mCrc = new CRC();
    private boolean[] inUse = new boolean[256];
    private int nInUse;
    private char[] seqToUnseq = new char[256];
    private char[] selector = new char[MAX_SELECTORS];
    private char[] selectorMtf = new char[MAX_SELECTORS];
    private int[] tt;
    private char[] ll8;

    /*
    freq table collected to save a pass over the data
    during decompression.
     */
    private int[] unzftab = new int[256];
    private int[][] limit = new int[N_GROUPS][MAX_ALPHA_SIZE];
    private int[][] base = new int[N_GROUPS][MAX_ALPHA_SIZE];
    private int[][] perm = new int[N_GROUPS][MAX_ALPHA_SIZE];
    private int[] minLens = new int[N_GROUPS];
    private ByteArrayInputStream bsStream;
    private boolean streamEnd = false;
    private int currentChar = -1;
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
    private int i2, count, chPrev, ch2;
    private int tPos;
    private int rNToGo = 0;
    private int rTPos = 0;
    private int j2;
    private char z;

    public BZip2Inflate() {
        this(8192);
    }

    public BZip2Inflate(int bufsize) {
        ll8 = null;
        tt = null;
        this.inputBuffer = new ByteArrayOutputStream(bufsize);
        bsSetStream();
    }

    private void makeMaps() {
        nInUse = 0;
        for (int j = 0; j < 256; j++) {
            if (inUse[j]) {
                seqToUnseq[nInUse] = (char) j;
                nInUse++;
            }
        }
    }

    private void initStream() throws IOException {
        initialize();
        initBlock();
        setupBlock();
    }

    private int read() throws IOException {
        if (streamEnd) {
            return -1;
        } else {
            int retChar = currentChar;
            switch (currentState) {
                case START_BLOCK_STATE:
                    break;
                case RAND_PART_A_STATE:
                    break;
                case RAND_PART_B_STATE:
                    setupRandPartB();
                    break;
                case RAND_PART_C_STATE:
                    setupRandPartC();
                    break;
                case NO_RAND_PART_A_STATE:
                    break;
                case NO_RAND_PART_B_STATE:
                    setupNoRandPartB();
                    break;
                case NO_RAND_PART_C_STATE:
                    setupNoRandPartC();
                    break;
                default:
                    break;
            }
            return retChar;
        }
    }

    private void initialize() throws IOException {
        char magic1, magic2;
        char magic3, magic4;
        magic1 = bsGetUChar();
        magic2 = bsGetUChar();
        magic3 = bsGetUChar();
        magic4 = bsGetUChar();
        if (magic1 != 'B' || magic2 != 'Z' || magic3 != 'h' || magic4 < '1' || magic4 > '9') {
            bsFinishedWithStream();
            streamEnd = true;
            return;
        }

        setDecompressStructureSizes(magic4 - '0');
        computedCombinedCRC = 0;
    }

    private void initBlock() throws IOException {
        char magic1, magic2, magic3, magic4;
        char magic5, magic6;
        magic1 = bsGetUChar();
        magic2 = bsGetUChar();
        magic3 = bsGetUChar();
        magic4 = bsGetUChar();
        magic5 = bsGetUChar();
        magic6 = bsGetUChar();
        if (magic1 == 0x17 && magic2 == 0x72 && magic3 == 0x45 && magic4 == 0x38 && magic5 == 0x50 && magic6 == 0x90) {
            complete();
            return;
        }

        if (magic1 != 0x31 || magic2 != 0x41 || magic3 != 0x59 || magic4 != 0x26 || magic5 != 0x53 || magic6 != 0x59) {
            throw new IOException("bad block header");
        }

        storedBlockCRC = bsGetInt32();

        if (bsR(1) == 1) {
            blockRandomised = true;
        } else {
            blockRandomised = false;
        }

        //        currBlockNo++;
        getAndMoveToFrontDecode();

        mCrc.initialiseCRC();
        currentState = START_BLOCK_STATE;
    }

    private void endBlock() throws IOException {
        computedBlockCRC = mCrc.getFinalCRC();
        /* A bad CRC is considered a fatal error. */
        if (storedBlockCRC != computedBlockCRC) {
            throw new IOException("CRC error");
        }

        computedCombinedCRC = (computedCombinedCRC << 1) | (computedCombinedCRC >>> 31);
        computedCombinedCRC ^= computedBlockCRC;
    }

    private void complete() throws IOException {
        storedCombinedCRC = bsGetInt32();
        if (storedCombinedCRC != computedCombinedCRC) {
            throw new IOException("CRC error");
        }

        bsFinishedWithStream();
        streamEnd = true;
    }

    private void bsFinishedWithStream() {
        try {
            if (this.bsStream != null && this.bsStream != System.in) {
                this.bsStream.close();
                this.bsStream = null;

            }
        } catch (IOException ioe) {
            //ignore
        }
    }

    private void bsSetStream() {
        bsStream = null;
        bsLive = 0;
        bsBuff = 0;
    }

    private int bsR(int n) throws IOException {
        int v;
        while (bsLive < n) {
            int zzi;
            char thech = 0;

            thech = (char) bsStream.read();

            if (thech == -1) {
                throw new IOException("unexpected end of file");
            }
            zzi = thech;
            bsBuff = (bsBuff << 8) | (zzi & 0xff);
            bsLive += 8;
        }

        v = (bsBuff >> (bsLive - n)) & ((1 << n) - 1);
        bsLive -= n;
        return v;
    }

    private char bsGetUChar() throws IOException {
        return (char) bsR(8);
    }

    private int bsGetint() throws IOException {
        int u = 0;
        u = (u << 8) | bsR(8);
        u = (u << 8) | bsR(8);
        u = (u << 8) | bsR(8);
        u = (u << 8) | bsR(8);
        return u;
    }

    private int bsGetIntVS(int numBits) throws IOException {
        return bsR(numBits);
    }

    private int bsGetInt32() throws IOException {
        return bsGetint();
    }

    private void hbCreateDecodeTables(int[] limit, int[] base,
                                      int[] perm, char[] length,
                                      int minLen, int maxLen, int alphaSize) {
        int pp, i, j, vec;

        pp = 0;
        for (i = minLen; i <= maxLen; i++) {
            for (j = 0; j < alphaSize; j++) {
                if (length[j] == i) {
                    perm[pp] = j;
                    pp++;
                }
            }
        }

        for (i = 0; i < MAX_CODE_LEN; i++) {
            base[i] = 0;
        }
        for (i = 0; i < alphaSize; i++) {
            base[length[i] + 1]++;
        }

        for (i = 1; i < MAX_CODE_LEN; i++) {
            base[i] += base[i - 1];
        }

        for (i = 0; i < MAX_CODE_LEN; i++) {
            limit[i] = 0;
        }
        vec = 0;

        for (i = minLen; i <= maxLen; i++) {
            vec += (base[i + 1] - base[i]);
            limit[i] = vec - 1;
            vec <<= 1;
        }
        for (i = minLen + 1; i <= maxLen; i++) {
            base[i] = ((limit[i - 1] + 1) << 1) - base[i];
        }
    }

    private void recvDecodingTables() throws IOException {
        char len[][] = new char[N_GROUPS][MAX_ALPHA_SIZE];
        int i, j, t, nGroups, nSelectors, alphaSize;
        int minLen, maxLen;
        boolean[] inUse16 = new boolean[16];

        /* Receive the mapping table */
        for (i = 0; i < 16; i++) {
            if (bsR(1) == 1) {
                inUse16[i] = true;
            } else {
                inUse16[i] = false;
            }
        }

        for (i = 0; i < 256; i++) {
            inUse[i] = false;
        }

        for (i = 0; i < 16; i++) {
            if (inUse16[i]) {
                for (j = 0; j < 16; j++) {
                    if (bsR(1) == 1) {
                        inUse[i * 16 + j] = true;
                    }
                }
            }
        }

        makeMaps();
        alphaSize = nInUse + 2;

        /* Now the selectors */
        nGroups = bsR(3);
        nSelectors = bsR(15);
        for (i = 0; i < nSelectors; i++) {
            j = 0;
            while (bsR(1) == 1) {
                j++;
            }
            selectorMtf[i] = (char) j;
        }

        /* Undo the MTF values for the selectors. */
        {
            char[] pos = new char[N_GROUPS];
            char tmp, v;
            for (v = 0; v < nGroups; v++) {
                pos[v] = v;
            }

            for (i = 0; i < nSelectors; i++) {
                v = selectorMtf[i];
                tmp = pos[v];
                while (v > 0) {
                    pos[v] = pos[v - 1];
                    v--;
                }
                pos[0] = tmp;
                selector[i] = tmp;
            }
        }

        /* Now the coding tables */
        for (t = 0; t < nGroups; t++) {
            int curr = bsR(5);
            for (i = 0; i < alphaSize; i++) {
                while (bsR(1) == 1) {
                    if (bsR(1) == 0) {
                        curr++;
                    } else {
                        curr--;
                    }
                }
                len[t][i] = (char) curr;
            }
        }

        /* Create the Huffman decoding tables */
        for (t = 0; t < nGroups; t++) {
            minLen = 32;
            maxLen = 0;
            for (i = 0; i < alphaSize; i++) {
                if (len[t][i] > maxLen) {
                    maxLen = len[t][i];
                }
                if (len[t][i] < minLen) {
                    minLen = len[t][i];
                }
            }
            hbCreateDecodeTables(limit[t], base[t], perm[t], len[t], minLen,
                    maxLen, alphaSize);
            minLens[t] = minLen;
        }
    }

    private void getAndMoveToFrontDecode() throws IOException {
        char[] yy = new char[256];
        int i, j, nextSym, limitLast;
        int EOB, groupNo, groupPos;

        limitLast = BASE_BLOCK_SIZE * blockSize100k;
        origPtr = bsGetIntVS(24);

        recvDecodingTables();
        EOB = nInUse + 1;
        groupNo = -1;
        groupPos = 0;

        /*
        Setting up the unzftab entries here is not strictly
        necessary, but it does save having to do it later
        in a separate pass, and so saves a block's worth of
        cache misses.
         */
        for (i = 0; i <= 255; i++) {
            unzftab[i] = 0;
        }

        for (i = 0; i <= 255; i++) {
            yy[i] = (char) i;
        }

        last = -1;

        {
            int zt, zn, zvec, zj;
            if (groupPos == 0) {
                groupNo++;
                groupPos = G_SIZE;
            }
            groupPos--;
            zt = selector[groupNo];
            zn = minLens[zt];
            zvec = bsR(zn);
            while (zvec > limit[zt][zn]) {
                zn++;
                {
                    {
                        while (bsLive < 1) {
                            int zzi;
                            char thech = 0;

                            thech = (char) bsStream.read();

                            if (thech == -1) {
                                throw new IOException("unexpected end of file");
                            }
                            zzi = thech;
                            bsBuff = (bsBuff << 8) | (zzi & 0xff);
                            bsLive += 8;
                        }
                    }
                    zj = (bsBuff >> (bsLive - 1)) & 1;
                    bsLive--;
                }
                zvec = (zvec << 1) | zj;
            }
            nextSym = perm[zt][zvec - base[zt][zn]];
        }

        while (true) {

            if (nextSym == EOB) {
                break;
            }

            if (nextSym == RUNA || nextSym == RUNB) {
                char ch;
                int s = -1;
                int N = 1;
                do {
                    if (nextSym == RUNA) {
                        s = s + (0 + 1) * N;
                    } else if (nextSym == RUNB) {
                        s = s + (1 + 1) * N;
                    }
                    N = N * 2;
                    {
                        int zt, zn, zvec, zj;
                        if (groupPos == 0) {
                            groupNo++;
                            groupPos = G_SIZE;
                        }
                        groupPos--;
                        zt = selector[groupNo];
                        zn = minLens[zt];
                        zvec = bsR(zn);
                        while (zvec > limit[zt][zn]) {
                            zn++;
                            {
                                {
                                    while (bsLive < 1) {
                                        int zzi;
                                        char thech = 0;

                                        thech = (char) bsStream.read();

                                        if (thech == -1) {
                                            throw new IOException("unexpected end of file");
                                        }
                                        zzi = thech;
                                        bsBuff = (bsBuff << 8) | (zzi & 0xff);
                                        bsLive += 8;
                                    }
                                }
                                zj = (bsBuff >> (bsLive - 1)) & 1;
                                bsLive--;
                            }
                            zvec = (zvec << 1) | zj;
                        }
                        nextSym = perm[zt][zvec - base[zt][zn]];
                    }
                } while (nextSym == RUNA || nextSym == RUNB);

                s++;
                ch = seqToUnseq[yy[0]];
                unzftab[ch] += s;

                while (s > 0) {
                    last++;
                    ll8[last] = ch;
                    s--;
                }

                if (last >= limitLast) {
                    throw new IOException("block overrun");
                }
                continue;
            } else {
                char tmp;
                last++;
                if (last >= limitLast) {
                    throw new IOException("block overrun");
                }

                tmp = yy[nextSym - 1];
                unzftab[seqToUnseq[tmp]]++;
                ll8[last] = seqToUnseq[tmp];

                /*
                This loop is hammered during decompression,
                hence the unrolling.

                for (j = nextSym-1; j > 0; j--) yy[j] = yy[j-1];
                 */

                j = nextSym - 1;
                for (; j > 3; j -= 4) {
                    yy[j] = yy[j - 1];
                    yy[j - 1] = yy[j - 2];
                    yy[j - 2] = yy[j - 3];
                    yy[j - 3] = yy[j - 4];
                }
                for (; j > 0; j--) {
                    yy[j] = yy[j - 1];
                }

                yy[0] = tmp;
                {
                    int zt, zn, zvec, zj;
                    if (groupPos == 0) {
                        groupNo++;
                        groupPos = G_SIZE;
                    }
                    groupPos--;
                    zt = selector[groupNo];
                    zn = minLens[zt];
                    zvec = bsR(zn);
                    while (zvec > limit[zt][zn]) {
                        zn++;
                        {
                            {
                                while (bsLive < 1) {
                                    int zzi;
                                    char thech = 0;

                                    thech = (char) bsStream.read();

                                    zzi = thech;
                                    bsBuff = (bsBuff << 8) | (zzi & 0xff);
                                    bsLive += 8;
                                }
                            }
                            zj = (bsBuff >> (bsLive - 1)) & 1;
                            bsLive--;
                        }
                        zvec = (zvec << 1) | zj;
                    }
                    nextSym = perm[zt][zvec - base[zt][zn]];
                }
                continue;
            }
        }
    }

    private void setupBlock() throws IOException {
        int i;
        int[] cftab = new int[257];
        char ch;

        cftab[0] = 0;
        for (i = 1; i <= 256; i++) {
            cftab[i] = unzftab[i - 1];
        }
        for (i = 1; i <= 256; i++) {
            cftab[i] += cftab[i - 1];
        }

        for (i = 0; i <= last; i++) {
            ch = ll8[i];
            tt[cftab[ch]] = i;
            cftab[ch]++;
        }
        cftab = null;

        tPos = tt[origPtr];

        count = 0;
        i2 = 0;
        ch2 = 256;   /* not a char and not EOF */

        if (blockRandomised) {
            rNToGo = 0;
            rTPos = 0;
            setupRandPartA();
        } else {
            setupNoRandPartA();
        }
    }

    private void setupRandPartA() throws IOException {
        if (i2 <= last) {
            chPrev = ch2;
            ch2 = ll8[tPos];
            tPos = tt[tPos];
            if (rNToGo == 0) {
                rNToGo = NUMS[rTPos];
                rTPos++;
                if (rTPos == 512) {
                    rTPos = 0;
                }
            }
            rNToGo--;
            ch2 ^= ((rNToGo == 1) ? 1 : 0);
            i2++;

            currentChar = ch2;
            currentState = RAND_PART_B_STATE;
            mCrc.updateCRC(ch2);
        } else {
            endBlock();
            initBlock();
            setupBlock();
        }
    }

    private void setupNoRandPartA() throws IOException {
        if (i2 <= last) {
            chPrev = ch2;
            ch2 = ll8[tPos];
            tPos = tt[tPos];
            i2++;

            currentChar = ch2;
            currentState = NO_RAND_PART_B_STATE;
            mCrc.updateCRC(ch2);
        } else {
            endBlock();
            initBlock();
            setupBlock();
        }
    }

    private void setupRandPartB() throws IOException {
        if (ch2 != chPrev) {
            currentState = RAND_PART_A_STATE;
            count = 1;
            setupRandPartA();
        } else {
            count++;
            if (count >= 4) {
                z = ll8[tPos];
                tPos = tt[tPos];
                if (rNToGo == 0) {
                    rNToGo = NUMS[rTPos];
                    rTPos++;
                    if (rTPos == 512) {
                        rTPos = 0;
                    }
                }
                rNToGo--;
                z ^= ((rNToGo == 1) ? 1 : 0);
                j2 = 0;
                currentState = RAND_PART_C_STATE;
                setupRandPartC();
            } else {
                currentState = RAND_PART_A_STATE;
                setupRandPartA();
            }
        }
    }

    private void setupRandPartC() throws IOException {
        if (j2 < (int) z) {
            currentChar = ch2;
            mCrc.updateCRC(ch2);
            j2++;
        } else {
            currentState = RAND_PART_A_STATE;
            i2++;
            count = 0;
            setupRandPartA();
        }
    }

    private void setupNoRandPartB() throws IOException {
        if (ch2 != chPrev) {
            currentState = NO_RAND_PART_A_STATE;
            count = 1;
            setupNoRandPartA();
        } else {
            count++;
            if (count >= 4) {
                z = ll8[tPos];
                tPos = tt[tPos];
                currentState = NO_RAND_PART_C_STATE;
                j2 = 0;
                setupNoRandPartC();
            } else {
                currentState = NO_RAND_PART_A_STATE;
                setupNoRandPartA();
            }
        }
    }

    private void setupNoRandPartC() throws IOException {
        if (j2 < (int) z) {
            currentChar = ch2;
            mCrc.updateCRC(ch2);
            j2++;
        } else {
            currentState = NO_RAND_PART_A_STATE;
            i2++;
            count = 0;
            setupNoRandPartA();
        }
    }

    private void setDecompressStructureSizes(int newSize100k) throws IOException {
        if (!(0 <= newSize100k && newSize100k <= 9 && 0 <= blockSize100k && blockSize100k <= 9)) {
            throw new IOException("invalid block size");
        }

        blockSize100k = newSize100k;

        if (newSize100k == 0) {
            return;
        }

        int n = BASE_BLOCK_SIZE * newSize100k;
        ll8 = new char[n];
        tt = new int[n];
    }

    public void setInput(byte[] b) throws IOException {
        this.setInput(b, 0, b.length);
    }

    public void setInput(byte[] b, int off, int len) throws IOException {
        this.inputBuffer.write(b, off, len);

        if (!this.readHeader) {
            if (this.inputBuffer.size() > 10) {
                this.bsStream = new ByteArrayInputStream(this.inputBuffer.toByteArray());
                this.inputBuffer.reset();

                this.initStream();

                if (this.streamEnd == true) {
                    throw new IOException("invalid BZip2 stream");
                }

                this.readHeader = true;
            }
        } else if (this.inputBuffer.size() > 0 && (this.bsStream == null || !(this.bsStream.available() > 0))) {
            this.flush();
        } else if (this.inputBuffer.size() > 8192) {
            this.flush();
        }
    }

    public boolean needsInput() {
        this.flush();

        return !(this.bsStream.available() > 0);
    }

    public int inflate(byte[] b) throws IOException {
        int rc = 0;

        while (this.bsStream != null && this.bsStream.available() > 0 && rc < b.length) {
            b[rc++] = (byte) this.read();
        }

        this.flush();

        return rc;
    }

    public void flush() {
        if (this.inputBuffer.size() > 0) {
            if (this.bsStream == null || !(this.bsStream.available() > 0)) {
                this.bsStream = new ByteArrayInputStream(this.inputBuffer.toByteArray());
            } else {
                byte[] buff = new byte[this.bsStream.available() + this.inputBuffer.size()];

                int i = 0;

                try {
                    i = this.bsStream.read(buff);
                } catch (IOException e) {
                }

                if (i == 0) {
                    this.bsStream = new ByteArrayInputStream(this.inputBuffer.toByteArray());
                } else {
                    byte[] o = this.inputBuffer.toByteArray();

                    System.arraycopy(o, 0, buff, i, o.length);

                    this.bsStream = new ByteArrayInputStream(buff);
                }
            }

            this.inputBuffer.reset();
        }
    }
}

