
package org.xbib.io.compress.xz.delta;

public class DeltaDecoder extends DeltaCoder {
    public DeltaDecoder(int distance) {
        super(distance);
    }

    public void decode(byte[] buf, int off, int len) {
        int end = off + len;
        for (int i = off; i < end; ++i) {
            buf[i] += history[(distance + pos) & DISTANCE_MASK];
            history[pos-- & DISTANCE_MASK] = buf[i];
        }
    }
}
