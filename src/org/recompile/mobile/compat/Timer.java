package org.recompile.mobile.compat;

import java.util.Date;

/**
 * MIDlet-compatible timer. A RuntimeException from one task cancels that task
 * and leaves the timer available for its other and future tasks, as in
 * KEmulator's MIDlet timer implementation.
 */
public class Timer {
	private static int nextTimerId;
	private static final boolean DEBUG_TASK_FAILURES =
			Boolean.getBoolean("freej2me.timer.debug");

	private final TaskQueue queue = new TaskQueue();
	private final String threadName;
	private final boolean daemon;
	private TimerThread thread;

	public Timer() {
		this(nextThreadName(), false);
	}

	public Timer(boolean isDaemon) {
		this(nextThreadName(), isDaemon);
	}

	public Timer(String name) {
		this(name, false);
	}

	public Timer(String name, boolean isDaemon) {
		if (name == null) {
			throw new NullPointerException("name");
		}
		threadName = name;
		daemon = isDaemon;
	}

	private static synchronized String nextThreadName() {
		return "MIDlet Timer-" + (++nextTimerId);
	}

	public void schedule(TimerTask task, long delay) {
		if (delay < 0L) {
			throw new IllegalArgumentException("Negative delay.");
		}
		scheduleInternal(task, System.currentTimeMillis() + delay, 0L);
	}

	public void schedule(TimerTask task, Date time) {
		scheduleInternal(task, time.getTime(), 0L);
	}

	public void schedule(TimerTask task, long delay, long period) {
		if (delay < 0L) {
			throw new IllegalArgumentException("Negative delay.");
		}
		if (period <= 0L) {
			throw new IllegalArgumentException("Non-positive period.");
		}
		scheduleInternal(task, System.currentTimeMillis() + delay, -period);
	}

	public void schedule(TimerTask task, Date firstTime, long period) {
		if (period <= 0L) {
			throw new IllegalArgumentException("Non-positive period.");
		}
		scheduleInternal(task, firstTime.getTime(), -period);
	}

	public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
		if (delay < 0L) {
			throw new IllegalArgumentException("Negative delay.");
		}
		if (period <= 0L) {
			throw new IllegalArgumentException("Non-positive period.");
		}
		scheduleInternal(task, System.currentTimeMillis() + delay, period);
	}

	public void scheduleAtFixedRate(TimerTask task, Date firstTime, long period) {
		if (period <= 0L) {
			throw new IllegalArgumentException("Non-positive period.");
		}
		scheduleInternal(task, firstTime.getTime(), period);
	}

	private void scheduleInternal(TimerTask task, long executionTime, long period) {
		if (task == null) {
			throw new NullPointerException("task");
		}
		if (executionTime < 0L) {
			throw new IllegalArgumentException("Illegal execution time.");
		}
		synchronized (queue) {
			if (!queue.newTasksMayBeScheduled) {
				throw new IllegalStateException("Timer already cancelled.");
			}
			synchronized (task.lock) {
				if (task.state != TimerTask.VIRGIN) {
					throw new IllegalStateException(
							"Task already scheduled or cancelled");
				}
				task.nextExecutionTime = executionTime;
				task.period = period;
				task.state = TimerTask.SCHEDULED;
			}
			queue.add(task);
			if (thread == null || !thread.isAlive()) {
				thread = new TimerThread(queue, threadName, daemon);
				thread.start();
			}
			queue.notifyAll();
		}
	}

	public void cancel() {
		synchronized (queue) {
			queue.newTasksMayBeScheduled = false;
			queue.clear();
			queue.notifyAll();
		}
	}

	public int purge() {
		synchronized (queue) {
			return queue.removeCancelledTasks();
		}
	}

	private static final class TaskQueue {
		private TimerTask[] tasks = new TimerTask[5];
		private int size;
		private boolean newTasksMayBeScheduled = true;

		private boolean isEmpty() {
			return size == 0;
		}

		private TimerTask getMin() {
			return tasks[1];
		}

		private void add(TimerTask task) {
			if (++size == tasks.length) {
				TimerTask[] expanded = new TimerTask[tasks.length * 2];
				System.arraycopy(tasks, 0, expanded, 0, tasks.length);
				tasks = expanded;
			}
			tasks[size] = task;
			fixUp(size);
		}

		private void removeMin() {
			removeAt(1);
		}

		private void removeAt(int index) {
			if (index == size) {
				tasks[size--] = null;
				return;
			}
			tasks[index] = tasks[size];
			tasks[size--] = null;
			fixDown(index);
			fixUp(index);
		}

		private void rescheduleMin(long executionTime) {
			tasks[1].nextExecutionTime = executionTime;
			fixDown(1);
		}

		private int removeCancelledTasks() {
			int removed = 0;
			for (int i = size; i > 0; i--) {
				if (tasks[i].isCancelled()) {
					removeAt(i);
					removed++;
				}
			}
			return removed;
		}

		private void clear() {
			for (int i = 1; i <= size; i++) {
				tasks[i] = null;
			}
			size = 0;
		}

		private void fixUp(int index) {
			while (index > 1) {
				int parent = index >> 1;
				if (tasks[parent].nextExecutionTime <= tasks[index].nextExecutionTime) {
					return;
				}
				TimerTask task = tasks[parent];
				tasks[parent] = tasks[index];
				tasks[index] = task;
				index = parent;
			}
		}

		private void fixDown(int index) {
			int child;
			while ((child = index << 1) <= size) {
				if (child < size && tasks[child].nextExecutionTime
						> tasks[child + 1].nextExecutionTime) {
					child++;
				}
				if (tasks[index].nextExecutionTime <= tasks[child].nextExecutionTime) {
					return;
				}
				TimerTask task = tasks[child];
				tasks[child] = tasks[index];
				tasks[index] = task;
				index = child;
			}
		}
	}

	private static final class TimerThread extends Thread {
		private final TaskQueue queue;

		private TimerThread(TaskQueue queue, String name, boolean daemon) {
			super(name);
			this.queue = queue;
			setDaemon(daemon);
		}

		public void run() {
			try {
				mainLoop();
			} catch (Throwable failure) {
				synchronized (queue) {
					queue.newTasksMayBeScheduled = false;
					queue.clear();
					queue.notifyAll();
				}
				if (failure instanceof Error) {
					throw (Error) failure;
				}
				if (failure instanceof RuntimeException) {
					throw (RuntimeException) failure;
				}
			}
		}

		private void mainLoop() {
			while (true) {
				TimerTask task;
				boolean due;
				synchronized (queue) {
					while (queue.isEmpty() && queue.newTasksMayBeScheduled) {
						try {
							queue.wait();
						} catch (InterruptedException ignored) {
						}
					}
					if (queue.isEmpty()) {
						return;
					}

					task = queue.getMin();
					long currentTime = System.currentTimeMillis();
					long executionTime;
					synchronized (task.lock) {
						if (task.state == TimerTask.CANCELLED) {
							queue.removeMin();
							continue;
						}
						executionTime = task.nextExecutionTime;
						due = executionTime <= currentTime;
						if (due) {
							if (task.period == 0L) {
								queue.removeMin();
								task.state = TimerTask.EXECUTED;
							} else {
								long next = task.period < 0L
										? currentTime - task.period
										: executionTime + task.period;
								queue.rescheduleMin(next);
							}
						}
					}
					if (!due) {
						try {
							queue.wait(executionTime - currentTime);
						} catch (InterruptedException ignored) {
						}
						continue;
					}
				}

				try {
					task.run();
				} catch (RuntimeException taskFailure) {
					task.cancel();
					if (DEBUG_TASK_FAILURES) {
						System.err.println("[FreeJ2ME] MIDlet TimerTask failed; cancelled this task: "
								+ taskFailure);
						taskFailure.printStackTrace();
					}
				}
			}
		}
	}
}
