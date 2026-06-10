package org.jahia.community.token.valve;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit tests for {@link SupportTokenAuthenticationValve#sanitizeForLog}, which strips control
 * characters from user-supplied values before they reach the log to prevent log-injection /
 * log-forging attacks (CRLF splitting).
 */
public class SupportTokenLogSanitizerTest {

    @Test
    public void sanitizeForLog_null_returnsNull() {
        assertThat(SupportTokenAuthenticationValve.sanitizeForLog(null)).isNull();
    }

    @Test
    public void sanitizeForLog_plainValue_isUnchanged() {
        assertThat(SupportTokenAuthenticationValve.sanitizeForLog("alice")).isEqualTo("alice");
    }

    @Test
    public void sanitizeForLog_stripsCarriageReturnAndLineFeed() {
        String forged = "alice" + (char) 13 + (char) 10 + "ADMIN logged in";

        String result = SupportTokenAuthenticationValve.sanitizeForLog(forged);

        assertThat(result).doesNotContain("\r").doesNotContain("\n");
        assertThat(result).isEqualTo("alice__ADMIN logged in");
    }

    @Test
    public void sanitizeForLog_stripsTabControlChar() {
        String input = "a" + (char) 9 + "b";

        String result = SupportTokenAuthenticationValve.sanitizeForLog(input);

        assertThat(result).isEqualTo("a_b");
    }

    @Test
    public void sanitizeForLog_keepsOrdinarySpace() {
        String input = "a" + (char) 9 + "b" + (char) 32 + "c";

        String result = SupportTokenAuthenticationValve.sanitizeForLog(input);

        // The tab (0x09) is replaced with '_'; the ordinary space (0x20) is preserved.
        assertThat(result).isEqualTo("a_b c");
    }
}
