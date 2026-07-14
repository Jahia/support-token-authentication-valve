package org.jahia.community.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Unit tests for {@link SupportTokenUtils#generateRandomToken()} (Gap G8, spec S4).
 *
 * <p>The generator is the sole source of the credential's entropy: the raw token is what a
 * support engineer types as a password. These tests pin the two properties everything else
 * relies on — that every value is a canonical RFC-4122 v4 UUID (122 bits of entropy) and that
 * values do not collide across many generations. This is stronger than the Cypress
 * {@code /^[0-9a-f-]{36}$/} shape check in {@code 01-supportTokenAPI.cy.ts}.
 *
 * <p>CHARACTERIZATION — entropy is a positive property; regression-pin it.
 */
public class SupportTokenGeneratorTest {

    /** Canonical RFC-4122 version-4 UUID: version nibble '4' and variant nibble in [89ab]. */
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private static final int SAMPLE_SIZE = 1000;

    @Test
    public void generateRandomToken_matchesRfc4122V4Format() {
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            String token = SupportTokenUtils.generateRandomToken();
            assertThat(token).matches(UUID_V4);
        }
    }

    @Test
    public void generateRandomToken_producesNoCollisions() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            generated.add(SupportTokenUtils.generateRandomToken());
        }
        assertThat(generated).hasSize(SAMPLE_SIZE);
    }
}
