package varahas.main.dto;

import java.nio.file.Path;

public record SaveResult(Path dir, boolean replaced) {

}
