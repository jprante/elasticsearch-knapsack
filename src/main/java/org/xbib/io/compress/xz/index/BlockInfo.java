
package org.xbib.io.compress.xz.index;

import org.xbib.io.compress.xz.common.StreamFlags;

public class BlockInfo {
    public StreamFlags streamFlags;
    public long compressedOffset;
    public long uncompressedOffset;
    public long unpaddedSize;
    public long uncompressedSize;
}
