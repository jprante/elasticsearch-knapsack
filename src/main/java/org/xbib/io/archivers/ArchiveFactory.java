
package org.xbib.io.archivers;

import org.xbib.io.archivers.cpio.CpioArchiveEntry;
import org.xbib.io.archivers.cpio.CpioArchiveInputStream;
import org.xbib.io.archivers.cpio.CpioArchiveOutputStream;
import org.xbib.io.archivers.cpio.CpioConstants;
import org.xbib.io.archivers.tar.TarArchiveEntry;
import org.xbib.io.archivers.tar.TarArchiveInputStream;
import org.xbib.io.archivers.tar.TarArchiveOutputStream;
import org.xbib.io.archivers.zip.ZipArchiveEntry;
import org.xbib.io.archivers.zip.ZipArchiveInputStream;
import org.xbib.io.archivers.zip.ZipArchiveOutputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <p>Factory to create Archive[In|Out]putStreams from names or the first bytes of
 * the InputStream. In order add other implementations you should extend
 * ArchiveStreamFactory and override the appropriate methods (and call their
 * implementation from super of course).</p>
 * <p/>
 * Compressing a ZIP file:
 * <p/>
 * <pre>
 * final OutputStream out = new FileOutputStream(output);
 * ArchiveOutputStream os = ArchiveStreamFactory.createArchiveOutputStream(ArchiveStreamFactory.ZIP, out);
 *
 * os.putArchiveEntry(new ZipArchiveEntry("testdata/test1.xml"));
 * IOUtils.copy(new FileInputStream(file1), os);
 * os.closeArchiveEntry();
 *
 * os.putArchiveEntry(new ZipArchiveEntry("testdata/test2.xml"));
 * IOUtils.copy(new FileInputStream(file2), os);
 * os.closeArchiveEntry();
 * os.close();
 * </pre>
 * <p/>
 * Decompressing a ZIP file:
 * <p/>
 * <pre>
 * final InputStream is = new FileInputStream(input);
 * ArchiveInputStream in = ArchiveStreamFactory.createArchiveInputStream(ArchiveStreamFactory.ZIP, is);
 * ZipArchiveEntry entry = (ZipArchiveEntry)in.getNextEntry();
 * OutputStream out = new FileOutputStream(new File(dir, entry.getName()));
 * IOUtils.copy(in, out);
 * out.close();
 * in.close();
 * </pre>
 */
public final class ArchiveFactory {

    /**
     * Constant used to identify the CPIO archive format.
     */
    public static final String CPIO = "cpio";
    /**
     * Constant used to identify the TAR archive format.
     */
    public static final String TAR = "tar";
    /**
     * Constant used to identify the ZIP archive format.
     */
    public static final String ZIP = "zip";

    private ArchiveFactory() {
    }

    public static ArchiveEntry createArchiveEntry(final String archiverName)
            throws IOException {
        if (archiverName == null) {
            throw new IllegalArgumentException("archiver name must not be null");
        }
        if (ZIP.equalsIgnoreCase(archiverName)) {
            return new ZipArchiveEntry();
        }
        if (TAR.equalsIgnoreCase(archiverName)) {
            return new TarArchiveEntry();
        }
        if (CPIO.equalsIgnoreCase(archiverName)) {
            return new CpioArchiveEntry(CpioConstants.FORMAT_NEW);
        }
        throw new IOException("archiver " + archiverName + " not found");
    }

    /**
     * Create an archive input stream from an archiver name and an input stream.
     *
     * @param archiverName the archive name, i.e. "ar", "zip", "tar", "jar", "dump" or "cpio"
     * @param in           the input stream
     * @return the archive input stream
     * @throws java.io.IOException      if the archiver name is not known
     * @throws IllegalArgumentException if the archiver name or stream is null
     */
    public static ArchiveInputStream createArchiveInputStream(final String archiverName, final InputStream in)
            throws IOException {
        if (archiverName == null) {
            throw new IllegalArgumentException("archiver name must not be null");
        }
        if (in == null) {
            throw new IllegalArgumentException("input stream must not be null");
        }
        if (ZIP.equalsIgnoreCase(archiverName)) {
            return new ZipArchiveInputStream(in);
        }
        if (TAR.equalsIgnoreCase(archiverName)) {
            return new TarArchiveInputStream(in);
        }
        if (CPIO.equalsIgnoreCase(archiverName)) {
            return new CpioArchiveInputStream(in);
        }
        throw new IOException("archiver " + archiverName + " not found");
    }

    /**
     * Create an archive output stream from an archiver name and an input stream.
     *
     * @param archiverName the archive name, i.e. "ar", "zip", "tar", "jar" or "cpio"
     * @param out          the output stream
     * @return the archive output stream
     * @throws java.io.IOException      if the archiver name is not known
     * @throws IllegalArgumentException if the archiver name or stream is null
     */
    public static ArchiveOutputStream createArchiveOutputStream(final String archiverName, final OutputStream out)
            throws IOException {
        if (archiverName == null) {
            throw new IllegalArgumentException("archiver name must not be null");
        }
        if (out == null) {
            throw new IllegalArgumentException("output stream must not be null");
        }
        if (ZIP.equalsIgnoreCase(archiverName)) {
            return new ZipArchiveOutputStream(out);
        }
        if (TAR.equalsIgnoreCase(archiverName)) {
            return new TarArchiveOutputStream(out);
        }
        if (CPIO.equalsIgnoreCase(archiverName)) {
            return new CpioArchiveOutputStream(out);
        }
        throw new IOException("archiver " + archiverName + " not found");
    }

    /**
     * Create an archive input stream from an input stream and autodetect
     * the archive type from the first few bytes of the stream. The InputStream
     * must support the mark()/reset() operation like BufferedInputStream.
     *
     * @param in the input stream
     * @return the archive input stream
     * @throws java.io.IOException      if the archiver name is not known
     * @throws IllegalArgumentException if the stream is null or does not support mark
     */
    public static ArchiveInputStream createArchiveInputStream(final InputStream in)
            throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("input stream must not be null");
        }
        if (!in.markSupported()) {
            throw new IllegalArgumentException("mark is not supported");
        }
        final byte[] signature = new byte[12];
        in.mark(signature.length);
        int signatureLength = in.read(signature);
        in.reset();
        if (ZipArchiveInputStream.matches(signature, signatureLength)) {
            return new ZipArchiveInputStream(in);
        } else if (CpioArchiveInputStream.matches(signature, signatureLength)) {
            return new CpioArchiveInputStream(in);
        }
        // Dump needs a bigger buffer to check the signature
        final byte[] dumpsig = new byte[32];
        in.mark(dumpsig.length);
        signatureLength = in.read(dumpsig);
        in.reset();
        // Tar needs an even bigger buffer to check the signature; read the first block
        final byte[] tarheader = new byte[512];
        in.mark(tarheader.length);
        signatureLength = in.read(tarheader);
        in.reset();
        if (TarArchiveInputStream.matches(tarheader, signatureLength)) {
            return new TarArchiveInputStream(in);
        }
        if (signatureLength >= 512) {
            try {
                TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(tarheader));
                tais.getNextEntry();
                return new TarArchiveInputStream(in);
            } catch (Exception e) {
                // ignored
            }
        }
        throw new IOException("no archiver found for the stream signature");
    }
}
