package dps.keygen.server;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class CertificateIssuer {

	private final PrivateKey caKey;
	private final String issuerName;
	private final int keySize;
	private final SecureRandom random = new SecureRandom();

	public CertificateIssuer(PrivateKey caKey, String issuerName, int keySize) {
		this.caKey = caKey;
		this.issuerName = issuerName;
		this.keySize = keySize;
	}

	public IssuedKey issue(String name) throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(keySize);
		KeyPair keyPair = generator.generateKeyPair();

		X500Name issuer = commonName(issuerName);
		X500Name subject = commonName(name);
		BigInteger serial = new BigInteger(64, random);
		Instant now = Instant.now();
		Date notBefore = Date.from(now);
		Date notAfter = Date.from(now.plus(Duration.ofDays(365)));

		JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
				issuer, serial, notBefore, notAfter, subject, keyPair.getPublic());

		ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKey);

		X509CertificateHolder certHolder = builder.build(signer);

		return new IssuedKey(toPem(keyPair.getPrivate()), toPem(certHolder));
	}

	private static String toPem(Object object) throws IOException {
		StringWriter out = new StringWriter();
		try (JcaPEMWriter writer = new JcaPEMWriter(out)) {
			writer.writeObject(object);
		}
		return out.toString();
	}

	// Не "CN=" + cn: иначе имя "bob,O=Evil" превратится в лишние поля сертификата
	private static X500Name commonName(String cn) {
		return new X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN, cn).build();
	}
}
