package org.jahia.modules.token.valve;

import java.util.Calendar;
import java.util.Iterator;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.usermanager.JahiaUserManagerService;
import org.jahia.bin.Login;
import org.jahia.modules.token.SupportTokenConstants;
import org.jahia.params.valves.AuthValveContext;
import org.jahia.params.valves.BaseAuthValve;
import org.jahia.params.valves.LoginEngineAuthValveImpl;
import org.jahia.pipelines.Pipeline;
import org.jahia.pipelines.PipelineException;
import org.jahia.pipelines.valves.ValveContext;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.pwd.PasswordService;
import org.jahia.services.usermanager.JahiaUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SupportTokenAuthenticationValve extends BaseAuthValve {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupportTokenAuthenticationValve.class);
    public static final String AUTH_VALVE_ID = "supportTokenAuthValve";
    private Pipeline authPipeline;
    private JahiaUserManagerService jahiaUserManagerService;

    public void setAuthPipeline(Pipeline authPipeline) {
        this.authPipeline = authPipeline;
    }

    public void setJahiaUserManagerService(JahiaUserManagerService jahiaUserManagerService) {
        this.jahiaUserManagerService = jahiaUserManagerService;
    }

    public void start() {
        setId(SupportTokenAuthenticationValve.AUTH_VALVE_ID);
        removeValve(authPipeline);
        addValve(authPipeline, -1, null, "LoginEngineAuthValve");
    }

    public void stop() {
        removeValve(authPipeline);
    }

    @Override
    public void invoke(Object context, ValveContext valveContext) throws PipelineException {

        if (!isEnabled()) {
            valveContext.invokeNext(context);
            return;
        }

        final AuthValveContext authContext = (AuthValveContext) context;
        final HttpServletRequest httpServletRequest = authContext.getRequest();

        JCRUserNode user = null;
        boolean ok = false;

        if (isLoginRequested(httpServletRequest)) {

            final String username = httpServletRequest.getParameter("username");
            final String token = httpServletRequest.getParameter("password");
            final String site = httpServletRequest.getParameter("site");

            if ((username != null) && (token != null)) {
                // Check if the user has site access ( even though it is not a user of this site )
                user = jahiaUserManagerService.lookupUser(username, site);
                if (user != null) {
                    if (verifyPassword(user, token)) {
                        if (!user.isAccountLocked()) {
                            ok = true;
                        } else {
                            LOGGER.warn("Login failed: account for user {} is locked.", user.getName());
                            httpServletRequest.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.ACCOUNT_LOCKED);
                        }
                    } else {
                        LOGGER.warn("Login failed: password verification failed for user {}", user.getName());
                        httpServletRequest.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.BAD_PASSWORD);
                    }
                } else if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Login failed. Unknown username {}", username.replaceAll("[\r\n]", ""));
                    httpServletRequest.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.UNKNOWN_USER);
                }
            }
        }

        if (ok) {

            LOGGER.debug("User {} logged in.", user);

            JahiaUser jahiaUser = user.getJahiaUser();

            if (httpServletRequest.getSession(false) != null) {
                httpServletRequest.getSession().invalidate();
            }

            httpServletRequest.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.OK);
            authContext.getSessionFactory().setCurrentUser(jahiaUser);
        } else {
            valveContext.invokeNext(context);
        }
    }

    private boolean verifyPassword(JCRUserNode user, String token) {
        try {
            if (user.hasNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY)) {
                final JCRNodeIteratorWrapper nodeIterator = user.getNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY).getNodes();
                for (Iterator<JCRNodeWrapper> iterator = nodeIterator.iterator(); nodeIterator.hasNext();) {
                    final JCRNodeWrapper node = iterator.next();
                    if (node.hasProperty(SupportTokenConstants.PROP_TOKEN)) {
                        boolean result = StringUtils.isNotEmpty(token) && PasswordService.getInstance().matches(token, node.getProperty(SupportTokenConstants.PROP_TOKEN).getString());
                        if (result) {
                            if (node.hasProperty(SupportTokenConstants.PROP_EXPIRATION)) {
                                final Calendar currentCalendar = Calendar.getInstance();
                                final Calendar expiredCalendar = Calendar.getInstance();
                                expiredCalendar.setTime(node.getCreationDateAsDate());
                                expiredCalendar.add(Calendar.MINUTE, node.getProperty(SupportTokenConstants.PROP_EXPIRATION).getDecimal().intValue());
                                if (currentCalendar.after(expiredCalendar)) {
                                    return false;
                                }
                            }
                            return true;
                        }
                    }
                }
            }
        } catch (RepositoryException ex) {
            LOGGER.warn("Unable to read tokens for user: " + user.getName(), ex);
            return false;
        }
        return false;
    }

    private boolean isLoginRequested(HttpServletRequest request) {
        String doLogin = request.getParameter(LoginEngineAuthValveImpl.LOGIN_TAG_PARAMETER);
        if (doLogin != null) {
            return Boolean.valueOf(doLogin) || "1".equals(doLogin);
        } else if ("/cms".equals(request.getServletPath())) {
            return Login.getMapping().equals(request.getPathInfo());
        }

        return false;
    }
}
