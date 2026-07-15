package org.jahia.community.token.command;

import javax.jcr.RepositoryException;
import org.apache.commons.lang.StringUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jahia.community.token.SupportTokenConstants;
import org.jahia.community.token.SupportTokenUtils;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Karaf shell command {@code support-token:create}.
 *
 * <p><b>Trust boundary (U14 / gap G1).</b> This command performs <em>no</em> Jahia permission
 * check: it mints an impersonation token for any user, including {@code root}. Unlike the GraphQL
 * and React surfaces — which enforce {@code supportTokenAdmin} and additionally refuse
 * token-authenticated sessions — the Karaf shell runs with the system identity and exposes no
 * per-caller Jahia identity to authorize against. Access to the Karaf shell (SSH / local console)
 * is itself the security control, so the shell is treated as a trusted server-administrator surface,
 * consistent with other Jahia admin modules (e.g. versions-cleaner, workflow-tasks) that expose
 * privileged shell commands without an in-command permission gate.
 *
 * <p><b>Residual risk:</b> anyone with Karaf shell access can impersonate any user. This is
 * accepted and documented rather than fixed, because no caller identity is available in-process to
 * enforce {@code supportTokenAdmin} here. The asymmetry is pinned by
 * {@code SupportTokenCommandGateTest}; if a future Jahia version surfaces the shell caller's
 * identity, add a {@code supportTokenAdmin} check here to close the gap.
 */
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
    private String description = SupportTokenConstants.DEFAULT_DESCRIPTION;

    @Option(name = "-e", aliases = "--expiration", description = "Expiration (in minutes)")
    private Long expiration = SupportTokenConstants.DEFAULT_EXPIRATION_MINUTES;

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
