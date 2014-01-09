
package org.xbib.io.compress.xz;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream that supports finishing without closing
 * the underlying stream.
 */
public abstract class FinishableOutputStream extends OutputStream {
    /**
     * Finish the stream without closing the underlying stream.
     * No more data may be written to the stream after finishing.
     * <p/>
     * The <code>finish</code> method of <code>FinishableOutputStream</code>
     * does nothing. Subclasses should override it if they need finishing
     * support, which is the case, for example, with compressors.
     *
     * @throws java.io.IOException
     */
    public void finish() throws IOException {
    }

    ;
}
