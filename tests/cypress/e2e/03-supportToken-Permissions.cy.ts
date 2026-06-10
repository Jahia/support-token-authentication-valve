import {DocumentNode} from 'graphql';
import {createUser, deleteUser, grantRoles} from '@jahia/cypress';

/**
 * Regression tests for the fine-grained `supportTokenAdmin` permission.
 *
 * These guard against the gate being silently removed or mismatched across the stack:
 *  - Backend: `@GraphQLRequiresPermission("supportTokenAdmin")` on the top-level
 *    `supportTokenListTokens` query is enforced as `session.getNode("/").hasPermission("supportTokenAdmin")`.
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
    const PASSWORD = 'StPerm9PwdTest';
    const ADMIN_PATH = '/jahia/administration/supportTokenAdmin';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const listTokens: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/listTokens.graphql');

    const errorsOf = (result: {graphQLErrors?: Array<{message: string}>; errors?: Array<{message: string}>}) =>
        result.graphQLErrors ?? result.errors ?? [];

    const listTokensAs = (username: string) => {
        cy.apolloClient({username, password: PASSWORD});
        // root always exists and has no support tokens — a safe, read-only gated query.
        return cy.apollo({query: listTokens, variables: {username: 'root', siteKey: null}});
    };

    before(() => {
        cy.login();
        createUser(DENIED_USER, PASSWORD);
        createUser(ALLOWED_USER, PASSWORD);
        // The annotation resolves the permission on the JCR root node, so grant the
        // module-shipped single-permission role on `/`.
        grantRoles('/', [ROLE_NAME], ALLOWED_USER, 'USER');
    });

    after(() => {
        cy.apolloClient(); // reset the current Apollo client back to root
        cy.login();
        deleteUser(DENIED_USER);
        deleteUser(ALLOWED_USER);
    });

    describe('GraphQL API authorization', () => {
        it('denies the gated query for a user without the permission', () => {
            listTokensAs(DENIED_USER).then((result: never) => {
                const errs = errorsOf(result);
                expect(errs, 'denial errors').to.have.length.greaterThan(0);
                expect(errs.map((e: {message: string}) => e.message).join(' ')).to.contain('Permission denied');
            });
        });

        it('allows the gated query for a user granted only the module permission', () => {
            listTokensAs(ALLOWED_USER).then((result: never) => {
                expect(errorsOf(result), 'should have no errors').to.have.length(0);
                // root exists and has no support tokens → an empty list is returned (not null).
                expect((result as {data: {supportTokenListTokens: unknown[]}}).data.supportTokenListTokens).to.be.an('array');
            });
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
