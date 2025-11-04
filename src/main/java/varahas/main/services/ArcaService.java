package varahas.main.services;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Service;

@Service
public class ArcaService {

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

}
