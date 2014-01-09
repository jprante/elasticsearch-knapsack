
package org.xbib.io.archivers.zip;

/**
 * Various constants used throughout the package.
 */
interface ZipConstants {

    /**
     * Masks last eight bits
     */
    int BYTE_MASK = 0xFF;

    /**
     * length of a ZipShort in bytes
     */
    int SHORT = 2;

    /**
     * length of a ZipLong in bytes
     */
    int WORD = 4;

    /**
     * length of a ZipEightByteInteger in bytes
     */
    int DWORD = 8;

    /**
     * Initial ZIP specification version
     */
    int INITIAL_VERSION = 10;

    /**
     * ZIP specification version that introduced data descriptor method
     */
    int DATA_DESCRIPTOR_MIN_VERSION = 20;

    /**
     * ZIP specification version that introduced ZIP64
     */
    int ZIP64_MIN_VERSION = 45;

    /**
     * Value stored in two-byte size and similar fields if ZIP64
     * extensions are used.
     */
    int ZIP64_MAGIC_SHORT = 0xFFFF;

    /**
     * Value stored in four-byte size and similar fields if ZIP64
     * extensions are used.
     */
    long ZIP64_MAGIC = 0xFFFFFFFFL;

}
