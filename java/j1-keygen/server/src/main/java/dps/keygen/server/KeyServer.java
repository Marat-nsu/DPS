package dps.keygen.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class KeyServer {

	private static final int MAX_NAME_LENGTH = 256;

	private static class Connection {
		// +1 место под завершающий нулевой байт
		final ByteBuffer readBuffer = ByteBuffer.allocate(MAX_NAME_LENGTH + 1);
		ByteBuffer writeBuffer;
		String name;
	}

	private record Completed(SelectionKey key, IssuedKey result) {
	}

	private final int port;
	private final KeyService keyService;
	private final Selector selector;
	private final Queue<Completed> completed = new ConcurrentLinkedQueue<>();

	public KeyServer(int port, KeyService keyService) throws IOException {
		this.port = port;
		this.keyService = keyService;
		this.selector = Selector.open();
	}

	public void run() throws IOException {
		try (selector; ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
			serverChannel.bind(new InetSocketAddress(port));
			serverChannel.configureBlocking(false);
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
			System.out.println("Listening on port " + port);

			while (true) {
				selector.select();

				processCompleted();

				Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
				while (keys.hasNext()) {
					SelectionKey key = keys.next();
					keys.remove();
					try {
						if (!key.isValid()) {
							continue;
						}
						if (key.isAcceptable()) {
							accept(serverChannel);
						} else if (key.isReadable()) {
							read(key);
						} else if (key.isWritable()) {
							write(key);
						}
					} catch (IOException e) {
						System.out.println("Connection error: " + e.getMessage());
						close(key);
					}
				}
			}
		}
	}

	private void accept(ServerSocketChannel serverChannel) throws IOException {
		SocketChannel channel = serverChannel.accept();
		if (channel == null) {
			return;
		}
		channel.configureBlocking(false);
		channel.register(selector, SelectionKey.OP_READ, new Connection());
		System.out.println("Accepted " + channel.getRemoteAddress());
	}

	private void read(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		Connection conn = (Connection) key.attachment();
		ByteBuffer buffer = conn.readBuffer;

		if (channel.read(buffer) == -1) {
			close(key);
			return;
		}

		for (int i = 0; i < buffer.position(); i++) {
			byte b = buffer.get(i);
			if (b == 0) {
				String name = new String(buffer.array(), 0, i, StandardCharsets.US_ASCII);
				if (name.isEmpty()) {
					close(key);
				} else {
					onNameReceived(key, conn, name);
				}
				return;
			}
			if (b < 0) {
				close(key);
				return;
			}
		}

		if (!buffer.hasRemaining()) {
			close(key);
		}
	}

	private void onNameReceived(SelectionKey key, Connection conn, String name) {
		conn.name = name;
		System.out.println("Request for '" + name + "'");

		// Пока ключ генерируется, читать уже нечего, а писать ещё нечего
		key.interestOps(0);

		keyService.request(name).whenComplete((result, error) -> {
			if (error != null) {
				System.out.println("Generation failed for '" + name + "': " + error);
			}
			completed.add(new Completed(key, result));
			selector.wakeup();
		});
	}

	private void processCompleted() {
		Completed done;
		while ((done = completed.poll()) != null) {
			SelectionKey key = done.key();
			if (!key.isValid()) {
				continue;
			}
			if (done.result() == null) {
				close(key);
				continue;
			}
			Connection conn = (Connection) key.attachment();
			String response = done.result().privateKeyPem() + done.result().certificatePem();
			conn.writeBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.US_ASCII));
			key.interestOps(SelectionKey.OP_WRITE);
		}
	}

	private void write(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		Connection conn = (Connection) key.attachment();

		channel.write(conn.writeBuffer);

		if (!conn.writeBuffer.hasRemaining()) {
			System.out.println("Sent keys for '" + conn.name + "'");
			close(key);
		}
	}

	private void close(SelectionKey key) {
		key.cancel();
		try {
			key.channel().close();
		} catch (IOException e) {
		}
	}
}
