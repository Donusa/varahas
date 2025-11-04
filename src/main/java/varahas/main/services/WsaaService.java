package varahas.main.services;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import varahas.main.dto.SaveResult;
import varahas.main.dto.TokenSign;
import varahas.main.exceptions.AlreadyAuthenticatedException;

@Service
public class WsaaService {

	{
		if (Security.getProvider("BC") == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
	private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

	public Path writeLoginTicketRequest(String tenantCode, String sourceCuit, String sourceCn, String service,
			boolean homologacion) throws IOException {
		String safe = tenantCode.replaceAll("[^a-zA-Z0-9._-]", "_");
		Path dir = Paths.get(safe + "-certs").toAbsolutePath().normalize();
		Files.createDirectories(dir);

		String source = "SERIALNUMBER=CUIT " + sourceCuit + ",CN=" + sourceCn;
		String dest = (homologacion ? "CN=wsaahomo, O=AFIP, C=AR, SERIALNUMBER=CUIT 33693450239"
				: "CN=wsaa, O=AFIP, C=AR, SERIALNUMBER=CUIT 33693450239");

		Instant now = Instant.now();
		String gen = ISO_OFFSET.format(ZonedDateTime.ofInstant(now.minus(Duration.ofMinutes(10)), AR));
		String exp = ISO_OFFSET.format(ZonedDateTime.ofInstant(now.plus(Duration.ofMinutes(30)), AR));
		long uniq = now.getEpochSecond();

		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<loginTicketRequest version="1.0">
				  <header>
				    <source>%s</source>
				    <destination>%s</destination>
				    <uniqueId>%d</uniqueId>
				    <generationTime>%s</generationTime>
				    <expirationTime>%s</expirationTime>
				  </header>
				  <service>%s</service>
				</loginTicketRequest>
				""".formatted(source, dest, uniq, gen, exp, service);

		Path out = dir.resolve("loginTicketRequest.xml");
		Files.writeString(out, xml, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
		return out;
	}

	public SaveResult saveCertAndKey(String tenantName, MultipartFile crt, MultipartFile key) throws IOException {
		if (tenantName == null || tenantName.isBlank())
			throw new IllegalArgumentException("tenantCode vacío");
		if (crt == null || crt.isEmpty())
			throw new IllegalArgumentException("archivo .crt vacío");
		if (key == null || key.isEmpty())
			throw new IllegalArgumentException("archivo .key vacío");

		String crtName = (crt.getOriginalFilename() == null ? "" : crt.getOriginalFilename()).toLowerCase(Locale.ROOT);
		String keyName = (key.getOriginalFilename() == null ? "" : key.getOriginalFilename()).toLowerCase(Locale.ROOT);
		if (!crtName.endsWith(".crt"))
			throw new IllegalArgumentException("el archivo crt debe terminar en .crt");
		if (!keyName.endsWith(".key"))
			throw new IllegalArgumentException("el archivo key debe terminar en .key");

		String safe = tenantName.replaceAll("[^a-zA-Z0-9._-]", "_");
		Path dir = Paths.get(safe + "-certs").toAbsolutePath().normalize();
		Files.createDirectories(dir);

		Path crtPath = dir.resolve("certificado.crt").normalize();
		Path keyPath = dir.resolve("private.key").normalize();
		if (!crtPath.startsWith(dir) || !keyPath.startsWith(dir))
			throw new SecurityException("Ruta inválida");

		boolean existed = Files.exists(crtPath) || Files.exists(keyPath);

		try (InputStream in = crt.getInputStream()) {
			Files.copy(in, crtPath, StandardCopyOption.REPLACE_EXISTING);
		}
		try (InputStream in = key.getInputStream()) {
			Files.copy(in, keyPath, StandardCopyOption.REPLACE_EXISTING);
		}

		try {
			Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
			Files.setPosixFilePermissions(crtPath, PosixFilePermissions.fromString("rw-r-----"));
		} catch (UnsupportedOperationException ignored) {
		}

		return new SaveResult(dir, existed);
	}

	public Path signCmsPem(Path tenantDir) throws Exception {
		Path ltr = tenantDir.resolve("loginTicketRequest.xml");
		Path crt = tenantDir.resolve("certificado.crt");
		Path key = tenantDir.resolve("private.key");
		byte[] data = Files.readAllBytes(ltr);

		X509Certificate cert = loadCertificate(crt);
		PrivateKey privateKey = loadPrivateKey(key);

		ContentSigner contentSigner = new JcaContentSignerBuilder("SHA1withRSA").setProvider("BC").build(privateKey);

		CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
		gen.addSignerInfoGenerator(
				new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
						.build(contentSigner, cert));
		gen.addCertificates(new JcaCertStore(Collections.singletonList(cert)));

		CMSTypedData cmsData = new CMSProcessableByteArray(data);
		CMSSignedData cms = gen.generate(cmsData, true);

		byte[] der = cms.toASN1Structure().getEncoded(ASN1Encoding.DER);
		Path out = tenantDir.resolve("loginCms.der");
		Files.write(out, der, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		return out;
	}

	public Path verifyAndExtractPayload(Path tenantDir) throws Exception {
		Path cmsPath = tenantDir.resolve("loginCms.der");
		CMSSignedData cms = new CMSSignedData(Files.readAllBytes(cmsPath));

		byte[] payload = (byte[]) cms.getSignedContent().getContent();
		Path out = tenantDir.resolve("payload.xml");
		Files.write(out, payload, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		Store<X509CertificateHolder> certs = cms.getCertificates();
		SignerInformationStore signers = cms.getSignerInfos();

		for (SignerInformation s : signers.getSigners()) {
			@SuppressWarnings("unchecked")
			Collection<X509CertificateHolder> matches = (Collection<X509CertificateHolder>) certs
					.getMatches(s.getSID());
			X509CertificateHolder h = matches.iterator().next();
			boolean ok = s.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(h));
			if (!ok)
				throw new SecurityException("Firma inválida");
		}
		return out;
	}

	private X509Certificate loadCertificate(Path crt) throws Exception {
		String first = Files.readString(crt);
		if (first.contains("-----BEGIN CERTIFICATE-----")) {
			try (PEMParser pem = new PEMParser(new StringReader(first))) {
				Object o = pem.readObject();
				if (o instanceof X509CertificateHolder h) {
					return new JcaX509CertificateConverter().setProvider("BC").getCertificate(h);
				}
			}
		}
		try (InputStream in = Files.newInputStream(crt)) {
			return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
		}
	}

	private PrivateKey loadPrivateKey(Path key) throws Exception {
		String pemText = Files.readString(key);
		try (PEMParser pem = new PEMParser(new StringReader(pemText))) {
			Object o = pem.readObject();
			JcaPEMKeyConverter conv = new JcaPEMKeyConverter().setProvider("BC");
			if (o instanceof PEMKeyPair kp)
				return conv.getKeyPair(kp).getPrivate();
			if (o instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo p8)
				return conv.getPrivateKey(p8);
			throw new IllegalArgumentException("Formato de clave no soportado");
		}
	}

	public Path buildAndCallWsaa(String tenantCode, boolean homologacion) throws Exception {
		String safe = tenantCode.replaceAll("[^a-zA-Z0-9._-]", "_");
		Path dir = Paths.get(safe + "-certs").toAbsolutePath().normalize();
		Files.createDirectories(dir);

		Path derPath = dir.resolve("loginCms.der");
		byte[] cms = Files.readAllBytes(derPath);
		String cmsB64 = Base64.getEncoder().encodeToString(cms);

		String soap = """
				<?xml version="1.0" encoding="UTF-8"?>
				<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
				  <soapenv:Header/>
				  <soapenv:Body>
				    <loginCms xmlns="http://wsaa.view.sua.dvadac.desein.afip.gov">
				      <in0>%s</in0>
				    </loginCms>
				  </soapenv:Body>
				</soapenv:Envelope>
				""".formatted(cmsB64);

		Path soapPath = dir.resolve("soap.xml");
		Files.writeString(soapPath, soap, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);

		String url = homologacion ? "https://wsaahomo.afip.gov.ar/ws/services/LoginCms"
				: "https://wsaa.afip.gov.ar/ws/services/LoginCms";

		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
				.header("Content-Type", "text/xml; charset=utf-8").header("SOAPAction", "loginCms")
				.POST(HttpRequest.BodyPublishers.ofString(soap, StandardCharsets.UTF_8)).build();

		HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());

		Path respPath = dir.resolve("wsaa_resp.xml");
		Files.write(respPath, resp.body(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		if (resp.statusCode() != 200) {
			throw new IllegalStateException("WSAA HTTP " + resp.statusCode() + " — ver " + respPath);
		}
		return respPath;
	}

	public TokenSign extractTokenSignFromWsaaResp(String tenantCode) throws Exception {
		String safe = tenantCode.replaceAll("[^a-zA-Z0-9._-]", "_");
		Path dir = Paths.get(safe + "-certs").toAbsolutePath().normalize();
		Path respPath = dir.resolve("wsaa_resp.xml");
		if (!Files.exists(respPath))
			throw new IllegalStateException("No existe wsaa_resp.xml en: " + dir);

		Document soapDoc = parseXml(Files.readString(respPath));
		XPath xp = XPathFactory.newInstance().newXPath();

		Node loginCmsReturn = (Node) xp.evaluate("//*[local-name()='loginCmsReturn']", soapDoc, XPathConstants.NODE);
		if (loginCmsReturn != null) {
			String taXml = loginCmsReturn.getTextContent();
			Document taDoc = parseXml(taXml);
			String token = xp.evaluate("/loginTicketResponse/credentials/token/text()", taDoc);
			String sign = xp.evaluate("/loginTicketResponse/credentials/sign/text()", taDoc);
			if (token == null || token.isBlank() || sign == null || sign.isBlank()) {
				throw new IllegalStateException("TA sin credenciales válidas");
			}
			return new TokenSign(token.trim(), sign.trim());
		}

		Node fault = (Node) xp.evaluate("//*[local-name()='Fault']", soapDoc, XPathConstants.NODE);
		if (fault != null) {
			String t = fault.getTextContent();
			if (t != null && t.contains("alreadyAuthenticated")) {
				throw new AlreadyAuthenticatedException("WSAA: ya existe un TA válido (coe.alreadyAuthenticated)");
			}
			throw new IllegalStateException("WSAA Fault: " + t);
		}

		String preview = Files.readString(respPath);
		throw new IllegalStateException(
				"WSAA respuesta inesperada: " + preview.substring(0, Math.min(400, preview.length())));
	}

	private static Document parseXml(String xml) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(false);
		dbf.setExpandEntityReferences(false);
		try {
			dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		} catch (Exception ignore) {
		}
		try {
			dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
		} catch (Exception ignore) {
		}
		try {
			dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		} catch (Exception ignore) {
		}

		DocumentBuilder db = dbf.newDocumentBuilder();
		return db.parse(new InputSource(new StringReader(xml)));
	}

}
