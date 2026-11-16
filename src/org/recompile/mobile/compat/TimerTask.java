package org.recompile.mobile.compat;

/**
 * Timer task used by MIDlets after java.util.Timer references are remapped.
 * The task state is kept here so the compatibility scheduler can cancel a
 * failing periodic task without terminating the timer's worker thread.
 */
public abstract class TimerTask extends java.util.TimerTask {
	static final int VIRGIN = 0;
	static final int SCHEDULED = 1;
	static final int EXECUTED = 2;
	static final int CANCELLED = 3;

	final Object lock = new Object();
	int state = VIRGIN;
	long nextExecutionTime;
	long period;

	protected TimerTask() {
		super();
	}

	public boolean cancel() {
		synchronized (lock) {
			boolean wasScheduled = state == SCHEDULED;
			state = CANCELLED;
			return wasScheduled;
		}
	}

	public long scheduledExecutionTime() {
		synchronized (lock) {
			return period < 0L
					? nextExecutionTime + period
					: nextExecutionTime - period;
		}
	}

	boolean isCancelled() {
		synchronized (lock) {
			return state == CANCELLED;
		}
	}
}
