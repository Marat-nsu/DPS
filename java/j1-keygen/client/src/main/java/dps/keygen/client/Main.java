package dps.keygen.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "keygen-client", mixinStandardHelpOptions = true, description = "Запрашивает у сервера ключ и сертификат и сохраняет их в <name>.key и <name>.crt")
public class Main implements Callable<Integer> {

	private static final String CERT_BEGIN = "-----BEGIN CERTIFICATE-----";

	@Parameters(index = "0", description = "Имя (ASCII), на которое выдаётся сертификат")
	private String name;

	@Parameters(index = "1", description = "Адрес или DNS-имя сервера")
	private String host;

	@Parameters(index = "2", description = "Порт сервера")
	private int port;

	@Option(names = "--delay", description = "Пауза в секундах между отправкой запроса и чтением ответа")
	private int delaySeconds = 0;

	@Option(names = "--abort", description = "Завершиться сразу после отправки запроса, не читая ответ")
	private boolean abort = false;

	public static void main(String[] args) {
		System.exit(new CommandLine(new Main()).execute(args));
	}

	@Override
	public Integer call() throws Exception {
		if (!StandardCharsets.US_ASCII.newEncoder().canEncode(name) || name.isEmpty() || name.indexOf('\0') >= 0) {
			System.err.println("Имя должно быть непустой ASCII-строкой без нулевых байтов");
			return 2;
		}

		try (Socket socket = new Socket(host, port)) {
			OutputStream out = socket.getOutputStream();
			out.write(name.getBytes(StandardCharsets.US_ASCII));
			out.write(0);
			out.flush();

			if (delaySeconds > 0) {
				Thread.sleep(delaySeconds * 1000L);
			}

			if (abort) {
				System.err.println("Aborting without reading the response");
				Runtime.getRuntime().halt(1);
			}

			InputStream in = socket.getInputStream();
			String response = new String(in.readAllBytes(), StandardCharsets.US_ASCII);

			int certStart = response.indexOf(CERT_BEGIN);
			if (certStart <= 0) {
				System.err.println("Сервер вернул пустой или некорректный ответ");
				return 1;
			}

			Files.writeString(Path.of(name + ".key"), response.substring(0, certStart));
			Files.writeString(Path.of(name + ".crt"), response.substring(certStart));
		}

		System.out.println("Saved " + name + ".key and " + name + ".crt");
		return 0;
	}
}
