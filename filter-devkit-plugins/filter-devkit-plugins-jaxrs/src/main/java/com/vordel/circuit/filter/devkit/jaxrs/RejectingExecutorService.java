package com.vordel.circuit.filter.devkit.jaxrs;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RejectingExecutorService extends AbstractExecutorService implements ScheduledExecutorService {
	private boolean shutdown = false;

	@Override
	public void execute(Runnable command) {
		throw new RejectedExecutionException("BackgroundScheduler is not available");
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		throw new RejectedExecutionException("BackgroundScheduler is not available");
	}

	@Override
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
		throw new RejectedExecutionException("BackgroundScheduler is not available");
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
		throw new RejectedExecutionException("BackgroundScheduler is not available");
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
		throw new RejectedExecutionException("BackgroundScheduler is not available");
	}

	@Override
	public void shutdown() {
		shutdown |= true;
	}

	@Override
	public List<Runnable> shutdownNow() {
		shutdown();
		
		return Collections.emptyList();
	}

	@Override
	public boolean isShutdown() {
		return shutdown;
	}

	@Override
	public boolean isTerminated() {
		return shutdown;
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return true;
	}

}
