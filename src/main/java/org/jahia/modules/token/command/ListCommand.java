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

@Command(scope = "support-token", name = "list", description = "List all tokens of a user")
@Service
public class ListCommand implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListCommand.class);

    @Option(name = "-s", aliases = "--site-key", description = "Site key")
    private String siteKey = null;

    @Option(name = "-u", aliases = "--username", description = "Username")
    private String username = null;

    @Override
    public Object execute() throws RepositoryException {
        final List<String> userTokens = new ArrayList<>();
        if (StringUtils.isBlank(username)) {
            LOGGER.warn("Impossible to clear the tokens, the username is empty");
        } else {
            userTokens.addAll(SupportTokenUtils.listUserTokens(username, siteKey, JahiaUserManagerService.getInstance()));
        }
        final StringBuilder results = new StringBuilder();
        for (String userToken : userTokens) {
            results.append(userToken).append("\n");
        }
        return results;
    }
}
