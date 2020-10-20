window.jahia.i18n.loadNamespaces('support-token-authentication-valve');

window.jahia.uiExtender.registry.add('adminRoute', 'supportTokenAuthenticationValve', {
    targets: ['administration-sites:10'],
    label: 'support-token-authentication-valve:label',
    isSelectable: true,
    iframeUrl: window.contextJsParameters.contextPath + '/cms/editframe/default/$lang/sites/$site-key.manageUsersTokens.html'
});