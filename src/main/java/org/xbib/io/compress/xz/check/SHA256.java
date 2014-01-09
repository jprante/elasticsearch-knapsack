
package org.xbib.io.compress.xz.check;

public class SHA256 extends Check {
    private final java.security.MessageDigest sha256;

    public SHA256() throws java.security.NoSuchAlgorithmException {
        size = 32;
        name = "SHA-256";
        sha256 = java.security.MessageDigest.getInstance("SHA-256");
    }

    public void update(byte[] buf, int off, int len) {
        sha256.update(buf, off, len);
    }

    public byte[] finish() {
        byte[] buf = sha256.digest();
        sha256.reset();
        return buf;
    }
}
