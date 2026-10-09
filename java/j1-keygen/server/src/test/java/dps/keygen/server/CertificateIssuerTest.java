package dps.keygen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CertificateIssuerTest {

	private static KeyPair ca;
	private static CertificateIssuer issuer;

	@BeforeAll
	static void setUp() throws Exception {
		ca = TestKeys.generateCaKeyPair();
		issuer = new CertificateIssuer(ca.getPrivate(), "Test CA", 1024);
	}

	@Test
	void certificateHasNamesAndIsSignedByCa() throws Exception {
		IssuedKey issued = issuer.issue("alice");
		X509Certificate cert = TestKeys.parseCertificate(issued.certificatePem());

		assertEquals("CN=alice", cert.getSubjectX500Principal().getName());
		assertEquals("CN=Test CA", cert.getIssuerX500Principal().getName());
		// Бросит исключение, если подпись не от ключа CA
		cert.verify(ca.getPublic());
		cert.checkValidity();
	}

	@Test
	void privateKeyMatchesCertificate() throws Exception {
		IssuedKey issued = issuer.issue("bob");
		RSAPublicKey publicKey = (RSAPublicKey) TestKeys.parseCertificate(issued.certificatePem()).getPublicKey();
		RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) TestKeys.parsePrivateKey(issued.privateKeyPem());

		assertEquals(1024, publicKey.getModulus().bitLength());
		assertEquals(publicKey.getModulus(), privateKey.getModulus());
	}

	@Test
	void specialCharactersStayInsideCommonName() throws Exception {
		X509Certificate cert = TestKeys.parseCertificate(issuer.issue("bob,O=Evil").certificatePem());

		// Запятая экранирована, то есть это одно поле CN, а не CN + O
		assertEquals("CN=bob\\,O\\=Evil", cert.getSubjectX500Principal().getName());
	}

	@Test
	void eachCallGeneratesNewKeyAndSerial() throws Exception {
		X509Certificate first = TestKeys.parseCertificate(issuer.issue("carol").certificatePem());
		X509Certificate second = TestKeys.parseCertificate(issuer.issue("carol").certificatePem());

		assertNotEquals(first.getPublicKey(), second.getPublicKey());
		assertNotEquals(first.getSerialNumber(), second.getSerialNumber());
	}
}
