package com.artmendez.urlshortener.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Rutas del Gateway (version basica, Dia 1 / Tarea #3 del plan).
 *
 * <p>Dos rutas activas:
 * <ul>
 *   <li>{@code /api/v1/**} (plano de control) -> Monolito V1, tal cual.</li>
 *   <li>{@code GET /{shortCode}} (plano de datos publico, sin prefijo) -> Monolito V1.
 *       Nota: la version final de esta ruta (ARCHITECTURE.md seccion 3.1) debe consultar
 *       primero el indice de codigos V2 en Redis y solo delegar a V1 si no lo encuentra.
 *       Esa logica se agrega cuando exista el servicio V2 con su propio indice; por ahora,
 *       con V1 como unico backend real, delegar directo es equivalente y evita construir
 *       una dependencia con Redis antes de que haya algo del otro lado que la use.</li>
 * </ul>
 *
 * <p>{@code /api/v2/**} no tiene ruta aqui a proposito: la resuelve {@link V2StubController}
 * con un 501 explicito, ya que el servicio V2 todavia no existe (arranca en la Tarea #4).
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                                @Value("${app.v1-legacy-monolith.base-url}") String v1BaseUrl) {
        return builder.routes()
                .route("v1-management", r -> r.path("/api/v1/**")
                        .uri(v1BaseUrl))
                .route("v1-public-redirect", r -> r.path("/{shortCode}")
                        .and().method(HttpMethod.GET)
                        .uri(v1BaseUrl))
                .build();
    }
}
