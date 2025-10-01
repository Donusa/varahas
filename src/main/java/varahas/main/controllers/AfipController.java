package varahas.main.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import varahas.main.services.AfipService;

import java.util.Map;

@RestController
public class AfipController {

    private final AfipService afipService;

    public AfipController(AfipService afipService) {
        this.afipService = afipService;
    }

    @GetMapping("/facturar")
    public Map<String, Object> facturar() {
        return afipService.generarFactura();
    }
}
