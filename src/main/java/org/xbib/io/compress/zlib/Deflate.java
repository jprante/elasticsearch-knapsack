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

public final class Deflate {

    private static final int MAX_MEM_LEVEL = 9;
    private static final int Z_DEFAULT_COMPRESSION = -1;
    private static final int MAX_WBITS = 15;            // 32K LZ77 window
    private static final int DEF_MEM_LEVEL = 8;

    static final class Config {

        private int goodlength; // reduce lazy search above this match length
        private int maxlazy;    // do not perform lazy search above this match length
        private int nicelength; // quit search above this match length
        private int maxchain;
        private int func;

        private Config(int goodlength, int maxlazy,
                int nicelength, int maxchain, int func) {
            this.goodlength = goodlength;
            this.maxlazy = maxlazy;
            this.nicelength = nicelength;
            this.maxchain = maxchain;
            this.func = func;
        }
    }
    private static final int STORED = 0;
    private static final int FAST = 1;
    private static final int SLOW = 2;
    private static final Config[] CONFIG_TABLE;

    static {
        CONFIG_TABLE = new Config[10];
        //                         good  lazy  nice  chain
        CONFIG_TABLE[0] = new Config(0, 0, 0, 0, STORED);
        CONFIG_TABLE[1] = new Config(4, 4, 8, 4, FAST);
        CONFIG_TABLE[2] = new Config(4, 5, 16, 8, FAST);
        CONFIG_TABLE[3] = new Config(4, 6, 32, 32, FAST);

        CONFIG_TABLE[4] = new Config(4, 4, 16, 16, SLOW);
        CONFIG_TABLE[5] = new Config(8, 16, 32, 32, SLOW);
        CONFIG_TABLE[6] = new Config(8, 16, 128, 128, SLOW);
        CONFIG_TABLE[7] = new Config(8, 32, 128, 256, SLOW);
        CONFIG_TABLE[8] = new Config(32, 128, 258, 1024, SLOW);
        CONFIG_TABLE[9] = new Config(32, 258, 258, 4096, SLOW);
    }
    private static final String[] zerrmsg = {
        "need dictionary", // Z_NEED_DICT       2
        "stream end", // Z_STREAM_END      1
        "", // Z_OK              0
        "file error", // Z_ERRNO         (-1)
        "stream error", // Z_STREAM_ERROR  (-2)
        "data error", // Z_DATA_ERROR    (-3)
        "insufficient memory", // Z_MEM_ERROR     (-4)
        "buffer error", // Z_BUF_ERROR     (-5)
        "incompatible version",// Z_VERSION_ERROR (-6)
        ""
    };
    // block not completed, need more input or more output
    private static final int NEED_MORE = 0;
    // block flush performed
    private static final int BLOCK_DONE = 1;
    // finish started, need only more output at next deflate
    private static final int FINISH_STARTED = 2;
    // finish done, accept no more input or output
    private static final int FINISH_DONE = 3;
    // preset dictionary flag in zlib header
    private static final int PRESET_DICT = 0x20;
    private static final int Z_FILTERED = 1;
    private static final int Z_HUFFMAN_ONLY = 2;
    private static final int Z_DEFAULT_STRATEGY = 0;
    private static final int Z_NO_FLUSH = 0;
    private static final int Z_PARTIAL_FLUSH = 1;
    private static final int Z_FULL_FLUSH = 3;
    private static final int Z_FINISH = 4;
    private static final int Z_OK = 0;
    private static final int Z_STREAM_END = 1;
    private static final int Z_NEED_DICT = 2;
    private static final int Z_STREAM_ERROR = -2;
    private static final int Z_DATA_ERROR = -3;
    private static final int Z_BUF_ERROR = -5;
    private static final int INIT_STATE = 42;
    private static final int BUSY_STATE = 113;
    private static final int FINISH_STATE = 666;
    // The deflate compression method
    private static final int Z_DEFLATED = 8;
    private static final int STORED_BLOCK = 0;
    private static final int STATIC_TREES = 1;
    private static final int DYN_TREES = 2;
    // The three kinds of block type
    private static final int Z_BINARY = 0;
    private static final int Z_ASCII = 1;
    private static final int Z_UNKNOWN = 2;
    private static final int BUF_SIZE = 8 * 2;
    // repeat previous bit length 3-6 times (2 bits of repeat count)
    private static final int REP_3_6 = 16;
    // repeat a zero length 3-10 times  (3 bits of repeat count)
    private static final int REPZ_3_10 = 17;
    // repeat a zero length 11-138 times  (7 bits of repeat count)
    private static final int REPZ_11_138 = 18;
    private static final int MIN_MATCH = 3;
    private static final int MAX_MATCH = 258;
    private static final int MIN_LOOKAHEAD = (MAX_MATCH + MIN_MATCH + 1);
    private static final int MAX_BITS = 15;
    private static final int D_CODES = 30;
    private static final int BL_CODES = 19;
    private static final int LENGTH_CODES = 29;
    private static final int LITERALS = 256;
    private static final int L_CODES = (LITERALS + 1 + LENGTH_CODES);
    private static final int HEAP_SIZE = (2 * L_CODES + 1);
    private static final int END_BLOCK = 256;
    private ZStream strm;         // pointer back to this zlib stream
    private int status;           // as the name implies
    private byte[] pendingBuf;   // output still pending
    private int pendingBufSize; // size of pending_buf
    private int pendingOut;      // next pending byte to output to the stream
    private int pending;          // nb of bytes in the pending buffer
    private int noheader;         // suppress zlib header and adler32
    private byte dataType;       // UNKNOWN, BINARY or ASCII
    private int lastFlush;       // value of flush param for previous deflate call
    private int wSize;           // LZ77 window size (32K by default)
    private int wBits;           // log2(w_size)  (8..16)
    private int wMask;           // w_size - 1
    private byte[] window;
    // Sliding window. Input bytes are read into the second half of the window,
    // and move to the first half later to keep a dictionary of at least wSize
    // bytes. With this organization, matches are limited to a distance of
    // wSize-MAX_MATCH bytes, but this ensures that IO is always
    // performed with a length multiple of the block size. Also, it limits
    // the window size to 64K, which is quite useful on MSDOS.
    // To do: use the user input buffer as sliding window.
    private int windowSize;
    // Actual size of window: 2*wSize, except when the user input buffer
    // is directly used as sliding window.
    private short[] prev;
    // Link to older string with same hash index. To limit the size of this
    // array to 64K, this link is maintained only for the last 32K strings.
    // An index in this array is thus a window index modulo 32K.
    private short[] head; // Heads of the hash chains or NIL.
    private int insh;          // hash index of string to be inserted
    private int hashSize;      // number of elements in hash table
    private int hashBits;      // log2(hash_size)
    private int hashMask;      // hash_size-1
    // Number of bits by which ins_h must be shifted at each input
    // step. It must be such that after MIN_MATCH steps, the oldest
    // byte no longer takes part in the hash key, that is:
    // hash_shift * MIN_MATCH >= hash_bits
    private int hashShift;
    // Window position at the beginning of the current output block. Gets
    // negative when the window is moved backwards.
    private int blockStart;
    private int matchLength;           // length of best match
    private int prevMatch;             // previous match
    private int matchAvailable;        // set if previous match exists
    private int strstart;               // start of string to insert
    private int matchStart;            // start of matching string
    private int lookahead;              // number of valid bytes ahead in window
    // Length of the best match at previous step. Matches not greater than this
    // are discarded. This is used in the lazy match evaluation.
    private int prevLength;
    // To speed up deflation, hash chains are never searched beyond this
    // length.  A higher limit improves compression ratio but degrades the speed.
    private int maxChainLength;
    // Attempt to find a better match only when the current match is strictly
    // smaller than this value. This mechanism is used only for compression
    // levels >= 4.
    private int maxLazyMatch;
    // Insert new strings in the hash table only if the match length is not
    // greater than this length. This saves time but degrades compression.
    // max_insert_length is used only for compression levels <= 3.
    private int level;    // compression level (1..9)
    private int strategy; // favor or force Huffman coding
    // Use a faster search when the previous match is longer than this
    private int goodMatch;
    // Stop searching when current match exceeds this
    private int niceMatch;
    private short[] dynLtree;       // literal and length tree
    private short[] dynDtree;       // distance tree
    private short[] blTree;         // Huffman tree for bit lengths
    private Tree lDesc = new Tree();  // desc for literal tree
    private Tree dDesc = new Tree();  // desc for distance tree
    private Tree blDesc = new Tree(); // desc for bit length tree
    // number of codes at each bit length for an optimal tree
    protected short[] blCount = new short[MAX_BITS + 1];
    // heap used to build the Huffman trees
    protected int[] heap = new int[2 * L_CODES + 1];
    protected int heapLen;               // number of elements in the heap
    protected int heapMax;               // element of largest frequency
    // The sons of heap[n] are heap[2*n] and heap[2*n+1]. heap[0] is not used.
    // The same heap array is used to build all trees.
    // Depth of each subtree used as tie breaker for trees of equal frequency
    protected byte[] depth = new byte[2 * L_CODES + 1];
    private int lBuf;               // index for literals or lengths */
    // Size of match buffer for literals/lengths.  There are 4 reasons for
    // limiting lit_bufsize to 64K:
    //   - frequencies can be kept in 16 bit counters
    //   - if compression is not successful for the first block, all input
    //     data is still in the window so we can still emit a stored block even
    //     when input comes from standard input.  (This can also be done for
    //     all blocks if lit_bufsize is not greater than 32K.)
    //   - if compression is not successful for a file smaller than 64K, we can
    //     even emit a stored file instead of a stored block (saving 5 bytes).
    //     This is applicable only for zip (not gzip or zlib).
    //   - creating new Huffman trees less frequently may not provide fast
    //     adaptation to changes in the input data statistics. (Take for
    //     example a binary file with poorly compressible code followed by
    //     a highly compressible string table.) Smaller buffer sizes give
    //     fast adaptation but have of course the overhead of transmitting
    //     trees more frequently.
    //   - I can't count above 4
    private int litBufsize;
    private int lastLit;      // running index in l_buf
    // Buffer for distances. To simplify the code, d_buf and l_buf have
    // the same number of elements. To use different lengths, an extra flag
    // array would be necessary.
    private int dBuf;         // index of pendig_buf
    protected int optLen;        // bit length of current block with optimal trees
    protected int staticLen;     // bit length of current block with static trees
    private int matches;        // number of string matches in current block
    private int lastEobLen;   // bit length of EOB code for last block
    // Output buffer. bits are inserted starting at the bottom (least
    // significant bits).
    private short biBuf;
    // Number of valid bits in bi_buf.  All bits above the last valid bit
    // are always zero.
    private int biValid;

    protected Deflate() {
        dynLtree = new short[HEAP_SIZE * 2];
        dynDtree = new short[(2 * D_CODES + 1) * 2]; // distance tree
        blTree = new short[(2 * BL_CODES + 1) * 2];  // Huffman tree for bit lengths
    }

    protected int getPending() {
        return pending;
    }

    protected void setPending(int n) {
        pending = n;
    }

    protected byte[] getPendingBuf() {
        return pendingBuf;
    }

    protected int getPendingOut() {
        return pendingOut;
    }

    protected void setPendingOut(int n) {
        pendingOut = n;
    }

    protected int getNoHeader() {
        return noheader;
    }

    private void lminit() {
        windowSize = 2 * wSize;

        head[hashSize - 1] = 0;
        for (int i = 0; i < hashSize - 1; i++) {
            head[i] = 0;
        }

        // Set the default configuration parameters:
        maxLazyMatch = Deflate.CONFIG_TABLE[level].maxlazy;
        goodMatch = Deflate.CONFIG_TABLE[level].goodlength;
        niceMatch = Deflate.CONFIG_TABLE[level].nicelength;
        maxChainLength = Deflate.CONFIG_TABLE[level].maxchain;

        strstart = 0;
        blockStart = 0;
        lookahead = 0;
        matchLength = prevLength = MIN_MATCH - 1;
        matchAvailable = 0;
        insh = 0;
    }

    // Initialize the tree data structures for a new zlib stream.
    private void trinit() {

        lDesc.dynTree = dynLtree;
        lDesc.statDesc = StaticTree.getLDesc();

        dDesc.dynTree = dynDtree;
        dDesc.statDesc = StaticTree.getDDesc();

        blDesc.dynTree = blTree;
        blDesc.statDesc = StaticTree.getBLDesc();

        biBuf = 0;
        biValid = 0;
        lastEobLen = 8; // enough lookahead for inflate

        // Initialize the first block of the first file:
        initBlock();
    }

    private void initBlock() {
        // Initialize the trees.
        for (int i = 0; i < L_CODES; i++) {
            dynLtree[i * 2] = 0;
        }
        for (int i = 0; i < D_CODES; i++) {
            dynDtree[i * 2] = 0;
        }
        for (int i = 0; i < BL_CODES; i++) {
            blTree[i * 2] = 0;
        }

        dynLtree[END_BLOCK * 2] = 1;
        optLen = staticLen = 0;
        lastLit = matches = 0;
    }

    // Restore the heap property by moving down the tree starting at node k,
    // exchanging a node with the smallest of its two sons if necessary, stopping
    // when the heap property is re-established (each father smaller than its
    // two sons).
    protected void pqdownheap(short[] tree, // the tree to restore
            int kk // node to move down
            ) {
        int k = kk;
        int v = heap[k];
        int j = k << 1;  // left son of k
        while (j <= heapLen) {
            // Set j to the smallest of the two sons:
            if (j < heapLen
                    && smaller(tree, heap[j + 1], heap[j], depth)) {
                j++;
            }
            // Exit if v is smaller than both sons
            if (smaller(tree, v, heap[j], depth)) {
                break;
            }

            // Exchange v with the smallest son
            heap[k] = heap[j];
            k = j;
            // And continue down the tree, setting j to the left son of k
            j <<= 1;
        }
        heap[k] = v;
    }

    private static boolean smaller(short[] tree, int n, int m, byte[] depth) {
        short tn2 = tree[n * 2];
        short tm2 = tree[m * 2];
        return (tn2 < tm2
                || (tn2 == tm2 && depth[n] <= depth[m]));
    }

    // Scan a literal or distance tree to determine the frequencies of the codes
    // in the bit length tree.
    private void scanTree(short[] tree,// the tree to be scanned
            int maxcode // and its largest code of non zero frequency
            ) {
        int n;                     // iterates over all tree elements
        int prevlen = -1;          // last emitted length
        int curlen;                // length of current code
        int nextlen = tree[0 * 2 + 1]; // length of next code
        int count = 0;             // repeat count of the current code
        int maxcount = 7;         // max repeat count
        int mincount = 4;         // min repeat count

        if (nextlen == 0) {
            maxcount = 138;
            mincount = 3;
        }
        tree[(maxcode + 1) * 2 + 1] = (short) 0xffff; // guard

        for (n = 0; n <= maxcode; n++) {
            curlen = nextlen;
            nextlen = tree[(n + 1) * 2 + 1];
            if (++count < maxcount && curlen == nextlen) {
                continue;
            } else if (count < mincount) {
                blTree[curlen * 2] += count;
            } else if (curlen != 0) {
                if (curlen != prevlen) {
                    blTree[curlen * 2]++;
                }
                blTree[REP_3_6 * 2]++;
            } else if (count <= 10) {
                blTree[REPZ_3_10 * 2]++;
            } else {
                blTree[REPZ_11_138 * 2]++;
            }
            count = 0;
            prevlen = curlen;
            if (nextlen == 0) {
                maxcount = 138;
                mincount = 3;
            } else if (curlen == nextlen) {
                maxcount = 6;
                mincount = 3;
            } else {
                maxcount = 7;
                mincount = 4;
            }
        }
    }

    // Construct the Huffman tree for the bit lengths and return the index in
    // BL_ORDER of the last bit length code to send.
    private int buildBLTree() {
        int maxblindex;  // index of last bit length code of non zero freq

        // Determine the bit length frequencies for literal and distance trees
        scanTree(dynLtree, lDesc.maxCode);
        scanTree(dynDtree, dDesc.maxCode);

        // Build the bit length tree:
        blDesc.buildTree(this);
        // opt_len now includes the length of the tree representations, except
        // the lengths of the bit lengths codes and the 5+5+4 bits for the counts.

        // Determine the number of bit length codes to send. The pkzip format
        // requires that at least 4 bit length codes be sent. (appnote.txt says
        // 3 but the actual value used is 4.)
        for (maxblindex = BL_CODES - 1; maxblindex >= 3; maxblindex--) {
            if (blTree[Tree.BL_ORDER[maxblindex] * 2 + 1] != 0) {
                break;
            }
        }
        // Update opt_len to include the bit length tree and counts
        optLen += 3 * (maxblindex + 1) + 5 + 5 + 4;

        return maxblindex;
    }

    // Send the header for a block using dynamic Huffman trees: the counts, the
    // lengths of the bit length codes, the literal tree and the distance tree.
    // IN assertion: lcodes >= 257, dcodes >= 1, blcodes >= 4.
    private void sendAllTree(int lcodes, int dcodes, int blcodes) {
        int rank;                    // index in BL_ORDER

        sendBits(lcodes - 257, 5); // not +255 as stated in appnote.txt
        sendBits(dcodes - 1, 5);
        sendBits(blcodes - 4, 4); // not -3 as stated in appnote.txt
        for (rank = 0; rank < blcodes; rank++) {
            sendBits(blTree[Tree.BL_ORDER[rank] * 2 + 1], 3);
        }
        sendTree(dynLtree, lcodes - 1); // literal tree
        sendTree(dynDtree, dcodes - 1); // distance tree
    }

    // Send a literal or distance tree in compressed form, using the codes in
    // bl_tree.
    private void sendTree(short[] tree,// the tree to be sent
            int max_code // and its largest code of non zero frequency
            ) {
        int n;                     // iterates over all tree elements
        int prevlen = -1;          // last emitted length
        int curlen;                // length of current code
        int nextlen = tree[0 * 2 + 1]; // length of next code
        int count = 0;             // repeat count of the current code
        int maxcount = 7;         // max repeat count
        int mincount = 4;         // min repeat count

        if (nextlen == 0) {
            maxcount = 138;
            mincount = 3;
        }

        for (n = 0; n <= max_code; n++) {
            curlen = nextlen;
            nextlen = tree[(n + 1) * 2 + 1];
            if (++count < maxcount && curlen == nextlen) {
                continue;
            } else if (count < mincount) {
                do {
                    sendCode(curlen, blTree);
                } while (--count != 0);
            } else if (curlen != 0) {
                if (curlen != prevlen) {
                    sendCode(curlen, blTree);
                    count--;
                }
                sendCode(REP_3_6, blTree);
                sendBits(count - 3, 2);
            } else if (count <= 10) {
                sendCode(REPZ_3_10, blTree);
                sendBits(count - 3, 3);
            } else {
                sendCode(REPZ_11_138, blTree);
                sendBits(count - 11, 7);
            }
            count = 0;
            prevlen = curlen;
            if (nextlen == 0) {
                maxcount = 138;
                mincount = 3;
            } else if (curlen == nextlen) {
                maxcount = 6;
                mincount = 3;
            } else {
                maxcount = 7;
                mincount = 4;
            }
        }
    }

    // Output a byte on the stream.
    // IN assertion: there is enough room in pending_buf.
    private void putByte(byte[] p, int start, int len) {
        System.arraycopy(p, start, pendingBuf, pending, len);
        pending += len;
    }

    private void putByte(byte c) {
        pendingBuf[pending++] = c;
    }

    private void putShort(int w) {
        putByte((byte) (w/*&0xff*/));
        putByte((byte) (w >>> 8));
    }

    private void putShortMSB(int b) {
        putByte((byte) (b >> 8));
        putByte((byte) (b/*&0xff*/));
    }

    private void sendCode(int c, short[] tree) {
        int c2 = c * 2;
        sendBits((tree[c2] & 0xffff), (tree[c2 + 1] & 0xffff));
    }

    private void sendBits(int value, int length) {
        int len = length;
        if (biValid > BUF_SIZE - len) {
            int val = value;
//      bi_buf |= (val << bi_valid);
            biBuf |= ((val << biValid) & 0xffff);
            putShort(biBuf);
            biBuf = (short) (val >>> (BUF_SIZE - biValid));
            biValid += len - BUF_SIZE;
        } else {
//      bi_buf |= (value) << bi_valid;
            biBuf |= (((value) << biValid) & 0xffff);
            biValid += len;
        }
    }

    // Send one empty static block to give enough lookahead for inflate.
    // This takes 10 bits, of which 7 may remain in the bit buffer.
    // The current inflate code requires 9 bits of lookahead. If the
    // last two codes for the previous block (real code plus EOB) were coded
    // on 5 bits or less, inflate may have only 5+3 bits of lookahead to decode
    // the last real code. In this case we send two empty static blocks instead
    // of one. (There are no problems if the previous block is stored or fixed.)
    // To simplify the code, we assume the worst case of last real code encoded
    // on one bit only.
    private void trAlign() {
        sendBits(STATIC_TREES << 1, 3);
        sendCode(END_BLOCK, StaticTree.STATIC_LTREE);

        biFlush();

        // Of the 10 bits for the empty block, we have already sent
        // (10 - bi_valid) bits. The lookahead for the last real code (before
        // the EOB of the previous block) was thus at least one plus the length
        // of the EOB plus what we have just sent of the empty static block.
        if (1 + lastEobLen + 10 - biValid < 9) {
            sendBits(STATIC_TREES << 1, 3);
            sendCode(END_BLOCK, StaticTree.STATIC_LTREE);
            biFlush();
        }
        lastEobLen = 7;
    }

    // Save the match info and tally the frequency counts. Return true if
    // the current block must be flushed.
    private boolean trTally(int dist, // distance of matched string
            int lc // match length-MIN_MATCH or unmatched char (if dist==0)
            ) {

        pendingBuf[dBuf + lastLit * 2] = (byte) (dist >>> 8);
        pendingBuf[dBuf + lastLit * 2 + 1] = (byte) dist;

        pendingBuf[lBuf + lastLit] = (byte) lc;
        lastLit++;

        if (dist == 0) {
            // lc is the unmatched char
            dynLtree[lc * 2]++;
        } else {
            matches++;
            // Here, lc is the match length - MIN_MATCH
            dist--;             // dist = match distance - 1
            dynLtree[(Tree.LENGTH_CODE[lc] + LITERALS + 1) * 2]++;
            dynDtree[Tree.distanceCode(dist) * 2]++;
        }

        if ((lastLit & 0x1fff) == 0 && level > 2) {
            // Compute an upper bound for the compressed length
            int out_length = lastLit * 8;
            int in_length = strstart - blockStart;
            int dcode;
            for (dcode = 0; dcode < D_CODES; dcode++) {
                out_length += (int) dynDtree[dcode * 2]
                        * (5L + Tree.EXTRA_DBITS[dcode]);
            }
            out_length >>>= 3;
            if ((matches < (lastLit / 2)) && out_length < in_length / 2) {
                return true;
            }
        }

        return (lastLit == litBufsize - 1);
        // We avoid equality with lit_bufsize because of wraparound at 64K
        // on 16 bit machines and because stored blocks are restricted to
        // 64K-1 bytes.
    }

    // Send the block data compressed using the given Huffman trees
    private void compressBlock(short[] ltree, short[] dtree) {
        int dist;      // distance of matched string
        int lc;         // match length or unmatched char (if dist == 0)
        int lx = 0;     // running index in l_buf
        int code;       // the code to send
        int extra;      // number of extra bits to send

        if (lastLit != 0) {
            do {
                dist = ((pendingBuf[dBuf + lx * 2] << 8) & 0xff00)
                        | (pendingBuf[dBuf + lx * 2 + 1] & 0xff);
                lc = (pendingBuf[lBuf + lx]) & 0xff;
                lx++;

                if (dist == 0) {
                    sendCode(lc, ltree); // send a literal byte
                } else {
                    // Here, lc is the match length - MIN_MATCH
                    code = Tree.LENGTH_CODE[lc];

                    sendCode(code + LITERALS + 1, ltree); // send the length code
                    extra = Tree.EXTRA_LBITS[code];
                    if (extra != 0) {
                        lc -= Tree.BASE_LENGTH[code];
                        sendBits(lc, extra);       // send the extra length bits
                    }
                    dist--; // dist is now the match distance - 1
                    code = Tree.distanceCode(dist);

                    sendCode(code, dtree);       // send the distance code
                    extra = Tree.EXTRA_DBITS[code];
                    if (extra != 0) {
                        dist -= Tree.BASE_DIST[code];
                        sendBits(dist, extra);   // send the extra distance bits
                    }
                } // literal or match pair ?

                // Check that the overlay between pending_buf and d_buf+l_buf is ok:
            } while (lx < lastLit);
        }

        sendCode(END_BLOCK, ltree);
        lastEobLen = ltree[END_BLOCK * 2 + 1];
    }

    // Set the data type to ASCII or BINARY, using a crude approximation:
    // binary if more than 20% of the bytes are <= 6 or >= 128, ascii otherwise.
    // IN assertion: the fields freq of dyn_ltree are set and the total of all
    // frequencies does not exceed 64K (to fit in an int on 16 bit machines).
    private void setDataType() {
        int n = 0;
        int asciifreq = 0;
        int binfreq = 0;
        while (n < 7) {
            binfreq += dynLtree[n * 2];
            n++;
        }
        while (n < 128) {
            asciifreq += dynLtree[n * 2];
            n++;
        }
        while (n < LITERALS) {
            binfreq += dynLtree[n * 2];
            n++;
        }
        dataType = (byte) (binfreq > (asciifreq >>> 2) ? Z_BINARY : Z_ASCII);
    }

    // Flush the bit buffer, keeping at most 7 bits in it.
    private void biFlush() {
        if (biValid == 16) {
            putShort(biBuf);
            biBuf = 0;
            biValid = 0;
        } else if (biValid >= 8) {
            putByte((byte) biBuf);
            biBuf >>>= 8;
            biValid -= 8;
        }
    }

    // Flush the bit buffer and align the output on a byte boundary
    private void biWindup() {
        if (biValid > 8) {
            putShort(biBuf);
        } else if (biValid > 0) {
            putByte((byte) biBuf);
        }
        biBuf = 0;
        biValid = 0;
    }

    // Copy a stored block, storing first the length and its
    // one's complement if requested.
    private void copyBlock(int buf, // the input data
            int len, // its length
            boolean header // true if block header must be written
            ) {        
        biWindup();      // align on byte boundary
        lastEobLen = 8; // enough lookahead for inflate

        if (header) {
            putShort((short) len);
            putShort((short) ~len);
        }
        putByte(window, buf, len);
    }

    private void flushBlockOnly(boolean eof) {
        trFlushBlock(blockStart >= 0 ? blockStart : -1,
                strstart - blockStart,
                eof);
        blockStart = strstart;
        strm.flushPending();
    }

    // Copy without compression as much as possible from the input stream, return
    // the current block state.
    // This function does not insert new strings in the dictionary since
    // uncompressible data is probably not useful. This function is used
    // only for the level=0 compression option.
    // NOTE: this function should be optimized to avoid extra copying from
    // window to pending_buf.
    private int deflateStored(int flush) {
        // Stored blocks are limited to 0xffff bytes, pending_buf is limited
        // to pending_buf_size, and each stored block has a 5 byte header:

        int maxblocksize = 0xffff;
        int maxstart;

        if (maxblocksize > pendingBufSize - 5) {
            maxblocksize = pendingBufSize - 5;
        }

        // Copy as much as possible from input to output:
        while (true) {
            // Fill the window as much as possible:
            if (lookahead <= 1) {
                fillWindow();
                if (lookahead == 0 && flush == Z_NO_FLUSH) {
                    return NEED_MORE;
                }
                if (lookahead == 0) {
                    break; // flush the current block
                }
            }

            strstart += lookahead;
            lookahead = 0;

            // Emit a stored block if pending_buf will be full:
            maxstart = blockStart + maxblocksize;
            if (strstart == 0 || strstart >= maxstart) {
                // strstart == 0 is possible when wraparound on 16-bit machine
                lookahead = (strstart - maxstart);
                strstart = maxstart;

                flushBlockOnly(false);
                if (strm.availout == 0) {
                    return NEED_MORE;
                }

            }

            // Flush if we may have to slide, otherwise block_start may become
            // negative and the data will be gone:
            if (strstart - blockStart >= wSize - MIN_LOOKAHEAD) {
                flushBlockOnly(false);
                if (strm.availout == 0) {
                    return NEED_MORE;
                }
            }
        }

        flushBlockOnly(flush == Z_FINISH);
        if (strm.availout == 0) {
            return (flush == Z_FINISH) ? FINISH_STARTED : NEED_MORE;
        }

        return flush == Z_FINISH ? FINISH_DONE : BLOCK_DONE;
    }

    // Send a stored block
    private void trStoredBlock(int buf, // input block
            int storedlen, // length of input block
            boolean eof // true if this is the last block for a file
            ) {
        sendBits((STORED_BLOCK << 1) + (eof ? 1 : 0), 3);  // send block type
        copyBlock(buf, storedlen, true);          // with header
    }

    // Determine the best encoding for the current block: dynamic trees, static
    // trees or store, and output the encoded block to the zip file.
    private void trFlushBlock(int buf, // input block, or NULL if too old
            int storedlen, // length of input block
            boolean eof // true if this is the last block for a file
            ) {
        int optlenb, staticlenb;// opt_len and static_len in bytes
        int maxblindex = 0;      // index of last bit length code of non zero freq

        // Build the Huffman trees unless a stored block is forced
        if (level > 0) {
            // Check if the file is ascii or binary
            if (dataType == Z_UNKNOWN) {
                setDataType();
            }

            // Construct the literal and distance trees
            lDesc.buildTree(this);

            dDesc.buildTree(this);

            // At this point, opt_len and static_len are the total bit lengths of
            // the compressed block data, excluding the tree representations.

            // Build the bit length tree for the above two trees, and get the index
            // in BL_ORDER of the last bit length code to send.
            maxblindex = buildBLTree();

            // Determine the best encoding. Compute first the block length in bytes
            optlenb = (optLen + 3 + 7) >>> 3;
            staticlenb = (staticLen + 3 + 7) >>> 3;

            if (staticlenb <= optlenb) {
                optlenb = staticlenb;
            }
        } else {
            optlenb = staticlenb = storedlen + 5; // force a stored block
        }

        if (storedlen + 4 <= optlenb && buf != -1) {
            // 4: two words for the lengths
            // The test buf != NULL is only necessary if LIT_BUFSIZE > WSIZE.
            // Otherwise we can't have processed more than WSIZE input bytes since
            // the last block flush, because compression would have been
            // successful. If LIT_BUFSIZE <= WSIZE, it is never too late to
            // transform a block into a stored block.
            trStoredBlock(buf, storedlen, eof);
        } else if (staticlenb == optlenb) {
            sendBits((STATIC_TREES << 1) + (eof ? 1 : 0), 3);
            compressBlock(StaticTree.STATIC_LTREE, StaticTree.STATIC_DTREE);
        } else {
            sendBits((DYN_TREES << 1) + (eof ? 1 : 0), 3);
            sendAllTree(lDesc.maxCode + 1, dDesc.maxCode + 1, maxblindex + 1);
            compressBlock(dynLtree, dynDtree);
        }

        // The above check is made mod 2^32, for files larger than 512 MB
        // and uLong implemented on 32 bits.

        initBlock();

        if (eof) {
            biWindup();
        }
    }

    // Fill the window when the lookahead becomes insufficient.
    // Updates strstart and lookahead.
    //
    // IN assertion: lookahead < MIN_LOOKAHEAD
    // OUT assertions: strstart <= window_size-MIN_LOOKAHEAD
    //    At least one byte has been read, or avail_in == 0; reads are
    //    performed for at least two bytes (required for the zip translate_eol
    //    option -- not supported here).
    private void fillWindow() {
        int n, m;
        int p;
        int more;    // Amount of free space at the end of the window.

        do {
            more = (windowSize - lookahead - strstart);

            // Deal with !@#$% 64K limit:
            if (more == 0 && strstart == 0 && lookahead == 0) {
                more = wSize;
            } else if (more == -1) {
                // Very unlikely, but possible on 16 bit machine if strstart == 0
                // and lookahead == 1 (input done one byte at time)
                more--;

                // If the window is almost full and there is insufficient lookahead,
                // move the upper half to the lower one to make room in the upper half.
            } else if (strstart >= wSize + wSize - MIN_LOOKAHEAD) {
                System.arraycopy(window, wSize, window, 0, wSize);
                matchStart -= wSize;
                strstart -= wSize; // we now have strstart >= MAX_DIST
                blockStart -= wSize;

                // Slide the hash table (could be avoided with 32 bit values
                // at the expense of memory usage). We slide even when level == 0
                // to keep the hash table consistent if we switch back to level > 0
                // later. (Using level 0 permanently is not an optimal usage of
                // zlib, so we don't care about this pathological case.)

                n = hashSize;
                p = n;
                do {
                    m = (head[--p] & 0xffff);
                    head[p] = (m >= wSize ? (short) (m - wSize) : 0);
                } while (--n != 0);

                n = wSize;
                p = n;
                do {
                    m = (prev[--p] & 0xffff);
                    prev[p] = (m >= wSize ? (short) (m - wSize) : 0);
                    // If n is not on any hash chain, prev[n] is garbage but
                    // its value will never be used.
                } while (--n != 0);
                more += wSize;
            }

            if (strm.availin == 0) {
                return;
            }

            // If there was no sliding:
            //    strstart <= WSIZE+MAX_DIST-1 && lookahead <= MIN_LOOKAHEAD - 1 &&
            //    more == window_size - lookahead - strstart
            // => more >= window_size - (MIN_LOOKAHEAD-1 + WSIZE + MAX_DIST-1)
            // => more >= window_size - 2*WSIZE + 2
            // In the BIG_MEM or MMAP case (not yet supported),
            //   window_size == input_size + MIN_LOOKAHEAD  &&
            //   strstart + s->lookahead <= input_size => more >= MIN_LOOKAHEAD.
            // Otherwise, window_size == 2*WSIZE so more >= 2.
            // If there was sliding, more >= WSIZE. So in all cases, more >= 2.

            n = strm.readBuf(window, strstart + lookahead, more);
            lookahead += n;

            // Initialize the hash value now that we have some input:
            if (lookahead >= MIN_MATCH) {
                insh = window[strstart] & 0xff;
                insh = (((insh) << hashShift) ^ (window[strstart + 1] & 0xff)) & hashMask;
            }
            // If the whole input has less than MIN_MATCH bytes, ins_h is garbage,
            // but this is not important since only literal bytes will be emitted.
        } while (lookahead < MIN_LOOKAHEAD && strm.availin != 0);
    }

    // Compress as much as possible from the input stream, return the current
    // block state.
    // This function does not perform lazy evaluation of matches and inserts
    // new strings in the dictionary only for unmatched strings or for short
    // matches. It is used only for the fast compression options.
    private int deflateFast(int flush) {
//    short hash_head = 0; // head of the hash chain
        int hashhead = 0; // head of the hash chain
        boolean bflush;      // set if current block must be flushed

        while (true) {
            // Make sure that we always have enough lookahead, except
            // at the end of the input file. We need MAX_MATCH bytes
            // for the next match, plus MIN_MATCH bytes to insert the
            // string following the next match.
            if (lookahead < MIN_LOOKAHEAD) {
                fillWindow();
                if (lookahead < MIN_LOOKAHEAD && flush == Z_NO_FLUSH) {
                    return NEED_MORE;
                }
                if (lookahead == 0) {
                    break; // flush the current block
                }
            }

            // Insert the string window[strstart .. strstart+2] in the
            // dictionary, and set hash_head to the head of the hash chain:
            if (lookahead >= MIN_MATCH) {
                insh = (((insh) << hashShift) ^ (window[(strstart) + (MIN_MATCH - 1)] & 0xff)) & hashMask;

//	prev[strstart&w_mask]=hash_head=head[ins_h];
                hashhead = (head[insh] & 0xffff);
                prev[strstart & wMask] = head[insh];
                head[insh] = (short) strstart;
            }

            // Find the longest match, discarding those <= prev_length.
            // At this point we have always match_length < MIN_MATCH

            // To simplify the code, we prevent matches with the string
            // of window index 0 (in particular we have to avoid a match
            // of the string with itself at the start of the input file).
            if (hashhead != 0L
                    && ((strstart - hashhead) & 0xffff) <= wSize - MIN_LOOKAHEAD
                    && (strategy != Z_HUFFMAN_ONLY)) {
                matchLength = longestMatch(hashhead);
            }
            // longest_match() sets match_start

            if (matchLength >= MIN_MATCH) {
                //        check_match(strstart, match_start, match_length);

                bflush = trTally(strstart - matchStart, matchLength - MIN_MATCH);

                lookahead -= matchLength;

                // Insert new strings in the hash table only if the match length
                // is not too large. This saves time but degrades compression.
                if (matchLength <= maxLazyMatch
                        && lookahead >= MIN_MATCH) {
                    matchLength--; // string at strstart already in hash table
                    do {
                        strstart++;

                        insh = ((insh << hashShift) ^ (window[(strstart) + (MIN_MATCH - 1)] & 0xff)) & hashMask;
//	    prev[strstart&w_mask]=hash_head=head[ins_h];
                        hashhead = (head[insh] & 0xffff);
                        prev[strstart & wMask] = head[insh];
                        head[insh] = (short) strstart;

                        // strstart never exceeds WSIZE-MAX_MATCH, so there are
                        // always MIN_MATCH bytes ahead.
                    } while (--matchLength != 0);
                    strstart++;
                } else {
                    strstart += matchLength;
                    matchLength = 0;
                    insh = window[strstart] & 0xff;

                    insh = (((insh) << hashShift) ^ (window[strstart + 1] & 0xff)) & hashMask;
                    // If lookahead < MIN_MATCH, ins_h is garbage, but it does not
                    // matter since it will be recomputed at next deflate call.
                }
            } else {
                // No match, output a literal byte

                bflush = trTally(0, window[strstart] & 0xff);
                lookahead--;
                strstart++;
            }
            if (bflush) {

                flushBlockOnly(false);
                if (strm.availout == 0) {
                    return NEED_MORE;
                }
            }
        }

        flushBlockOnly(flush == Z_FINISH);
        if (strm.availout == 0) {
            if (flush == Z_FINISH) {
                return FINISH_STARTED;
            } else {
                return NEED_MORE;
            }
        }
        return flush == Z_FINISH ? FINISH_DONE : BLOCK_DONE;
    }

    // Same as above, but achieves better compression. We use a lazy
    // evaluation for matches: a match is finally adopted only if there is
    // no better match at the next window position.
    private int deflateSlow(int flush) {
//    short hash_head = 0;    // head of hash chain
        int hashhead = 0;    // head of hash chain
        boolean bflush;         // set if current block must be flushed

        // Process the input block.
        while (true) {
            // Make sure that we always have enough lookahead, except
            // at the end of the input file. We need MAX_MATCH bytes
            // for the next match, plus MIN_MATCH bytes to insert the
            // string following the next match.

            if (lookahead < MIN_LOOKAHEAD) {
                fillWindow();
                if (lookahead < MIN_LOOKAHEAD && flush == Z_NO_FLUSH) {
                    return NEED_MORE;
                }
                if (lookahead == 0) {
                    break; // flush the current block
                }
            }

            // Insert the string window[strstart .. strstart+2] in the
            // dictionary, and set hash_head to the head of the hash chain:

            if (lookahead >= MIN_MATCH) {
                insh = (((insh) << hashShift) ^ (window[(strstart) + (MIN_MATCH - 1)] & 0xff)) & hashMask;
//	prev[strstart&w_mask]=hash_head=head[ins_h];
                hashhead = (head[insh] & 0xffff);
                prev[strstart & wMask] = head[insh];
                head[insh] = (short) strstart;
            }

            // Find the longest match, discarding those <= prev_length.
            prevLength = matchLength;
            prevMatch = matchStart;
            matchLength = MIN_MATCH - 1;

            if (hashhead != 0 && prevLength < maxLazyMatch
                    && ((strstart - hashhead) & 0xffff) <= wSize - MIN_LOOKAHEAD) {
                // To simplify the code, we prevent matches with the string
                // of window index 0 (in particular we have to avoid a match
                // of the string with itself at the start of the input file).

                if (strategy != Z_HUFFMAN_ONLY) {
                    matchLength = longestMatch(hashhead);
                }
                // longest_match() sets match_start

                if (matchLength <= 5 && (strategy == Z_FILTERED
                        || (matchLength == MIN_MATCH
                        && strstart - matchStart > 4096))) {

                    // If prev_match is also MIN_MATCH, match_start is garbage
                    // but we will ignore the current match anyway.
                    matchLength = MIN_MATCH - 1;
                }
            }

            // If there was a match at the previous step and the current
            // match is not better, output the previous match:
            if (prevLength >= MIN_MATCH && matchLength <= prevLength) {
                int max_insert = strstart + lookahead - MIN_MATCH;
                // Do not insert strings in hash table beyond this.

                //          check_match(strstart-1, prev_match, prev_length);

                bflush = trTally(strstart - 1 - prevMatch, prevLength - MIN_MATCH);

                // Insert in hash table all strings up to the end of the match.
                // strstart-1 and strstart are already inserted. If there is not
                // enough lookahead, the last two strings are not inserted in
                // the hash table.
                lookahead -= prevLength - 1;
                prevLength -= 2;
                do {
                    if (++strstart <= max_insert) {
                        insh = (((insh) << hashShift) ^ (window[(strstart) + (MIN_MATCH - 1)] & 0xff)) & hashMask;
                        //prev[strstart&w_mask]=hash_head=head[ins_h];
                        hashhead = (head[insh] & 0xffff);
                        prev[strstart & wMask] = head[insh];
                        head[insh] = (short) strstart;
                    }
                } while (--prevLength != 0);
                matchAvailable = 0;
                matchLength = MIN_MATCH - 1;
                strstart++;

                if (bflush) {
                    flushBlockOnly(false);
                    if (strm.availout == 0) {
                        return NEED_MORE;
                    }
                }
            } else if (matchAvailable != 0) {

                // If there was no match at the previous position, output a
                // single literal. If there was a match but the current match
                // is longer, truncate the previous match to a single literal.

                bflush = trTally(0, window[strstart - 1] & 0xff);

                if (bflush) {
                    flushBlockOnly(false);
                }
                strstart++;
                lookahead--;
                if (strm.availout == 0) {
                    return NEED_MORE;
                }
            } else {
                // There is no previous match to compare with, wait for
                // the next step to decide.

                matchAvailable = 1;
                strstart++;
                lookahead--;
            }
        }

        if (matchAvailable != 0) {
            bflush = trTally(0, window[strstart - 1] & 0xff);
            matchAvailable = 0;
        }
        flushBlockOnly(flush == Z_FINISH);

        if (strm.availout == 0) {
            if (flush == Z_FINISH) {
                return FINISH_STARTED;
            } else {
                return NEED_MORE;
            }
        }

        return flush == Z_FINISH ? FINISH_DONE : BLOCK_DONE;
    }

    private int longestMatch(int cmatch) {
        int curmatch = cmatch;
        int chainlength = maxChainLength; // max hash chain length
        int scan = strstart;                 // current string
        int match;                           // matched string
        int len;                             // length of current match
        int bestlen = prevLength;          // best match length so far
        int limit = strstart > (wSize - MIN_LOOKAHEAD) ? strstart - (wSize - MIN_LOOKAHEAD) : 0;
        int nmatch = this.niceMatch;

        // Stop when cur_match becomes <= limit. To simplify the code,
        // we prevent matches with the string of window index 0.

        int wmask = wMask;

        int strend = strstart + MAX_MATCH;
        byte scanend1 = window[scan + bestlen - 1];
        byte scanend = window[scan + bestlen];

        // The code is optimized for HASH_BITS >= 8 and MAX_MATCH-2 multiple of 16.
        // It is easy to get rid of this optimization if necessary.

        // Do not waste too much time if we already have a good match:
        if (prevLength >= goodMatch) {
            chainlength >>= 2;
        }

        // Do not look for matches beyond the end of the input. This is necessary
        // to make deflate deterministic.
        if (nmatch > lookahead) {
            nmatch = lookahead;
        }

        do {
            match = curmatch;

            // Skip to next match if the match length cannot increase
            // or if the match length is less than 2:
            if (window[match + bestlen] != scanend
                    || window[match + bestlen - 1] != scanend1
                    || window[match] != window[scan]
                    || window[++match] != window[scan + 1]) {
                continue;
            }

            // The check at best_len-1 can be removed because it will be made
            // again later. (This heuristic is not always a win.)
            // It is not necessary to compare scan[2] and match[2] since they
            // are always equal when the other bytes match, given that
            // the hash keys are equal and that HASH_BITS >= 8.
            scan += 2;
            match++;

            // We check for insufficient lookahead only every 8th comparison;
            // the 256th check will be made at strstart+258.
            do {
            } while (window[++scan] == window[++match]
                    && window[++scan] == window[++match]
                    && window[++scan] == window[++match]
                    && window[++scan] == window[++match]
                    && window[++scan] == window[++match]
                    && window[++scan] == window[++match]
                    && window[++scan] == window[++match]
                    && window[++scan] == window[++match]
                    && scan < strend);

            len = MAX_MATCH - (strend - scan);
            scan = strend - MAX_MATCH;

            if (len > bestlen) {
                matchStart = curmatch;
                bestlen = len;
                if (len >= nmatch) {
                    break;
                }
                scanend1 = window[scan + bestlen - 1];
                scanend = window[scan + bestlen];
            }

        } while ((curmatch = (prev[curmatch & wmask] & 0xffff)) > limit && --chainlength != 0);

        if (bestlen <= lookahead) {
            return bestlen;
        }
        return lookahead;
    }

    public int deflateInit(ZStream strm, int level, int bits) {
        return deflateInit2(strm, level, Z_DEFLATED, bits, DEF_MEM_LEVEL,
                Z_DEFAULT_STRATEGY);
    }

    public int deflateInit(ZStream strm, int level) {
        return deflateInit(strm, level, MAX_WBITS);
    }

    private int deflateInit2(ZStream strm, int level, int method, int windowBits,
            int memLevel, int strategy) {
        int nheader = 0;

        strm.msg = null;

        if (level == Z_DEFAULT_COMPRESSION) {
            level = 6;
        }

        if (windowBits < 0) { // undocumented feature: suppress zlib header
            nheader = 1;
            windowBits = -windowBits;
        }

        if (memLevel < 1 || memLevel > MAX_MEM_LEVEL
                || method != Z_DEFLATED
                || windowBits < 9 || windowBits > 15 || level < 0 || level > 9
                || strategy < 0 || strategy > Z_HUFFMAN_ONLY) {
            return Z_STREAM_ERROR;
        }

        strm.dstate = this;

        this.noheader = nheader;
        wBits = windowBits;
        wSize = 1 << wBits;
        wMask = wSize - 1;

        hashBits = memLevel + 7;
        hashSize = 1 << hashBits;
        hashMask = hashSize - 1;
        hashShift = ((hashBits + MIN_MATCH - 1) / MIN_MATCH);

        window = new byte[wSize * 2];
        prev = new short[wSize];
        head = new short[hashSize];

        litBufsize = 1 << (memLevel + 6); // 16K elements by default

        // We overlay pending_buf and d_buf+l_buf. This works since the average
        // output size for (length,distance) codes is <= 24 bits.
        pendingBuf = new byte[litBufsize * 4];
        pendingBufSize = litBufsize * 4;

        dBuf = litBufsize / 2;
        lBuf = (1 + 2) * litBufsize;

        this.level = level;

        this.strategy = strategy;
        return deflateReset(strm);
    }

    private int deflateReset(ZStream strm) {
        strm.totalin = strm.totalout = 0;
        strm.msg = null; //
        strm.dataType = Z_UNKNOWN;

        pending = 0;
        pendingOut = 0;

        if (noheader < 0) {
            noheader = 0; // was set to -1 by deflate(..., Z_FINISH);
        }
        status = (noheader != 0) ? BUSY_STATE : INIT_STATE;
        strm.adler = Adler32.adler32(0, null, 0, 0);

        lastFlush = Z_NO_FLUSH;

        trinit();
        lminit();
        return Z_OK;
    }

    public int deflateEnd() {
        if (status != INIT_STATE && status != BUSY_STATE && status != FINISH_STATE) {
            return Z_STREAM_ERROR;
        }
        // Deallocate in reverse order of allocations:
        pendingBuf = null;
        head = null;
        prev = null;
        window = null;
        // free
        // dstate=null;
        return status == BUSY_STATE ? Z_DATA_ERROR : Z_OK;
    }

    public int deflateParams(ZStream strm, int _level, int _strategy) {
        int err = Z_OK;

        if (_level == Z_DEFAULT_COMPRESSION) {
            _level = 6;
        }
        if (_level < 0 || _level > 9
                || _strategy < 0 || _strategy > Z_HUFFMAN_ONLY) {
            return Z_STREAM_ERROR;
        }

        if (CONFIG_TABLE[level].func != CONFIG_TABLE[_level].func
                && strm.totalin != 0) {
            // Flush the last buffer:
            err = strm.deflate(Z_PARTIAL_FLUSH);
        }

        if (level != _level) {
            level = _level;
            maxLazyMatch = CONFIG_TABLE[level].maxlazy;
            goodMatch = CONFIG_TABLE[level].goodlength;
            niceMatch = CONFIG_TABLE[level].nicelength;
            maxChainLength = CONFIG_TABLE[level].maxchain;
        }
        strategy = _strategy;
        return err;
    }

    public int deflateSetDictionary(ZStream strm, byte[] dictionary, int dictLength) {
        int length = dictLength;
        int index = 0;

        if (dictionary == null || status != INIT_STATE) {
            return Z_STREAM_ERROR;
        }

        strm.adler = Adler32.adler32(strm.adler, dictionary, 0, dictLength);

        if (length < MIN_MATCH) {
            return Z_OK;
        }
        if (length > wSize - MIN_LOOKAHEAD) {
            length = wSize - MIN_LOOKAHEAD;
            index = dictLength - length; // use the tail of the dictionary
        }
        System.arraycopy(dictionary, index, window, 0, length);
        strstart = length;
        blockStart = length;

        // Insert all strings in the hash table (except for the last two bytes).
        // s->lookahead stays null, so s->ins_h will be recomputed at the next
        // call of fill_window.

        insh = window[0] & 0xff;
        insh = (((insh) << hashShift) ^ (window[1] & 0xff)) & hashMask;

        for (int n = 0; n <= length - MIN_MATCH; n++) {
            insh = (((insh) << hashShift) ^ (window[(n) + (MIN_MATCH - 1)] & 0xff)) & hashMask;
            prev[n & wMask] = head[insh];
            head[insh] = (short) n;
        }
        return Z_OK;
    }

    public int deflate(ZStream strm, int flush) {
        int oldflush;

        if (flush > Z_FINISH || flush < 0) {
            return Z_STREAM_ERROR;
        }

        if (strm.nextout == null
                || (strm.nextin == null && strm.availin != 0)
                || (status == FINISH_STATE && flush != Z_FINISH)) {
            strm.msg = zerrmsg[Z_NEED_DICT - (Z_STREAM_ERROR)];
            return Z_STREAM_ERROR;
        }
        if (strm.availout == 0) {
            strm.msg = zerrmsg[Z_NEED_DICT - (Z_BUF_ERROR)];
            return Z_BUF_ERROR;
        }

        this.strm = strm; // just in case
        oldflush = lastFlush;
        lastFlush = flush;

        // Write the zlib header
        if (status == INIT_STATE) {
            int header = (Z_DEFLATED + ((wBits - 8) << 4)) << 8;
            int level_flags = ((level - 1) & 0xff) >> 1;

            if (level_flags > 3) {
                level_flags = 3;
            }
            header |= (level_flags << 6);
            if (strstart != 0) {
                header |= PRESET_DICT;
            }
            header += 31 - (header % 31);

            status = BUSY_STATE;
            putShortMSB(header);


            // Save the adler32 of the preset dictionary:
            if (strstart != 0) {
                putShortMSB((int) (strm.adler >>> 16));
                putShortMSB((int) (strm.adler & 0xffff));
            }
            strm.adler = Adler32.adler32(0, null, 0, 0);
        }

        // Flush as much pending output as possible
        if (pending != 0) {
            strm.flushPending();
            if (strm.availout == 0) {
                // Since avail_out is 0, deflate will be called again with
                // more output space, but possibly with both pending and
                // avail_in equal to zero. There won't be anything to do,
                // but this is not an error situation so make sure we
                // return OK instead of BUF_ERROR at next call of deflate:
                lastFlush = -1;
                return Z_OK;
            }

            // Make sure there is something to do and avoid duplicate consecutive
            // flushes. For repeated and useless calls with Z_FINISH, we keep
            // returning Z_STREAM_END instead of Z_BUFF_ERROR.
        } else if (strm.availin == 0 && flush <= oldflush
                && flush != Z_FINISH) {
            strm.msg = zerrmsg[Z_NEED_DICT - (Z_BUF_ERROR)];
            return Z_BUF_ERROR;
        }

        // User must not provide more input after the first FINISH:
        if (status == FINISH_STATE && strm.availin != 0) {
            strm.msg = zerrmsg[Z_NEED_DICT - (Z_BUF_ERROR)];
            return Z_BUF_ERROR;
        }

        // Start a new block or continue the current one.
        if (strm.availin != 0 || lookahead != 0
                || (flush != Z_NO_FLUSH && status != FINISH_STATE)) {
            int bstate = -1;
            switch (CONFIG_TABLE[level].func) {
                case STORED:
                    bstate = deflateStored(flush);
                    break;
                case FAST:
                    bstate = deflateFast(flush);
                    break;
                case SLOW:
                    bstate = deflateSlow(flush);
                    break;
                default:
            }

            if (bstate == FINISH_STARTED || bstate == FINISH_DONE) {
                status = FINISH_STATE;
            }
            if (bstate == NEED_MORE || bstate == FINISH_STARTED) {
                if (strm.availout == 0) {
                    lastFlush = -1; // avoid BUF_ERROR next call, see above
                }
                return Z_OK;
                // If flush != Z_NO_FLUSH && avail_out == 0, the next call
                // of deflate should use the same flush parameter to make sure
                // that the flush is complete. So we don't have to output an
                // empty block here, this will be done at next call. This also
                // ensures that for a very small output buffer, we emit at most
                // one empty block.
            }

            if (bstate == BLOCK_DONE) {
                if (flush == Z_PARTIAL_FLUSH) {
                    trAlign();
                } else { // FULL_FLUSH or SYNC_FLUSH
                    trStoredBlock(0, 0, false);
                    // For a full flush, this empty block will be recognized
                    // as a special marker by inflate_sync().
                    if (flush == Z_FULL_FLUSH) {
                        //state.head[s.hash_size-1]=0;
                        for (int i = 0; i < hashSize/*-1*/; i++) // forget history
                        {
                            head[i] = 0;
                        }
                    }
                }
                strm.flushPending();
                if (strm.availout == 0) {
                    lastFlush = -1; // avoid BUF_ERROR at next call, see above
                    return Z_OK;
                }
            }
        }

        if (flush != Z_FINISH) {
            return Z_OK;
        }
        if (noheader != 0) {
            return Z_STREAM_END;
        }

        // Write the zlib trailer (adler32)
        putShortMSB((int) (strm.adler >>> 16));
        putShortMSB((int) (strm.adler & 0xffff));
        strm.flushPending();

        // If avail_out is zero, the application will call deflate again
        // to flush the rest.
        noheader = -1; // write the trailer only once!
        return pending != 0 ? Z_OK : Z_STREAM_END;
    }
}
