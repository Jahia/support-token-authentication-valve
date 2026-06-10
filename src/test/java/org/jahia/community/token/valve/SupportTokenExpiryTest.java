package org.jahia.community.token.valve;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.jahia.community.token.SupportTokenConstants;
import org.junit.Test;

/**
 * Unit tests for the pure token-expiry decision in {@link SupportTokenAuthenticationValve#isExpired}.
 * This is the security-critical branch of the auth valve: any ambiguous input must fail closed
 * (treated as expired) so a malformed token can never authenticate.
 */
public class SupportTokenExpiryTest {

    private static final long ONE_MINUTE_MS = SupportTokenConstants.MILLIS_PER_MINUTE;

    @Test
    public void isExpired_nullCreationDate_failsClosed() {
        assertThat(SupportTokenAuthenticationValve.isExpired(null, 60, new Date())).isTrue();
    }

    @Test
    public void isExpired_nullNow_failsClosed() {
        assertThat(SupportTokenAuthenticationValve.isExpired(new Date(), 60, null)).isTrue();
    }

    @Test
    public void isExpired_zeroLifetime_failsClosed() {
        assertThat(SupportTokenAuthenticationValve.isExpired(new Date(), 0, new Date())).isTrue();
    }

    @Test
    public void isExpired_negativeLifetime_failsClosed() {
        assertThat(SupportTokenAuthenticationValve.isExpired(new Date(), -5, new Date())).isTrue();
    }

    @Test
    public void isExpired_withinLifetime_returnsFalse() {
        Date created = new Date(1_000_000L);
        Date now = new Date(created.getTime() + 30 * ONE_MINUTE_MS);

        assertThat(SupportTokenAuthenticationValve.isExpired(created, 60, now)).isFalse();
    }

    @Test
    public void isExpired_exactlyAtExpiry_returnsTrue() {
        Date created = new Date(1_000_000L);
        Date now = new Date(created.getTime() + 60 * ONE_MINUTE_MS);

        assertThat(SupportTokenAuthenticationValve.isExpired(created, 60, now)).isTrue();
    }

    @Test
    public void isExpired_afterExpiry_returnsTrue() {
        Date created = new Date(1_000_000L);
        Date now = new Date(created.getTime() + 61 * ONE_MINUTE_MS);

        assertThat(SupportTokenAuthenticationValve.isExpired(created, 60, now)).isTrue();
    }

    @Test
    public void isExpired_largeLifetimeDoesNotOverflow() {
        Date created = new Date(0L);
        Date now = new Date(created.getTime() + ONE_MINUTE_MS);

        // A token valid for ~4000 years must still be considered live after one minute.
        assertThat(SupportTokenAuthenticationValve.isExpired(created, Integer.MAX_VALUE, now)).isFalse();
    }
}
