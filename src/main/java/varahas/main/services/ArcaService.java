package varahas.main.services;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import varahas.main.dto.FeCaeSolicitarRequestDto;
import varahas.main.dto.FeCaeSolicitarResponseDto;
import varahas.main.dto.NextCbteRequestDto;
import varahas.main.dto.NextCbteResponseDto;
import varahas.main.dto.PtoVta;
import varahas.main.entities.Tenant;

@Service
public class ArcaService {
	
	@Value("${WSFEv1}")
	private String wsfeEndpoint;

	private static String escXml(String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String fmtDecimal(BigDecimal v) {
		return v == null ? "0" : v.stripTrailingZeros().toPlainString();
	}

	private String resolveWsfeEndpoint() {
		return (wsfeEndpoint == null || wsfeEndpoint.isBlank()) ? "https://wswhomo.afip.gov.ar/wsfev1/service.asmx"
				: wsfeEndpoint;
	}

	public Path createTenantCertsFolder(String tenantCode) throws IOException {
		return createTenantCertsFolder(tenantCode, Paths.get("").toAbsolutePath());
	}

	public Path createTenantCertsFolder(String tenantCode, Path baseDir) throws IOException {
		if (tenantCode == null || tenantCode.isBlank())
			throw new IllegalArgumentException("tenantCode vacío");
		if (baseDir == null)
			baseDir = Paths.get("").toAbsolutePath();

		String safe = tenantCode.replaceAll("[^a-zA-Z0-9._-]", "_");
		Path base = baseDir.toAbsolutePath().normalize();
		Path target = base.resolve(safe + "-certs").normalize();
		if (!target.startsWith(base))
			throw new SecurityException("Ruta inválida");

		try {
			return Files.createDirectory(target);
		} catch (FileAlreadyExistsException e) {
			if (Files.isDirectory(target)) {
				throw new FileAlreadyExistsException("La carpeta ya existe");
			}
			throw new FileAlreadyExistsException("Existe un archivo con ese nombre");
		}
	}
	
	public Map<Integer, String> getIvaList(Tenant tenant) {

	    String cuit = tenant.getCuil();

	    Function<String, String> esc = s ->
	            s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");

	    String envelope = """
	        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ar="http://ar.gov.afip.dif.FEV1/">
	          <soapenv:Header/>
	          <soapenv:Body>
	            <ar:FEParamGetTiposIva>
	              <ar:Auth>
	                <ar:Token>%s</ar:Token>
	                <ar:Sign>%s</ar:Sign>
	                <ar:Cuit>%s</ar:Cuit>
	              </ar:Auth>
	            </ar:FEParamGetTiposIva>
	          </soapenv:Body>
	        </soapenv:Envelope>
	        """.formatted(esc.apply(tenant.getArcaToken()), esc.apply(tenant.getArcaSign()), esc.apply(cuit));

	    HttpClient client = HttpClient.newBuilder()
	            .version(HttpClient.Version.HTTP_1_1)
	            .connectTimeout(Duration.ofSeconds(5))
	            .build();

	    String[] actions = {
	            "\"http://ar.gov.afip.dif.FEV1/FEParamGetTiposIva\"",
	            "http://ar.gov.afip.dif.FEV1/FEParamGetTiposIva"
	    };

	    StringBuilder tries = new StringBuilder();
	    for (String action : actions) {
	        try {
	            HttpRequest req = HttpRequest.newBuilder(URI.create(wsfeEndpoint))
	                    .header("Content-Type", "text/xml; charset=utf-8")
	                    .header("Accept", "text/xml") 
	                    .header("SOAPAction", action)
	                    .timeout(Duration.ofSeconds(20))
	                    .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
	                    .build();

	            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	            int sc = resp.statusCode();
	            if (sc == 200) {
	                return parseIvaList(resp.body());
	            } else {
	                String body = resp.body();
	                tries.append("Attempt SOAPAction=").append(action)
	                     .append(" -> HTTP ").append(sc)
	                     .append(", body(head 500): ")
	                     .append(body == null ? "null" : body.substring(0, Math.min(500, body.length())))
	                     .append("\n");
	            }
	        } catch (Exception ex) {
	            tries.append("Attempt SOAPAction=").append(action)
	                 .append(" -> exception: ").append(ex.getClass().getSimpleName())
	                 .append(": ").append(ex.getMessage()).append("\n");
	        }
	    }
	    throw new RuntimeException("Error consultando FEParamGetTiposIva. Detalle:\n" + tries.toString());
	}

	private Map<Integer, String> parseIvaList(String soapXml) throws Exception {
	    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	    dbf.setNamespaceAware(true);
	    Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(soapXml)));

	    XPath xp = XPathFactory.newInstance().newXPath();

	    String wsErr = xp.evaluate("string(//*[local-name()='Errors']/*[local-name()='Err']/*[local-name()='Msg'])", doc);
	    if (wsErr != null && !wsErr.isBlank()) {
	        throw new RuntimeException("WSFE error: " + wsErr);
	    }

	    NodeList nodes = (NodeList) xp.evaluate("//*[local-name()='IvaTipo']", doc, XPathConstants.NODESET);
	    Map<Integer, String> result = new LinkedHashMap<>(Math.max(8, nodes.getLength()));
	    for (int i = 0; i < nodes.getLength(); i++) {
	        Element n = (Element) nodes.item(i);
	        String id = xp.evaluate("./*[local-name()='Id']/text()", n).trim();
	        String desc = xp.evaluate("./*[local-name()='Desc']/text()", n).trim();
	        if (!id.isEmpty()) result.put(Integer.parseInt(id), desc);
	    }
	    return result;
	}
	
	
	public Map<Integer, String> getTributos(Tenant tenant) {
	    try {
	        Function<String, String> esc = s ->
	                s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");

	        String envelope = """
	            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ar="http://ar.gov.afip.dif.FEV1/">
	              <soapenv:Header/>
	              <soapenv:Body>
	                <ar:FEParamGetTiposTributos>
	                  <ar:Auth>
	                    <ar:Token>%s</ar:Token>
	                    <ar:Sign>%s</ar:Sign>
	                    <ar:Cuit>%s</ar:Cuit>
	                  </ar:Auth>
	                </ar:FEParamGetTiposTributos>
	              </soapenv:Body>
	            </soapenv:Envelope>
	            """.formatted(
	                esc.apply(tenant.getArcaToken()),
	                esc.apply(tenant.getArcaSign()),
	                esc.apply(String.valueOf(tenant.getCuil()))
	            );

	        HttpRequest req = HttpRequest.newBuilder(URI.create(wsfeEndpoint))
	            .header("Content-Type", "text/xml; charset=utf-8")
	            .header("SOAPAction", "\"http://ar.gov.afip.dif.FEV1/FEParamGetTiposTributos\"")
	            .timeout(Duration.ofSeconds(15))
	            .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
	            .build();

	        HttpResponse<String> resp = HttpClient.newHttpClient()
	            .send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	        if (resp.statusCode() != 200) {
	            throw new RuntimeException("WSFE HTTP " + resp.statusCode() + ": " + resp.body());
	        }

	        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	        dbf.setNamespaceAware(true);
	        Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(resp.body())));

	        XPath xp = XPathFactory.newInstance().newXPath();

	        String wsErr = xp.evaluate("string(//*[local-name()='Errors']/*[local-name()='Err']/*[local-name()='Msg'])", doc);
	        if (wsErr != null && !wsErr.isBlank()) {
	            throw new RuntimeException("WSFE error: " + wsErr);
	        }

	        NodeList nodes = (NodeList) xp.evaluate("//*[local-name()='TributoTipo' or local-name()='Tributo']", doc, XPathConstants.NODESET);

	        Map<Integer, String> result = new LinkedHashMap<>(nodes.getLength());
	        for (int i = 0; i < nodes.getLength(); i++) {
	            Element n = (Element) nodes.item(i);
	            String id = xp.evaluate("./*[local-name()='Id']/text()", n).trim();
	            String desc = xp.evaluate("./*[local-name()='Desc']/text()", n).trim();
	            if (!id.isEmpty()) result.put(Integer.parseInt(id), desc);
	        }
	        return result;

	    } catch (Exception e) {
	        throw new RuntimeException("Error consultando FEParamGetTiposTributos", e);
	    }
	}
	
	public List<PtoVta> getPtosVenta(Tenant tenant) {
	    String endpoint = (wsfeEndpoint == null || wsfeEndpoint.isBlank())
	            ? "https://wswhomo.afip.gov.ar/wsfev1/service.asmx"
	            : wsfeEndpoint;

	    String cuit = tenant.getCuil();
	    Function<String, String> esc = s ->
	            s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");

	    String envelope = """
	        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ar="http://ar.gov.afip.dif.FEV1/">
	          <soapenv:Header/>
	          <soapenv:Body>
	            <ar:FEParamGetPtosVenta>
	              <ar:Auth>
	                <ar:Token>%s</ar:Token>
	                <ar:Sign>%s</ar:Sign>
	                <ar:Cuit>%s</ar:Cuit>
	              </ar:Auth>
	            </ar:FEParamGetPtosVenta>
	          </soapenv:Body>
	        </soapenv:Envelope>
	        """.formatted(esc.apply(tenant.getArcaToken()), esc.apply(tenant.getArcaSign()), esc.apply(cuit));

	    var client = HttpClient.newBuilder()
	            .version(HttpClient.Version.HTTP_1_1)
	            .connectTimeout(Duration.ofSeconds(5))
	            .build();

	    String[] actions = {
	            "\"http://ar.gov.afip.dif.FEV1/FEParamGetPtosVenta\"",
	            "http://ar.gov.afip.dif.FEV1/FEParamGetPtosVenta"
	    };

	    StringBuilder tries = new StringBuilder();
	    for (String action : actions) {
	        try {
	            var req = HttpRequest.newBuilder(URI.create(endpoint))
	                    .header("Content-Type", "text/xml; charset=utf-8")
	                    .header("Accept", "text/xml")
	                    .header("SOAPAction", action)
	                    .timeout(Duration.ofSeconds(20))
	                    .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
	                    .build();

	            var resp = client.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
	            if (resp.statusCode() != 200) {
	                String body = resp.body();
	                tries.append("Attempt SOAPAction=").append(action)
	                        .append(" -> HTTP ").append(resp.statusCode())
	                        .append(", body(head 500): ")
	                        .append(body == null ? "null" : body.substring(0, Math.min(500, body.length())))
	                        .append("\n");
	                continue;
	            }

	            var dbf = DocumentBuilderFactory.newInstance();
	            dbf.setNamespaceAware(true);
	            try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
	            var doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(resp.body())));
	            var xp = XPathFactory.newInstance().newXPath();

	            String wsErr = xp.evaluate("string(//*[local-name()='Errors']/*[local-name()='Err']/*[local-name()='Msg'])", doc);
	            if (wsErr != null && !wsErr.isBlank()) throw new RuntimeException("WSFE error: " + wsErr);

	            var nodes = (NodeList) xp.evaluate("//*[local-name()='PtoVenta']", doc, XPathConstants.NODESET);
	            List<PtoVta> out = new ArrayList<>(nodes.getLength());

	            for (int i = 0; i < nodes.getLength(); i++) {
	                Element n = (Element) nodes.item(i);
	                String nroStr = xp.evaluate("./*[local-name()='Nro']/text()", n).trim();
	                String emision = xp.evaluate("./*[local-name()='EmisionTipo']/text()", n).trim();
	                String blq = xp.evaluate("./*[local-name()='Bloqueado']/text()", n).trim();
	                String fchBaja = xp.evaluate("./*[local-name()='FchBaja']/text()", n).trim();

	                boolean bloqueado = "S".equalsIgnoreCase(blq) || "true".equalsIgnoreCase(blq) || "1".equals(blq);
	                int nro = nroStr.isEmpty() ? -1 : Integer.parseInt(nroStr);

	                out.add(new PtoVta(nro, emision, bloqueado, fchBaja.isEmpty() ? null : fchBaja));
	            }

	            out.sort(Comparator.comparingInt(PtoVta::nro));
	            return out;

	        } catch (Exception ex) {
	            tries.append("Attempt SOAPAction=").append(action)
	                 .append(" -> exception: ").append(ex.getClass().getSimpleName())
	                 .append(": ").append(ex.getMessage()).append("\n");
	        }
	    }
	    throw new RuntimeException("Error consultando FEParamGetPtosVenta. Detalle:\n" + tries);
	}

	public Map<Integer, String> getTiposCbte(Tenant tenant) {

	    String cuit = String.valueOf(tenant.getCuil()).replaceAll("\\D", "");
	    Function<String, String> esc =
	            s -> s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");

	    String envelope = """
	        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ar="http://ar.gov.afip.dif.FEV1/">
	          <soapenv:Header/>
	          <soapenv:Body>
	            <ar:FEParamGetTiposCbte>
	              <ar:Auth>
	                <ar:Token>%s</ar:Token>
	                <ar:Sign>%s</ar:Sign>
	                <ar:Cuit>%s</ar:Cuit>
	              </ar:Auth>
	            </ar:FEParamGetTiposCbte>
	          </soapenv:Body>
	        </soapenv:Envelope>
	        """.formatted(esc.apply(tenant.getArcaToken()), esc.apply(tenant.getArcaSign()), esc.apply(cuit));

	    var client = HttpClient.newBuilder()
	            .version(HttpClient.Version.HTTP_1_1)
	            .connectTimeout(Duration.ofSeconds(5))
	            .build();

	    String[] actions = {
	            "\"http://ar.gov.afip.dif.FEV1/FEParamGetTiposCbte\"",
	            "http://ar.gov.afip.dif.FEV1/FEParamGetTiposCbte"
	    };

	    StringBuilder tries = new StringBuilder();
	    for (String action : actions) {
	        try {
	            var req = HttpRequest.newBuilder(URI.create(wsfeEndpoint))
	                    .header("Content-Type", "text/xml; charset=utf-8")
	                    .header("Accept", "text/xml")
	                    .header("SOAPAction", action)
	                    .timeout(Duration.ofSeconds(20))
	                    .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
	                    .build();

	            var resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	            if (resp.statusCode() != 200) {
	                String body = resp.body();
	                tries.append("Attempt SOAPAction=").append(action)
	                        .append(" -> HTTP ").append(resp.statusCode())
	                        .append(", body(head 500): ")
	                        .append(body == null ? "null" : body.substring(0, Math.min(500, body.length())))
	                        .append("\n");
	                continue;
	            }

	            var dbf = DocumentBuilderFactory.newInstance();
	            dbf.setNamespaceAware(true);
	            try { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
	            var doc = dbf.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(resp.body())));
	            var xp = XPathFactory.newInstance().newXPath();

	            String wsErr = xp.evaluate("string(//*[local-name()='Errors']/*[local-name()='Err']/*[local-name()='Msg'])", doc);
	            if (wsErr != null && !wsErr.isBlank()) throw new RuntimeException("WSFE error: " + wsErr);

	            var nodes = (NodeList) xp.evaluate("//*[local-name()='CbteTipo']", doc, XPathConstants.NODESET);
	            Map<Integer, String> out = new LinkedHashMap<>(Math.max(8, nodes.getLength()));
	            for (int i = 0; i < nodes.getLength(); i++) {
	                Element n = (Element) nodes.item(i);
	                String id = xp.evaluate("./*[local-name()='Id']/text()", n).trim();
	                String desc = xp.evaluate("./*[local-name()='Desc']/text()", n).trim();
	                if (!id.isEmpty()) out.put(Integer.parseInt(id), desc);
	            }
	            return out;

	        } catch (Exception ex) {
	            tries.append("Attempt SOAPAction=").append(action)
	                 .append(" -> exception: ").append(ex.getClass().getSimpleName())
	                 .append(": ").append(ex.getMessage()).append("\n");
	        }
	    }
	    throw new RuntimeException("Error consultando FEParamGetTiposCbte. Detalle:\n" + tries);
	}

	public varahas.main.dto.FeParamGetTiposDocResponseDto getTiposDoc(Tenant tenant) {
		if (tenant == null) {
			throw new IllegalArgumentException("Tenant nulo");
		}
		if (tenant.getArcaToken() == null || tenant.getArcaToken().isBlank() || tenant.getArcaSign() == null
				|| tenant.getArcaSign().isBlank()) {
			throw new IllegalArgumentException("Tenant sin token/sign de ARCA");
		}
		if (tenant.getCuil() == null || tenant.getCuil().isBlank()) {
			throw new IllegalArgumentException("Tenant sin CUIT/CUIL configurado");
		}

		String endpoint = resolveWsfeEndpoint();
		String cuit = escXml(tenant.getCuil().replaceAll("\\D", ""));
		String token = escXml(tenant.getArcaToken());
		String sign = escXml(tenant.getArcaSign());

		String envelope = """
				<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ar="http://ar.gov.afip.dif.FEV1/">
				  <soapenv:Header/>
				  <soapenv:Body>
				    <ar:FEParamGetTiposDoc>
				      <ar:Auth>
				        <ar:Token>%s</ar:Token>
				        <ar:Sign>%s</ar:Sign>
				        <ar:Cuit>%s</ar:Cuit>
				      </ar:Auth>
				    </ar:FEParamGetTiposDoc>
				  </soapenv:Body>
				</soapenv:Envelope>
				""".formatted(token, sign, cuit);

		var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5))
				.build();

		String[] actions = { "\"http://ar.gov.afip.dif.FEV1/FEParamGetTiposDoc\"",
				"http://ar.gov.afip.dif.FEV1/FEParamGetTiposDoc" };

		StringBuilder tries = new StringBuilder();
		for (String action : actions) {
			try {
				var req = HttpRequest.newBuilder(URI.create(endpoint)).header("Content-Type", "text/xml; charset=utf-8")
						.header("Accept", "text/xml").header("SOAPAction", action).timeout(Duration.ofSeconds(20))
						.POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8)).build();

				var resp = client.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (resp.statusCode() != 200) {
					String body = resp.body();
					tries.append("Attempt SOAPAction=").append(action).append(" -> HTTP ").append(resp.statusCode())
							.append(", body(head 500): ")
							.append(body == null ? "null" : body.substring(0, Math.min(500, body.length()))).append("\n");
					continue;
				}
				return parseTiposDoc(resp.body());
			} catch (Exception ex) {
				tries.append("Attempt SOAPAction=").append(action).append(" -> exception: ")
						.append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage()).append("\n");
			}
		}
		throw new RuntimeException("Error consultando FEParamGetTiposDoc. Detalle:\n" + tries);
	}

	private varahas.main.dto.FeParamGetTiposDocResponseDto parseTiposDoc(String soapXml) throws Exception {
		var dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		try {
			dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		} catch (Exception ignored) {
		}
		Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(soapXml)));
		XPath xp = XPathFactory.newInstance().newXPath();

		String wsErr = xp.evaluate("string(//*[local-name()='Errors']/*[local-name()='Err']/*[local-name()='Msg'])", doc);
		if (wsErr != null && !wsErr.isBlank()) {
			throw new RuntimeException("WSFE error: " + wsErr);
		}

		NodeList nodes = (NodeList) xp.evaluate("//*[local-name()='DocTipo']", doc, XPathConstants.NODESET);
		List<varahas.main.dto.FeParamGetTiposDocResponseDto.DocTipoDto> list = new ArrayList<>(Math.max(8, nodes.getLength()));
		for (int i = 0; i < nodes.getLength(); i++) {
			Element n = (Element) nodes.item(i);
			String idStr = xp.evaluate("./*[local-name()='Id']/text()", n).trim();
			String desc = xp.evaluate("./*[local-name()='Desc']/text()", n).trim();
			String fchDesde = xp.evaluate("./*[local-name()='FchDesde']/text()", n).trim();
			String fchHasta = xp.evaluate("./*[local-name()='FchHasta']/text()", n).trim();
			
			if (!idStr.isEmpty()) {
				try {
					list.add(varahas.main.dto.FeParamGetTiposDocResponseDto.DocTipoDto.builder()
						.id(Integer.parseInt(idStr))
						.desc(desc)
						.fchDesde(fchDesde.isEmpty() ? null : fchDesde)
						.fchHasta(fchHasta.isEmpty() || "NULL".equalsIgnoreCase(fchHasta) ? null : fchHasta)
						.build());
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return varahas.main.dto.FeParamGetTiposDocResponseDto.builder().tiposDocumentos(list).build();
	}


	public Map<String, String> getTiposMonedas(Tenant tenant) {
		if (tenant == null) {
			throw new IllegalArgumentException("Tenant nulo");
		}
		if (tenant.getArcaToken() == null || tenant.getArcaToken().isBlank() || tenant.getArcaSign() == null
				|| tenant.getArcaSign().isBlank()) {
			throw new IllegalArgumentException("Tenant sin token/sign de ARCA");
		}
		if (tenant.getCuil() == null || tenant.getCuil().isBlank()) {
			throw new IllegalArgumentException("Tenant sin CUIT/CUIL configurado");
		}

		String endpoint = resolveWsfeEndpoint();
		String cuit = escXml(tenant.getCuil().replaceAll("\\D", ""));
		String token = escXml(tenant.getArcaToken());
		String sign = escXml(tenant.getArcaSign());

		String envelope = """
				<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ar="http://ar.gov.afip.dif.FEV1/">
				  <soapenv:Header/>
				  <soapenv:Body>
				    <ar:FEParamGetTiposMonedas>
				      <ar:Auth>
				        <ar:Token>%s</ar:Token>
				        <ar:Sign>%s</ar:Sign>
				        <ar:Cuit>%s</ar:Cuit>
				      </ar:Auth>
				    </ar:FEParamGetTiposMonedas>
				  </soapenv:Body>
				</soapenv:Envelope>
				""".formatted(token, sign, cuit);

		var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5))
				.build();

		String[] actions = { "\"http://ar.gov.afip.dif.FEV1/FEParamGetTiposMonedas\"",
				"http://ar.gov.afip.dif.FEV1/FEParamGetTiposMonedas" };

		StringBuilder tries = new StringBuilder();
		for (String action : actions) {
			try {
				var req = HttpRequest.newBuilder(URI.create(endpoint)).header("Content-Type", "text/xml; charset=utf-8")
						.header("Accept", "text/xml").header("SOAPAction", action).timeout(Duration.ofSeconds(20))
						.POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8)).build();

				var resp = client.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (resp.statusCode() != 200) {
					String body = resp.body();
					tries.append("Attempt SOAPAction=").append(action).append(" -> HTTP ").append(resp.statusCode())
							.append(", body(head 500): ")
							.append(body == null ? "null" : body.substring(0, Math.min(500, body.length()))).append("\n");
					continue;
				}
				return parseMonedas(resp.body());
			} catch (Exception ex) {
				tries.append("Attempt SOAPAction=").append(action).append(" -> exception: ")
						.append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage()).append("\n");
			}
		}
		throw new RuntimeException("Error consultando FEParamGetTiposMonedas. Detalle:\n" + tries);
	}

	private Map<String, String> parseMonedas(String soapXml) throws Exception {
		var dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		try {
			dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		} catch (Exception ignored) {
		}
		Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(soapXml)));
		XPath xp = XPathFactory.newInstance().newXPath();

		String wsErr = xp.evaluate("string(//*[local-name()='Errors']/*[local-name()='Err']/*[local-name()='Msg'])", doc);
		if (wsErr != null && !wsErr.isBlank()) {
			throw new RuntimeException("WSFE error: " + wsErr);
		}

		NodeList nodes = (NodeList) xp.evaluate("//*[local-name()='Moneda']", doc, XPathConstants.NODESET);
		Map<String, String> out = new LinkedHashMap<>(Math.max(8, nodes.getLength()));
		for (int i = 0; i < nodes.getLength(); i++) {
			Element n = (Element) nodes.item(i);
			String id = xp.evaluate("./*[local-name()='Id']/text()", n).trim();
			String desc = xp.evaluate("./*[local-name()='Desc']/text()", n).trim();
			if (!id.isEmpty()) {
				out.put(id, desc);
			}
		}
		return out;
	}

	public FeCaeSolicitarResponseDto solicitarCae(Tenant tenant, FeCaeSolicitarRequestDto request) {
		if (tenant == null) {
			throw new IllegalArgumentException("Tenant nulo");
		}
		if (request == null) {
			throw new IllegalArgumentException("Request nulo");
		}
		if (tenant.getArcaToken() == null || tenant.getArcaToken().isBlank() || tenant.getArcaSign() == null
				|| tenant.getArcaSign().isBlank()) {
			throw new IllegalArgumentException("Tenant sin token/sign de ARCA");
		}
		if (tenant.getCuil() == null || tenant.getCuil().isBlank()) {
			throw new IllegalArgumentException("Tenant sin CUIT/CUIL configurado");
		}
		if (request.getCabecera() == null) {
			throw new IllegalArgumentException("Cabecera requerida");
		}
		if (request.getDetalles() == null || request.getDetalles().isEmpty()) {
			throw new IllegalArgumentException("Detalles requeridos");
		}
		if (request.getCabecera().getCantReg() == null || request.getCabecera().getPtoVta() == null
				|| request.getCabecera().getCbteTipo() == null) {
			throw new IllegalArgumentException("Cabecera incompleta (cantReg, ptoVta, cbteTipo)");
		}

		String endpoint = wsfeEndpoint;
		String cuit = escXml(tenant.getCuil().replaceAll("\\D", ""));
		String token = escXml(tenant.getArcaToken());
		String sign = escXml(tenant.getArcaSign());

		StringBuilder detXml = new StringBuilder();
		int detCount = 0;
		for (FeCaeSolicitarRequestDto.FeCaeDetRequestDto det : request.getDetalles()) {
			if (det == null) {
				continue;
			}
			detCount++;
			detXml.append("<ar:FECAEDetRequest>")
					.append("<ar:Concepto>").append(det.getConcepto() == null ? "" : det.getConcepto()).append("</ar:Concepto>")
					.append("<ar:DocTipo>").append(det.getDocTipo() == null ? "" : det.getDocTipo()).append("</ar:DocTipo>")
					.append("<ar:DocNro>").append(escXml(det.getDocNro())).append("</ar:DocNro>")
					.append("<ar:CbteDesde>").append(det.getCbteDesde() == null ? "" : det.getCbteDesde()).append("</ar:CbteDesde>")
					.append("<ar:CbteHasta>").append(det.getCbteHasta() == null ? "" : det.getCbteHasta()).append("</ar:CbteHasta>")
					.append("<ar:CbteFch>").append(escXml(det.getCbteFch())).append("</ar:CbteFch>")
					.append("<ar:ImpTotal>").append(fmtDecimal(det.getImpTotal())).append("</ar:ImpTotal>")
					.append("<ar:ImpTotConc>").append(fmtDecimal(det.getImpTotConc())).append("</ar:ImpTotConc>")
					.append("<ar:ImpNeto>").append(fmtDecimal(det.getImpNeto())).append("</ar:ImpNeto>")
					.append("<ar:ImpOpEx>").append(fmtDecimal(det.getImpOpEx())).append("</ar:ImpOpEx>")
					.append("<ar:ImpTrib>").append(fmtDecimal(det.getImpTrib())).append("</ar:ImpTrib>")
					.append("<ar:ImpIVA>").append(fmtDecimal(det.getImpIVA())).append("</ar:ImpIVA>")
					.append("<ar:FchServDesde>").append(escXml(det.getFchServDesde())).append("</ar:FchServDesde>")
					.append("<ar:FchServHasta>").append(escXml(det.getFchServHasta())).append("</ar:FchServHasta>")
					.append("<ar:FchVtoPago>").append(escXml(det.getFchVtoPago())).append("</ar:FchVtoPago>")
					.append("<ar:MonId>").append(escXml(det.getMonId())).append("</ar:MonId>")
					.append("<ar:MonCotiz>").append(fmtDecimal(det.getMonCotiz())).append("</ar:MonCotiz>")
					.append("<ar:CondicionIVAReceptorId>")
					.append(det.getCondicionIVAReceptorId() == null ? "" : det.getCondicionIVAReceptorId())
					.append("</ar:CondicionIVAReceptorId>");

			if (det.getTributos() != null && !det.getTributos().isEmpty()) {
				detXml.append("<ar:Tributos>");
				for (FeCaeSolicitarRequestDto.TributoDto t : det.getTributos()) {
					if (t == null) {
						continue;
					}
					detXml.append("<ar:Tributo>")
							.append("<ar:Id>").append(t.getId() == null ? "" : t.getId()).append("</ar:Id>")
							.append("<ar:Desc>").append(escXml(t.getDesc())).append("</ar:Desc>")
							.append("<ar:BaseImp>").append(fmtDecimal(t.getBaseImp())).append("</ar:BaseImp>")
							.append("<ar:Alic>").append(fmtDecimal(t.getAlic())).append("</ar:Alic>")
							.append("<ar:Importe>").append(fmtDecimal(t.getImporte())).append("</ar:Importe>")
							.append("</ar:Tributo>");
				}
				detXml.append("</ar:Tributos>");
			}

			if (det.getIva() != null && !det.getIva().isEmpty()) {
				detXml.append("<ar:Iva>");
				for (FeCaeSolicitarRequestDto.AlicIvaDto a : det.getIva()) {
					if (a == null) {
						continue;
					}
					detXml.append("<ar:AlicIva>")
							.append("<ar:Id>").append(a.getId() == null ? "" : a.getId()).append("</ar:Id>")
							.append("<ar:BaseImp>").append(fmtDecimal(a.getBaseImp())).append("</ar:BaseImp>")
							.append("<ar:Importe>").append(fmtDecimal(a.getImporte())).append("</ar:Importe>")
							.append("</ar:AlicIva>");
				}
				detXml.append("</ar:Iva>");
			}

			detXml.append("</ar:FECAEDetRequest>");
		}
		if (detCount == 0) {
			throw new IllegalArgumentException("Detalles requeridos");
		}

		FeCaeSolicitarRequestDto.FeCabReqDto cab = request.getCabecera();
		String envelope = """
				<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ar="http://ar.gov.afip.dif.FEV1/">
				  <soapenv:Header/>
				  <soapenv:Body>
				    <ar:FECAESolicitar>
				      <ar:Auth>
				        <ar:Token>%s</ar:Token>
				        <ar:Sign>%s</ar:Sign>
				        <ar:Cuit>%s</ar:Cuit>
				      </ar:Auth>
				      <ar:FeCAEReq>
				        <ar:FeCabReq>
				          <ar:CantReg>%s</ar:CantReg>
				          <ar:PtoVta>%s</ar:PtoVta>
				          <ar:CbteTipo>%s</ar:CbteTipo>
				        </ar:FeCabReq>
				        <ar:FeDetReq>
				          %s
				        </ar:FeDetReq>
				      </ar:FeCAEReq>
				    </ar:FECAESolicitar>
				  </soapenv:Body>
				</soapenv:Envelope>
				""".formatted(token, sign, cuit, cab.getCantReg(), cab.getPtoVta(), cab.getCbteTipo(), detXml);

		var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5))
				.build();

		String[] actions = { "\"http://ar.gov.afip.dif.FEV1/FECAESolicitar\"", "http://ar.gov.afip.dif.FEV1/FECAESolicitar" };

		StringBuilder tries = new StringBuilder();
		for (String action : actions) {
			try {
				var req = HttpRequest.newBuilder(URI.create(endpoint)).header("Content-Type", "text/xml; charset=utf-8")
						.header("Accept", "text/xml").header("SOAPAction", action).timeout(Duration.ofSeconds(30))
						.POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8)).build();

				var resp = client.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (resp.statusCode() != 200) {
					String body = resp.body();
					tries.append("Attempt SOAPAction=").append(action).append(" -> HTTP ").append(resp.statusCode())
							.append(", body(head 500): ")
							.append(body == null ? "null" : body.substring(0, Math.min(500, body.length()))).append("\n");
					continue;
				}
				return parseFeCaeSolicitar(resp.body());
			} catch (Exception ex) {
				tries.append("Attempt SOAPAction=").append(action).append(" -> exception: ")
						.append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage()).append("\n");
			}
		}
		throw new RuntimeException("Error consultando FECAESolicitar. Detalle:\n" + tries);
	}

	private FeCaeSolicitarResponseDto parseFeCaeSolicitar(String soapXml) throws Exception {
		var dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		try {
			dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		} catch (Exception ignored) {
		}
		Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(soapXml)));
		XPath xp = XPathFactory.newInstance().newXPath();

		String fault = xp.evaluate("string(//*[local-name()='Fault']/*[local-name()='faultstring'])", doc);
		if (fault != null && !fault.isBlank()) {
			return FeCaeSolicitarResponseDto.builder().errores(List.of(FeCaeSolicitarResponseDto.MessageDto.builder().msg(fault).build()))
					.soapResponse(soapXml).build();
		}

		NodeList errNodes = (NodeList) xp.evaluate("//*[local-name()='Errors']/*[local-name()='Err']", doc, XPathConstants.NODESET);
		List<FeCaeSolicitarResponseDto.MessageDto> errors = new ArrayList<>();
		for (int i = 0; i < errNodes.getLength(); i++) {
			Element n = (Element) errNodes.item(i);
			String code = xp.evaluate("./*[local-name()='Code']/text()", n).trim();
			String msg = xp.evaluate("./*[local-name()='Msg']/text()", n).trim();
			errors.add(FeCaeSolicitarResponseDto.MessageDto.builder().code(code.isEmpty() ? null : code).msg(msg).build());
		}

		String resultado = xp.evaluate("string(//*[local-name()='FeCabResp']/*[local-name()='Resultado'])", doc).trim();
		String reproceso = xp.evaluate("string(//*[local-name()='FeCabResp']/*[local-name()='Reproceso'])", doc).trim();
		String ptoVtaStr = xp.evaluate("string(//*[local-name()='FeCabResp']/*[local-name()='PtoVta'])", doc).trim();
		String cbteTipoStr = xp.evaluate("string(//*[local-name()='FeCabResp']/*[local-name()='CbteTipo'])", doc).trim();

		Integer ptoVta = ptoVtaStr.isEmpty() ? null : Integer.parseInt(ptoVtaStr);
		Integer cbteTipo = cbteTipoStr.isEmpty() ? null : Integer.parseInt(cbteTipoStr);

		NodeList detNodes = (NodeList) xp.evaluate("//*[local-name()='FeDetResp']/*[local-name()='FECAEDetResponse']", doc,
				XPathConstants.NODESET);
		List<FeCaeSolicitarResponseDto.FeCaeDetResponseDto> dets = new ArrayList<>(detNodes.getLength());
		for (int i = 0; i < detNodes.getLength(); i++) {
			Element n = (Element) detNodes.item(i);
			String cbteDesdeStr = xp.evaluate("./*[local-name()='CbteDesde']/text()", n).trim();
			String cbteHastaStr = xp.evaluate("./*[local-name()='CbteHasta']/text()", n).trim();
			String detResultado = xp.evaluate("./*[local-name()='Resultado']/text()", n).trim();
			String cae = xp.evaluate("./*[local-name()='CAE']/text()", n).trim();
			String caeFchVto = xp.evaluate("./*[local-name()='CAEFchVto']/text()", n).trim();

			Long cbteDesde = cbteDesdeStr.isEmpty() ? null : Long.parseLong(cbteDesdeStr);
			Long cbteHasta = cbteHastaStr.isEmpty() ? null : Long.parseLong(cbteHastaStr);

			NodeList obsNodes = (NodeList) xp.evaluate("./*[local-name()='Observaciones']/*[local-name()='Obs']", n,
					XPathConstants.NODESET);
			List<FeCaeSolicitarResponseDto.MessageDto> obs = new ArrayList<>(obsNodes.getLength());
			for (int j = 0; j < obsNodes.getLength(); j++) {
				Element o = (Element) obsNodes.item(j);
				String code = xp.evaluate("./*[local-name()='Code']/text()", o).trim();
				String msg = xp.evaluate("./*[local-name()='Msg']/text()", o).trim();
				obs.add(FeCaeSolicitarResponseDto.MessageDto.builder().code(code.isEmpty() ? null : code).msg(msg).build());
			}

			dets.add(FeCaeSolicitarResponseDto.FeCaeDetResponseDto.builder().cbteDesde(cbteDesde).cbteHasta(cbteHasta)
					.resultado(detResultado.isEmpty() ? null : detResultado).cae(cae.isEmpty() ? null : cae)
					.caeFchVto(caeFchVto.isEmpty() ? null : caeFchVto).observaciones(obs.isEmpty() ? null : obs).build());
		}

		return FeCaeSolicitarResponseDto.builder().resultado(resultado.isEmpty() ? null : resultado)
				.reproceso(reproceso.isEmpty() ? null : reproceso).ptoVta(ptoVta).cbteTipo(cbteTipo).detalles(dets)
				.errores(errors.isEmpty() ? null : errors).soapResponse(soapXml).build();
	}

	public NextCbteResponseDto getProximoCbte(Tenant tenant, NextCbteRequestDto request) {
		if (tenant == null) {
			throw new IllegalArgumentException("Tenant nulo");
		}
		if (request == null) {
			throw new IllegalArgumentException("Request nulo");
		}
		if (request.getPtoVta() == null || request.getCbteTipo() == null) {
			throw new IllegalArgumentException("Request incompleto (ptoVta, cbteTipo)");
		}
		if (tenant.getArcaToken() == null || tenant.getArcaToken().isBlank() || tenant.getArcaSign() == null
				|| tenant.getArcaSign().isBlank()) {
			throw new IllegalArgumentException("Tenant sin token/sign de ARCA");
		}
		if (tenant.getCuil() == null || tenant.getCuil().isBlank()) {
			throw new IllegalArgumentException("Tenant sin CUIT/CUIL configurado");
		}

		String endpoint = resolveWsfeEndpoint();
		String cuit = escXml(tenant.getCuil().replaceAll("\\D", ""));
		String token = escXml(tenant.getArcaToken());
		String sign = escXml(tenant.getArcaSign());

		String envelope = """
				<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ar="http://ar.gov.afip.dif.FEV1/">
				  <soapenv:Header/>
				  <soapenv:Body>
				    <ar:FECompUltimoAutorizado>
				      <ar:Auth>
				        <ar:Token>%s</ar:Token>
				        <ar:Sign>%s</ar:Sign>
				        <ar:Cuit>%s</ar:Cuit>
				      </ar:Auth>
				      <ar:PtoVta>%s</ar:PtoVta>
				      <ar:CbteTipo>%s</ar:CbteTipo>
				    </ar:FECompUltimoAutorizado>
				  </soapenv:Body>
				</soapenv:Envelope>
				""".formatted(token, sign, cuit, request.getPtoVta(), request.getCbteTipo());

		var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5))
				.build();

		String[] actions = { "\"http://ar.gov.afip.dif.FEV1/FECompUltimoAutorizado\"",
				"http://ar.gov.afip.dif.FEV1/FECompUltimoAutorizado" };

		StringBuilder tries = new StringBuilder();
		for (String action : actions) {
			try {
				var req = HttpRequest.newBuilder(URI.create(endpoint)).header("Content-Type", "text/xml; charset=utf-8")
						.header("Accept", "text/xml").header("SOAPAction", action).timeout(Duration.ofSeconds(20))
						.POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8)).build();

				var resp = client.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (resp.statusCode() != 200) {
					String body = resp.body();
					tries.append("Attempt SOAPAction=").append(action).append(" -> HTTP ").append(resp.statusCode())
							.append(", body(head 500): ")
							.append(body == null ? "null" : body.substring(0, Math.min(500, body.length()))).append("\n");
					continue;
				}
				return parseFeCompUltimoAutorizado(resp.body(), request.getPtoVta(), request.getCbteTipo());
			} catch (Exception ex) {
				tries.append("Attempt SOAPAction=").append(action).append(" -> exception: ")
						.append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage()).append("\n");
			}
		}
		throw new RuntimeException("Error consultando FECompUltimoAutorizado. Detalle:\n" + tries);
	}

	private NextCbteResponseDto parseFeCompUltimoAutorizado(String soapXml, Integer ptoVta, Integer cbteTipo) throws Exception {
		var dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		try {
			dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		} catch (Exception ignored) {
		}
		Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(soapXml)));
		XPath xp = XPathFactory.newInstance().newXPath();

		String fault = xp.evaluate("string(//*[local-name()='Fault']/*[local-name()='faultstring'])", doc);
		if (fault != null && !fault.isBlank()) {
			throw new RuntimeException(fault);
		}

		String wsErr = xp.evaluate("string(//*[local-name()='Errors']/*[local-name()='Err']/*[local-name()='Msg'])", doc);
		if (wsErr != null && !wsErr.isBlank()) {
			throw new RuntimeException("WSFE error: " + wsErr);
		}

		String lastStr = xp.evaluate("string(//*[local-name()='FECompUltimoAutorizadoResult']/*[local-name()='CbteNro'])", doc)
				.trim();
		Long last = lastStr.isEmpty() ? null : Long.parseLong(lastStr);
		Long next = last == null ? 1L : (last + 1);

		return NextCbteResponseDto.builder().ptoVta(ptoVta).cbteTipo(cbteTipo).ultimoAutorizado(last).proximo(next)
				.soapResponse(soapXml).build();
	}

}
