package varahas.main.services;



import varahas.main.dto.AuthResponseAFIP;
import varahas.main.dto.AuthRequestAFIP;

import java.text.SimpleDateFormat;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AfipService {

    private final RestTemplate restTemplate = new RestTemplate();

    public AuthResponseAFIP authenticate() {
        AuthRequestAFIP request = new AuthRequestAFIP();
        request.setEnvironment("dev");
        request.setTax_id("20409378472");
        request.setWsid("wsfe");

        String url = "https://app.afipsdk.com/api/v1/afip/auth";

        return restTemplate.postForObject(url, request, AuthResponseAFIP.class);
    }

    public Map<String, Object> generarFactura() {
    	AuthResponseAFIP auth = authenticate();

        String fechaActual = new SimpleDateFormat("yyyyMMdd").format(new Date());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("environment", "dev");
        payload.put("method", "FECAESolicitar");
        payload.put("wsid", "wsfe");

        Map<String, Object> authMap = new LinkedHashMap<>();
        authMap.put("Token", auth.getToken());
        authMap.put("Sign", auth.getSign());
        authMap.put("Cuit", "20409378472"); //CUIT DEL TENANT EMISOR DE LA FACTURA. EL DUENIO

        Map<String, Object> feCabReq = new LinkedHashMap<>();
        feCabReq.put("CantReg", 1);
        feCabReq.put("PtoVta", 1); //PUNTO DE VENTA DEL EMISOR DE LA FACTURA. EL DUENIO
        feCabReq.put("CbteTipo", 6); //TIPO DE COMPROBANTE (6 = B, 1 = A, ETC.)

        Map<String, Object> iva = new LinkedHashMap<>();
        iva.put("Id", 5);
        iva.put("BaseImp", 100);
        iva.put("Importe", 21);

        Map<String, Object> detRequest = new LinkedHashMap<>();
        detRequest.put("Concepto", 1); /// concepto: 1=Productos, 2=Servicios, 3=Productos y Servicios
        detRequest.put("DocTipo", 99); // tipo de documento del receptor: 99 = Consumidor Final, 80 = CUIT, etc.
        detRequest.put("DocNro", 0); // nro de documento (0 si es consumidor final)
        detRequest.put("CbteDesde", 1);  // nro de comprobante desde (ej: factura #1)
        detRequest.put("CbteHasta", 1);// nro de comprobante hasta (igual a "desde" si es uno solo)
        detRequest.put("CbteFch", fechaActual);
        detRequest.put("ImpTotal", 121.0); //IMPORTE TOTAL DE LA FACTURA. SUMATORIA. NETO GRAVADO + IVA + IMP INTERNOS + ETC....
        detRequest.put("ImpTotConc", 0);  //importe neto no gravado
        detRequest.put("ImpNeto", 100); // importe neto gravado. IMPORTE SIN IVA
        detRequest.put("ImpOpEx", 0); // operaciones exentas
        detRequest.put("ImpIVA", 21); // total de IVA
        detRequest.put("ImpTrib", 0); // tributos adicionales (percepciones, tasas, etc.) 
        detRequest.put("MonId", "PES");
        detRequest.put("MonCotiz", 1);
        detRequest.put("CondicionIVAReceptorId", 5); // condición de IVA del receptor (5 = consumidor final)
        detRequest.put("Iva", Map.of("AlicIva", List.of(iva)));

        Map<String, Object> feDetReq = new LinkedHashMap<>();
        feDetReq.put("FECAEDetRequest", List.of(detRequest));

        Map<String, Object> feCAEReq = new LinkedHashMap<>();
        feCAEReq.put("FeCabReq", feCabReq);
        feCAEReq.put("FeDetReq", feDetReq);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("Auth", authMap);
        params.put("FeCAEReq", feCAEReq);

        payload.put("params", params);

        String url = "https://app.afipsdk.com/api/v1/afip/requests";
        return restTemplate.postForObject(url, payload, Map.class);
    }
}