package dps.keygen.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.concurrent.Callable;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "keygen-server", mixinStandardHelpOptions = true, showDefaultValues = true,
		description = "Выдаёт клиентам пары ключей RSA и сертификаты X.509 по имени")
public class Main implements Callable<Integer> {

	@Option(names = { "-p", "--port" }, description = "TCP-порт")
	private int port = 5555;

	@Option(names = { "-t", "--threads" }, description = "Число генерирующих нитей")
	private int threads = Runtime.getRuntime().availableProcessors();

	@Option(names = "--ca-key", description = "Приватный ключ CA в PEM, которым подписываются сертификаты")
	private Path caKeyPath = Path.of("ca.key");

	@Option(names = "--issuer", description = "Issuer Name (CN) в выдаваемых сертификатах")
	private String issuerName = "DPS Lab CA";

	@Option(names = "--key-size", description = "Размер генерируемого ключа RSA в битах")
	private int keySize = 8192;

	public static void main(String[] args) {
		System.exit(new CommandLine(new Main()).execute(args));
	}

	@Override
	public Integer call() throws Exception {
		if (threads < 1) {
			System.err.println("--threads должно быть не меньше 1");
			return 2;
		}

		PrivateKey caKey = readPrivateKey(caKeyPath);
		CertificateIssuer issuer = new CertificateIssuer(caKey, issuerName, keySize);
		KeyService keyService = new KeyService(issuer, threads);

		System.out.println("Issuer: CN=" + issuerName + ", key size: " + keySize + ", threads: " + threads);
		new KeyServer(port, keyService).run();
		return 0;
	}

	static PrivateKey readPrivateKey(Path path) throws Exception {
		try (PEMParser parser = new PEMParser(Files.newBufferedReader(path))) {
			Object pem = parser.readObject();
			JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
			// "BEGIN PRIVATE KEY" (PKCS#8, так пишет openssl genpkey)
			if (pem instanceof PrivateKeyInfo info) {
				return converter.getPrivateKey(info);
			}
			// "BEGIN RSA PRIVATE KEY" (старый формат PKCS#1)
			if (pem instanceof PEMKeyPair pair) {
				return converter.getKeyPair(pair).getPrivate();
			}
			throw new IllegalArgumentException(path + ": not a private key");
		}
	}
}
