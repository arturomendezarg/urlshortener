package com.artmendez.urlshortener.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Stub temporal para {@code /api/v2/**}: el servicio V2 (microservicios) todavia no existe
 * (arranca en la Tarea #4, escenario Greenfield, ver ARCHITECTURE.md seccion 6). En vez de
 * dejar que el Gateway devuelva un 404 generico sin contexto para estas rutas, este stub
 * responde 501 con un mensaje explicito. Se elimina cuando el Gateway enrute de verdad hacia
 * los microservicios V2 reales.
 */
@RestController
public class V2StubController {

    @RequestMapping("/api/v2/**")
    public ResponseEntity<Map<String, String>> notImplementedYet() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of(
                        "message",
                        "V2 aun no implementado. Ver ARCHITECTURE.md, seccion 6 (Escenario A) "
                                + "y seccion 7 (plan dia a dia)."
                ));
    }
}
