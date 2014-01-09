
package org.xbib.io.compress.zlib;

public interface ZConstants {

    // compression levels
    int Z_NO_COMPRESSION = 0;
    int Z_BEST_SPEED = 1;
    int Z_BEST_COMPRESSION = 9;
    int Z_DEFAULT_COMPRESSION = (-1);

    // compression strategy
    int Z_FILTERED = 1;
    int Z_HUFFMAN_ONLY = 2;
    int Z_DEFAULT_STRATEGY = 0;
    int Z_NO_FLUSH = 0;
    int Z_PARTIAL_FLUSH = 1;
    int Z_SYNC_FLUSH = 2;
    int Z_FULL_FLUSH = 3;
    int Z_FINISH = 4;
    int Z_OK = 0;
    int Z_STREAM_END = 1;
    int Z_NEED_DICT = 2;
    int Z_ERRNO = -1;
    int Z_STREAM_ERROR = -2;
    int Z_DATA_ERROR = -3;
    int Z_MEM_ERROR = -4;
    int Z_BUF_ERROR = -5;
    int Z_VERSION_ERROR = -6;

    int MAX_WBITS = 15;        // 32K LZ77 window

}
