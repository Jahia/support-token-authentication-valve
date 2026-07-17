package org.jahia.community.token.valve;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.api.usermanager.JahiaUserManagerService;
import org.jahia.bin.Login;
import org.jahia.community.token.SupportTokenConstants;
import org.jahia.osgi.FrameworkService;
import org.jahia.params.valves.AuthValveContext;
import org.jahia.params.valves.BaseAuthValve;
import org.jahia.params.valves.BaseLoginEvent;
import org.jahia.params.valves.LoginEngineAuthValveImpl;
import org.jahia.pipelines.Pipeline;
import org.jahia.pipelines.PipelineException;
import org.jahia.pipelines.valves.Valve;
import org.jahia.pipelines.valves.ValveContext;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.observation.JahiaEventService;
import org.jahia.services.preferences.user.UserPreferencesHelper;
import org.jahia.services.pwd.PasswordService;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component(service = Valve.class, immediate = true)
public final class SupportTokenAuthenticationValve extends BaseAuthValve {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupportTokenAuthenticationValve.class);
    public static final String AUTH_VALVE_ID = "supportTokenAuthValve";
    private Pipeline authPipeline;
    private JahiaUserManagerService userManagerService;

    @Reference(service = Pipeline.class, target = "(type=authentication)")
    public void setAuthPipeline(Pipeline authPipeline) {
        this.authPipeline = authPipeline;
    }

    @Reference
    public void setUserManagerService(JahiaUserManagerService userManagerService) {
        this.userManagerService = userManagerService;
    }

    @Activate
    public void start() {
        setId(AUTH_VALVE_ID);
        removeValve(authPipeline);
        addValve(authPipeline, -1, null, "LoginEngineAuthValve");
    }

    @Deactivate
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

        JCRUserNode user = tryAuthenticate(httpServletRequest);

        if (user != null) {
            handleSuccessfulLogin(authContext, httpServletRequest, user);
        } else {
            valveContext.invokeNext(context);
        }
    }

    private JCRUserNode tryAuthenticate(HttpServletRequest request) {
        if (!isLoginRequested(request)) {
            return null;
        }

        final String username = request.getParameter("username");
        final String token = request.getParameter("password");
        final String site = request.getParameter("site");

        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(token)) {
            return null;
        }

        JCRUserNode user = userManagerService.lookupUser(username, site);
        if (user == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Login failed. Unknown username {}", sanitizeForLog(username));
            }
            request.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.UNKNOWN_USER);
            return null;
        }

        if (!verifyPassword(username, site, token)) {
            LOGGER.warn("Login failed: password verification failed for user {}", user.getName());
            request.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.BAD_PASSWORD);
            return null;
        }

        if (user.isAccountLocked()) {
            LOGGER.warn("Login failed: account for user {} is locked.", user.getName());
            request.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.ACCOUNT_LOCKED);
            return null;
        }

        return user;
    }

    private void handleSuccessfulLogin(AuthValveContext authContext, HttpServletRequest request, JCRUserNode user) {
        LOGGER.debug("User {} logged in.", user);

        // AUDIT: a support-token login is an impersonation event and must be distinguishable in the
        // logs from an ordinary password login. user.getName() comes from the resolved JCR node
        // (trusted), and is additionally run through sanitizeForLog as a defence-in-depth measure.
        LOGGER.info("[AUDIT] support-token login: session established for user='{}' via support-token valve"
                + " (impersonation login, NOT a password login)", sanitizeForLog(user.getName()));

        JahiaUser jahiaUser = user.getJahiaUser();

        invalidateExistingSession(request);

        request.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.OK);
        authContext.getSessionFactory().setCurrentUser(jahiaUser);

        setUserLocale(request, user);
        publishLoginEvents(jahiaUser, authContext);
    }

    private void invalidateExistingSession(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession().invalidate();
        }
    }

    private void setUserLocale(HttpServletRequest request, JCRUserNode user) {
        Locale preferredLocale = UserPreferencesHelper.getPreferredLocale(user,
                LanguageCodeConverters.resolveLocaleForGuest(request));
        request.getSession().setAttribute(Constants.SESSION_UI_LOCALE, preferredLocale);
        if (SettingsBean.getInstance().isConsiderPreferredLanguageAfterLogin()) {
            request.getSession().setAttribute(Constants.SESSION_LOCALE, preferredLocale);
        }
        // Mark this session as established by a support token so that
        // token-management operations can refuse service to token-authed callers.
        request.getSession().setAttribute(SupportTokenConstants.SESSION_SUPPORT_TOKEN_AUTH, Boolean.TRUE);
    }

    private void publishLoginEvents(JahiaUser jahiaUser, AuthValveContext authContext) {
        LoginEvent event = new LoginEvent(this, jahiaUser, authContext);
        SpringContextSingleton.getInstance().publishEvent(event);
        ((JahiaEventService) SpringContextSingleton.getBean("jahiaEventService")).publishEvent(event);

        Map<String, Object> m = new HashMap<>();
        m.put("user", jahiaUser);
        m.put("authContext", authContext);
        m.put("source", this);
        FrameworkService.sendEvent("org/jahia/usersgroups/login/LOGIN", m, false);
    }

    /**
     * Verifies the raw token against the user's stored token hashes using a JCR system session.
     * A system session is required so the token history is readable regardless of the user
     * node's ACLs — notably the {@code root} user, whose node is not readable by the unprivileged
     * session backing a login request. This mirrors how the rest of the module (add/list/clear)
     * accesses token data. Reading with elevated rights is safe here: the caller has already
     * presented the token, so this only confirms a credential rather than exposing data.
     */
    private boolean verifyPassword(String username, String site, String token) {
        try {
            return Boolean.TRUE.equals(JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(userManagerService.lookupUser(username, site).getJahiaUser(), Constants.EDIT_WORKSPACE,null,(JCRSessionWrapper session) -> {
                final JCRUserNode user = userManagerService.lookupUserByPath(session.getUser().getLocalPath(), session);
                if (user == null || !user.hasNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY)) {
                    return false;
                }
                final JCRNodeIteratorWrapper nodeIterator = user.getNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY).getNodes();
                while (nodeIterator.hasNext()) {
                    final JCRNodeWrapper node = (JCRNodeWrapper) nodeIterator.next();
                    if (isTokenMatch(node, token) && !isTokenExpired(node)) {
                        return true;
                    }
                }
                return false;
            }));
        } catch (RepositoryException ex) {
            LOGGER.warn("Unable to read tokens for user: {}", sanitizeForLog(username), ex);
            return false;
        }
    }

    static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        // Strip all ISO control characters (CR, LF, TAB, etc.) to prevent log injection.
        return value.replaceAll("\\p{Cntrl}", "_");
    }

    // Package-private for unit testing (see SupportTokenMatchTest); behaviour unchanged.
    boolean isTokenMatch(JCRNodeWrapper node, String token) throws RepositoryException {
        if (!node.hasProperty(SupportTokenConstants.PROP_TOKEN)) {
            return false;
        }
        String storedToken = node.getProperty(SupportTokenConstants.PROP_TOKEN).getString();
        return StringUtils.isNotEmpty(token) && PasswordService.getInstance().matches(token, storedToken);
    }

    // Package-private for unit testing (see SupportTokenNodeExpiryTest); behaviour unchanged.
    boolean isTokenExpired(JCRNodeWrapper node) throws RepositoryException {
        // Fail closed: a token without an expiration property is treated as expired rather
        // than as a token that never expires. An auth valve must not grant a permanent token.
        if (!node.hasProperty(SupportTokenConstants.PROP_EXPIRATION)) {
            return true;
        }
        final int expirationMinutes = node.getProperty(SupportTokenConstants.PROP_EXPIRATION).getDecimal().intValue();
        return isExpired(node.getCreationDateAsDate(), expirationMinutes, new Date());
    }

    /**
     * Pure expiry check. A token is expired when it has no creation date, a non-positive
     * lifetime, or when {@code now} is at/after {@code creationDate + expirationMinutes}.
     * Any ambiguous input is treated as expired (fail closed).
     */
    static boolean isExpired(Date creationDate, int expirationMinutes, Date now) {
        if (creationDate == null || now == null || expirationMinutes <= 0) {
            return true;
        }
        final long expiryMillis = creationDate.getTime()
                + expirationMinutes * SupportTokenConstants.MILLIS_PER_MINUTE;
        return now.getTime() >= expiryMillis;
    }

    public static class LoginEvent extends BaseLoginEvent {
        private static final long serialVersionUID = 8966163034180381951L;

        public LoginEvent(Object source, JahiaUser jahiaUser, AuthValveContext authValveContext) {
            super(source, jahiaUser, authValveContext);
        }
    }

    // Package-private for unit testing (see SupportTokenLoginRequestedTest); behaviour unchanged.
    boolean isLoginRequested(HttpServletRequest request) {
        String doLogin = request.getParameter(LoginEngineAuthValveImpl.LOGIN_TAG_PARAMETER);
        if (doLogin != null) {
            return Boolean.parseBoolean(doLogin) || "1".equals(doLogin);
        } else if ("/cms".equals(request.getServletPath())) {
            return Login.getMapping().equals(request.getPathInfo());
        }

        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        SupportTokenAuthenticationValve other = (SupportTokenAuthenticationValve) obj;
        return Objects.equals(authPipeline, other.authPipeline)
                && Objects.equals(userManagerService, other.userManagerService);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), authPipeline, userManagerService);
    }

}
