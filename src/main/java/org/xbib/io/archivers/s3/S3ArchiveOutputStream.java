
package org.xbib.io.archivers.s3;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.xbib.io.archivers.ArchiveEntry;
import org.xbib.io.archivers.ArchiveOutputStream;

import java.io.FileInputStream;
import java.io.IOException;

public class S3ArchiveOutputStream extends ArchiveOutputStream {

    private final AmazonS3Client client;

    private final String bucketName;

    private final String key;

    S3ArchiveOutputStream(AmazonS3Client client, String bucketName, String key) {
        this.client = client;
        this.bucketName = bucketName;
        this.key = key;
    }

    @Override
    public void putArchiveEntry(ArchiveEntry entry) throws IOException {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(entry.getEntrySize());
        metadata.setLastModified(entry.getLastModified());
        FileInputStream input = new FileInputStream(entry.getName());
        PutObjectRequest request = new PutObjectRequest(bucketName, key, input, metadata);
        client.putObject(request);
    }

    @Override
    public void closeArchiveEntry() throws IOException {
        // do nothing
    }

    @Override
    public void finish() throws IOException {
        // do nothing
    }

}
