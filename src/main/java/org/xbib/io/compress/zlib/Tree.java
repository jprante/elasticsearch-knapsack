
package org.xbib.io.compress.zlib;

public class Tree {

    private static final int MAX_BITS = 15;
    private static final int LITERALS = 256;
    private static final int LENGTH_CODES = 29;
    private static final int L_CODES = (LITERALS + 1 + LENGTH_CODES);
    private static final int HEAP_SIZE = (2 * L_CODES + 1);
    // extra bits for each length code
    protected static final int[] EXTRA_LBITS = {
            0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
    };
    // extra bits for each distance code
    protected static final int[] EXTRA_DBITS = {
            0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
    };
    // extra bits for each bit length code
    protected static final int[] EXTRA_BLBITS = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 7
    };
    protected static final byte[] BL_ORDER = {
            16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
    private static final byte[] DIST_CODE = {
            0, 1, 2, 3, 4, 4, 5, 5, 6, 6, 6, 6, 7, 7, 7, 7, 8, 8, 8, 8,
            8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 10, 10, 10, 10, 10, 10, 10, 10,
            10, 10, 10, 10, 10, 10, 10, 10, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11,
            11, 11, 11, 11, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
            12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 13, 13, 13, 13,
            13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13,
            13, 13, 13, 13, 13, 13, 13, 13, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 15, 15, 15, 15, 15, 15, 15, 15,
            15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
            15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
            15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 0, 0, 16, 17,
            18, 18, 19, 19, 20, 20, 20, 20, 21, 21, 21, 21, 22, 22, 22, 22, 22, 22, 22, 22,
            23, 23, 23, 23, 23, 23, 23, 23, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
            24, 24, 24, 24, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25,
            26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26,
            26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 27, 27, 27, 27, 27, 27, 27, 27,
            27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27,
            27, 27, 27, 27, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
            28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
            28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
            28, 28, 28, 28, 28, 28, 28, 28, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29,
            29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29,
            29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29,
            29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29, 29
    };
    protected static final byte[] LENGTH_CODE = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 12, 12,
            13, 13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15, 16, 16, 16, 16, 16, 16, 16, 16,
            17, 17, 17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 18, 18, 19, 19, 19, 19,
            19, 19, 19, 19, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20,
            21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 22, 22, 22, 22,
            22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 23, 23, 23, 23, 23, 23, 23, 23,
            23, 23, 23, 23, 23, 23, 23, 23, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
            24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
            25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25,
            25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 26, 26, 26, 26, 26, 26, 26, 26,
            26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26, 26,
            26, 26, 26, 26, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27,
            27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 27, 28
    };
    protected static final int[] BASE_LENGTH = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56,
            64, 80, 96, 112, 128, 160, 192, 224, 0
    };
    protected static final int[] BASE_DIST = {
            0, 1, 2, 3, 4, 6, 8, 12, 16, 24,
            32, 48, 64, 96, 128, 192, 256, 384, 512, 768,
            1024, 1536, 2048, 3072, 4096, 6144, 8192, 12288, 16384, 24576
    };

    // Mapping from a distance to a distance code. dist is the distance - 1 and
    // must not have side effects. DIST_CODE[256] and DIST_CODE[257] are never
    // used.
    protected static int distanceCode(int dist) {
        return ((dist) < 256 ? DIST_CODE[dist] : DIST_CODE[256 + ((dist) >>> 7)]);
    }

    protected short[] dynTree;      // the dynamic tree
    protected int maxCode;      // largest code with non zero frequency
    protected StaticTree statDesc;  // the corresponding static tree

    // Compute the optimal bit lengths for a tree and update the total bit length
    // for the current block.
    // IN assertion: the fields freq and dad are set, heap[heap_max] and
    //    above are the tree nodes sorted by increasing frequency.
    // OUT assertions: the field len is set to the optimal bit length, the
    //     array bl_count contains the frequencies for each bit length.
    //     The length opt_len is updated; static_len is also updated if stree is
    //     not null.
    private void genBitLen(Deflate s) {
        short[] tree = dynTree;
        short[] stree = statDesc.statictree;
        int[] extra = statDesc.extrabits;
        int base = statDesc.extrabase;
        int max_length = statDesc.maxlength;
        int h;              // heap index
        int n, m;           // iterate over the tree elements
        int bits;           // bit length
        int xbits;          // extra bits
        short f;            // frequency
        int overflow = 0;   // number of elements with bit length too large

        for (bits = 0; bits <= MAX_BITS; bits++) {
            s.blCount[bits] = 0;
        }

        // In a first pass, compute the optimal bit lengths (which may
        // overflow in the case of the bit length tree).
        tree[s.heap[s.heapMax] * 2 + 1] = 0; // root of the heap

        for (h = s.heapMax + 1; h < HEAP_SIZE; h++) {
            n = s.heap[h];
            bits = tree[tree[n * 2 + 1] * 2 + 1] + 1;
            if (bits > max_length) {
                bits = max_length;
                overflow++;
            }
            tree[n * 2 + 1] = (short) bits;
            // We overwrite tree[n*2+1] which is no longer needed

            if (n > maxCode) {
                continue;  // not a leaf node
            }
            s.blCount[bits]++;
            xbits = 0;
            if (n >= base) {
                xbits = extra[n - base];
            }
            f = tree[n * 2];
            s.optLen += f * (bits + xbits);
            if (stree != null) {
                s.staticLen += f * (stree[n * 2 + 1] + xbits);
            }
        }
        if (overflow == 0) {
            return;
        }

        // This happens for example on obj2 and pic of the Calgary corpus
        // Find the first bit length which could increase:
        do {
            bits = max_length - 1;
            while (s.blCount[bits] == 0) {
                bits--;
            }
            s.blCount[bits]--;      // move one leaf down the tree
            s.blCount[bits + 1] += 2;   // move one overflow item as its brother
            s.blCount[max_length]--;
            // The brother of the overflow item also moves one step up,
            // but this does not affect bl_count[max_length]
            overflow -= 2;
        } while (overflow > 0);

        for (bits = max_length; bits != 0; bits--) {
            n = s.blCount[bits];
            while (n != 0) {
                m = s.heap[--h];
                if (m > maxCode) {
                    continue;
                }
                if (tree[m * 2 + 1] != bits) {
                    s.optLen += ((long) bits - (long) tree[m * 2 + 1]) * (long) tree[m * 2];
                    tree[m * 2 + 1] = (short) bits;
                }
                n--;
            }
        }
    }

    // Construct one Huffman tree and assigns the code bit strings and lengths.
    // Update the total bit length for the current block.
    // IN assertion: the field freq is set for all tree elements.
    // OUT assertions: the fields len and code are set to the optimal bit length
    //     and corresponding code. The length opt_len is updated; static_len is
    //     also updated if stree is not null. The field max_code is set.
    protected void buildTree(Deflate s) {
        short[] tree = dynTree;
        short[] stree = statDesc.statictree;
        int elems = statDesc.elems;
        int n, m;          // iterate over heap elements
        int maxCode = -1;   // largest code with non zero frequency
        int node;          // new node being created

        // Construct the initial heap, with least frequent element in
        // heap[1]. The sons of heap[n] are heap[2*n] and heap[2*n+1].
        // heap[0] is not used.
        s.heapLen = 0;
        s.heapMax = HEAP_SIZE;

        for (n = 0; n < elems; n++) {
            if (tree[n * 2] != 0) {
                s.heap[++s.heapLen] = maxCode = n;
                s.depth[n] = 0;
            } else {
                tree[n * 2 + 1] = 0;
            }
        }

        // The pkzip format requires that at least one distance code exists,
        // and that at least one bit should be sent even if there is only one
        // possible code. So to avoid special checks later on we force at least
        // two codes of non zero frequency.
        while (s.heapLen < 2) {
            node = s.heap[++s.heapLen] = (maxCode < 2 ? ++maxCode : 0);
            tree[node * 2] = 1;
            s.depth[node] = 0;
            s.optLen--;
            if (stree != null) {
                s.staticLen -= stree[node * 2 + 1];
            }
            // node is 0 or 1 so it does not have extra bits
        }
        this.maxCode = maxCode;

        // The elements heap[heap_len/2+1 .. heap_len] are leaves of the tree,
        // establish sub-heaps of increasing lengths:

        for (n = s.heapLen / 2; n >= 1; n--) {
            s.pqdownheap(tree, n);
        }

        // Construct the Huffman tree by repeatedly combining the least two
        // frequent nodes.

        node = elems;                 // next internal node of the tree
        do {
            // n = node of least frequency
            n = s.heap[1];
            s.heap[1] = s.heap[s.heapLen--];
            s.pqdownheap(tree, 1);
            m = s.heap[1];                // m = node of next least frequency

            s.heap[--s.heapMax] = n; // keep the nodes sorted by frequency
            s.heap[--s.heapMax] = m;

            // Create a new node father of n and m
            tree[node * 2] = (short) (tree[n * 2] + tree[m * 2]);
            s.depth[node] = (byte) (Math.max(s.depth[n], s.depth[m]) + 1);
            tree[n * 2 + 1] = tree[m * 2 + 1] = (short) node;

            // and insert the new node in the heap
            s.heap[1] = node++;
            s.pqdownheap(tree, 1);
        } while (s.heapLen >= 2);

        s.heap[--s.heapMax] = s.heap[1];

        // At this point, the fields freq and dad are set. We can now
        // generate the bit lengths.

        genBitLen(s);

        // The field len is now set, we can generate the bit codes
        genCodes(tree, maxCode, s.blCount);
    }

    // Generate the codes for a given tree and bit counts (which need not be
    // optimal).
    // IN assertion: the array bl_count contains the bit length statistics for
    // the given tree and the field len is set for all tree elements.
    // OUT assertion: the field code is set for all tree elements of non
    //     zero code length.
    private void genCodes(short[] tree, // the tree to decorate
                          int max_code, // largest code with non zero frequency
                          short[] bl_count // number of codes at each bit length
    ) {
        short[] next_code = new short[MAX_BITS + 1]; // next code value for each bit length
        short code = 0;            // running code value
        int bits;                  // bit index
        int n;                     // code index

        // The distribution counts are first used to generate the code values
        // without bit reversal.
        for (bits = 1; bits <= MAX_BITS; bits++) {
            next_code[bits] = code = (short) ((code + bl_count[bits - 1]) << 1);
        }

        // Check that the bit counts in bl_count are consistent. The last code
        // must be all ones.
        //Assert (code + bl_count[MAX_BITS]-1 == (1<<MAX_BITS)-1,
        //        "inconsistent bit counts");
        //Tracev((stderr,"\ngen_codes: max_code %d ", max_code));

        for (n = 0; n <= max_code; n++) {
            int len = tree[n * 2 + 1];
            if (len == 0) {
                continue;
            }
            // Now reverse the bits
            tree[n * 2] = (short) (biReverse(next_code[len]++, len));
        }
    }

    // Reverse the first len bits of a code, using straightforward code (a faster
    // method would use a table)
    // IN assertion: 1 <= len <= 15
    private int biReverse(int code, // the value to invert
                          int len // its bit length
    ) {
        int res = 0;
        do {
            res |= code & 1;
            code >>>= 1;
            res <<= 1;
        } while (--len > 0);
        return res >>> 1;
    }
}

