package org.jahia.modules.token.users;

import java.io.Serializable;
import java.security.Principal;
import java.util.*;
import javax.jcr.RepositoryException;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.data.viewhelper.principal.PrincipalViewHelper;
import org.jahia.modules.sitesettings.users.management.UserProperties;
import org.jahia.modules.token.SupportTokenUtils;
import org.jahia.services.content.*;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.services.usermanager.SearchCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.binding.message.MessageBuilder;
import org.springframework.binding.message.MessageContext;

public final class UsersFlowHandler implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsersFlowHandler.class);
    private static final long serialVersionUID = -7240178997123886031L;
    private transient JahiaUserManagerService userManagerService;
    private String siteKey;

    public void initRealm(RenderContext renderContext) throws RepositoryException {
        final JCRNodeWrapper mainNode = renderContext.getMainResource().getNode();
        if (mainNode != null && mainNode.isNodeType(Constants.JAHIANT_VIRTUALSITE)) {
            siteKey = ((JCRSiteNode) mainNode).getSiteKey();
        }
    }

    public Set<JCRUserNode> init() {
        return PrincipalViewHelper.getSearchResult(null, null, null, null, null, null, false);
    }

    public SearchCriteria initCriteria() {
        return new SearchCriteria();
    }

    public UserProperties initUser() {
        final UserProperties properties = new UserProperties();
        properties.setSiteKey(siteKey);
        return properties;
    }

    public UserProperties populateUser(String selectedUser) {
        final UserProperties userProperties = new UserProperties();
        final JCRUserNode userNode = userManagerService.lookupUserByPath(selectedUser);
        if (userNode != null) {
            userProperties.populate(userNode);
        }
        return userProperties;
    }

    public Set<JCRUserNode> search(SearchCriteria searchCriteria) {
        String searchTerm = searchCriteria.getSearchString();
        if (StringUtils.isNotEmpty(searchTerm) && searchTerm.indexOf('*') == -1) {
            searchTerm += '*';
        }
        return PrincipalViewHelper.getSearchResult(searchCriteria.getSearchIn(),
                siteKey, searchTerm, searchCriteria.getProperties(), searchCriteria.getStoredOn(),
                searchCriteria.getProviders(), false);
    }

    @Autowired
    public void setUserManagerService(JahiaUserManagerService userManagerService) {
        this.userManagerService = userManagerService;
    }

    public boolean addToken(final UserProperties userProperties, String recipient, String description, Long expiration, final MessageContext context) throws RepositoryException {
        final String token = SupportTokenUtils.generateRandomToken();
        final boolean result = SupportTokenUtils.addToken(userProperties.getUsername(), siteKey, recipient, description, expiration, token, userManagerService);
        if (result) {
            context.addMessage(new MessageBuilder().info().defaultText(String.format("Token %s successfully added", token)).build());
        }
        return result;
    }

    public boolean clearAllTokens(final UserProperties userProperties, final MessageContext context) throws RepositoryException {
        LOGGER.info("Clearing all tokens");
        final boolean result = SupportTokenUtils.clearAllTokens(userProperties.getUsername(), userProperties.getSiteKey(), userManagerService);
        if (result) {
            context.addMessage(new MessageBuilder().info().defaultText("All tokens successfully deleted").build());
        }
        return result;
    }

    public Set<Principal> populateUsers(String selectedUsers) {
        final String[] split = selectedUsers.split(",");
        final Set<Principal> searchResult = new HashSet<>();
        for (String userPath : split) {
            final JCRUserNode jahiaUser = userManagerService.lookupUserByPath(userPath);
            if (jahiaUser != null) {
                searchResult.add(jahiaUser.getJahiaUser());
            }
        }
        return searchResult;
    }

    public List<String> retrieveUserTokens(UserProperties userProperties) throws RepositoryException {
        return SupportTokenUtils.listUserTokens(userProperties.getUsername(), siteKey, userManagerService);
    }
}
