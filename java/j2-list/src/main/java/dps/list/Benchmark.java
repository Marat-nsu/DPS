package dps.list;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "benchmark", mixinStandardHelpOptions = true, showDefaultValues = true)
public class Benchmark implements Callable<Integer> {

	@Option(names = { "-d", "--delay" }, description = "Задержка внутри шага и между шагами, мс")
	private long delayMillis = 100;

	@Option(names = "--seconds", description = "Сколько секунд работают сортировщики в каждом замере")
	private int seconds = 10;

	@Option(names = "--size", description = "Число строк в списке")
	private int size = 100;

	@Option(names = { "-t", "--threads" }, split = ",", description = "Числа сортирующих нитей через запятую")
	private int[] threadCounts = { 1, 2, 4, 8 };

	public static void main(String[] args) {
		System.exit(new CommandLine(new Benchmark()).execute(args));
	}

	@Override
	public Integer call() throws Exception {
		System.out.printf("Задержка %d мс, %d с на замер, %d строк%n%n", delayMillis, seconds, size);
		System.out.println("нитей | linked: теория  факт | array: теория  факт");
		for (int threads : threadCounts) {
			long linked = measure(threads, false);
			long array = measure(threads, true);
			System.out.printf("%5d | %14d %5d | %13d %5d%n",
					threads, theoryLinked(threads), linked, theoryArray(threads), array);
		}
		return 0;
	}

	private long theoryLinked(int threads) {
		return threads * seconds * 1000L / (2 * delayMillis);
	}

	private long theoryArray(int threads) {
		return Math.min(theoryLinked(threads), seconds * 1000L / delayMillis);
	}

	private long measure(int threads, boolean array) throws InterruptedException {
		List<String> strings = randomStrings();
		AtomicLong steps = new AtomicLong();
		List<Thread> sorters = new ArrayList<>();

		if (array) {
			List<String> list = Collections.synchronizedList(new ArrayList<>(strings));
			for (int i = 0; i < threads; i++) {
				sorters.add(new Thread(new ArrayListSorter(list, delayMillis, steps)));
			}
		} else {
			ConcurrentLinkedList list = new ConcurrentLinkedList();
			strings.forEach(list::addFirst);
			for (int i = 0; i < threads; i++) {
				sorters.add(new Thread(new LinkedListSorter(list, delayMillis, steps)));
			}
		}

		sorters.forEach(Thread::start);
		Thread.sleep(seconds * 1000L);
		long result = steps.get();
		for (Thread sorter : sorters) {
			sorter.interrupt();
		}
		for (Thread sorter : sorters) {
			sorter.join();
		}
		return result;
	}

	private List<String> randomStrings() {
		Random random = new Random(42);
		List<String> strings = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			strings.add(Integer.toString(random.nextInt(1_000_000), 36));
		}
		return strings;
	}
}
