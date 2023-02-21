package org.jahia.modules.token;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.jcr.RepositoryException;

import org.apache.commons.lang.time.DateFormatUtils;
import org.jahia.api.Constants;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.mail.MailService;
import org.jahia.services.pwd.PasswordService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SupportTokenUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupportTokenUtils.class);

    private SupportTokenUtils() {
    }

    public static boolean clearAllTokens(String username, String siteKey, JahiaUserManagerService userManagerService) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSession((JCRSessionWrapper session) -> {
            final JCRUserNode jahiaUser = userManagerService.lookupUser(username, siteKey, session);
            if (jahiaUser != null && jahiaUser.hasNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY)) {
                jahiaUser.getNode(SupportTokenConstants.NODE_NAME_TOKEN_HISTORY).remove();
                try {
                    session.save();
                } catch (RepositoryException ex) {
                    LOGGER.error("Cannot clean all tokens", ex);
                    return false;
                }
            }
            return true;
        });
    }

    public static List<String> listUserTokens(String username, String siteKey, JahiaUserManagerService userManagerService) throws RepositoryException {
        return JCRTemplate.getInstance().doExecuteWithSystemSession((JCRSessionWrapper session) -> {
            final List<String> userTokens = new ArrayList<>();
            final JCRUserNode jahiaUser = userManagerService.lookupUser(username, siteKey, session);
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

    public static boolean addToken(String username, String siteKey, String recipient, String description, Long expiration, String token, JahiaUserManagerService userManagerService) throws RepositoryException {
        LOGGER.info("Updating user");
        return JCRTemplate.getInstance().doExecuteWithSystemSession((JCRSessionWrapper session) -> {
            final JCRUserNode jahiaUser = userManagerService.lookupUser(username, siteKey, session);
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
                                + "\tExpiration (in min)\t: %s\n"
                                + "\tExpiration date\t: %s"
                                + "\n"
                                + "\n"
                                + "\n"
                                + "Regards,";

                        mailService.sendMessage(sender, mailService.defaultRecipient(), null, null, subject,
                                String.format(body, userKey, tokenDate, description, expiration, DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.format(tokenDate.getTime() + expiration)));
                    }
                } catch (RepositoryException ex) {
                    LOGGER.error("Cannot save user properties", ex);
                    return false;
                }
            }
            return true;
        });
    }

    public static String generateRandomToken() {
        return UUID.randomUUID().toString();
    }
}
