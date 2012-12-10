package org.xbib.io.compress.gzip;

import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipException;
import org.xbib.io.compress.BufferRecycler;
import org.xbib.io.compress.DataHandler;
import org.xbib.io.compress.Uncompressor;

/**
 * {@link com.ning.compress.Uncompressor} implementation for uncompressing GZIP
 * encoded data in "push" mode, in which input is not read using
 * {@link java.io.InputStream} but rather pushed to uncompressor in variable
 * length chunks.
 */
public class GZIPUncompressor extends Uncompressor {
    /*
     * GZIP constants
     */

    // little-endian marker bytes:
    protected final static int GZIP_MAGIC = 0x8b1f;
    protected final static byte GZIP_MAGIC_0 = (byte) (GZIP_MAGIC & 0xFF);
    protected final static byte GZIP_MAGIC_1 = (byte) (GZIP_MAGIC >> 8);
    // // // File header flags.
    //protected final static int FTEXT    = 1;    // Extra text
    protected final static int FHCRC = 2;    // Header CRC
    protected final static int FEXTRA = 4;    // Extra field
    protected final static int FNAME = 8;    // File name
    protected final static int FCOMMENT = 16;   // File comment
    /**
     * Size of input chunks fed to underlying decoder. Since it is not 100%
     * clear what its effects are on
     */
    protected final static int DEFAULT_CHUNK_SIZE = 4096;
    /**
     * For decoding we should use buffer that is big enough to contain typical
     * amount of decoded data; 64k seems like a nice big number
     */
    protected final static int DECODE_BUFFER_SIZE = 0xFFFF;
    /*
     ///////////////////////////////////////////////////////////////////////
     // State constants
     ///////////////////////////////////////////////////////////////////////
     */
    /**
     * State in which a new compression stream can start.
     */
    protected final static int STATE_INITIAL = 0;
    // State in which first byte of signature has been matched, second exepcted
    protected final static int STATE_HEADER_SIG1 = 1;
    // State in which 'compression type' byte is expected
    protected final static int STATE_HEADER_COMP_TYPE = 2;
    // State in which flag byte is expected
    protected final static int STATE_HEADER_FLAGS = 3;
    // State in which we are to skip 6 bytes 
    protected final static int STATE_HEADER_SKIP = 4;
    protected final static int STATE_HEADER_EXTRA0 = 5;
    protected final static int STATE_HEADER_EXTRA1 = 6;
    protected final static int STATE_HEADER_FNAME = 7;
    protected final static int STATE_HEADER_COMMENT = 8;
    protected final static int STATE_HEADER_CRC0 = 9;
    protected final static int STATE_HEADER_CRC1 = 10;
    protected final static int STATE_TRAILER_INITIAL = 11;
    protected final static int STATE_TRAILER_CRC1 = 12;
    protected final static int STATE_TRAILER_CRC2 = 13;
    protected final static int STATE_TRAILER_CRC3 = 14;
    protected final static int STATE_TRAILER_LEN0 = 15;
    protected final static int STATE_TRAILER_LEN1 = 16;
    protected final static int STATE_TRAILER_LEN2 = 17;
    protected final static int STATE_TRAILER_LEN3 = 18;
    /**
     * State in which we are buffering compressed data for decompression
     */
    protected final static int STATE_BODY = 20;
    /*
     ///////////////////////////////////////////////////////////////////////
     // Configuration, helper objects
     ///////////////////////////////////////////////////////////////////////
     */
    /**
     * Handler that will receive uncompressed data.
     */
    protected final DataHandler _handler;
    /**
     * Object that handles details of buffer recycling
     */
    protected final BufferRecycler _recycler;
    protected final GZIPRecycler _gzipRecycler;
    protected Inflater _inflater;
    protected final CRC32 _crc;
    protected final int _inputChunkLength;
    /**
     * Buffer used for data uncompressed from
     * <code>_inputBuffer</code>.
     */
    protected byte[] _decodeBuffer;
    /*
     ///////////////////////////////////////////////////////////////////////
     // Decoder state
     ///////////////////////////////////////////////////////////////////////
     */
    /**
     * Current decoding state, which determines meaning of following byte(s).
     */
    protected int _state = STATE_INITIAL;
    /**
     * Header flags read from gzip header
     */
    protected int _flags;
    /**
     * Expected CRC for header, from gzip file itself.
     */
    protected int _headerCRC;
    /**
     * Simple counter used when skipping fixed number of bytes
     */
    protected int _skippedBytes;
    /**
     * CRC container in trailer, should match calculated CRC over data
     */
    protected int _trailerCRC;
    /**
     * Number of bytes that trailer indicates preceding data stream should have
     * had.
     */
    protected int _trailerCount;

    /*
     ///////////////////////////////////////////////////////////////////////
     // Instance creation
     ///////////////////////////////////////////////////////////////////////
     */
    public GZIPUncompressor(DataHandler h) {
        this(h, DEFAULT_CHUNK_SIZE);
    }

    public GZIPUncompressor(DataHandler h, int inputChunkLength) {
        _inputChunkLength = inputChunkLength;
        _handler = h;
        _recycler = BufferRecycler.instance();
        _decodeBuffer = _recycler.allocDecodeBuffer(DECODE_BUFFER_SIZE);
        _gzipRecycler = GZIPRecycler.instance();
        _inflater = _gzipRecycler.allocInflater();
        _crc = new CRC32();
    }

    /*
     ///////////////////////////////////////////////////////////////////////
     // Uncompressor API implementation
     ///////////////////////////////////////////////////////////////////////
     */
    @Override
    public void feedCompressedData(byte[] comp, int offset, int len) throws IOException {
        final int end = offset + len;
        if (_state != STATE_BODY) {
            if (_state < STATE_TRAILER_INITIAL) { // header
                offset = _handleHeader(comp, offset, end);
                if (offset >= end) { // not fully handled yet
                    return;
                }
                // fall through to body
            } else { // trailer
                offset = _handleTrailer(comp, offset, end);
                if (offset < end) { // sanity check
                    throw new IllegalStateException();
                }
                // either way, we are done
                return;
            }
        }

        // Ok, decode...
        while (true) {
            // first: if input is needed, give some
            if (_inflater.needsInput()) {
                final int left = end - offset;
                if (left < 1) { // need input but nothing to give, leve
                    return;
                }
                final int amount = Math.min(left, _inputChunkLength);
                _inflater.setInput(comp, offset, amount);
                offset += amount;
            }
            // and then see what we can get out if anything
            while (true) {
                int decoded;
                try {
                    decoded = _inflater.inflate(_decodeBuffer);
                } catch (DataFormatException e) {
                    ZipException z = new ZipException("Problems inflating gzip data: " + e.getMessage());
                    z.initCause(e);
                    throw z;
                }
                if (decoded == 0) {
                    break;
                }
                _crc.update(_decodeBuffer, 0, decoded);
                _handler.handleData(_decodeBuffer, 0, decoded);
            }
            if (_inflater.finished() || _inflater.needsDictionary()) {
                _state = STATE_TRAILER_INITIAL;
                // also: push back some of data that is buffered
                int remains = _inflater.getRemaining();
                if (remains > 0) {
                    offset -= remains;
                }
                break;
            }
        }

        // finally; handle trailer if we got this far
        offset = _handleTrailer(comp, offset, end);
        if (offset < end) { // sanity check
            throw new IllegalStateException();
        }
    }

    @Override
    public void complete() throws IOException {
        byte[] b = _decodeBuffer;
        if (b != null) {
            _decodeBuffer = null;
            _recycler.releaseDecodeBuffer(b);
        }
        Inflater i = _inflater;
        if (i != null) {
            _inflater = null;
            _gzipRecycler.releaseInflater(i);
        }
        // 24-May-2012, tatu: Should we call this here; or fail with exception?
        _handler.allDataHandled();
        if (_state != STATE_INITIAL) {
            if (_state >= STATE_TRAILER_INITIAL) {
                if (_state == STATE_BODY) {
                    throw new ZipException("Invalid GZIP stream: end-of-input in the middle of compressed data");
                }
                throw new ZipException("Invalid GZIP stream: end-of-input in the trailer (state: " + _state + ")");
            }
            throw new ZipException("Invalid GZIP stream: end-of-input in header (state: " + _state + ")");
        }
    }

    /*
     // Helper methods, header/trailer
     */
    protected final boolean _hasFlag(int flag) {
        return (_flags & flag) == flag;
    }

    private int _handleHeader(byte[] comp, int offset, final int end) throws IOException {

        main_loop:
        while (offset < end) {
            byte b = comp[offset++];
            _crc.update(b);

            switch (_state) {
                case STATE_INITIAL:
                    if (b != GZIP_MAGIC_0) {
                        _reportBadHeader(comp, offset, end, 0);
                    }
                    if (offset >= end) {
                        _state = STATE_HEADER_SIG1;
                        break;
                    }
                    b = comp[offset++];
                    _crc.update(b);
                // fall through
                case STATE_HEADER_SIG1:
                    if (b != GZIP_MAGIC_1) {
                        _reportBadHeader(comp, offset, end, 1);
                    }
                    if (offset >= end) {
                        _state = STATE_HEADER_COMP_TYPE;
                        break;
                    }
                    b = comp[offset++];
                    _crc.update(b);
                // fall through
                case STATE_HEADER_COMP_TYPE:
                    if (b != Deflater.DEFLATED) {
                        _reportBadHeader(comp, offset, end, 1);
                    }
                    if (offset >= end) {
                        _state = STATE_HEADER_FLAGS;
                        break;
                    }
                    b = comp[offset++];
                    _crc.update(b);
                // fall through
                case STATE_HEADER_FLAGS:
                    _flags = b; // should we validate these?
                    _skippedBytes = 0;
                    _state = STATE_HEADER_SKIP;
                    if (offset >= end) {
                        break;
                    }
                    b = comp[offset++];
                    _crc.update(b);
                // fall through
                case STATE_HEADER_SKIP:
                    while (++_skippedBytes < 6) {
                        if (offset >= end) {
                            break main_loop;
                        }
                        b = comp[offset++];
                        _crc.update(b);
                    }
                    if (_hasFlag(FEXTRA)) {
                        _state = STATE_HEADER_EXTRA0;
                    } else if (_hasFlag(FNAME)) {
                        _state = STATE_HEADER_FNAME;
                    } else if (_hasFlag(FCOMMENT)) {
                        _state = STATE_HEADER_COMMENT;
                    } else if (_hasFlag(FHCRC)) {
                        _state = STATE_HEADER_CRC0;
                    } else { // no extras... body, I guess?
                        _state = STATE_BODY;
                        break main_loop;
                    }
                    // let's keep things simple, do explicit re-loop to sort it out:
                    continue;
                case STATE_HEADER_EXTRA0:
                    _state = STATE_HEADER_EXTRA1;
                    break;
                case STATE_HEADER_EXTRA1:
                    if (_hasFlag(FNAME)) {
                        _state = STATE_HEADER_FNAME;
                    } else if (_hasFlag(FCOMMENT)) {
                        _state = STATE_HEADER_COMMENT;
                    } else if (_hasFlag(FHCRC)) {
                        _state = STATE_HEADER_CRC0;
                    } else {
                        _state = STATE_BODY;
                        break main_loop;
                    }
                    break;
                case STATE_HEADER_FNAME: // skip until zero
                    while (b != 0) {
                        if (offset >= end) {
                            break main_loop;
                        }
                        b = comp[offset++];
                        _crc.update(b);
                    }
                    if (_hasFlag(FCOMMENT)) {
                        _state = STATE_HEADER_COMMENT;
                    } else if (_hasFlag(FHCRC)) {
                        _state = STATE_HEADER_CRC0;
                    } else {
                        _state = STATE_BODY;
                        break main_loop;
                    }
                    break;
                case STATE_HEADER_COMMENT:
                    while (b != 0) {
                        if (offset >= end) {
                            break main_loop;
                        }
                        b = comp[offset++];
                    }
                    if (_hasFlag(FHCRC)) {
                        _state = STATE_HEADER_CRC0;
                    } else {
                        _state = STATE_BODY;
                        break main_loop;
                    }
                    break;
                case STATE_HEADER_CRC0:
                    _headerCRC = b & 0xFF;
                    if (offset >= end) {
                        _state = STATE_HEADER_CRC1;
                        break;
                    }
                    b = comp[offset++];
                    _crc.update(b);
                // fall through
                case STATE_HEADER_CRC1:
                    _headerCRC += ((b & 0xFF) << 8);
                    int act = (int) _crc.getValue() & 0xffff;
                    if (act != _headerCRC) {
                        throw new ZipException("Corrupt GZIP header: header CRC 0x"
                                + Integer.toHexString(act) + ", expected 0x "
                                + Integer.toHexString(_headerCRC));
                    }
                    _state = STATE_BODY;
                    break main_loop;
                default:
                    throw new IllegalStateException("Unknown header state: " + _state);
            }
        }
        if (_state == STATE_BODY) {
            _crc.reset();
        }
        return offset;
    }

    private int _handleTrailer(byte[] comp, int offset, final int end) throws IOException {
        while (offset < end) {
            byte b = comp[offset++];

            switch (_state) {
                case STATE_TRAILER_INITIAL:
                    _trailerCRC = b & 0xFF;
                    _state = STATE_TRAILER_CRC1;
                    break;
                case STATE_TRAILER_CRC1:
                    _trailerCRC += (b & 0xFF) << 8;
                    _state = STATE_TRAILER_CRC2;
                    break;
                case STATE_TRAILER_CRC2:
                    _trailerCRC += (b & 0xFF) << 16;
                    _state = STATE_TRAILER_CRC3;
                    break;
                case STATE_TRAILER_CRC3:
                    _trailerCRC += (b & 0xFF) << 24;
                    final int actCRC = (int) _crc.getValue();
                    // verify CRC:
                    if (_trailerCRC != actCRC) {
                        throw new ZipException("Corrupt block or trailer: expected CRC "
                                + Integer.toHexString(_trailerCRC) + ", computed " + Integer.toHexString(actCRC));
                    }
                    _state = STATE_TRAILER_LEN0;
                    break;
                case STATE_TRAILER_LEN0:
                    _trailerCount = b & 0xFF;
                    _state = STATE_TRAILER_LEN1;
                    break;
                case STATE_TRAILER_LEN1:
                    _trailerCount += (b & 0xFF) << 8;
                    _state = STATE_TRAILER_LEN2;
                    break;
                case STATE_TRAILER_LEN2:
                    _trailerCount += (b & 0xFF) << 16;
                    _state = STATE_TRAILER_LEN3;
                    break;
                case STATE_TRAILER_LEN3:
                    _trailerCount += (b & 0xFF) << 24;
                    _state = STATE_INITIAL;
                    // Verify count...
                    int actCount32 = (int) _inflater.getBytesWritten();

                    if (actCount32 != _trailerCount) {
                        throw new ZipException("Corrupt block or trailed: expected byte count " + _trailerCount + ", read " + actCount32);
                    }
                    break;
                default:
                    throw new IllegalStateException("Unknown trailer state: " + _state);
            }
        }
        return offset;
    }

    /*
     ///////////////////////////////////////////////////////////////////////
     // Helper methods, other
     ///////////////////////////////////////////////////////////////////////
     */
    protected void _reportBadHeader(byte[] comp, int nextOffset, int end, int relative)
            throws IOException {
        String byteStr = "0x" + Integer.toHexString(comp[nextOffset] & 0xFF);
        if (relative <= 1) {
            int exp = (relative == 0) ? (GZIP_MAGIC & 0xFF) : (GZIP_MAGIC >> 8);
            --nextOffset;
            throw new ZipException("Bad GZIP stream: byte #" + relative + " of header not '"
                    + exp + "' (0x" + Integer.toHexString(exp) + ") but " + byteStr);
        }
        if (relative == 2) { // odd that 
            throw new ZipException("Bad GZIP stream: byte #2 of header invalid: type " + byteStr
                    + " not supported, 0x" + Integer.toHexString(Deflater.DEFLATED)
                    + " expected");
        }
        throw new IOException("Bad GZIP stream: byte #" + relative + " of header invalid: " + byteStr);
    }
}
