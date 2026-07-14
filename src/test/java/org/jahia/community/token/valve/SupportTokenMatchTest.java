package org.jahia.community.token.valve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jahia.community.token.SupportTokenConstants;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.pwd.PasswordService;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Unit tests for {@link SupportTokenAuthenticationValve#isTokenMatch} (Gap G6, spec S5).
 *
 * <p>This is the credential-verification branch the pure {@link SupportTokenAuthenticationValve#isExpired}
 * test cannot reach: it reads the stored bcrypt hash off a JCR node and delegates the comparison to
 * {@code PasswordService.matches}. The node and the (static-singleton) password service are mocked so
 * the branch logic is exercised deterministically without a Jahia runtime or a real bcrypt round.
 *
 * <p>CHARACTERIZATION — pins the current fail-closed matching behaviour. (The per-token O(n) loop in
 * {@code verifyPassword} is a low-severity, token-count timing side-channel — a Stage-7 decision, not a
 * fix requirement.)
 */
public class SupportTokenMatchTest {

    private static final String RAW_TOKEN = "11111111-2222-4333-8444-555555555555";
    private static final String STORED_HASH = "$2a$10$abcdefghijklmnopqrstuv";

    private final SupportTokenAuthenticationValve valve = new SupportTokenAuthenticationValve();

    private JCRNodeWrapper nodeWithStoredHash() throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        when(node.hasProperty(SupportTokenConstants.PROP_TOKEN)).thenReturn(true);
        when(node.getProperty(SupportTokenConstants.PROP_TOKEN)).thenReturn(property);
        when(property.getString()).thenReturn(STORED_HASH);
        return node;
    }

    @Test
    public void isTokenMatch_matchingToken_returnsTrue() throws Exception {
        JCRNodeWrapper node = nodeWithStoredHash();
        try (MockedStatic<PasswordService> passwordService = Mockito.mockStatic(PasswordService.class)) {
            PasswordService service = mock(PasswordService.class);
            passwordService.when(PasswordService::getInstance).thenReturn(service);
            when(service.matches(RAW_TOKEN, STORED_HASH)).thenReturn(true);

            assertThat(valve.isTokenMatch(node, RAW_TOKEN)).isTrue();
        }
    }

    @Test
    public void isTokenMatch_nonMatchingToken_returnsFalse() throws Exception {
        JCRNodeWrapper node = nodeWithStoredHash();
        try (MockedStatic<PasswordService> passwordService = Mockito.mockStatic(PasswordService.class)) {
            PasswordService service = mock(PasswordService.class);
            passwordService.when(PasswordService::getInstance).thenReturn(service);
            when(service.matches("wrong-token", STORED_HASH)).thenReturn(false);

            assertThat(valve.isTokenMatch(node, "wrong-token")).isFalse();
        }
    }

    @Test
    public void isTokenMatch_missingTokenProperty_failsClosed() throws Exception {
        JCRNodeWrapper node = mock(JCRNodeWrapper.class);
        when(node.hasProperty(SupportTokenConstants.PROP_TOKEN)).thenReturn(false);

        // No property present ⇒ never consults the password service; fail closed.
        assertThat(valve.isTokenMatch(node, RAW_TOKEN)).isFalse();
    }

    @Test
    public void isTokenMatch_blankSubmittedToken_failsClosed() throws Exception {
        JCRNodeWrapper node = nodeWithStoredHash();
        try (MockedStatic<PasswordService> passwordService = Mockito.mockStatic(PasswordService.class)) {
            PasswordService service = mock(PasswordService.class);
            passwordService.when(PasswordService::getInstance).thenReturn(service);
            // matches() must never be reached for a blank token; if it were, this would flip the result.
            lenient().when(service.matches("", STORED_HASH)).thenReturn(true);

            assertThat(valve.isTokenMatch(node, "")).isFalse();
        }
    }
}
