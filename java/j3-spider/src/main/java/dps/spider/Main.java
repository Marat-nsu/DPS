package dps.spider;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "j3-spider", mixinStandardHelpOptions = true, description = "Обходит HTTP-сервер и выводит сообщения в лексикографическом порядке")
public class Main implements Callable<Integer> {
        @Parameters(index = "0", defaultValue = "http://localhost:8080", description = "Адрес сервера (по умолчанию http://localhost:8080)")
        private URI server;

        @Option(names = "--timeout", defaultValue = "30", description = "Таймаут одного запроса в секундах (по умолчанию 30)")
        private long timeoutSeconds;

        public static void main(String[] args) {
                System.exit(new CommandLine(new Main()).execute(args));
        }

        @Override
        public Integer call() throws Exception {
                long start = System.nanoTime();
                var messages = new Spider(server, Duration.ofSeconds(timeoutSeconds)).crawl();
                messages.forEach(System.out::println);
                System.err.printf("Сообщений: %d, время: %.3f с%n",
                                messages.size(), (System.nanoTime() - start) / 1_000_000_000.0);
                return 0;
        }
}
