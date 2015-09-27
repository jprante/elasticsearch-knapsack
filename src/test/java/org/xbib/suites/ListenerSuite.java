package org.xbib.suites;

import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class ListenerSuite extends Suite {

    private final TestListener listener = new TestListener();

    public ListenerSuite(Class<?> klass, RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    @Override
    protected void runChild(Runner runner, RunNotifier notifier) {
        notifier.addListener(listener);
        runner.run(notifier);
        notifier.removeListener(listener);
    }
}
