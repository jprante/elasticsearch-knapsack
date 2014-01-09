
package org.xbib.io.compress.xz;

import org.xbib.io.compress.xz.simple.X86;

import java.io.InputStream;

/**
 * BCJ filter for x86 (32-bit and 64-bit) instructions.
 */
public class X86Options extends BCJOptions {
    private static final int ALIGNMENT = 1;

    public X86Options() {
        super(ALIGNMENT);
    }

    public FinishableOutputStream getOutputStream(FinishableOutputStream out) {
        return new SimpleOutputStream(out, new X86(true, startOffset));
    }

    public InputStream getInputStream(InputStream in) {
        return new SimpleInputStream(in, new X86(false, startOffset));
    }

    FilterEncoder getFilterEncoder() {
        return new BCJEncoder(this, BCJCoder.X86_FILTER_ID);
    }
}
