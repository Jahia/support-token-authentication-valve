window.jahia.i18n.loadNamespaces('support-token-authentication-valve');

window.jahia.uiExtender.registry.add('adminRoute', 'supportTokenAuthenticationValve', {
    targets: ['administration-sites:10'],
    requiredPermission: 'siteAdminUsers',
    label: 'support-token-authentication-valve:label',
    isSelectable: true,
    iframeUrl: window.contextJsParameters.contextPath + '/cms/editframe/default/$lang/sites/$site-key.manageUsersTokens.html'
});

window.jahia.uiExtender.registry.add('adminRoute', 'supportTokenAuthenticationValveServer', {
    targets: ['administration-server-usersAndRoles:10'],
    requiredPermission: 'adminUsers',
    label: 'support-token-authentication-valve:label',
    isSelectable: true,
    iframeUrl: window.contextJsParameters.contextPath + '/cms/adminframe/default/$lang/settings.manageUsersTokens.html'
});