
package org.xbib.io.compress;

import org.xbib.io.StreamCodec;
import org.xbib.io.compress.bzip2.Bzip2InputStream;
import org.xbib.io.compress.bzip2.Bzip2OutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Bzip2StreamCodec implements StreamCodec<Bzip2InputStream, Bzip2OutputStream> {

    @Override
    public String getName() {
        return "bz2";
    }

    @Override
    public Bzip2InputStream decode(InputStream in) throws IOException {
        return new Bzip2InputStream(in);
    }

    @Override
    public Bzip2InputStream decode(InputStream in, int bufsize) throws IOException {
        return new Bzip2InputStream(in, bufsize);
    }

    @Override
    public Bzip2OutputStream encode(OutputStream out) throws IOException {
        return new Bzip2OutputStream(out);
    }

    @Override
    public Bzip2OutputStream encode(OutputStream out, int bufsize) throws IOException {
        return new Bzip2OutputStream(out, bufsize);
    }
}
