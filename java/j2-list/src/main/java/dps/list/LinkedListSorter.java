package dps.list;

import java.util.concurrent.atomic.AtomicLong;

import dps.list.ConcurrentLinkedList.Node;

public class LinkedListSorter implements Runnable {

	private final ConcurrentLinkedList list;
	private final long delayMillis;
	private final AtomicLong steps;

	public LinkedListSorter(ConcurrentLinkedList list, long delayMillis, AtomicLong steps) {
		this.list = list;
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
		Node prev = list.head;
		while (true) {
			Node nextPrev = step(prev);
			if (nextPrev == null) {
				return stepsInPass;
			}
			stepsInPass++;
			Thread.sleep(delayMillis);
			prev = nextPrev;
		}
	}

	private Node step(Node prev) throws InterruptedException {
		prev.lock();
		try {
			Node a = prev.next;
			if (a == null) {
				return null;
			}
			a.lock();
			try {
				Node b = a.next;
				if (b == null) {
					return null;
				}
				b.lock();
				try {
					Thread.sleep(delayMillis);
					steps.incrementAndGet();

					if (a.value.compareTo(b.value) > 0) {
						prev.next = b;
						a.next = b.next;
						b.next = a;
						return b;
					}
					return a;
				} finally {
					b.unlock();
				}
			} finally {
				a.unlock();
			}
		} finally {
			prev.unlock();
		}
	}
}
