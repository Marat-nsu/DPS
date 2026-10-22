package dps.spider;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class SpiderTest {
    private HttpServer server;
    private ExecutorService executor;
    private URI root;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
        root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void stop() {
        server.stop(0);
        executor.shutdownNow();
        executor.close();
    }

    @Test
    void visitsCyclesAndSharedPathsOnceAndKeepsEqualMessages() throws Exception {
        var hits = new ConcurrentHashMap<String, AtomicInteger>();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            hits.computeIfAbsent(path, key -> new AtomicInteger()).incrementAndGet();
            String body = switch (path) {
                case "/" -> "{\"message\":\"я\",\"successors\":[\"a\",\"/b\",\"a\"]}";
                case "/a" -> "{\"message\":\"same\",\"successors\":[\"/\",\"b\",\"c\"]}";
                case "/b" -> "{\"message\":\"same\",\"successors\":[\"c\"]}";
                case "/c" -> "{\"message\":\"A\",\"successors\":[\"c\"]}";
                default -> throw new AssertionError(path);
            };
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        assertEquals(List.of("A", "same", "same", "я"), spider().crawl());
        assertEquals(4, hits.size());
        hits.values().forEach(count -> assertEquals(1, count.get()));
    }

    @Test
    void requestsIndependentSuccessorsConcurrently() throws Exception {
        var arrived = new CountDownLatch(3);
        var concurrent = new AtomicInteger();
        server.createContext("/", exchange -> {
            String body;
            if (exchange.getRequestURI().getPath().equals("/")) {
                body = "{\"message\":\"root\",\"successors\":[\"a\",\"b\",\"c\"]}";
            } else {
                arrived.countDown();
                try {
                    if (arrived.await(3, TimeUnit.SECONDS))
                        concurrent.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                body = "{\"message\":\"leaf\",\"successors\":[]}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        assertEquals(4, spider().crawl().size());
        assertEquals(3, concurrent.get(), "Все три запроса должны выполняться одновременно");
    }

    @Test
    void rejectsHttpErrors() {
        respond(404, "Not found");
        IOException error = assertThrows(IOException.class, () -> spider().crawl());
        assertTrue(error.getCause().getMessage().contains("404"));
    }

    @Test
    void rejectsMalformedJson() {
        respond(200, "not json");
        assertThrows(IOException.class, () -> spider().crawl());
    }

    @Test
    void timesOutSlowRequests() {
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        assertThrows(IOException.class, () -> new Spider(root, Duration.ofMillis(200)).crawl());
    }

    private Spider spider() {
        return new Spider(root, Duration.ofSeconds(5));
    }

    private void respond(int status, String body) {
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }
}
