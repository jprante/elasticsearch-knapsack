package org.xbib.io.compress.bzip2;

import org.xbib.io.compress.CompressCodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BZip2CompressCodec implements CompressCodec<BZip2InputStream, BZip2OutputStream> {

    @Override
    public String getName() {
        return "bz2";
    }

    @Override
    public BZip2InputStream decode(InputStream in) throws IOException {
        return new BZip2InputStream(in);
    }

    @Override
    public BZip2InputStream decode(InputStream in, int bufsize) throws IOException {
        return new BZip2InputStream(in, bufsize);
    }

    @Override
    public BZip2OutputStream encode(OutputStream out) throws IOException {
        return new BZip2OutputStream(out);
    }

    @Override
    public BZip2OutputStream encode(OutputStream out, int bufsize) throws IOException {
        return new BZip2OutputStream(out, bufsize);
    }
}
