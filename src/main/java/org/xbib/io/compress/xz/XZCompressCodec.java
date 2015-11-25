package org.xbib.io.compress.xz;

import org.xbib.io.compress.CompressCodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class XZCompressCodec implements CompressCodec<XZInputStream, XZOutputStream> {

    @Override
    public String getName() {
        return "xz";
    }

    @Override
    public XZInputStream decode(InputStream in) throws IOException {
        return new XZInputStream(in);
    }

    @Override
    public XZInputStream decode(InputStream in, int bufsize) throws IOException {
        return new XZInputStream(in, bufsize / 1024); // KB limit
    }

    @Override
    public XZOutputStream encode(OutputStream out) throws IOException {
        return new XZOutputStream(out, new LZMA2Options());
    }

    @Override
    public XZOutputStream encode(OutputStream out, int bufsize) throws IOException {
        return new XZOutputStream(out, new LZMA2Options()); // ignore bufsize
    }
}
