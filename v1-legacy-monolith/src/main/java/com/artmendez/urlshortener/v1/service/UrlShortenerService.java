package com.artmendez.urlshortener.v1.service;

import com.artmendez.urlshortener.v1.domain.UrlRecord;
import com.artmendez.urlshortener.v1.repository.UrlRecordRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

/**
 * Logica de negocio del Monolito V1.
 *
 * <p>El generador de codigos es deliberadamente simple (alfabeto alfanumerico + SecureRandom +
 * reintento ante colision), a diferencia del generador Base62 con manejo formal de colisiones
 * que se implementara para V2 (ver ARCHITECTURE.md, seccion 6, Escenario A / Tarea #4). Esta
 * simplicidad es intencional: V1 representa el sistema "legacy" tal como fue construido antes
 * del rediseno, no la solucion ideal.
 */
@Service
public class UrlShortenerService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;

    private final UrlRecordRepository repository;
    private final SecureRandom random = new SecureRandom();

    public UrlShortenerService(UrlRecordRepository repository) {
        this.repository = repository;
    }

    public UrlRecord createShortUrl(String longUrl) {
        String shortCode = generateUniqueCode();
        UrlRecord record = new UrlRecord(shortCode, longUrl, OffsetDateTime.now());
        return repository.save(record);
    }

    public UrlRecord resolve(String shortCode) {
        return repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!repository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "No se pudo generar un short code unico tras " + MAX_ATTEMPTS + " intentos");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
