package org.xbib.io.compress.bzip2;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.junit.Test;

/**
 * Tests HuffmanAllocator
 */
public class HuffmanAllocatorTests {

	/**
	 * Fibonacci sequence
	 */
	private static int[] fibonacci = {
			0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597, 2584, 4181,
			6765, 10946, 17711, 28657, 46368, 75025, 121393, 196418, 317811, 514229, 832040,
			1346269, 2178309, 3524578, 5702887, 9227465, 14930352
	};


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testShort1() throws IOException {

		int[] expectedLengths = new int[] {
				1
		};
		int[] frequencies = new int[] { 1 };
		HuffmanAllocator.allocateHuffmanCodeLengths (frequencies, 32);

		assertArrayEquals (expectedLengths, frequencies);

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testShort2() throws IOException {

		int[] expectedLengths = new int[] {
				1, 1
		};
		int[] frequencies = new int[] { 1, 1 };
		HuffmanAllocator.allocateHuffmanCodeLengths (frequencies, 32);

		assertArrayEquals (expectedLengths, frequencies);


	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testRegular1() throws IOException {

		int[] expectedLengths = new int[] {
				3, 3, 2, 2, 2
		};
		int[] frequencies = new int[] { 1, 1, 1, 1, 1 };
		HuffmanAllocator.allocateHuffmanCodeLengths (frequencies, 32);

		assertArrayEquals (expectedLengths, frequencies);


	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testBoundary1() throws IOException {

		int[] expectedLengths = new int[] {
				3, 3, 3, 3, 2, 2
		};
		int[] frequencies = new int[] { 0, 0, 1, 1, 1, 1 };
		HuffmanAllocator.allocateHuffmanCodeLengths (frequencies, 3);

		assertArrayEquals (expectedLengths, frequencies);


	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testFibonacci1() throws IOException {

		int[] expectedLengths = new int[] {
				20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 19, 19, 18, 17, 16,
				16, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1
		};
		int[] frequencies = Arrays.copyOf (fibonacci, 36);
		HuffmanAllocator.allocateHuffmanCodeLengths (frequencies, 20);

		assertArrayEquals (expectedLengths, frequencies);

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testFibonacci2() throws IOException {

		int[] expectedLengths = new int[] {
				20, 20, 19, 19, 19, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1,
		};
		int[] frequencies = Arrays.copyOf (fibonacci, 22);
		HuffmanAllocator.allocateHuffmanCodeLengths (frequencies, 20);

		assertArrayEquals (expectedLengths, frequencies);

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testFibonacci3() throws IOException {

		int[] expectedLengths = new int[] {
				20, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 
		};
		int[] frequencies = Arrays.copyOf (fibonacci, 21);
		HuffmanAllocator.allocateHuffmanCodeLengths (frequencies, 20);

		assertArrayEquals (expectedLengths, frequencies);

	}


	/**
	 * @throws java.io.IOException
	 */
	@Test
	public void testFibonacci4() throws IOException {

		int[] expectedLengths = new int[] {
				6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
				6, 6, 5, 5, 5, 4, 3, 2
		};
		int[] frequencies = Arrays.copyOf (fibonacci, 36);
		HuffmanAllocator.allocateHuffmanCodeLengths (frequencies, 6);

		assertArrayEquals (expectedLengths, frequencies);

	}


	/**
	 * Pointless test to bump coverage to 100%
	 * @throws Exception  
	 */
	@Test
	public void testPointlessConstructorTestCoverage() throws Exception {

		Constructor<?> c[] = HuffmanAllocator.class.getDeclaredConstructors();

		c[0].setAccessible (true);
		c[0].newInstance ((Object[])null);

	}

}
