package org.xbib.elasticsearch.s3;

public interface S3Factory {

    /**
     * Returns the s3client
     * @param target archive location
     * @param accessKeyId aws access key id parameter
     * @param secretAccessKey aws secret access key parameter
     * @return
     */
    S3 getS3(String target, String accessKeyId, String secretAccessKey);

}