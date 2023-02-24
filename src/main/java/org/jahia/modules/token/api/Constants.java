package org.jahia.modules.token.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jahia.modules.token.valve.SupportTokenAuthenticationValve.AUTH_VALVE_ID;

public class Constants {

    private static final Logger logger = LoggerFactory.getLogger(Constants.class);
    public static final String SUPPORT_TOKEN_AUTH = AUTH_VALVE_ID;

    public String getSupportTokenAuthKey() {
        return SUPPORT_TOKEN_AUTH;
    }
}
