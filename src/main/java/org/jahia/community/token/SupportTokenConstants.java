package org.jahia.community.token;

public final class SupportTokenConstants {

    public static final String MIXIN_TOKEN_HISTORY = "jmix:supportTokenUser";
    public static final String NODE_NAME_TOKEN_HISTORY = "tokenHistory";
    public static final String NODE_TYPE_TOKEN = "jnt:supportToken";
    public static final String NODE_TYPE_TOKEN_HISTORY = "jnt:supportTokenHistory";
    public static final String PROP_DESCRIPTION = "description";
    public static final String PROP_EXPIRATION = "expiration";
    public static final String PROP_RECIPIENT = "recipient";
    public static final String PROP_TOKEN = "token";

    /**
     * HTTP session attribute set when the current session was established via a support token.
     * Callers that must not serve token-authenticated sessions (e.g. token-management GraphQL
     * operations) check for the presence of this attribute.
     */
    public static final String SESSION_SUPPORT_TOKEN_AUTH = "supportTokenAuth";

    private SupportTokenConstants() {
    }

}
