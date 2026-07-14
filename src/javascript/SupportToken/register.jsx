import {registry} from '@jahia/ui-extender';
import {SupportTokenAdmin} from './SupportToken';
import React from 'react';

export default () => {
    console.debug('%c support-token-authentication-valve: activation in progress', 'color: #006633');
    registry.add('adminRoute', 'supportTokenAdmin', {
        targets: ['administration-server-usersAndRoles:10'],
        requiredPermission: 'supportTokenAdmin',
        label: 'support-token-authentication-valve:label.menu_entry',
        isSelectable: true,
        render: () => React.createElement(SupportTokenAdmin)
    });
    // D5: the UI route permission is aligned with the GraphQL resolver gate
    // (`@GraphQLRequiresPermission("supportTokenAdmin")`). Previously this site route required
    // `siteAdminUsers`, which let a site administrator see the menu entry while every underlying
    // GraphQL call (list/create/clearAll) was still refused server-side — a confusing dead-end and
    // a UI/authorization split. Both admin routes now gate on the single `supportTokenAdmin`
    // permission so UI visibility matches what the backend will actually authorize.
    registry.add('adminRoute', 'supportTokenSiteAdmin', {
        targets: ['administration-sites:10'],
        requiredPermission: 'supportTokenAdmin',
        label: 'support-token-authentication-valve:label.menu_entry',
        isSelectable: true,
        render: () => React.createElement(SupportTokenAdmin)
    });
};
