package org.jahia.community.token.valve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import org.jahia.community.token.SupportTokenConstants;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.junit.Test;

/**
 * Unit tests for {@link SupportTokenAuthenticationValve#isTokenExpired} (Gap G7, spec S6).
 *
 * <p>Where {@link SupportTokenExpiryTest} covers the pure arithmetic of {@code isExpired}, this
 * exercises the JCR-node wrapper that reads {@code creationDate} + {@code expiration} off a token
 * node before delegating to it. The security-critical case is a node missing its {@code expiration}
 * property: it must be treated as expired (fail closed) rather than as a token that never expires.
 *
 * <p>CHARACTERIZATION — fail-closed on malformed JCR data is a positive property.
 */
public class SupportTokenNodeExpiryTest {

    private static final long ONE_MINUTE_MS = SupportTokenConstants.MILLIS_PER_MINUTE;

    private final SupportTokenAuthenticationValve valve = new SupportTokenAuthenticationValve();

    @Test
    public void isTokenExpired_missingExpirationProperty_failsClosed() throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.hasProperty(SupportTokenConstants.PROP_EXPIRATION)).thenReturn(false);

        assertThat(valve.isTokenExpired(node)).isTrue();
    }

    @Test
    public void isTokenExpired_liveLifetime_returnsFalse() throws Exception {
        JCRNodeWrapper node = nodeWithExpiration(new Date(), 60);

        assertThat(valve.isTokenExpired(node)).isFalse();
    }

    @Test
    public void isTokenExpired_pastLifetime_returnsTrue() throws Exception {
        // Created two hours ago with a 30-minute lifetime ⇒ long expired.
        Date created = new Date(System.currentTimeMillis() - 120 * ONE_MINUTE_MS);
        JCRNodeWrapper node = nodeWithExpiration(created, 30);

        assertThat(valve.isTokenExpired(node)).isTrue();
    }

    private JCRNodeWrapper nodeWithExpiration(Date creationDate, int expirationMinutes) throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(node.hasProperty(SupportTokenConstants.PROP_EXPIRATION)).thenReturn(true);
        when(node.getProperty(SupportTokenConstants.PROP_EXPIRATION)).thenReturn(property);
        when(property.getDecimal()).thenReturn(BigDecimal.valueOf(expirationMinutes));
        when(node.getCreationDateAsDate()).thenReturn(creationDate);
        return node;
    }
}
