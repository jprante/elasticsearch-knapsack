
package org.xbib.io.compress.xz.check;

public class None extends Check {
    public None() {
        size = 0;
        name = "None";
    }

    public void update(byte[] buf, int off, int len) {
    }

    public byte[] finish() {
        byte[] empty = new byte[0];
        return empty;
    }
}
