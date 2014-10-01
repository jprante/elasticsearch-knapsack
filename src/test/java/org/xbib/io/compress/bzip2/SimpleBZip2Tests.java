package org.xbib.io.compress.bzip2;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertEquals;

public class SimpleBZip2Tests {

    @Test
    public void testBZip2HelloWorld() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Bzip2OutputStream zOut = new Bzip2OutputStream(out);
        ObjectOutputStream objOut = new ObjectOutputStream(zOut);
        String helloWorld = "Hello World!";
        objOut.writeObject(helloWorld);
        zOut.close();
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Bzip2InputStream zIn = new Bzip2InputStream(in);
        ObjectInputStream objIn = new ObjectInputStream(zIn);
        assertEquals("Hello World!", objIn.readObject());
    }

    @Test
    public void testEmptyBZip2() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (Bzip2OutputStream out = new Bzip2OutputStream(b)) {
            out.finish();
        } finally {
            ;
        }
    }
}
