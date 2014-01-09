
package org.xbib.io.compress.xz;

interface FilterEncoder extends FilterCoder {

    long getFilterID();

    byte[] getFilterProps();

    boolean supportsFlushing();

    FinishableOutputStream getOutputStream(FinishableOutputStream out);
}
