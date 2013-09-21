package org.xbib.elasticsearch.knapsack;

import org.elasticsearch.common.inject.AbstractModule;

public class KnapsackModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(KnapsackService.class).asEagerSingleton();
	}

}
