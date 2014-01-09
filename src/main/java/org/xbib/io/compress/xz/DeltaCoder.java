
package org.xbib.io.compress.xz;

abstract class DeltaCoder implements FilterCoder {
    public static final long FILTER_ID = 0x03;

    public boolean changesSize() {
        return false;
    }

    public boolean nonLastOK() {
        return true;
    }

    public boolean lastOK() {
        return false;
    }
}
