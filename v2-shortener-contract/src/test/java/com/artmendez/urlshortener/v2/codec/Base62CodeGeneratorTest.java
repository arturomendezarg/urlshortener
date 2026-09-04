package com.artmendez.urlshortener.v2.codec;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62CodeGeneratorTest {

    private static final String BASE62_PATTERN = "^[0-9A-Za-z]+$";

    private final Base62CodeGenerator generator = new Base62CodeGenerator();

    @RepeatedTest(20)
    void generate_returnsCodeOfInitialLengthWhenNoCollisions() {
        String code = generator.generate(candidate -> false);

        assertThat(code).hasSize(7);
        assertThat(code).matches(BASE62_PATTERN);
    }

    @Test
    void generate_retriesAtSameLengthBeforeGrowing() {
        AtomicInteger callCount = new AtomicInteger();
        Set<String> seen = new HashSet<>();

        // The first 3 calls "collide"; the 4th finds a free code.
        String code = generator.generate(candidate -> {
            seen.add(candidate);
            return callCount.getAndIncrement() < 3;
        });

        assertThat(code).hasSize(7);
        assertThat(callCount.get()).isEqualTo(4);
        assertThat(seen).hasSize(4);
    }

    @Test
    void generate_fallsBackToLongerCodeWhenShortLengthIsExhausted() {
        // Simulates that ALL codes of length 7 (INITIAL_LENGTH) already exist, forcing
        // the fallback to length 8.
        String code = generator.generate(candidate -> candidate.length() == 7);

        assertThat(code).hasSize(8);
    }

    @Test
    void generate_throwsWhenEveryLengthIsExhausted() {
        assertThatThrownBy(() -> generator.generate(candidate -> true))
                .isInstanceOf(CodeGenerationExhaustedException.class)
                .hasMessageContaining("12");
    }
}
