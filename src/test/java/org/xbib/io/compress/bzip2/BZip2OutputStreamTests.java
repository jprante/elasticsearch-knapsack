package org.xbib.io.compress.bzip2;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

/**
 * Tests BZip2OutputStream
 */
public class BZip2OutputStreamTests {

	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testEmpty() throws IOException {

		byte[] testData = new byte [0];

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Test EOF
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testOneByte() throws IOException {

		byte[] testData = new byte[] { 'A' };

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read(decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals(testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testTwoBytes() throws IOException {

		byte[] testData = new byte[] { 'B', 'A' };

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read(decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals(testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testRegular1() throws IOException {

		byte[] testData = "Mary had a little lamb, its fleece was white as snow".getBytes();

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read(decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals(testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testRegular2() throws IOException {

		byte[] testData = "Mary had a little lamb, its fleece was white as snow".getBytes();

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		for (int i = 0; i < testData.length; i++) {
			output.write (testData[i]);
		}
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read(decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals(testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testHeaderless() throws IOException {

		byte[] testData = "Mary had a little lamb, its fleece was white as snow".getBytes();

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		byte[] compressedData = byteOutput.toByteArray();
		ByteArrayInputStream byteInput = new ByteArrayInputStream (Arrays.copyOfRange (compressedData, 2, compressedData.length));
		Bzip2InputStream input = new Bzip2InputStream(byteInput, true);
		byte[] decodedTestData = new byte [testData.length];
		input.read(decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals(testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testReadPastEnd() throws IOException {

		byte[] testData = "Mary had a little lamb, its fleece was white as snow".getBytes();

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read(decodedTestData, 0, decodedTestData.length);

		// Test
		assertEquals (-1, input.read());
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testReadAfterClose() throws IOException {

		byte[] testData = "Mary had a little lamb, its fleece was white as snow".getBytes();

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);

		// Test
		input.close();
		try {
			input.read();
		} catch (IOException e) {
			assertEquals ("Stream closed", e.getMessage());
			return;
		}

		fail();

	}


	/**
	 * Test coverage : InputStream throws an exception during BZip2InputStream.close()
	 * @throws java.io.IOException
	 */
	@Test(expected=IOException.class)
	public void testExceptionDuringClose() throws IOException {

		byte[] testData = "Mary had a little lamb, its fleece was white as snow".getBytes();

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray()) {
			@Override
			public void close() throws IOException {
				throw new IOException();
			}
		};
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);

		// Test
		input.close();

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testWriteAfterClose1() throws IOException {

		byte[] testData = "Mary had a little lamb, its fleece was white as snow".getBytes();

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Test
		try {
			output.write (testData);
		} catch (IOException e) {
			assertEquals ("Stream closed", e.getMessage());
			return;
		}

		fail();

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testWriteAfterClose2() throws IOException {

		byte[] testData = "Mary had a little lamb, its fleece was white as snow".getBytes();

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Test
		try {
			output.write (1);
		} catch (IOException e) {
			assertEquals ("Stream closed", e.getMessage());
			return;
		}

		fail();

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testWriteAfterFinish1() throws IOException {

		byte[] testData = "Mary had a little lamb, its fleece was white as snow".getBytes();

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.finish();

		// Test
		try {
			output.write (testData);
		} catch (IOException e) {
			assertEquals ("Write beyond end of stream", e.getMessage());
			return;
		}

		fail();

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testWriteAfterFinish2() throws IOException {

		byte[] testData = "Mary had a little lamb, its fleece was white as snow".getBytes();

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.finish();

		// Test
		try {
			output.write (1);
		} catch (IOException e) {
			assertEquals ("Write beyond end of stream", e.getMessage());
			return;
		}

		fail();

	}


	/**
	 * Test coverage : OutputStream throws an exception during BZip2OutputStream.close()
	 * @throws java.io.IOException
	 */
	@Test(expected=IOException.class)
	public void testExceptionDuringFinish() throws IOException {

		byte[] testData = new byte[] { 'A' };

		// Compress
		OutputStream byteOutput = new OutputStream() {

			private int count = 0;
			@Override
			public void write (int b) throws IOException {
				if (++this.count == 35) {
					throw new IOException();
				}
			}

		};

		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();


	}


	/**
	 * @throws java.io.IOException
	 */
	@Test(expected=IllegalArgumentException.class)
	public void testNullInputStream() throws IOException {

		new Bzip2InputStream(null, false);

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test(expected=IllegalArgumentException.class)
	public void testNullOutputStream() throws IOException {

		new Bzip2OutputStream(null, 1);

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test(expected=IllegalArgumentException.class)
	public void testOutputStreamInvalidBlockSize1() throws IOException {

		new Bzip2OutputStream(new ByteArrayOutputStream(), 0);

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test(expected=IllegalArgumentException.class)
	public void testOutputStreamInvalidBlockSize2() throws IOException {

		new Bzip2OutputStream(new ByteArrayOutputStream(), 10);

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void test3Tables() throws IOException {

		byte[] testData = new byte [500];

		// Create test block
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read (decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void test4Tables() throws IOException {

		byte[] testData = new byte [1100];

		// Create test block
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read (decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());
	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void test5Tables() throws IOException {

		byte[] testData = new byte [2300];

		// Create test block
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read (decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());

	}



	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testLargeRandom() throws IOException {

		byte[] testData = new byte [1048576];

		// Create test block
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		int remaining = testData.length;
		while (remaining > 0) {
			int read = input.read (decodedTestData, testData.length - remaining, remaining);
			if (read > 0) {
				remaining -= read;
			} else {
				break;
			}
		}

		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testLargeRandomWriteSingleBytes() throws IOException {

		byte[] testData = new byte [1048576];

		// Create test block
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		for (int i = 0; i < testData.length; i++) {
			output.write (testData[i]);
		}
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		int remaining = testData.length;
		while (remaining > 0) {
			int read = input.read (decodedTestData, testData.length - remaining, remaining);
			if (read > 0) {
				remaining -= read;
			} else {
				break;
			}
		}


		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testLargeRandomReadSingleBytes() throws IOException {

		byte[] testData = new byte [1048576];

		// Create test block
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		for (int i = 0; i < testData.length; i++) {
			decodedTestData[i] = (byte)input.read();
		}

		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testPartRandom() throws IOException {

		byte[] testData = new byte [12345];

		// Create test block
		Random random = new Random (1234);
		random.nextBytes (testData);
		for (int i = 0; i < 512; i++) {
			testData[i] = (byte)123;
		}

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read (decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testCompressible() throws IOException {

		byte[] testData = new byte [10000];

		// Create test block
		Random random = new Random(1234);
		for (int i = 0; i < testData.length; i++) {
			testData[i] = ((i % 4) != 0) ? 0 : (byte)random.nextInt();
		}

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read (decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testLongBlank() throws IOException {

		// Blank test block
		byte[] testData = new byte [100000];

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read (decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testLongSame() throws IOException {

		byte[] testData = new byte [100000];

		// Create test block
		Arrays.fill (testData, (byte)123);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read (decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testRandomised() throws IOException {

		// Test block
		byte[] compressedData = new byte [] {
				0x42, 0x5a, 0x68, 0x39, 0x31, 0x41, 0x59, 0x26, 0x53, 0x59, (byte)0xf5, (byte)0xdc,
				0x6d, (byte)0x8a, (byte)0x80, 0x00, 0x65, (byte)0x84, 0x00, 0x38, 0x00, 0x20, 0x00,
				0x30, (byte)0xcc, 0x05, 0x29, (byte)0xa6, (byte)0xd5, 0x55, 0x58, 0x01, (byte)0xe2,
				(byte)0xee, 0x48, (byte)0xa7, 0x0a, 0x12, 0x1e, (byte)0xbb, (byte)0x8d, (byte)0xb1,
				0x40
		};
		byte[] uncompressedData = new byte[1024];
		for (int i = 0; i < 1024;) {
			uncompressedData[i++] = 'A';
			uncompressedData[i++] = 'B';
		}

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (compressedData);
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [uncompressedData.length];
		input.read (decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals (uncompressedData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testInvalidHeader() throws IOException {

		// Create test block
		byte[] testData = new byte [1000];
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		byte[] compressedData = byteOutput.toByteArray();
		compressedData[0] = '1';
		ByteArrayInputStream byteInput = new ByteArrayInputStream (compressedData);
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		try {
			input.read (decodedTestData, 0, decodedTestData.length);
			fail();
		} catch (IOException e) {
			assertEquals ("Invalid BZip2 header", e.getMessage());
			return;
		}

		fail();

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testInvalidHeaderSecondRead() throws IOException {

		// Create test block
		byte[] testData = new byte [1000];
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		byte[] compressedData = byteOutput.toByteArray();
		compressedData[0] = '1';
		ByteArrayInputStream byteInput = new ByteArrayInputStream (compressedData);
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		try {
			input.read (decodedTestData, 0, decodedTestData.length);
			fail();
		} catch (IOException e) {
			assertEquals (-1, input.read());
			return;
		}

		fail();

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testInvalidBlockSize1() throws IOException {

		// Create test block
		byte[] testData = new byte [200000];
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		byte[] compressedData = byteOutput.toByteArray();
		compressedData[3] = '1';
		ByteArrayInputStream byteInput = new ByteArrayInputStream (compressedData);
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		try {
			input.read (decodedTestData, 0, decodedTestData.length);
			fail();
		} catch (IOException e) {
			assertEquals ("BZip2 block exceeds declared block size", e.getMessage());
			return;
		}

		fail();

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testInvalidBlockSize2() throws IOException {

		// Create test block
		byte[] testData = new byte [200000];
		Random random = new Random (1234);
		random.nextBytes (testData);
		Arrays.fill(testData, 100000, testData.length, (byte)0);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		byte[] compressedData = byteOutput.toByteArray();
		compressedData[3] = '1';
		ByteArrayInputStream byteInput = new ByteArrayInputStream (compressedData);
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		try {
			input.read (decodedTestData, 0, decodedTestData.length);
			fail();
		} catch (IOException e) {
			assertEquals ("BZip2 block exceeds declared block size", e.getMessage());
			return;
		}

		fail();

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testInvalidBlockCRC() throws IOException {

		// Create test block
		byte[] testData = new byte [1000];
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		byte[] compressedData = byteOutput.toByteArray();
		compressedData[10] = -1;
		ByteArrayInputStream byteInput = new ByteArrayInputStream (compressedData);
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		try {
			input.read (decodedTestData, 0, decodedTestData.length);
			input.read();
			fail();
		} catch (IOException e) {
			assertEquals ("BZip2 block CRC error", e.getMessage());
			return;
		}

		fail();

	}

	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testInvalidStartPointer() throws IOException {

		// Create test block
		byte[] testData = new byte [1000];
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		byte[] compressedData = byteOutput.toByteArray();
		compressedData[14] = -1;
		ByteArrayInputStream byteInput = new ByteArrayInputStream (compressedData);
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		try {
			input.read (decodedTestData, 0, decodedTestData.length);
			input.read();
			fail();
		} catch (IOException e) {
			assertEquals ("BZip2 start pointer invalid", e.getMessage());
			return;
		}

		fail();

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testInvalidStreamCRC() throws IOException {

		// Create test block
		byte[] testData = new byte [1000];
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		byte[] compressedData = byteOutput.toByteArray();
		compressedData[compressedData.length - 2] = -1;
		ByteArrayInputStream byteInput = new ByteArrayInputStream (compressedData);
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		try {
			input.read (decodedTestData, 0, decodedTestData.length);
			input.read();
			fail();
		} catch (IOException e) {
			assertEquals ("BZip2 stream CRC error", e.getMessage());
			return;
		}

		fail();

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testInvalidBlockHeader() throws IOException {

		// Create test block
		byte[] testData = new byte [1000];
		Random random = new Random (1234);
		random.nextBytes (testData);

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		byte[] compressedData = byteOutput.toByteArray();
		compressedData[compressedData.length - 6] = -1;
		ByteArrayInputStream byteInput = new ByteArrayInputStream (compressedData);
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		try {
			input.read (decodedTestData, 0, decodedTestData.length);
			input.read();
			fail();
		} catch (IOException e) {
			assertEquals ("BZip2 stream format error", e.getMessage());
			return;
		}

		fail();

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testDecompressionBug1() throws IOException {

		byte[] testData = new byte [49];

		// Create test block
		for (int i = 0; i < testData.length; i++) {
			testData[i] = (byte)i;
		}

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read (decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testDecompressionBug2() throws IOException {

		byte[] testData = new byte [0];

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);

		// Compare
		assertEquals (-1, input.read());
		assertEquals (-1, input.read());

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testCompressionBug1() throws IOException {

		byte[] testData = new byte [4];

		// Compress
		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		Bzip2OutputStream output = new Bzip2OutputStream(byteOutput);
		output.write (testData);
		output.close();

		// Decompress
		ByteArrayInputStream byteInput = new ByteArrayInputStream (byteOutput.toByteArray());
		Bzip2InputStream input = new Bzip2InputStream(byteInput, false);
		byte[] decodedTestData = new byte [testData.length];
		input.read (decodedTestData, 0, decodedTestData.length);

		// Compare
		assertArrayEquals (testData, decodedTestData);
		assertEquals (-1, input.read());

	}

	// TODO Test BZip2BlockCompressor#close write run at block limit
}
