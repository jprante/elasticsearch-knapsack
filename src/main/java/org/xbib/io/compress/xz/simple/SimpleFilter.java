
package org.xbib.io.compress.xz.simple;

public interface SimpleFilter {
    int code(byte[] buf, int off, int len);
}
