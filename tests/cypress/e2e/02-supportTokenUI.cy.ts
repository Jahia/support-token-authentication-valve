import {DocumentNode} from 'graphql';

describe('Support Token Authentication Valve - Admin UI', () => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const clearTokens: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/clearTokens.graphql');

    const ADMIN_URL = '/jahia/administration/supportTokenAdmin';
    const TEST_USER = 'root';
    const TEST_RECIPIENT = 'support-ui-test@jahia.com';

    before(() => {
        cy.login();
        cy.apollo({mutation: clearTokens, variables: {username: TEST_USER}});
    });

    after(() => {
        cy.apollo({mutation: clearTokens, variables: {username: TEST_USER}});
    });

    describe('Page structure', () => {
        it('loads the admin page', () => {
            cy.login();
            cy.visit(ADMIN_URL);
            cy.get('[class*="st_container"]').should('exist');
        });

        it('shows username and site key inputs', () => {
            cy.login();
            cy.visit(ADMIN_URL);
            cy.get('#st-username').should('exist');
            cy.get('#st-sitekey').should('exist');
        });

        it('shows a disabled search button when username is empty', () => {
            cy.login();
            cy.visit(ADMIN_URL);
            cy.get('#st-username').clear();
            cy.get('#st-search').should('be.disabled');
        });

        it('enables the search button when username is filled', () => {
            cy.login();
            cy.visit(ADMIN_URL);
            cy.get('#st-username').type(TEST_USER);
            cy.get('#st-search').should('not.be.disabled');
        });
    });

    describe('User search', () => {
        it('shows "user not found" for an unknown username', () => {
            cy.login();
            cy.visit(ADMIN_URL);
            cy.get('#st-username').type('nonexistent-user-xyz');
            cy.get('#st-search').click();
            cy.get('[class*="st_alert--error"]').should('contain.text', 'not found');
        });

        it('shows token table for a known user', () => {
            cy.login();
            cy.visit(ADMIN_URL);
            cy.get('#st-username').type(TEST_USER);
            cy.get('#st-search').click();
            cy.get('[class*="st_table"]').should('exist');
        });

        it('shows "no tokens" message when user has no tokens', () => {
            cy.login();
            cy.visit(ADMIN_URL);
            cy.get('#st-username').type(TEST_USER);
            cy.get('#st-search').click();
            cy.get('[class*="st_emptyMsg"]').should('exist');
        });
    });

    describe('Token creation', () => {
        beforeEach(() => {
            cy.login();
            cy.visit(ADMIN_URL);
            cy.get('#st-username').type(TEST_USER);
            cy.get('#st-search').click();
        });

        it('shows the create token form after search', () => {
            cy.get('#st-recipient').should('exist');
            cy.get('#st-expiration').should('exist');
            cy.get('#st-description').should('exist');
        });

        it('shows success alert and generated token after creation', () => {
            cy.get('#st-recipient').type(TEST_RECIPIENT);
            cy.get('#st-expiration').clear().type('30');
            cy.get('button').contains('Create Token').click();
            cy.get('[class*="st_alert--success"]').should('contain.text', 'created');
            cy.get('[class*="st_tokenBox"]').should('exist');
            cy.get('[class*="st_tokenValue"]').should('not.be.empty');
        });

        it('adds the new token to the table', () => {
            cy.get('#st-recipient').type(TEST_RECIPIENT);
            cy.get('button').contains('Create Token').click();
            cy.get('[class*="st_table"] tbody tr').should('have.length.greaterThan', 0);
        });

        it('stores the correct recipient in the token row', () => {
            cy.apollo({mutation: clearTokens, variables: {username: TEST_USER}});
            cy.reload();
            cy.get('#st-username').type(TEST_USER);
            cy.get('#st-search').click();
            cy.get('#st-recipient').type(TEST_RECIPIENT);
            cy.get('button').contains('Create Token').click();
            cy.get('[class*="st_table"] tbody tr').first().should('contain.text', TEST_RECIPIENT);
        });
    });

    describe('Token clear', () => {
        beforeEach(() => {
            cy.login();
            cy.visit(ADMIN_URL);
            cy.get('#st-username').type(TEST_USER);
            cy.get('#st-search').click();
            // Ensure at least one token exists
            cy.get('#st-recipient').type(TEST_RECIPIENT);
            cy.get('button').contains('Create Token').click();
        });

        it('removes all tokens and shows success', () => {
            cy.get('button').contains('Clear All Tokens').click();
            cy.get('[class*="st_alert--success"]').should('contain.text', 'cleared');
            cy.get('[class*="st_emptyMsg"]').should('exist');
        });

        it('disables clear button when no tokens exist', () => {
            cy.get('button').contains('Clear All Tokens').click();
            cy.get('button').contains('Clear All Tokens').should('be.disabled');
        });
    });
});
