
package org.xbib.io.compress.xz;

/**
 * Generic {@link java.io.IOException IOException} specific to this package.
 * The other IOExceptions in this package extend
 * from <code>XZIOException</code>.
 */
public class XZIOException extends java.io.IOException {
    private static final long serialVersionUID = 3L;

    public XZIOException() {
        super();
    }

    public XZIOException(String s) {
        super(s);
    }
}
