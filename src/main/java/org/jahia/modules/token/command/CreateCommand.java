package org.jahia.modules.token.command;

import java.util.ArrayList;
import java.util.List;
import javax.jcr.RepositoryException;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jahia.modules.token.SupportTokenUtils;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "support-token", name = "create", description = "Create a token for a user")
@Service
public class CreateCommand implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateCommand.class);

    @Option(name = "-s", aliases = "--site-key", description = "Site key")
    private String siteKey = null;

    @Option(name = "-u", aliases = "--username", description = "Username")
    private String username = null;

    @Option(name = "-r", aliases = "--recipient", description = "Recipient")
    private String recipient = null;

    @Option(name = "-d", aliases = "--description", description = "Description")
    private String description = "Access for Jahia Support";

    @Option(name = "-e", aliases = "--expiration", description = "Expiration (in minutes)")
    private Long expiration = 60L;

    @Override
    public Object execute() throws RepositoryException {
        final String token;
        if (StringUtils.isBlank(username) || StringUtils.isBlank(recipient)) {
            token = "";
            LOGGER.warn("Impossible to add a token, the username or the recipient are missing");
        } else {
            token = SupportTokenUtils.generateRandomToken();
            SupportTokenUtils.addToken(username, siteKey, recipient, description, expiration, token, JahiaUserManagerService.getInstance());
        }
        return token;
    }
}
