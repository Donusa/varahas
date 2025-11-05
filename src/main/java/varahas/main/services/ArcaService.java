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

import varahas.main.dto.PtoVta;
import varahas.main.entities.Tenant;

@Service
public class ArcaService {
	
	@Value("${WSFEv1}")
	private String wsfeEndpoint;

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

}
