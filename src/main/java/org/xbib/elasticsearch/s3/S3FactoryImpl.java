
package org.xbib.elasticsearch.s3;

public final class S3FactoryImpl implements S3Factory {

    @Override
    public S3 getS3(String target, String accessKeyId, String secretAccessKey) {
        S3 s3 = new S3Impl(target, accessKeyId, secretAccessKey);
        return s3;
    }

}
