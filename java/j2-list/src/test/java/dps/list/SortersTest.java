package dps.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dps.list.ConcurrentLinkedList.Node;

@Timeout(30)
class SortersTest {

	private static final int THREADS = 8;

	@Test
	void linkedListGetsSorted() throws Exception {
		ConcurrentLinkedList list = new ConcurrentLinkedList();
		List<String> input = randomStrings(50);
		input.forEach(list::addFirst);

		runSorters(() -> new LinkedListSorter(list, 0, new AtomicLong()), () -> isSorted(snapshot(list)));

		assertEquals(sorted(input), snapshot(list));
	}

	@Test
	void arrayListGetsSorted() throws Exception {
		List<String> list = Collections.synchronizedList(new ArrayList<>());
		List<String> input = randomStrings(50);
		input.forEach(s -> list.add(0, s));

		runSorters(() -> new ArrayListSorter(list, 0, new AtomicLong()), () -> isSorted(snapshot(list)));

		assertEquals(sorted(input), snapshot(list));
	}

	@Test
	void linkedSorterRelinksNodesInsteadOfSwappingStrings() throws Exception {
		ConcurrentLinkedList list = new ConcurrentLinkedList();
		randomStrings(30).forEach(list::addFirst);
		Map<Node, String> before = nodeValues(list);

		runSorters(() -> new LinkedListSorter(list, 0, new AtomicLong()), () -> isSorted(snapshot(list)));

		Map<Node, String> after = nodeValues(list);
		// Те же объекты-узлы, и в каждом та же строка: двигались ссылки, а не содержимое
		assertEquals(before.keySet(), after.keySet());
		for (Map.Entry<Node, String> entry : before.entrySet()) {
			assertSame(entry.getValue(), after.get(entry.getKey()));
		}
	}

	@Test
	void linkedListContentIsConsistentWhileSortingAndInserting() throws Exception {
		ConcurrentLinkedList list = new ConcurrentLinkedList();
		checkConsistency(list::addFirst, () -> snapshot(list),
				() -> new LinkedListSorter(list, 1, new AtomicLong()));
	}

	@Test
	void arrayListContentIsConsistentWhileSortingAndInserting() throws Exception {
		List<String> list = Collections.synchronizedList(new ArrayList<>());
		checkConsistency(s -> list.add(0, s), () -> snapshot(list),
				() -> new ArrayListSorter(list, 1, new AtomicLong()));
	}

	@Test
	void stepsAreCounted() throws Exception {
		ConcurrentLinkedList list = new ConcurrentLinkedList();
		randomStrings(10).forEach(list::addFirst);
		AtomicLong steps = new AtomicLong();

		runSorters(() -> new LinkedListSorter(list, 0, steps), () -> steps.get() >= 1000);

		assertTrue(steps.get() >= 1000);
	}

	// Главная нить вставляет и печатает, пока сортировщики работают.
	// Каждый вывод должен содержать ровно введённые строки: без потерь и дублей.
	private void checkConsistency(Consumer<String> addFirst, Supplier<List<String>> print,
			Supplier<Runnable> newSorter) throws Exception {
		List<String> input = new ArrayList<>(randomStrings(30));
		input.forEach(addFirst);

		List<Thread> sorters = startSorters(newSorter);
		try {
			long end = System.currentTimeMillis() + 2000;
			for (int i = 0; System.currentTimeMillis() < end; i++) {
				if (i % 10 == 0) {
					String s = "new-" + i;
					input.add(s);
					addFirst.accept(s);
				}
				assertEquals(sorted(input), sorted(print.get()), "Состав списка не совпал с введённым");
			}
		} finally {
			stop(sorters);
		}
	}

	private void runSorters(Supplier<Runnable> newSorter, Supplier<Boolean> done) throws Exception {
		List<Thread> sorters = startSorters(newSorter);
		try {
			while (!done.get()) {
				Thread.sleep(10);
			}
		} finally {
			stop(sorters);
		}
	}

	private List<Thread> startSorters(Supplier<Runnable> newSorter) {
		List<Thread> sorters = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			Thread sorter = new Thread(newSorter.get());
			sorter.start();
			sorters.add(sorter);
		}
		return sorters;
	}

	private void stop(List<Thread> sorters) throws InterruptedException {
		sorters.forEach(Thread::interrupt);
		for (Thread sorter : sorters) {
			sorter.join();
		}
	}

	private static List<String> snapshot(ConcurrentLinkedList list) {
		return ConcurrentLinkedListTest.toList(list);
	}

	private static List<String> snapshot(List<String> synchronizedList) {
		synchronized (synchronizedList) {
			return new ArrayList<>(synchronizedList);
		}
	}

	private static Map<Node, String> nodeValues(ConcurrentLinkedList list) {
		Map<Node, String> values = new IdentityHashMap<>();
		for (Node node = list.head.next; node != null; node = node.next) {
			values.put(node, node.value);
		}
		return values;
	}

	private static boolean isSorted(List<String> list) {
		return list.equals(sorted(list));
	}

	private static List<String> sorted(List<String> list) {
		List<String> copy = new ArrayList<>(list);
		Collections.sort(copy);
		return copy;
	}

	private static List<String> randomStrings(int count) {
		Random random = new Random(42);
		List<String> strings = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			strings.add(Integer.toString(random.nextInt(1_000_000), 36));
		}
		return strings;
	}
}
