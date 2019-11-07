package org.jahia.modules.token.users;

import java.io.Serializable;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.jcr.RepositoryException;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.data.viewhelper.principal.PrincipalViewHelper;
import org.jahia.modules.sitesettings.users.management.SearchCriteria;
import org.jahia.modules.sitesettings.users.management.UserProperties;
import org.jahia.modules.token.SupportTokenConstants;
import org.jahia.services.content.*;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.mail.MailService;
import org.jahia.services.pwd.PasswordService;
import org.jahia.services.render.RenderContext;
import org.jahia.services.usermanager.JahiaUserManagerService;
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
        final Set<JCRUserNode> searchResult = PrincipalViewHelper.getSearchResult(searchCriteria.getSearchIn(),
                siteKey, searchTerm, searchCriteria.getProperties(), searchCriteria.getStoredOn(),
                searchCriteria.getProviders(), false);
        return searchResult;
    }

    @Autowired
    public void setUserManagerService(JahiaUserManagerService userManagerService) {
        this.userManagerService = userManagerService;
    }

    public boolean addToken(final UserProperties userProperties, String recipient, String description, Long expiration, final MessageContext context) throws RepositoryException {
        LOGGER.info("Updating user");
        return JCRTemplate.getInstance().doExecuteWithSystemSession((JCRSessionWrapper session) -> {
            final JCRUserNode jahiaUser = userManagerService.lookupUserByPath(userProperties.getUserKey(), session);
            if (jahiaUser != null) {
                jahiaUser.addMixin(SupportTokenConstants.MIXIN_TOKEN_HISTORY);
                final JCRNodeWrapper tokenHistory;
                if (jahiaUser.hasNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY)) {
                    tokenHistory = jahiaUser.getNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY);
                } else {
                    tokenHistory = jahiaUser.addNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY, SupportTokenConstants.NODE_TYPE_TOKEN_HISTORY);
                }
                final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
                final Date tokenDate = new Date();
                final String nodeName = dateFormat.format(tokenDate);
                final JCRNodeWrapper tokenNode = tokenHistory.addNode(nodeName, SupportTokenConstants.NODE_TYPE_TOKEN);
                final String token = generateRandomToken();
                LOGGER.info(token);
                tokenNode.setProperty(SupportTokenConstants.PROP_TOKEN, PasswordService.getInstance().digest(token));
                tokenNode.setProperty(SupportTokenConstants.PROP_EXPIRATION, expiration);
                tokenNode.setProperty(SupportTokenConstants.PROP_DESCRIPTION, description);
                tokenNode.setProperty(SupportTokenConstants.PROP_RECIPIENT, recipient);
                try {
                    session.save();
                    final MailService mailService = MailService.getInstance();
                    if (mailService.isEnabled()) {

                        // We send an email to the recipient of the token
                        final String userKey = jahiaUser.getUserKey();
                        final String sender = mailService.defaultSender();
                        final String subject = "Jahia support token generated";
                        String body = "Hi,\n"
                                + "\n"
                                + "We're sending this email following a successful token generation for the user %s.\n"
                                + "\n"
                                + "\tToken\t: %s\n"
                                + "\tGeneration time\t: %s\n"
                                + "\tDescription\t: %s\n"
                                + "\tExpiration (in min)\t: %s"
                                + "\n"
                                + "\n"
                                + "\n"
                                + "Regards,";

                        mailService.sendMessage(sender, recipient, null, null, subject,
                                String.format(body, userKey, token, tokenDate, description, expiration));

                        // We send an email to the default recipient of the Jahia notification for audit purposes
                        body = "Hi,\n"
                                + "\n"
                                + "We're sending this email following a successful token generation for the user %s.\n"
                                + "\n"
                                + "\tGeneration time\t: %s\n"
                                + "\tDescription\t: %s\n"
                                + "\tExpiration (in min)\t: %s"
                                + "\n"
                                + "\n"
                                + "\n"
                                + "Regards,";

                        mailService.sendMessage(sender, mailService.defaultRecipient(), null, null, subject,
                                String.format(body, userKey, tokenDate, description, expiration));
                    }
                    context.addMessage(new MessageBuilder().info().defaultText("Token successfully added").build());
                } catch (RepositoryException ex) {
                    LOGGER.error("Cannot save user properties", ex);
                    return false;
                }
            }
            return true;
        });
    }

    public boolean clearAllTokens(final UserProperties userProperties, final MessageContext context) throws RepositoryException {
        LOGGER.info("Clearing all tokens");
        return JCRTemplate.getInstance().doExecuteWithSystemSession((JCRSessionWrapper session) -> {
            final JCRUserNode jahiaUser = userManagerService.lookupUserByPath(userProperties.getUserKey(), session);
            if (jahiaUser != null && jahiaUser.hasNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY)) {
                jahiaUser.getNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY).remove();
                try {
                    session.save();
                    context.addMessage(new MessageBuilder().info().defaultText("All tokens successfully deleted").build());
                } catch (RepositoryException ex) {
                    LOGGER.error("Cannot clean all tokens", ex);
                    return false;
                }
            }
            return true;
        });
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

    public List<String> retrieveUserTokens(final String selectedUser) throws RepositoryException {

        return JCRTemplate.getInstance().doExecuteWithSystemSession((JCRSessionWrapper session) -> {
            final List<String> userTokens = new ArrayList<>();
            final JCRUserNode jahiaUser = userManagerService.lookupUserByPath(selectedUser, session);
            if (jahiaUser != null && jahiaUser.hasNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY)) {
                final JCRNodeIteratorWrapper nodeIteratorWrapper = jahiaUser.getNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY).getNodes();
                for (Iterator<JCRNodeWrapper> iterator = nodeIteratorWrapper.iterator(); nodeIteratorWrapper.hasNext();) {
                    final JCRNodeWrapper node = iterator.next();
                    final String description = node.getPropertyAsString(SupportTokenConstants.PROP_DESCRIPTION);
                    final String expiration = node.getPropertyAsString(SupportTokenConstants.PROP_EXPIRATION);
                    final String recipient = node.getPropertyAsString(SupportTokenConstants.PROP_RECIPIENT);
                    final String createdDate = node.getPropertyAsString(Constants.JCR_CREATED);

                    userTokens.add(String.format("%s: for %s, expiration set to %s minutes in order to \"%s\"", createdDate, recipient, expiration, description));
                }
            }
            return userTokens;
        });
    }

    private String generateRandomToken() {
        return UUID.randomUUID().toString();
    }
}
