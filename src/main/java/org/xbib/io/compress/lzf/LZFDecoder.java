
package org.xbib.io.compress.lzf;

import java.io.IOException;

/**
 * Decoder that handles decoding of sequence of encoded LZF chunks, combining
 * them into a single contiguous result byte array. As of version 0.9, this
 * class has been mostly replaced by {@link ChunkDecoder}, although static
 * methods are left here and may still be used for convenience. All static
 * methods use {@link ChunkDecoderFactory#optimalInstance} to find actual
 * {@link ChunkDecoder} instance to use.
 */
public class LZFDecoder {

    public static byte[] decode(final byte[] inputBuffer) throws IOException {
        return decode(inputBuffer, 0, inputBuffer.length);
    }

    public static byte[] decode(final byte[] inputBuffer, int offset, int length) throws IOException {
        return ChunkDecoderFactory.optimalInstance().decode(inputBuffer, offset, length);
    }

    public static int decode(final byte[] inputBuffer, final byte[] targetBuffer) throws IOException {
        return decode(inputBuffer, 0, inputBuffer.length, targetBuffer);
    }

    public static int decode(final byte[] sourceBuffer, int offset, int length, final byte[] targetBuffer) throws IOException {
        return ChunkDecoderFactory.optimalInstance().decode(sourceBuffer, offset, length, targetBuffer);
    }

    public static int calculateUncompressedSize(byte[] data, int offset, int length) throws IOException {
        return ChunkDecoder.calculateUncompressedSize(data, length, length);
    }
}
