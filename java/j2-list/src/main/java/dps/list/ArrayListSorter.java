package dps.list;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class ArrayListSorter implements Runnable {

	private final List<String> list;
	private final long delayMillis;
	private final AtomicLong steps;

	public ArrayListSorter(List<String> synchronizedList, long delayMillis, AtomicLong steps) {
		this.list = synchronizedList;
		this.delayMillis = delayMillis;
		this.steps = steps;
	}

	@Override
	public void run() {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				if (pass() == 0) {
					Thread.sleep(delayMillis);
				}
			}
		} catch (InterruptedException e) {
		}
	}

	private int pass() throws InterruptedException {
		int stepsInPass = 0;
		for (int i = 0; step(i); i++) {
			stepsInPass++;
			Thread.sleep(delayMillis);
		}
		return stepsInPass;
	}

	private boolean step(int i) throws InterruptedException {
		synchronized (list) {
			if (i + 1 >= list.size()) {
				return false;
			}
			Thread.sleep(delayMillis);
			steps.incrementAndGet();

			String a = list.get(i);
			String b = list.get(i + 1);
			if (a.compareTo(b) > 0) {
				list.set(i, b);
				list.set(i + 1, a);
			}
			return true;
		}
	}
}
