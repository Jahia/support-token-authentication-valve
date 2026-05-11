import {registry} from '@jahia/ui-extender';
import register from './SupportToken/register';
import i18next from 'i18next';

export default function () {
    registry.add('callback', 'support-token-authentication-valve', {
        targets: ['jahiaApp-init:50'],
        callback: async () => {
            await i18next.loadNamespaces('support-token-authentication-valve', () => {
                console.debug('%c support-token-authentication-valve: i18n namespace loaded', 'color: #006633');
            });
            register();
            console.debug('%c support-token-authentication-valve: activation completed', 'color: #006633');
        }
    });
}
