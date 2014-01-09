
package org.xbib.io.compress.xz;

interface FilterCoder {

    boolean changesSize();

    boolean nonLastOK();

    boolean lastOK();
}
