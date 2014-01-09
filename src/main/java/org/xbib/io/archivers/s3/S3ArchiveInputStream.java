
package org.xbib.io.archivers.s3;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import org.xbib.io.archivers.ArchiveEntry;
import org.xbib.io.archivers.ArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;

public class S3ArchiveInputStream extends ArchiveInputStream {

    private final AmazonS3Client client;

    private final String bucketName;

    private final String key;

    private InputStream in;

    private S3Object object;

    S3ArchiveInputStream(AmazonS3Client client, String bucketName, String key) {
        this.client = client;
        this.bucketName = bucketName;
        this.key = key;
    }

    @Override
    public ArchiveEntry getNextEntry() throws IOException {
        GetObjectRequest request = new GetObjectRequest(bucketName, key);
        object = client.getObject(request);
        S3ArchiveEntry entry = new S3ArchiveEntry();
        entry.setName(object.getKey());
        entry.setLastModified(object.getObjectMetadata().getLastModified());
        entry.setEntrySize(object.getObjectMetadata().getContentLength());
        in = object.getObjectContent();
        return entry;
    }

    public InputStream getInputStream() {
        return in;
    }

    public void close() throws IOException {
        super.close();
        if (object != null) {
            object.close();
        }
    }

}
