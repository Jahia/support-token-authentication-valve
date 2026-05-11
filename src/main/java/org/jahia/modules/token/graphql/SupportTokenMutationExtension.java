package org.jahia.modules.token.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.modules.token.SupportTokenUtils;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLName("SupportTokenMutations")
@GraphQLDescription("Support Token mutations")
public class SupportTokenMutationExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupportTokenMutationExtension.class);

    private SupportTokenMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("supportTokenCreate")
    @GraphQLDescription("Creates a temporary support token for a user. Returns the generated token string, or null on failure.")
    @GraphQLRequiresPermission("admin")
    public static String createToken(
            @GraphQLName("username") @GraphQLDescription("Username to create the token for") String username,
            @GraphQLName("siteKey") @GraphQLDescription("Site key (null for global users)") String siteKey,
            @GraphQLName("recipient") @GraphQLDescription("Email address that will receive the token") String recipient,
            @GraphQLName("description") @GraphQLDescription("Purpose of this token") String description,
            @GraphQLName("expiration") @GraphQLDescription("Expiration in minutes (default: 60)") Long expiration) {

        if (username == null || username.isEmpty() || recipient == null || recipient.isEmpty()) {
            LOGGER.warn("supportTokenCreate: username and recipient are required");
            return null;
        }
        final String token = SupportTokenUtils.generateRandomToken();
        try {
            final boolean ok = SupportTokenUtils.addToken(
                    username,
                    siteKey,
                    recipient,
                    description != null ? description : "Access for Jahia Support",
                    expiration != null ? expiration : 60L,
                    token,
                    JahiaUserManagerService.getInstance());
            return ok ? token : null;
        } catch (RepositoryException e) {
            LOGGER.error("Failed to create token for user: {}", username, e);
            return null;
        }
    }

    @GraphQLField
    @GraphQLName("supportTokenClearAll")
    @GraphQLDescription("Removes all support tokens for a user")
    @GraphQLRequiresPermission("admin")
    public static Boolean clearAllTokens(
            @GraphQLName("username") @GraphQLDescription("Username whose tokens should be cleared") String username,
            @GraphQLName("siteKey") @GraphQLDescription("Site key (null for global users)") String siteKey) {

        if (username == null || username.isEmpty()) {
            return Boolean.FALSE;
        }
        try {
            return SupportTokenUtils.clearAllTokens(username, siteKey, JahiaUserManagerService.getInstance());
        } catch (RepositoryException e) {
            LOGGER.error("Failed to clear tokens for user: {}", username, e);
            return Boolean.FALSE;
        }
    }
}
