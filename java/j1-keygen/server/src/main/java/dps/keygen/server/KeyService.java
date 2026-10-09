package dps.keygen.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KeyService {

	private final CertificateIssuer issuer;
	private final ExecutorService pool;
	private final ConcurrentHashMap<String, CompletableFuture<IssuedKey>> keys = new ConcurrentHashMap<>();

	public KeyService(CertificateIssuer issuer, int threads) {
		this.issuer = issuer;
		this.pool = Executors.newFixedThreadPool(threads);
	}

	public CompletableFuture<IssuedKey> request(String name) {
		CompletableFuture<IssuedKey> created = new CompletableFuture<>();
		CompletableFuture<IssuedKey> existing = keys.putIfAbsent(name, created);
		if (existing != null) {
			return existing;
		}

		pool.execute(() -> {
			try {
				created.complete(issuer.issue(name));
			} catch (Exception e) {
				// Удаляем до completeExceptionally, чтобы следующий запрос
				// с этим именем начал генерацию заново, а не получил эту ошибку
				keys.remove(name, created);
				created.completeExceptionally(e);
			}
		});
		return created;
	}
}
