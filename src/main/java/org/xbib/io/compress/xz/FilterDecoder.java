
package org.xbib.io.compress.xz;

import java.io.InputStream;

interface FilterDecoder extends FilterCoder {

    int getMemoryUsage();

    InputStream getInputStream(InputStream in);
}
