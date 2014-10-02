package org.xbib.io.compress.bzip2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

/**
 * Tests BZip2HuffmanStageDecoder
 */
public class BZip2HuffmanStageDecoderTests {

	/**
	 * Tests decoding an invalid symbol
	 * @throws Exception 
	 */
	@Test(expected=IOException.class)
	public void testExceptionOnInvalidSymbol() throws Exception {

		byte[][] tableCodeLengths = { { 23, 23, 23, 22, 22, 21, 21, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 3, 3, 3, 3, 3, 3 } };
		byte[] selectors = new byte[1024];

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		BZip2BitOutputStream bitOutputStream = new BZip2BitOutputStream (outputStream);
		bitOutputStream.writeBits (23, 8388607); // This value would be the 4th 23-length code
		bitOutputStream.flush();

		BZip2BitInputStream bitInputStream = new BZip2BitInputStream (new ByteArrayInputStream (outputStream.toByteArray()));
		BZip2HuffmanStageDecoder decoder = new BZip2HuffmanStageDecoder (bitInputStream, tableCodeLengths[0].length, tableCodeLengths, selectors);

		decoder.nextSymbol();

	}


	/**
	 * Tests that an exception is thrown when there is data beyond the available selectors
	 * @throws Exception
	 */
	@Test(expected=IOException.class)
	public void testExceptionOnTooMuchData() throws Exception {

		byte[][] tableCodeLengths = { { 1, 1 } };
		byte[] selectors = new byte[1];

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		BZip2BitOutputStream bitOutputStream = new BZip2BitOutputStream (outputStream);
		for (int i = 0; i < 51; i++) {
			bitOutputStream.writeBits (1, 0);
		}
		bitOutputStream.flush();

		BZip2BitInputStream bitInputStream = new BZip2BitInputStream (new ByteArrayInputStream (outputStream.toByteArray()));
		BZip2HuffmanStageDecoder decoder = new BZip2HuffmanStageDecoder (bitInputStream, tableCodeLengths[0].length, tableCodeLengths, selectors);

		for (int i = 0; i < 51; i++) {
			decoder.nextSymbol();
		}

	}

}
