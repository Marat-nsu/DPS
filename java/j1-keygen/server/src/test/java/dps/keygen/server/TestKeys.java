package dps.keygen.server;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

final class TestKeys {

	private TestKeys() {
	}

	static KeyPair generateCaKeyPair() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

	static X509Certificate parseCertificate(String pem) throws Exception {
		CertificateFactory factory = CertificateFactory.getInstance("X.509");
		return (X509Certificate) factory.generateCertificate(
				new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
	}

	static PrivateKey parsePrivateKey(String pem) throws Exception {
		try (PEMParser parser = new PEMParser(new StringReader(pem))) {
			return new JcaPEMKeyConverter().getKeyPair((PEMKeyPair) parser.readObject()).getPrivate();
		}
	}
}
