package dps.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class ConcurrentLinkedListTest {

	@Test
	void emptyListHasNoElements() {
		ConcurrentLinkedList list = new ConcurrentLinkedList();
		Iterator<String> it = list.iterator();

		assertFalse(it.hasNext());
		assertThrows(NoSuchElementException.class, it::next);
	}

	@Test
	void addFirstPutsElementAtHead() {
		ConcurrentLinkedList list = new ConcurrentLinkedList();
		list.addFirst("c");
		list.addFirst("b");
		list.addFirst("a");

		assertEquals(List.of("a", "b", "c"), toList(list));
	}

	@Test
	void fullTraversalReleasesAllLocks() throws Exception {
		ConcurrentLinkedList list = new ConcurrentLinkedList();
		list.addFirst("a");
		list.addFirst("b");
		toList(list);

		Thread other = new Thread(() -> list.addFirst("c"));
		other.start();
		other.join(TimeUnit.SECONDS.toMillis(2));

		assertFalse(other.isAlive(), "Вставка из другой нити зависла: замок не отпущен");
		assertEquals(List.of("c", "b", "a"), toList(list));
	}

	@Test
	void iteratorBlocksOnlyItsCurrentNode() throws Exception {
		ConcurrentLinkedList list = new ConcurrentLinkedList();
		list.addFirst("b");
		list.addFirst("a");

		Iterator<String> it = list.iterator();
		// Итератор держит head, вставка в начало должна ждать
		Thread blocked = new Thread(() -> list.addFirst("x"));
		blocked.start();
		blocked.join(300);
		assertTrue(blocked.isAlive());

		// Ушёл с head на "a": вставка проходит
		assertEquals("a", it.next());
		blocked.join(TimeUnit.SECONDS.toMillis(2));
		assertFalse(blocked.isAlive());

		assertEquals("b", it.next());
		assertFalse(it.hasNext());
		assertEquals(List.of("x", "a", "b"), toList(list));
	}

	static List<String> toList(Iterable<String> list) {
		List<String> result = new ArrayList<>();
		for (String s : list) {
			result.add(s);
		}
		return result;
	}
}
