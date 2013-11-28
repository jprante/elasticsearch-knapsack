package org.xbib.elasticsearch.s3;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

public class S3Impl implements S3 {

    private final AmazonS3 s3client;

    private final String archivePath;

    public S3Impl(String target, String accessKeyId, String secretAccessKey) {
        if(accessKeyId != null && secretAccessKey != null){
            this.s3client = new AmazonS3Client(new BasicAWSCredentials(accessKeyId, secretAccessKey));
        } else {
            this.s3client = new AmazonS3Client(new DefaultAWSCredentialsProviderChain());
        }

        this.archivePath = getArchivePath(target);
    }

    private String getArchivePath(String target) {
        if (!(target.endsWith("tar.gz") || target.endsWith("tar.bz2") || target.endsWith(".tar.xz") || target
                .endsWith(".tar"))) {
            target += ".tar.gz";
        }
        return target;
    }

    @Override
    public void writeToS3(String s3bucket, String s3Path) {
        File file = new File(archivePath);
        s3client.putObject(new PutObjectRequest(s3bucket, s3Path, file));
        file.delete();
    }

    @Override
    public void readFromS3(String s3bucket, String s3Path) throws IOException {
       S3Object object = s3client.getObject(new GetObjectRequest(s3bucket, s3Path));
       saveS3ObjectToDisk(object, archivePath);
    }

    private void saveS3ObjectToDisk(S3Object object, String filePath) throws IOException{
        InputStream reader = new BufferedInputStream(object.getObjectContent());
        File file = new File(filePath);

        OutputStream writer = new BufferedOutputStream(new FileOutputStream(file));

        int read = -1;
        while ((read = reader.read()) != -1) {
            writer.write(read);
        }

        writer.flush();
        writer.close();
        reader.close();
    }

}
