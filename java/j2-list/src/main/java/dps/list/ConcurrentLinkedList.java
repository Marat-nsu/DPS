package dps.list;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentLinkedList implements Iterable<String> {

	static final class Node {
		final String value;
		Node next;
		private final ReentrantLock lock = new ReentrantLock();

		Node(String value) {
			this.value = value;
		}

		void lock() {
			lock.lock();
		}

		void unlock() {
			lock.unlock();
		}
	}

	final Node head = new Node(null);

	public void addFirst(String value) {
		Node node = new Node(value);
		head.lock();
		try {
			node.next = head.next;
			head.next = node;
		} finally {
			head.unlock();
		}
	}

	@Override
	public Iterator<String> iterator() {
		return new LockingIterator();
	}

	private class LockingIterator implements Iterator<String> {
		private Node current;

		LockingIterator() {
			current = head;
			current.lock();
		}

		@Override
		public boolean hasNext() {
			if (current == null) {
				return false;
			}
			if (current.next != null) {
				return true;
			}
			current.unlock();
			current = null;
			return false;
		}

		@Override
		public String next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			Node next = current.next;
			next.lock();
			current.unlock();
			current = next;
			return next.value;
		}
	}
}
