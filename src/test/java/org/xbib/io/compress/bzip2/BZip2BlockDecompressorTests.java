package org.xbib.io.compress.bzip2;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

/**
 * Tests BZip2BlockDecompressor
 */
public class BZip2BlockDecompressorTests {

	/**
	 * Tests decoding an invalid BZip2 block with zero huffman tables
	 * @throws Exception
	 */
	@Test
	public void testErrorZeroTables() throws Exception {

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		BZip2BitOutputStream bitOutputStream = new BZip2BitOutputStream (outputStream);
		bitOutputStream.writeInteger (0); // block CRC
		bitOutputStream.writeBoolean (false); // randomisation
		bitOutputStream.writeBits (24, 0); // start pointer
		bitOutputStream.writeBits (16, 0); // huffman used ranges
		bitOutputStream.writeBits (3, 0); // total tables
		bitOutputStream.writeBits (15, 1); // total selectors
		bitOutputStream.flush();

		BZip2BitInputStream bitInputStream = new BZip2BitInputStream (new ByteArrayInputStream (outputStream.toByteArray()));

		try {
			new BZip2BlockDecompressor (bitInputStream, 900000);
			fail();
		} catch (IOException e) {
			assertEquals ("BZip2 block Huffman tables invalid", e.getMessage());
		}

	}


	/**
	 * Tests decoding an invalid BZip2 block with one huffman table
	 * @throws Exception
	 */
	@Test
	public void testErrorOneTable() throws Exception {

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		BZip2BitOutputStream bitOutputStream = new BZip2BitOutputStream (outputStream);
		bitOutputStream.writeInteger (0); // block CRC
		bitOutputStream.writeBoolean (false); // randomisation
		bitOutputStream.writeBits (24, 0); // start pointer
		bitOutputStream.writeBits (16, 0); // huffman used ranges
		bitOutputStream.writeBits (3, 1); // total tables
		bitOutputStream.writeBits (15, 1); // total selectors
		bitOutputStream.flush();

		BZip2BitInputStream bitInputStream = new BZip2BitInputStream (new ByteArrayInputStream (outputStream.toByteArray()));

		try {
			new BZip2BlockDecompressor (bitInputStream, 900000);
			fail();
		} catch (IOException e) {
			assertEquals ("BZip2 block Huffman tables invalid", e.getMessage());
		}

	}


	/**
	 * Tests decoding an invalid BZip2 block with 7 huffman tables
	 * @throws Exception
	 */
	@Test
	public void tesErrortSevenTables() throws Exception {

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		BZip2BitOutputStream bitOutputStream = new BZip2BitOutputStream (outputStream);
		bitOutputStream.writeInteger (0); // block CRC
		bitOutputStream.writeBoolean (false); // randomisation
		bitOutputStream.writeBits (24, 0); // start pointer
		bitOutputStream.writeBits (16, 0); // huffman used ranges
		bitOutputStream.writeBits (3, 7); // total tables
		bitOutputStream.writeBits (15, 1); // total selectors
		bitOutputStream.flush();

		BZip2BitInputStream bitInputStream = new BZip2BitInputStream (new ByteArrayInputStream (outputStream.toByteArray()));

		try {
			new BZip2BlockDecompressor (bitInputStream, 900000);
			fail();
		} catch (IOException e) {
			assertEquals ("BZip2 block Huffman tables invalid", e.getMessage());
		}

	}

	/**
	 * Tests decoding an invalid BZip2 block with zero selectors
	 * @throws Exception
	 */
	@Test
	public void testErrorZeroSelectors() throws Exception {

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		BZip2BitOutputStream bitOutputStream = new BZip2BitOutputStream (outputStream);
		bitOutputStream.writeInteger (0); // block CRC
		bitOutputStream.writeBoolean (false); // randomisation
		bitOutputStream.writeBits (24, 0); // start pointer
		bitOutputStream.writeBits (16, 0); // huffman used ranges
		bitOutputStream.writeBits (3, 2); // total tables
		bitOutputStream.writeBits (15, 0); // total selectors
		bitOutputStream.flush();

		BZip2BitInputStream bitInputStream = new BZip2BitInputStream (new ByteArrayInputStream (outputStream.toByteArray()));

		try {
			new BZip2BlockDecompressor (bitInputStream, 900000);
			fail();
		} catch (IOException e) {
			assertEquals ("BZip2 block Huffman tables invalid", e.getMessage());
		}

	}


	/**
	 * Tests decoding an invalid BZip2 block with too many
	 * @throws Exception
	 */
	@Test
	public void testErrorTooManySelectors() throws Exception {

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		BZip2BitOutputStream bitOutputStream = new BZip2BitOutputStream (outputStream);
		bitOutputStream.writeInteger (0); // block CRC
		bitOutputStream.writeBoolean (false); // randomisation
		bitOutputStream.writeBits (24, 0); // start pointer
		bitOutputStream.writeBits (16, 0); // huffman used ranges
		bitOutputStream.writeBits (3, 2); // total tables
		bitOutputStream.writeBits (15, 18002); // total selectors
		bitOutputStream.flush();

		BZip2BitInputStream bitInputStream = new BZip2BitInputStream (new ByteArrayInputStream (outputStream.toByteArray()));

		try {
			new BZip2BlockDecompressor (bitInputStream, 900000);
			fail();
		} catch (IOException e) {
			assertEquals ("BZip2 block Huffman tables invalid", e.getMessage());
		}

	}

}

