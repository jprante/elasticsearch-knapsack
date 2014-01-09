
package org.xbib.io.compress.xz;

class RawCoder {
    static void validate(FilterCoder[] filters)
            throws UnsupportedOptionsException {
        for (int i = 0; i < filters.length - 1; ++i) {
            if (!filters[i].nonLastOK()) {
                throw new UnsupportedOptionsException(
                        "Unsupported XZ filter chain");
            }
        }

        if (!filters[filters.length - 1].lastOK()) {
            throw new UnsupportedOptionsException(
                    "Unsupported XZ filter chain");
        }

        int changesSizeCount = 0;
        for (int i = 0; i < filters.length; ++i) {
            if (filters[i].changesSize()) {
                ++changesSizeCount;
            }
        }

        if (changesSizeCount > 3) {
            throw new UnsupportedOptionsException(
                    "Unsupported XZ filter chain");
        }
    }
}
