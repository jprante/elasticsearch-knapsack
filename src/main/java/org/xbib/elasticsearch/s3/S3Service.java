
package org.xbib.elasticsearch.s3;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public class S3Service {

    private final static S3Service instance = new S3Service();

    public static S3Service getInstance() {
        return instance;
    }

    public S3Factory getS3Factory(){
        S3Factory s3Factory;
        ServiceLoader<S3Factory> loader = ServiceLoader.load(S3Factory.class);
        Iterator<S3Factory> it = loader.iterator();
        while (it.hasNext()) {
            s3Factory = it.next();
            return s3Factory;
        }
        throw new ServiceConfigurationError("no suitable implementation found");
    }

}
