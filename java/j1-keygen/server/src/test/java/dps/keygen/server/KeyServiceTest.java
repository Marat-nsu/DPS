package dps.keygen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// Настоящий RSA здесь не нужен: подменяем CertificateIssuer заглушкой,
// чтобы проверять только пул и дедупликацию
@Timeout(10)
class KeyServiceTest {

	private static class FakeIssuer extends CertificateIssuer {
		final AtomicInteger calls = new AtomicInteger();

		FakeIssuer() {
			super(null, "Fake CA", 0);
		}

		@Override
		public IssuedKey issue(String name) throws Exception {
			calls.incrementAndGet();
			return new IssuedKey("key:" + name, "cert:" + name);
		}
	}

	@Test
	void concurrentRequestsForSameNameGenerateOnce() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		FakeIssuer issuer = new FakeIssuer() {
			@Override
			public IssuedKey issue(String name) throws Exception {
				release.await();
				return super.issue(name);
			}
		};
		KeyService service = new KeyService(issuer, 4);

		int requesters = 50;
		ExecutorService threads = Executors.newFixedThreadPool(requesters);
		CyclicBarrier start = new CyclicBarrier(requesters);
		List<Future<CompletableFuture<IssuedKey>>> results = new ArrayList<>();
		for (int i = 0; i < requesters; i++) {
			results.add(threads.submit(() -> {
				start.await();
				return service.request("alice");
			}));
		}

		CompletableFuture<IssuedKey> first = results.get(0).get();
		for (Future<CompletableFuture<IssuedKey>> result : results) {
			assertSame(first, result.get());
		}
		release.countDown();
		assertEquals("key:alice", first.get().privateKeyPem());
		assertEquals(1, issuer.calls.get());
		threads.shutdown();
	}

	@Test
	void requestReturnsBeforeGenerationFinishes() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		FakeIssuer issuer = new FakeIssuer() {
			@Override
			public IssuedKey issue(String name) throws Exception {
				release.await();
				return super.issue(name);
			}
		};
		KeyService service = new KeyService(issuer, 1);

		CompletableFuture<IssuedKey> future = service.request("alice");
		assertFalse(future.isDone());

		release.countDown();
		assertEquals("cert:alice", future.get().certificatePem());
	}

	@Test
	void finishedKeyIsReturnedAgain() throws Exception {
		FakeIssuer issuer = new FakeIssuer();
		KeyService service = new KeyService(issuer, 2);

		IssuedKey first = service.request("alice").get();
		CompletableFuture<IssuedKey> again = service.request("alice");

		assertTrue(again.isDone());
		assertSame(first, again.get());
		assertEquals(1, issuer.calls.get());
	}

	@Test
	void differentNamesAreGeneratedInParallel() throws Exception {
		int threads = 4;
		// Каждая генерация ждёт, пока до барьера дойдут все 4.
		// Если бы пул выполнял задачи по одной, барьер бы не сработал.
		CyclicBarrier allRunning = new CyclicBarrier(threads);
		FakeIssuer issuer = new FakeIssuer() {
			@Override
			public IssuedKey issue(String name) throws Exception {
				allRunning.await(5, TimeUnit.SECONDS);
				return super.issue(name);
			}
		};
		KeyService service = new KeyService(issuer, threads);

		List<CompletableFuture<IssuedKey>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(service.request("user" + i));
		}
		for (CompletableFuture<IssuedKey> future : futures) {
			future.get();
		}
		assertEquals(threads, issuer.calls.get());
	}

	@Test
	void failedGenerationIsRetriedOnNextRequest() throws Exception {
		AtomicInteger attempts = new AtomicInteger();
		FakeIssuer issuer = new FakeIssuer() {
			@Override
			public IssuedKey issue(String name) throws Exception {
				if (attempts.incrementAndGet() == 1) {
					throw new IllegalStateException("first attempt fails");
				}
				return super.issue(name);
			}
		};
		KeyService service = new KeyService(issuer, 1);

		CompletableFuture<IssuedKey> failed = service.request("alice");
		assertThrows(ExecutionException.class, failed::get);

		CompletableFuture<IssuedKey> retried = service.request("alice");
		assertNotSame(failed, retried);
		assertEquals("key:alice", retried.get().privateKeyPem());
	}
}
