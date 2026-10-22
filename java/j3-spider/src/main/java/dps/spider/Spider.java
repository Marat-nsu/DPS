package dps.spider;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

public class Spider {
    private final URI root;
    private final Duration timeout;
    private final Gson gson = new Gson();

    public Spider(URI server, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Таймаут должен быть положительным");
        }
        this.root = server.resolve("/");
        this.timeout = timeout;
    }

    public List<String> crawl() throws IOException, InterruptedException {
        var visited = new HashSet<URI>();
        var messages = new ArrayList<String>();
        try (var client = HttpClient.newBuilder().connectTimeout(timeout).build();
                var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var completed = new ExecutorCompletionService<Page>(executor);
            visited.add(root);
            completed.submit(() -> fetch(client, root));
            int pending = 1;
            try {
                while (pending > 0) {
                    Page page = completed.take().get();
                    pending--;
                    messages.add(page.message());
                    for (String path : page.successors()) {
                        URI uri = root.resolve(path).normalize();
                        if (visited.add(uri)) {
                            completed.submit(() -> fetch(client, uri));
                            pending++;
                        }
                    }
                }
            } catch (ExecutionException e) {
                throw new IOException("Не удалось завершить обход", e.getCause());
            } finally {
                executor.shutdownNow();
            }
        }
        Collections.sort(messages);
        return messages;
    }

    private Page fetch(HttpClient client, URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("GET " + uri + ": HTTP " + response.statusCode());
        }
        try {
            return gson.fromJson(response.body(), Page.class);
        } catch (JsonParseException e) {
            throw new IOException("Некорректный JSON от " + uri, e);
        }
    }

    private record Page(String message, List<String> successors) {
    }
}
