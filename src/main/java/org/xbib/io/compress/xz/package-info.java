/**
 * XZ data compression
 *
 * This aims to be a complete implementation of XZ
 * data compression in pure Java. Features: <ul> <li>Full support for the .xz
 * file format specification version 1.0.4</li> <li>Single-threaded streamed
 * compression and decompression</li> <li>Single-threaded decompression with
 * limited random access support</li> <li>Raw streams (no .xz headers) for
 * advanced users, including LZMA2 with preset dictionary</li> </ul> <p>
 * Threading is planned but it is unknown when it will be implemented.
 *
 * Start by reading the documentation of
 * {@link org.xbib.io.compress.xz.XZOutputStream} and
 * {@link org.xbib.io.compress.xz.XZInputStream}. If you use XZ inside another file
 * format or protocol, see also {@link org.xbib.io.compress.xz.SingleXZInputStream}.

 */
package org.xbib.io.compress.xz;
