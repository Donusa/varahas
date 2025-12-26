package varahas.main.controllers;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import varahas.main.dto.FeCaeSolicitarRequestDto;
import varahas.main.dto.NextCbteRequestDto;
import varahas.main.dto.SaveResult;
import varahas.main.dto.TokenSign;
import varahas.main.entities.Tenant;
import varahas.main.exceptions.AlreadyAuthenticatedException;
import varahas.main.services.ArcaService;
import varahas.main.services.TenantService;
import varahas.main.services.WsaaService;

@RestController
@RequestMapping("/arca")
public class ArcaController {
	
	@Autowired
	private ArcaService arcaService;
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

	@GetMapping("/generateCerts")
	public ResponseEntity<?> signCerts(@RequestParam String tenantName, @RequestParam String cnName) {
		if (cnName == null || cnName.isBlank()) {
			return ResponseEntity.badRequest().body("cnName vacío");
		}
		Tenant tenant = tenantService.getTenantByName(tenantName);
		  try {
		    Path p = Paths.get(tenantName + "-certs").toAbsolutePath().normalize();

		    wsaaService.writeLoginTicketRequest(tenantName, tenant.getCuil(), cnName, "wsfe", true);
		    wsaaService.signCmsPem(p);
		    wsaaService.buildAndCallWsaa(tenantName);

		    TokenSign ts = wsaaService.extractTokenSignFromWsaaResp(tenantName);
		    tenant.setArcaToken(ts.token());
		    tenant.setArcaSign(ts.sign());
		    tenantService.save(tenant);

		    return ResponseEntity.ok(tenantName + ": TA generado correctamente y token/sign guardados.");

		  } catch (AlreadyAuthenticatedException ex) {
		    if (tenant.getArcaToken() != null && !tenant.getArcaToken().isBlank()
		        && tenant.getArcaSign() != null && !tenant.getArcaSign().isBlank()) {
		      return ResponseEntity.ok("TA vigente reutilizado para " + tenantName);
		    }
		    return ResponseEntity.status(HttpStatus.CONFLICT)
		        .body("WSAA: ya existe un TA válido para este certificado/servicio, pero no hay token/sign guardados. " +
		              "Esperá a que expire el TA vigente y volvé a generar.");

		  } catch (Exception e) {
		    return ResponseEntity.badRequest().body("Error al generar el TA.");
		  }
	}

	@PostMapping(path = "/certs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> uploadCerts(@RequestParam String tenantName, @RequestPart("crt") MultipartFile crt) {

		try {
			SaveResult r = wsaaService.saveCertOnly(tenantName, crt);
			if (r.replaced()) {
				return ResponseEntity.ok("Certificado reemplazado en: " + r.dir());
			}
			return ResponseEntity.status(HttpStatus.CREATED).body("Certificado subido en: " + r.dir());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (SecurityException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al guardar los certificados.");
		}
	}

	@PostMapping("/certs/keygen")
	public ResponseEntity<?> generateKey(@RequestParam String tenantName) {
		try {
			tenantService.getTenantByName(tenantName);
			Path keyPath = wsaaService.generatePrivateKey(tenantName);
			Path csrPath = wsaaService.generateCsr(tenantName);
			byte[] csrBytes = Files.readAllBytes(csrPath);
			String safe = tenantName.replaceAll("[^a-zA-Z0-9._-]", "_");
			return ResponseEntity.status(HttpStatus.CREATED)
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safe + ".csr\"")
					.contentType(MediaType.APPLICATION_OCTET_STREAM)
					.body(csrBytes);
		} catch (FileAlreadyExistsException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (SecurityException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
		} catch (RuntimeException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al generar la clave.");
		}
	}

	@PostMapping("/tenant/cuil")
	public ResponseEntity<?> setTenantCuil(@RequestParam String tenantName, @RequestParam String cuil) {
		if (cuil == null || cuil.isBlank()) {
			return ResponseEntity.badRequest().body("cuil vacío");
		}
		String normalized = cuil.replaceAll("\\D", "");
		if (normalized.isBlank()) {
			return ResponseEntity.badRequest().body("cuil inválido");
		}
		try {
			Tenant tenant = tenantService.getTenantByName(tenantName);
			tenant.setCuil(normalized);
			tenantService.save(tenant);
			return ResponseEntity.ok("CUIL actualizado para " + tenantName);
		} catch (RuntimeException e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar el CUIL.");
		}
	}
	
	@GetMapping("/iva")
	public ResponseEntity<?> getIvaList(@RequestParam String tenantName) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		try {
			return ResponseEntity.ok(arcaService.getIvaList(tenant));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}
	
	@GetMapping("/tributos")
	public ResponseEntity<?> getTributosList(@RequestParam String tenantName) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		try {
			return ResponseEntity.ok(arcaService.getTributos(tenant));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}
	
	@GetMapping("/ptovta")
	public ResponseEntity<?> getPtoVtaList(@RequestParam String tenantName) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		try {
			return ResponseEntity.ok(arcaService.getPtosVenta(tenant));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}
	
	@GetMapping("/cbtes")
	public ResponseEntity<?> getCbtesList(@RequestParam String tenantName) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		try {
			return ResponseEntity.ok(arcaService.getTiposCbte(tenant));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	@GetMapping("/monedas")
	public ResponseEntity<?> getMonedas(@RequestParam String tenantName) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		try {
			return ResponseEntity.ok(arcaService.getTiposMonedas(tenant));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	@PostMapping(path = "/cae", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> solicitarCae(@RequestParam String tenantName, @RequestBody FeCaeSolicitarRequestDto request) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		try {
			return ResponseEntity.ok(arcaService.solicitarCae(tenant, request));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	@PostMapping(path = "/cbte/proximo", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> getProximoCbte(@RequestParam String tenantName, @RequestBody NextCbteRequestDto request) {
		Tenant tenant = tenantService.getTenantByName(tenantName);
		try {
			return ResponseEntity.ok(arcaService.getProximoCbte(tenant, request));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}
}
