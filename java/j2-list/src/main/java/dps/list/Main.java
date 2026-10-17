package dps.list;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "j2-list", mixinStandardHelpOptions = true, showDefaultValues = true, description = "Читает строки в начало списка, пустая строка печатает список; дочерние нити сортируют его пузырьком")
public class Main implements Callable<Integer> {

	private static final int MAX_LINE_LENGTH = 80;

	enum Impl {
		LINKED, ARRAY
	}

	@Option(names = { "-t", "--threads" }, description = "Число сортирующих нитей")
	private int threads = 2;

	@Option(names = { "-d", "--delay" }, description = "Задержка внутри шага и между шагами, мс")
	private long delayMillis = 1000;

	@Option(names = "--impl", description = "Реализация списка: ${COMPLETION-CANDIDATES}")
	private Impl impl = Impl.LINKED;

	public static void main(String[] args) {
		System.exit(new CommandLine(new Main()).setCaseInsensitiveEnumValuesAllowed(true).execute(args));
	}

	@Override
	public Integer call() throws Exception {
		if (threads < 1 || delayMillis < 0) {
			System.err.println("--threads должно быть не меньше 1, --delay не меньше 0");
			return 2;
		}

		AtomicLong steps = new AtomicLong();
		Consumer<String> addFirst;
		Runnable printList;
		Supplier<Runnable> newSorter;
		if (impl == Impl.LINKED) {
			ConcurrentLinkedList list = new ConcurrentLinkedList();
			addFirst = list::addFirst;
			printList = () -> print(list, steps.get());
			newSorter = () -> new LinkedListSorter(list, delayMillis, steps);
		} else {
			List<String> list = Collections.synchronizedList(new ArrayList<>());
			addFirst = s -> list.add(0, s);
			printList = () -> {
				synchronized (list) {
					print(list, steps.get());
				}
			};
			newSorter = () -> new ArrayListSorter(list, delayMillis, steps);
		}

		List<Thread> sorters = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Thread sorter = new Thread(newSorter.get(), "sorter-" + i);
			sorter.start();
			sorters.add(sorter);
		}

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String line;
		while ((line = in.readLine()) != null) {
			if (line.isEmpty()) {
				printList.run();
			} else {
				for (String part : split(line)) {
					addFirst.accept(part);
				}
			}
		}

		for (Thread sorter : sorters) {
			sorter.interrupt();
		}
		for (Thread sorter : sorters) {
			sorter.join();
		}
		return 0;
	}

	private static void print(Iterable<String> list, long steps) {
		int count = 0;
		System.out.println("--- список ---");
		for (String s : list) {
			System.out.println(s);
			count++;
		}
		System.out.println("--- строк: " + count + ", шагов сортировки: " + steps + " ---");
	}

	private static List<String> split(String line) {
		List<String> parts = new ArrayList<>();
		for (int start = 0; start < line.length(); start += MAX_LINE_LENGTH) {
			parts.add(line.substring(start, Math.min(start + MAX_LINE_LENGTH, line.length())));
		}
		return parts;
	}
}
