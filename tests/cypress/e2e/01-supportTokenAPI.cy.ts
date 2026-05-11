import {DocumentNode} from 'graphql';

describe('Support Token Authentication Valve - GraphQL API', () => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const listTokens: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/listTokens.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const createToken: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/createToken.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const clearTokens: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/clearTokens.graphql');

    const TEST_USER = 'root';
    const TEST_RECIPIENT = 'support-test@jahia.com';

    before(() => {
        cy.login();
    });

    after(() => {
        cy.apollo({
            mutation: clearTokens,
            variables: {username: TEST_USER, siteKey: null}
        });
    });

    // --- supportTokenListTokens ---

    describe('supportTokenListTokens', () => {
        it('returns an array for a known user', () => {
            cy.apollo({query: listTokens, variables: {username: TEST_USER}})
                .its('data.supportTokenListTokens')
                .should('be.an', 'array');
        });

        it('returns null for an unknown user', () => {
            cy.apollo({query: listTokens, variables: {username: 'nonexistent-user-xyz'}})
                .its('data.supportTokenListTokens')
                .should('be.empty');
        });

        it('returns token objects with all required fields after creation', () => {
            cy.apollo({
                mutation: createToken,
                variables: {
                    username: TEST_USER,
                    recipient: TEST_RECIPIENT,
                    description: 'API test token',
                    expiration: 30
                }
            });
            cy.apollo({query: listTokens, variables: {username: TEST_USER}})
                .its('data.supportTokenListTokens')
                .should(tokens => {
                    expect(tokens).to.be.an('array').with.length.greaterThan(0);
                    const token = tokens[0];
                    expect(token).to.have.property('createdDate');
                    expect(token).to.have.property('recipient');
                    expect(token).to.have.property('expiration');
                    expect(token).to.have.property('description');
                });
        });

        it('returns tokens with correct field types', () => {
            cy.apollo({query: listTokens, variables: {username: TEST_USER}})
                .its('data.supportTokenListTokens')
                .should(tokens => {
                    expect(tokens).to.be.an('array').with.length.greaterThan(0);
                    const token = tokens[0];
                    expect(token.createdDate).to.be.a('string').and.not.be.empty;
                    expect(token.recipient).to.be.a('string');
                    expect(token.expiration).to.be.a('number');
                    expect(token.description).to.be.a('string');
                });
        });
    });

    // --- supportTokenCreate ---

    describe('supportTokenCreate', () => {
        it('returns a non-empty string token on success', () => {
            cy.apollo({
                mutation: createToken,
                variables: {
                    username: TEST_USER,
                    recipient: TEST_RECIPIENT,
                    description: 'Create test',
                    expiration: 60
                }
            })
                .its('data.supportTokenCreate')
                .should('be.a', 'string')
                .and('not.be.empty');
        });

        it('returned token is a UUID-format string', () => {
            cy.apollo({
                mutation: createToken,
                variables: {username: TEST_USER, recipient: TEST_RECIPIENT, expiration: 60}
            })
                .its('data.supportTokenCreate')
                .should('match', /^[0-9a-f-]{36}$/);
        });

        it('returns null for missing username', () => {
            cy.apollo({
                mutation: createToken,
                variables: {username: '', recipient: TEST_RECIPIENT, expiration: 60}
            })
                .its('data.supportTokenCreate')
                .should('be.null');
        });

        it('returns null for missing recipient', () => {
            cy.apollo({
                mutation: createToken,
                variables: {username: TEST_USER, recipient: '', expiration: 60}
            })
                .its('data.supportTokenCreate')
                .should('be.null');
        });

        it('increments the token count after creation', () => {
            cy.apollo({mutation: clearTokens, variables: {username: TEST_USER}});
            cy.apollo({query: listTokens, variables: {username: TEST_USER}})
                .its('data.supportTokenListTokens.length')
                .should('eq', 0);
            cy.apollo({
                mutation: createToken,
                variables: {username: TEST_USER, recipient: TEST_RECIPIENT, expiration: 60}
            });
            cy.apollo({query: listTokens, variables: {username: TEST_USER}})
                .its('data.supportTokenListTokens.length')
                .should('eq', 1);
        });

        it('stores the correct recipient and expiration', () => {
            cy.apollo({mutation: clearTokens, variables: {username: TEST_USER}});
            cy.apollo({
                mutation: createToken,
                variables: {
                    username: TEST_USER,
                    recipient: TEST_RECIPIENT,
                    description: 'expiration check',
                    expiration: 120
                }
            });
            cy.apollo({query: listTokens, variables: {username: TEST_USER}})
                .its('data.supportTokenListTokens.0')
                .should(token => {
                    expect(token.recipient).to.eq(TEST_RECIPIENT);
                    expect(token.expiration).to.eq(120);
                    expect(token.description).to.eq('expiration check');
                });
        });
    });

    // --- supportTokenClearAll ---

    describe('supportTokenClearAll', () => {
        it('returns true on success', () => {
            cy.apollo({mutation: clearTokens, variables: {username: TEST_USER}})
                .its('data.supportTokenClearAll')
                .should('eq', true);
        });

        it('removes all tokens', () => {
            cy.apollo({
                mutation: createToken,
                variables: {username: TEST_USER, recipient: TEST_RECIPIENT, expiration: 60}
            });
            cy.apollo({mutation: clearTokens, variables: {username: TEST_USER}});
            cy.apollo({query: listTokens, variables: {username: TEST_USER}})
                .its('data.supportTokenListTokens.length')
                .should('eq', 0);
        });

        it('returns false for an empty username', () => {
            cy.apollo({mutation: clearTokens, variables: {username: ''}})
                .its('data.supportTokenClearAll')
                .should('eq', false);
        });
    });
});
