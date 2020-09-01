package com.vordel.circuit.jaxrs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

import org.glassfish.jersey.server.BackgroundScheduler;
import org.glassfish.jersey.server.ManagedAsyncExecutor;
import org.glassfish.jersey.spi.ExecutorServiceProvider;
import org.glassfish.jersey.spi.ScheduledExecutorServiceProvider;

@ConstrainedTo(RuntimeType.SERVER)
public class JerseyExecutorFeature implements Feature {
	@Context
	private ResourceContext resourceContext;

	@Override
	public boolean configure(final FeatureContext context) {
		if (!context.getConfiguration().isRegistered(JerseyExecutorProvider.class)) {
			context.register(new JerseyExecutorProvider(resourceContext));
		}

		return true;
	}

	@BackgroundScheduler
	@ManagedAsyncExecutor
	public class JerseyExecutorProvider implements ExecutorServiceProvider, ScheduledExecutorServiceProvider {
		private final RejectingExecutorService executor = new RejectingExecutorService();

		public JerseyExecutorProvider(ResourceContext resourceContext) {
		}

		@Override
		public void dispose(ExecutorService executorService) {
			/* nothing to dispose */
		}

		@Override
		public ScheduledExecutorService getExecutorService() {
			return executor;
		}
	}
}
