package org.xbib.io.compress.bzip2;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

/**
 * Tests BitInputStream
 */
public class BZip2BitInputStreamTests {

	// Boolean

	/**
	 * Test reading 8 zeroes
	 * @throws java.io.IOException
	 */
	@Test
	public void testBooleanFalse8() throws IOException {

		byte[] testData = { 0 };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		for (int i = 0; i < 8; i++) {
			assertFalse (inputStream.readBoolean());
		}

	}


	/**
	 * Test reading 8 ones
	 * @throws java.io.IOException
	 */
	@Test
	public void testBooleanTrue8() throws IOException {

		byte[] testData = { (byte)0xff };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		for (int i = 0; i < 8; i++) {
			assertTrue (inputStream.readBoolean());
		}

	}


	/**
	 * Test reading a single 1 in any position as a boolean
	 * @throws java.io.IOException
	 */
	@Test
	public void testBooleanSingleOne() throws IOException {

		for (int i = 0; i < 8; i++) {

			byte[] testData = { (byte)(1 << (7 - i)) };
			BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));
	
			for (int j = 0; j < 8; j++) {
				if (j == i) {
					assertTrue (inputStream.readBoolean());
				} else {
					assertFalse (inputStream.readBoolean());
				}
			}

		}

	}


	/**
	 * Test reaching the end of the stream reading a boolean
	 * @throws java.io.IOException
	 */
	@Test(expected=IOException.class)
	public void testBooleanEndOfStream() throws IOException {

		byte[] testData = { };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		inputStream.readBoolean();

	}


	// Unary

	/**
	 * Test reading unary 0
	 * @throws java.io.IOException
	 */
	@Test
	public void testUnaryZero() throws IOException {

		byte[] testData = { 0x00 };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		assertEquals (0, inputStream.readUnary());

	}

	/**
	 * Test reading unary 0
	 * @throws java.io.IOException
	 */
	@Test
	public void testUnaryOne() throws IOException {

		byte[] testData = { (byte)(1 << 7) };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		assertEquals (1, inputStream.readUnary());

	}


	/**
	 * Test reading unary 0
	 * @throws java.io.IOException
	 */
	@Test
	public void testUnary31() throws IOException {

		byte[] testData = { (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xfe };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		assertEquals (31, inputStream.readUnary());

	}


	/**
	 * Test reaching the end of the stream reading a unary number
	 * @throws java.io.IOException
	 */
	@Test(expected=IOException.class)
	public void testUnaryEndOfStream() throws IOException {

		byte[] testData = { };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		inputStream.readUnary();

	}


	// Bits
	/**
	 * Test reading a single 0 as bits
	 * @throws java.io.IOException
	 */
	@Test
	public void testBits1_0() throws IOException {

		byte[] testData = { (byte)0x00 };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		assertEquals (0, inputStream.readBits(1));

	}

	/**
	 * Test reading a single 1 as bits
	 * @throws java.io.IOException
	 */
	@Test
	public void testBits1_1() throws IOException {

		byte[] testData = { (byte)(1 << 7) };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		assertEquals (1, inputStream.readBits(1));

	}


	/**
	 * Test reading 23 bits
	 * @throws java.io.IOException
	 */
	@Test
	public void testBits23() throws IOException {

		byte[] testData = { 0x02, 0x03, 0x04 };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		assertEquals (0x020304 >> 1, inputStream.readBits(23));

	}


	/**
	 * Test reaching the end of the stream reading bits
	 * @throws java.io.IOException
	 */
	@Test(expected=IOException.class)
	public void testBitsEndOfStream() throws IOException {

		byte[] testData = { };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		inputStream.readBits(1);

	}


	// Integer

	/**
	 * Test reading an integer
	 * @throws java.io.IOException
	 */
	@Test
	public void testInteger() throws IOException {

		byte[] testData = { 0x12, 0x34, 0x56, 0x78 };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		assertEquals (0x12345678, inputStream.readInteger());

	}


	/**
	 * Test reaching the end of the stream reading an integer
	 * @throws java.io.IOException
	 */
	@Test(expected=IOException.class)
	public void testIntegerEndOfStream() throws IOException {

		byte[] testData = { };
		BZip2BitInputStream inputStream = new BZip2BitInputStream (new ByteArrayInputStream (testData));

		inputStream.readInteger();

	}


}
