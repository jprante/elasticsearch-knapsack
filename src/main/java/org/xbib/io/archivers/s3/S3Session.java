
package org.xbib.io.archivers.s3;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3Client;

import org.xbib.io.ObjectPacket;
import org.xbib.io.Packet;
import org.xbib.io.Session;
import org.xbib.io.StreamUtil;
import org.xbib.io.URIUtil;
import org.xbib.io.archivers.ArchiveEntry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Map;

public class S3Session implements Session {

    private boolean isOpen;

    private URI uri;

    private AmazonS3Client s3client;

    private S3ArchiveInputStream inputStream;

    private S3ArchiveOutputStream outputStream;

    private String accessKey;

    private String secretKey;

    private String bucketName;

    private String key;

    public S3Session() {
    }

    public S3Session setURI(URI uri) {
        this.uri = uri;
        // authorization
        String auth = uri.getAuthority();
        if (auth != null) {
            String[] s = auth.split(":");
            this.accessKey = s[0];
            this.secretKey = s.length > 1 ? s[1] : null;
        }
        this.s3client = accessKey != null && secretKey != null ?
                new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey)) :
                new AmazonS3Client(new DefaultAWSCredentialsProviderChain());
        // s3://host --> host
        s3client.setEndpoint(uri.getHost());
        return this;
    }

    @Override
    public synchronized void open(Mode mode) throws IOException {
        if (isOpen) {
            return;
        }
        // map URI params to input/output stream services
        switch (mode) {
            case READ: {
                Map<String,String> params = URIUtil.parseQueryString(uri);
                bucketName = params.get("bucketName");
                key = params.get("key");
                inputStream = new S3ArchiveInputStream(s3client, bucketName, key);
                File f = new File(bucketName + File.separator + key);
                if (!f.exists()) {
                    f.mkdirs();
                }
                if (!f.canWrite()) {
                    throw new IOException("can't write to " + f.getAbsolutePath());
                }
                isOpen = f.canWrite();
                break;
            }
            case WRITE: {
                Map<String,String> params = URIUtil.parseQueryString(uri);
                bucketName = params.get("bucketName");
                key = params.get("key");
                outputStream = new S3ArchiveOutputStream(s3client, bucketName, key);
                isOpen = true;
                break;
            }
        }
    }

    @Override
    public Packet<Object> newPacket() {
        return new ObjectPacket();
    }

    @Override
    public synchronized Packet read() throws IOException {
        if (!isOpen()) {
            throw new IOException("not open");
        }
        if (inputStream == null) {
            throw new IOException("no input stream found");
        }
        ArchiveEntry entry = inputStream.getNextEntry();
        if (entry == null) {
            return null;
        }
        Packet<Object> packet = newPacket();
        String name = entry.getName();
        packet.name(name);
        packet.packet(bucketName + File.separator + key);
        File f = new File(bucketName + File.separator + key);
        if (!f.exists()) {
            f.mkdirs();
        }
        FileOutputStream out = new FileOutputStream(f);
        StreamUtil.copy(inputStream.getInputStream(), out);
        out.close();
        inputStream.close();
        // packet name = S3 key
        // packet payload = path where the S3 file is now copied to
        return packet;
    }

    @Override
    public synchronized void write(Packet packet) throws IOException {
        if (!isOpen()) {
            throw new IOException("not open");
        }
        if (outputStream == null) {
            throw new IOException("no output stream found");
        }
        if (packet == null) {
            throw new IOException("no packet to write");
        }
        // we only need a packet name
        ArchiveEntry entry = new S3ArchiveEntry();
        File f = new File(packet.name());
        if (!f.exists()) {
            throw new FileNotFoundException("file not found: " + packet.name());
        }
        entry.setName(packet.name());
        entry.setLastModified(new Date(f.lastModified()));
        entry.setEntrySize(f.length());
        outputStream.putArchiveEntry(entry); // move file up to S3
        outputStream.closeArchiveEntry(); // do nothing
    }

    @Override
    public void close() throws IOException {
        s3client.shutdown();
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }
}
