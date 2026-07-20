package org.jahia.community.token.valve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.junit.Test;

public class SupportTokenUserResolutionTest {

    @Test
    public void resolveTokenOwner_usesAlreadyResolvedUserPath() throws Exception {
        String userPath = "/users/providers/ldap.ldapsystemsite/cf/ji/dh/spw_dtic";
        JCRSessionWrapper systemSession = mock(JCRSessionWrapper.class);
        JCRNodeWrapper expectedUser = mock(JCRNodeWrapper.class);
        when(systemSession.getNode(userPath)).thenReturn(expectedUser);

        JCRNodeWrapper result = SupportTokenAuthenticationValve.resolveTokenOwner(systemSession, userPath);

        assertThat(result).isSameAs(expectedUser);
        verify(systemSession).getNode(userPath);
    }
}
