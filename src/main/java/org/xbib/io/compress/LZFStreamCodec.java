
package org.xbib.io.compress;

import org.xbib.io.StreamCodec;
import org.xbib.io.compress.lzf.LZFInputStream;
import org.xbib.io.compress.lzf.LZFOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class LZFStreamCodec implements StreamCodec<LZFInputStream, LZFOutputStream> {

    @Override
    public String getName() {
        return "lzf";
    }

    @Override
    public LZFInputStream decode(InputStream in) throws IOException {
        return new LZFInputStream(in);
    }

    @Override
    public LZFInputStream decode(InputStream in, int bufsize) throws IOException {
        return new LZFInputStream(in, true);
    }

    @Override
    public LZFOutputStream encode(OutputStream out) throws IOException {
        return new LZFOutputStream(out);
    }

    @Override
    public LZFOutputStream encode(OutputStream out, int bufsize) throws IOException {
        return new LZFOutputStream(out, bufsize);
    }
}
