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
    registry.add('adminRoute', 'supportTokenSiteAdmin', {
        targets: ['administration-sites:10'],
        requiredPermission: 'siteAdminUsers',
        label: 'support-token-authentication-valve:label.menu_entry',
        isSelectable: true,
        render: () => React.createElement(SupportTokenAdmin)
    });
};
