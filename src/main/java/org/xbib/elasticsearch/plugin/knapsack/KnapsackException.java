
package org.xbib.elasticsearch.plugin.knapsack;

public class KnapsackException extends RuntimeException {

    public KnapsackException(String message) {
        super(message);
    }

    public KnapsackException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
