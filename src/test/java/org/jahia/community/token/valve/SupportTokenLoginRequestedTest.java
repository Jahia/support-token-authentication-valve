package org.jahia.community.token.valve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import org.jahia.params.valves.LoginEngineAuthValveImpl;
import org.junit.Test;

/**
 * Unit tests for {@link SupportTokenAuthenticationValve#isLoginRequested} (Gap G15, spec S27).
 *
 * <p>The valve recognises a login attempt from the standard {@code doLogin} request parameter
 * (the token itself rides in the ordinary {@code password} field — there is no custom header).
 * These tests pin the {@code doLogin} recognition variants deterministically. The {@code /cms}
 * servlet-path branch depends on {@code Login.getMapping()} (Jahia runtime state) and is left to
 * the Cypress {@code /cms/login} coverage.
 *
 * <p>CHARACTERIZATION.
 */
public class SupportTokenLoginRequestedTest {

    private final SupportTokenAuthenticationValve valve = new SupportTokenAuthenticationValve();

    private HttpServletRequest requestWithDoLogin(String doLoginValue) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter(LoginEngineAuthValveImpl.LOGIN_TAG_PARAMETER)).thenReturn(doLoginValue);
        return request;
    }

    @Test
    public void isLoginRequested_doLoginTrue_isRecognised() {
        assertThat(valve.isLoginRequested(requestWithDoLogin("true"))).isTrue();
    }

    @Test
    public void isLoginRequested_doLoginOne_isRecognised() {
        assertThat(valve.isLoginRequested(requestWithDoLogin("1"))).isTrue();
    }

    @Test
    public void isLoginRequested_doLoginFalse_isNotRecognised() {
        assertThat(valve.isLoginRequested(requestWithDoLogin("false"))).isFalse();
    }

    @Test
    public void isLoginRequested_noDoLoginAndNotCmsPath_isNotRecognised() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter(LoginEngineAuthValveImpl.LOGIN_TAG_PARAMETER)).thenReturn(null);
        when(request.getServletPath()).thenReturn("/somethingElse");

        assertThat(valve.isLoginRequested(request)).isFalse();
    }
}
