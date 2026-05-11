package org.jahia.community.token.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.api.Constants;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.community.token.SupportTokenConstants;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLName("SupportTokenQueries")
@GraphQLDescription("Support Token queries")
public class SupportTokenQueryExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupportTokenQueryExtension.class);

    private SupportTokenQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("supportTokenListTokens")
    @GraphQLDescription("Lists the active tokens for a user")
    @GraphQLRequiresPermission("admin")
    public static List<GqlSupportTokenInfo> listTokens(
            @GraphQLName("username") @GraphQLDescription("Username to query") String username,
            @GraphQLName("siteKey") @GraphQLDescription("Site key (null for global users)") String siteKey) {

        if (username == null || username.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSession(session -> {
                final JCRUserNode user = JahiaUserManagerService.getInstance().lookupUser(username, siteKey, session);
                if (user == null) {
                    return null;
                }
                final List<GqlSupportTokenInfo> result = new ArrayList<>();
                if (user.hasNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY)) {
                    final JCRNodeIteratorWrapper iter = user.getNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY).getNodes();
                    for (Iterator<JCRNodeWrapper> it = iter.iterator(); iter.hasNext(); ) {
                        final JCRNodeWrapper node = it.next();
                        final String createdDate = node.getPropertyAsString(Constants.JCR_CREATED);
                        final String recipient = node.getPropertyAsString(SupportTokenConstants.PROP_RECIPIENT);
                        final String description = node.getPropertyAsString(SupportTokenConstants.PROP_DESCRIPTION);
                        final Long expiration = node.hasProperty(SupportTokenConstants.PROP_EXPIRATION)
                                ? node.getProperty(SupportTokenConstants.PROP_EXPIRATION).getLong()
                                : null;
                        result.add(new GqlSupportTokenInfo(createdDate, recipient, expiration, description));
                    }
                }
                return result;
            });
        } catch (RepositoryException e) {
            LOGGER.error("Failed to list tokens for user: {}", username, e);
            return Collections.emptyList();
        }
    }

    @GraphQLName("SupportTokenInfo")
    @GraphQLDescription("Information about a support token (the token value itself is never returned)")
    public static class GqlSupportTokenInfo {

        private final String createdDate;
        private final String recipient;
        private final Long expiration;
        private final String description;

        public GqlSupportTokenInfo(String createdDate, String recipient, Long expiration, String description) {
            this.createdDate = createdDate;
            this.recipient = recipient;
            this.expiration = expiration;
            this.description = description;
        }

        @GraphQLField
        @GraphQLName("createdDate")
        @GraphQLDescription("ISO creation date of the token")
        public String getCreatedDate() {
            return createdDate;
        }

        @GraphQLField
        @GraphQLName("recipient")
        @GraphQLDescription("Email address the token was sent to")
        public String getRecipient() {
            return recipient;
        }

        @GraphQLField
        @GraphQLName("expiration")
        @GraphQLDescription("Expiration in minutes from creation time")
        public Long getExpiration() {
            return expiration;
        }

        @GraphQLField
        @GraphQLName("description")
        @GraphQLDescription("Purpose description for the token")
        public String getDescription() {
            return description;
        }
    }
}
