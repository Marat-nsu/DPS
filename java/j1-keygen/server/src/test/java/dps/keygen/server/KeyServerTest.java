package dps.keygen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// Настоящий сервер на настоящем сокете. Клиенты здесь написаны на голых сокетах,
// а не через client.Main: его --abort убил бы JVM с тестами.
@Timeout(120)
class KeyServerTest {

	private static final int KEY_SIZE = 1024;

	private static KeyPair ca;
	// Имя → сколько раз для него запускалась генерация
	private static final Map<String, AtomicInteger> generations = new ConcurrentHashMap<>();
	private static int port;
	private static ExecutorService clients;

	@BeforeAll
	static void startServer() throws Exception {
		ca = TestKeys.generateCaKeyPair();
		CertificateIssuer issuer = new CertificateIssuer(ca.getPrivate(), "Test CA", KEY_SIZE) {
			@Override
			public IssuedKey issue(String name) throws Exception {
				generations.computeIfAbsent(name, n -> new AtomicInteger()).incrementAndGet();
				return super.issue(name);
			}
		};
		port = freePort();
		KeyServer server = new KeyServer(port, new KeyService(issuer, 4));

		Thread serverThread = new Thread(() -> {
			try {
				server.run();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		serverThread.setDaemon(true);
		serverThread.start();
		waitUntilListening();

		// Нити на стороне тестовых клиентов, не сервера: каждый клиент блокирующий
		clients = Executors.newFixedThreadPool(200);
	}

	@AfterAll
	static void stopClients() {
		clients.shutdownNow();
	}

	@Test
	void clientReceivesKeyAndCertificate() throws Exception {
		String response = request("alice", 0);

		X509Certificate cert = checkResponse("alice", response);
		cert.verify(ca.getPublic());
	}

	@Test
	void nameSentInPiecesIsAssembled() throws Exception {
		try (Socket socket = new Socket("localhost", port)) {
			OutputStream out = socket.getOutputStream();
			out.write("pie".getBytes(StandardCharsets.US_ASCII));
			out.flush();
			Thread.sleep(300);
			out.write("ces\0".getBytes(StandardCharsets.US_ASCII));
			out.flush();

			checkResponse("pieces", readAll(socket));
		}
	}

	@Test
	void invalidRequestsAreClosedWithoutResponse() throws Exception {
		assertEquals("", rawRequest("x".repeat(1000).getBytes(StandardCharsets.US_ASCII)));
		assertEquals("", rawRequest("привет\0".getBytes(StandardCharsets.UTF_8)));
		assertEquals("", rawRequest(new byte[] { 0 }));
		// Сервер после этого продолжает работать
		checkResponse("after-invalid", request("after-invalid", 0));
	}

	@Test
	void slowClientDoesNotBlockOthers() throws Exception {
		// Медленный клиент не читает ответ 5 секунд
		Future<String> slow = clients.submit(() -> request("slow-reader", 5000));
		Thread.sleep(200);

		long start = System.currentTimeMillis();
		checkResponse("fast-reader", request("fast-reader", 0));
		long fastMillis = System.currentTimeMillis() - start;

		assertTrue(fastMillis < 4000, "Быстрый клиент ждал " + fastMillis + " мс");
		checkResponse("slow-reader", slow.get());
	}

	@Test
	void abortedClientsDoNotBreakServer() throws Exception {
		for (int i = 0; i < 20; i++) {
			abort("aborted-" + i);
		}
		checkResponse("after-abort", request("after-abort", 0));
	}

	@Test
	void hundredsOfClientsWithRepeatedNames() throws Exception {
		int names = 40;
		int clientsPerName = 3;
		List<String> requested = new ArrayList<>();
		List<Future<String>> responses = new ArrayList<>();
		List<Future<?>> aborted = new ArrayList<>();

		for (int i = 0; i < names * clientsPerName; i++) {
			String name = "load-" + (i % names);
			// Каждый третий клиент медленный
			int delay = i % 3 == 0 ? 2000 : 0;
			requested.add(name);
			responses.add(clients.submit(() -> request(name, delay)));
		}
		for (int i = 0; i < 30; i++) {
			String name = "load-aborted-" + i;
			aborted.add(clients.submit(() -> {
				abort(name);
				return null;
			}));
		}

		Map<String, String> firstResponse = new HashMap<>();
		for (int i = 0; i < responses.size(); i++) {
			String name = requested.get(i);
			String response = responses.get(i).get();
			checkResponse(name, response);
			// Все клиенты с одним именем получают одну и ту же пару ключей
			String previous = firstResponse.putIfAbsent(name, response);
			if (previous != null) {
				assertEquals(previous, response, "Разные ключи для " + name);
			}
		}
		for (Future<?> f : aborted) {
			f.get();
		}

		// 3 клиента на имя, но генерация на каждое имя ровно одна
		for (int i = 0; i < names; i++) {
			assertEquals(1, generationCount("load-" + i));
		}
	}

	@Test
	void repeatedNameIsGeneratedOnlyOnce() throws Exception {
		List<Future<String>> responses = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			responses.add(clients.submit(() -> request("same-name", 0)));
		}
		String first = responses.get(0).get();
		for (Future<String> response : responses) {
			assertEquals(first, response.get());
		}
		assertEquals(1, generationCount("same-name"));
	}

	// --- клиенты на голых сокетах ---

	private static String request(String name, long delayMillis) throws Exception {
		try (Socket socket = new Socket("localhost", port)) {
			OutputStream out = socket.getOutputStream();
			out.write((name + "\0").getBytes(StandardCharsets.US_ASCII));
			out.flush();
			Thread.sleep(delayMillis);
			return readAll(socket);
		}
	}

	private static String rawRequest(byte[] bytes) throws Exception {
		try (Socket socket = new Socket("localhost", port)) {
			OutputStream out = socket.getOutputStream();
			out.write(bytes);
			out.flush();
			return readAll(socket);
		} catch (IOException e) {
			// Сервер мог закрыть соединение, пока мы ещё писали: это тоже "без ответа"
			return "";
		}
	}

	// Как --abort у клиента: отправили имя и оборвали соединение пакетом RST
	private static void abort(String name) throws Exception {
		Socket socket = new Socket("localhost", port);
		socket.getOutputStream().write((name + "\0").getBytes(StandardCharsets.US_ASCII));
		socket.setSoLinger(true, 0);
		socket.close();
	}

	private static String readAll(Socket socket) throws IOException {
		InputStream in = socket.getInputStream();
		return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
	}

	private static X509Certificate checkResponse(String name, String response) throws Exception {
		int certStart = response.indexOf("-----BEGIN CERTIFICATE-----");
		assertTrue(certStart > 0, "Нет ключа или сертификата в ответе для " + name);

		X509Certificate cert = TestKeys.parseCertificate(response.substring(certStart));
		assertEquals("CN=" + name, cert.getSubjectX500Principal().getName());
		RSAPrivateCrtKey key = (RSAPrivateCrtKey) TestKeys.parsePrivateKey(response.substring(0, certStart));
		assertEquals(((RSAPublicKey) cert.getPublicKey()).getModulus(), key.getModulus(),
				"Ключ не парный к сертификату для " + name);
		return cert;
	}

	private static int generationCount(String name) {
		AtomicInteger count = generations.get(name);
		return count == null ? 0 : count.get();
	}

	private static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static void waitUntilListening() throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			try {
				new Socket("localhost", port).close();
				return;
			} catch (IOException e) {
				Thread.sleep(50);
			}
		}
		throw new IllegalStateException("Server did not start");
	}
}
