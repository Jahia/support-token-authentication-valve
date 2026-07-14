import {DocumentNode} from 'graphql';
import {createUser, deleteUser, grantRoles} from '@jahia/cypress';

/**
 * Regression tests for the fine-grained `supportTokenAdmin` permission.
 *
 * These guard against the gate being silently removed or mismatched across the stack:
 *  - Backend: `@GraphQLRequiresPermission("supportTokenAdmin")` on the
 *    `supportToken.listTokens` query is enforced as `session.getNode("/").hasPermission("supportTokenAdmin")`.
 *  - Frontend: `requiredPermission: 'supportTokenAdmin'` in register.jsx gates the server admin route.
 *  - RBAC content: the module ships the assignable `support-token-authentication-valve-administrator`
 *    role (src/main/import/roles.xml) granting only `administrationAccess` + `supportTokenAdmin`.
 *
 * The "allowed" user is granted that role and nothing else — never `admin` — so the tests prove
 * fine-grained granularity, not merely that a full administrator can pass.
 */
describe('Support Token — permission enforcement', () => {
    const ROLE_NAME = 'support-token-authentication-valve-administrator';
    const DENIED_USER = 'stDeniedUser';
    const ALLOWED_USER = 'stAllowedUser';
    // A user holding only siteAdminUsers (via the built-in site-administrator role) — enough to
    // reach the *site-settings* admin route (register.jsx `administration-sites:10` gates on
    // `siteAdminUsers`) but NOT the `supportTokenAdmin` permission the GraphQL layer requires.
    const SITE_ADMIN_USER = 'stSiteAdminUser';
    const PASSWORD = 'StPerm9PwdTest';
    const ADMIN_PATH = '/jahia/administration/supportTokenAdmin';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const listTokens: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/listTokens.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const createToken: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/createToken.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const clearTokens: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/clearTokens.graphql');

    const errorsOf = (result: {graphQLErrors?: Array<{message: string}>; errors?: Array<{message: string}>}) =>
        result.graphQLErrors ?? result.errors ?? [];

    const expectPermissionDenied = (result: never) => {
        const errs = errorsOf(result);
        expect(errs, 'denial errors').to.have.length.greaterThan(0);
        expect(errs.map((e: {message: string}) => e.message).join(' ')).to.contain('Permission denied');
    };

    const asUser = (username: string) => cy.apolloClient({username, password: PASSWORD});

    const listTokensAs = (username: string) => {
        asUser(username);
        // root always exists and has no support tokens — a safe, read-only gated query.
        return cy.apollo({query: listTokens, variables: {username: 'root', siteKey: null}});
    };

    before(() => {
        cy.login();
        createUser(DENIED_USER, PASSWORD);
        createUser(ALLOWED_USER, PASSWORD);
        createUser(SITE_ADMIN_USER, PASSWORD);
        // The annotation resolves the permission on the JCR root node, so grant the
        // module-shipped single-permission role on `/`.
        grantRoles('/', [ROLE_NAME], ALLOWED_USER, 'USER');
        // Grant only the built-in site-administrator role (which carries siteAdminUsers but not
        // supportTokenAdmin) on the always-present systemsite. Pins the D5 route/gate mismatch.
        grantRoles('/sites/systemsite', ['site-administrator'], SITE_ADMIN_USER, 'USER');
    });

    after(() => {
        cy.apolloClient(); // reset the current Apollo client back to root
        cy.login();
        deleteUser(DENIED_USER);
        deleteUser(ALLOWED_USER);
        deleteUser(SITE_ADMIN_USER);
    });

    describe('GraphQL API authorization', () => {
        it('denies the gated query for a user without the permission', () => {
            listTokensAs(DENIED_USER).then((result: never) => {
                expectPermissionDenied(result);
            });
        });

        // Gap G3 (S19 downgrade): the mutation gate was previously unproven — only the listTokens
        // *query* denial was tested, over-claiming "denies all three ops". These pin that
        // `create` and `clearAll` are equally gated by `@GraphQLRequiresPermission("supportTokenAdmin")`.
        it('denies the create mutation for a user without the permission', () => {
            asUser(DENIED_USER);
            cy.apollo({
                mutation: createToken,
                variables: {username: 'root', siteKey: null, recipient: 'denied@jahia.com', description: 'nope', expiration: 60},
                errorPolicy: 'all'
            }).then((result: never) => {
                expectPermissionDenied(result);
            });
        });

        it('denies the clearAll mutation for a user without the permission', () => {
            asUser(DENIED_USER);
            cy.apollo({
                mutation: clearTokens,
                variables: {username: 'root', siteKey: null},
                errorPolicy: 'all'
            }).then((result: never) => {
                expectPermissionDenied(result);
            });
        });

        it('allows the gated query for a user granted only the module permission', () => {
            listTokensAs(ALLOWED_USER).then((result: never) => {
                expect(errorsOf(result), 'should have no errors').to.have.length(0);
                // root exists and has no support tokens → an empty list is returned (not null).
                expect((result as {data: {supportToken: {listTokens: unknown[]}}}).data.supportToken.listTokens).to.be.an('array');
            });
        });
    });

    // Gap G3b / D5: the site-settings admin route (register.jsx `administration-sites:10`) gates on
    // `siteAdminUsers`, but every GraphQL operation gates on `supportTokenAdmin`. A siteAdminUsers
    // holder can therefore reach the UI route yet be refused by the backend — an inconsistent split.
    // This pins the backend half of that mismatch (deterministic); the UI-route-reachability half is
    // a Stage-6 assertion requiring site navigation context. Stage-7 candidate: align the route
    // permission to supportTokenAdmin, or document the split intentionally.
    describe('siteAdminUsers / supportTokenAdmin gate mismatch (D5)', () => {
        it('denies every GraphQL op to a siteAdminUsers-only holder', () => {
            asUser(SITE_ADMIN_USER);
            cy.apollo({query: listTokens, variables: {username: 'root', siteKey: null}, errorPolicy: 'all'})
                .then((result: never) => expectPermissionDenied(result));
            cy.apollo({
                mutation: createToken,
                variables: {username: 'root', siteKey: null, recipient: 'site@jahia.com', description: 'nope', expiration: 60},
                errorPolicy: 'all'
            }).then((result: never) => expectPermissionDenied(result));
            cy.apollo({mutation: clearTokens, variables: {username: 'root', siteKey: null}, errorPolicy: 'all'})
                .then((result: never) => expectPermissionDenied(result));
        });
    });

    describe('Admin UI authorization', () => {
        it('hides the admin panel from a user without the permission', () => {
            cy.login(DENIED_USER, PASSWORD);
            cy.visit(ADMIN_PATH, {failOnStatusCode: false});
            cy.get('[class*="st_container"]').should('not.exist');
        });

        it('shows the admin panel to a user granted only the module permission', () => {
            cy.login(ALLOWED_USER, PASSWORD);
            cy.visit(ADMIN_PATH);
            cy.get('[class*="st_container"]').should('be.visible');
        });
    });
});
