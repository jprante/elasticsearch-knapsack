package org.xbib.io.compress.bzip2;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertEquals;

public class BZip2Tests {

    @Test
    public void testBZip2HelloWorld() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BZip2OutputStream zOut = new BZip2OutputStream(out);
        ObjectOutputStream objOut = new ObjectOutputStream(zOut);
        String helloWorld = "Hello World!";
        objOut.writeObject(helloWorld);
        zOut.close();
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        BZip2InputStream zIn = new BZip2InputStream(in);
        ObjectInputStream objIn = new ObjectInputStream(zIn);
        assertEquals("Hello World!", objIn.readObject());
    }

    @Test
    public void testEmptyBZip2() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (BZip2OutputStream out = new BZip2OutputStream(b)) {
            out.finish();
        } finally {
            ;
        }
    }
}
