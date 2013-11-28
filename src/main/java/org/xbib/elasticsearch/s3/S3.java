package org.xbib.elasticsearch.s3;

import java.io.IOException;

public interface S3 {
    /**
     * Transfer a local file to S3
     * @param bucket S3 bucket name
     * @param s3Path The path inside the S3 bucket where the file should be saved
     */
    public void writeToS3(String bucket, String s3path);

    /**
     * Save an artifact from S3 to the local disk
     * @param bucket S3 bucket name
     * @param s3Path The path inside the S3 bucket from where the file should be downloaded
     * @throws IOException An IOException is thrown when is unable to write to the target
     */
    public void readFromS3(String bucket, String s3path) throws IOException;
}
