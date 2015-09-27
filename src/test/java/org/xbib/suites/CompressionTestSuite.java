package org.xbib.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.xbib.io.compress.bzip2.BZip2BitInputStreamTests;
import org.xbib.io.compress.bzip2.BZip2BitOutputStreamTests;
import org.xbib.io.compress.bzip2.BZip2BlockDecompressorTests;
import org.xbib.io.compress.bzip2.BZip2DivSufSortTests;
import org.xbib.io.compress.bzip2.BZip2HuffmanStageDecoderTests;
import org.xbib.io.compress.bzip2.BZip2OutputStreamTests;
import org.xbib.io.compress.bzip2.SimpleBZip2Tests;
import org.xbib.io.compress.bzip2.HuffmanAllocatorTests;


@RunWith(Suite.class)
@Suite.SuiteClasses({
        BZip2BitInputStreamTests.class,
        BZip2BitOutputStreamTests.class,
        BZip2BlockDecompressorTests.class,
        BZip2DivSufSortTests.class,
        BZip2HuffmanStageDecoderTests.class,
        BZip2OutputStreamTests.class,
        HuffmanAllocatorTests.class,
        SimpleBZip2Tests.class
})
public class CompressionTestSuite {

}
