package varahas.main.controllers;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import varahas.main.dto.SaveResult;
import varahas.main.dto.TokenSign;
import varahas.main.entities.Tenant;
import varahas.main.exceptions.AlreadyAuthenticatedException;
import varahas.main.services.TenantService;
import varahas.main.services.WsaaService;

@RestController
@RequestMapping("/test")
public class TestController {

	@Autowired
	private WsaaService wsaaService;
	@Autowired
	private TenantService tenantService;

	/*
	 * @PostMapping("/arca") public ResponseEntity<?> testArca(@RequestParam String
	 * tenantName) { Tenant tenant = tenantService.getTenantByName(tenantName); try
	 * { wsaaService.writeLoginTicketRequest(tenantName, tenant.getCuil(), "taHomo",
	 * "wsfe", true); } catch (Exception e) { return
	 * ResponseEntity.badRequest().body(e.getMessage()); } return
	 * ResponseEntity.ok("Carpeta creada correctamente para el tenant: " +
	 * tenantName); }
	 */

	@GetMapping("/arca")
	public ResponseEntity<?> signCerts(@RequestParam String tenantName) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		  try {
		    Path p = Paths.get(tenantName + "-certs").toAbsolutePath().normalize();

		    wsaaService.writeLoginTicketRequest(tenantName, tenant.getCuil(), "taHomo", "wsfe", true);
		    wsaaService.signCmsPem(p);
		    wsaaService.buildAndCallWsaa(tenantName, true);

		    TokenSign ts = wsaaService.extractTokenSignFromWsaaResp(tenantName);
		    tenant.setArcaToken(ts.token());
		    tenant.setArcaSign(ts.sign());
		    tenantService.save(tenant);

		    return ResponseEntity.ok("token/sign actualizados para " + tenantName);

		  } catch (AlreadyAuthenticatedException ex) {
		    if (tenant.getArcaToken() != null && !tenant.getArcaToken().isBlank()
		        && tenant.getArcaSign() != null && !tenant.getArcaSign().isBlank()) {
		      return ResponseEntity.ok("TA vigente reutilizado para " + tenantName);
		    }
		    return ResponseEntity.status(HttpStatus.CONFLICT)
		        .body("WSAA: ya existe un TA válido para este certificado/servicio, pero no hay token/sign guardados. " +
		              "Esperá a que expire el TA vigente y volvé a generar.");

		  } catch (Exception e) {
		    return ResponseEntity.badRequest().body(e.getMessage());
		  }
	}

	@PostMapping(path = "/certs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> uploadCerts(@RequestParam String tenantName, @RequestPart("crt") MultipartFile crt,
			@RequestPart("key") MultipartFile key) {

		try {
			SaveResult r = wsaaService.saveCertAndKey(tenantName, crt, key);
			if (r.replaced()) {
				return ResponseEntity.ok("Certs reemplazados en: " + r.dir());
			}
			return ResponseEntity.status(HttpStatus.CREATED).body("Certs subidos en: " + r.dir());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (SecurityException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al guardar los certificados.");
		}
	}
}
