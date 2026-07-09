import { DocumentNode } from 'graphql'
import { createUser, deleteUser } from '@jahia/cypress'

/**
 * End-to-end coverage for the module's core purpose: authenticating a user with a
 * generated support token.
 *
 * These tests drive the real authentication pipeline rather than any shortcut:
 *  - A generated token is created via the GraphQL mutation (as an authorised admin).
 *  - A raw form login is POSTed to `/cms/login` with the token placed in the
 *    `password` field — exactly what a browser login form sends — which is what
 *    `SupportTokenAuthenticationValve.tryAuthenticate` intercepts (username + password + site).
 *  - `restMode=true` makes Jahia return the authentication outcome in the response body
 *    (a rejected login yields the literal `unauthorized`) instead of a browser redirect,
 *    so the assertion reflects the auth pipeline's own decision — i.e. whether the
 *    support-token valve accepted the token — with no downstream permission gating to confound it.
 *
 * The negative cases prove the valve fails closed:
 *  - An already-expired token (expiration = 0 → the valve treats expirationMinutes <= 0
 *    as expired) must not authenticate.
 *  - A token value that matches no stored hash must not authenticate.
 *  - A user with no tokens at all must not authenticate.
 */
describe('Support Token — login with a generated token', () => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const createToken: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/createToken.graphql')
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const clearTokens: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/clearTokens.graphql')

    const LOGIN_USER = 'stLoginUser'
    // The user's real password — deliberately never used to log in; auth here is by token only.
    const USER_PASSWORD = 'StLogin9PwdTest'
    const RECIPIENT = 'support-login-test@jahia.com'
    // Jahia's restMode login returns this literal body when authentication is refused.
    const UNAUTHORIZED_BODY = 'unauthorized'

    // Perform a Jahia form login using the support token as the password. `restMode` returns
    // the authentication result in the response body rather than as a redirect.
    const tokenLogin = (username: string, token: string) =>
        cy.request({
            method: 'POST',
            url: '/cms/login',
            form: true,
            failOnStatusCode: false,
            body: { username, password: token, doLogin: 'true', restMode: 'true' },
        })

    const createTokenFor = (username: string, expiration: number, description: string) => {
        cy.apollo({ mutation: clearTokens, variables: { username, siteKey: null } })
        return cy
            .apollo({
                mutation: createToken,
                variables: { username, siteKey: null, recipient: RECIPIENT, description, expiration },
            })
            .its('data.supportToken.create')
    }

    before(() => {
        cy.login()
        createUser(LOGIN_USER, USER_PASSWORD)
    })

    after(() => {
        cy.login()
        cy.apollo({ mutation: clearTokens, variables: { username: LOGIN_USER, siteKey: null } })
        // Never leave a usable login token behind for the shared root account.
        cy.apollo({ mutation: clearTokens, variables: { username: 'root', siteKey: null } })
        deleteUser(LOGIN_USER)
    })

    beforeEach(() => {
        // Start each test as an anonymous guest so a successful login is attributable
        // solely to the token, not to a leftover admin session.
        cy.clearCookies()
    })

    it('authenticates the target user with a valid token', () => {
        createTokenFor(LOGIN_USER, 60, 'login happy path').then((token: string) => {
            expect(token, 'generated token').to.be.a('string').and.not.be.empty
            // A valid token is accepted: the login is not refused.
            tokenLogin(LOGIN_USER, token).its('body').should('not.contain', UNAUTHORIZED_BODY)
        })
    })

    // Regression: root's user node is ACL-restricted, so reading its token history requires a
    // system session. A valid token for root must authenticate just like any other user.
    it('authenticates the root user with a valid token', () => {
        createTokenFor('root', 60, 'root login regression').then((token: string) => {
            expect(token, 'generated token').to.be.a('string').and.not.be.empty
            tokenLogin('root', token).its('body').should('not.contain', UNAUTHORIZED_BODY)
        })
    })

    it('does not authenticate an expired token', () => {
        createTokenFor(LOGIN_USER, 0, 'already expired').then((token: string) => {
            expect(token, 'generated token').to.be.a('string').and.not.be.empty
            tokenLogin(LOGIN_USER, token).its('body').should('contain', UNAUTHORIZED_BODY)
        })
    })

    it('does not authenticate a token that matches no stored hash', () => {
        // A valid, unexpired token exists for the user, but we log in with a different value.
        createTokenFor(LOGIN_USER, 60, 'valid token present').then(() => {
            tokenLogin(LOGIN_USER, 'not-a-valid-token-000000000000').its('body').should('contain', UNAUTHORIZED_BODY)
        })
    })

    it('does not authenticate when the user has no tokens at all', () => {
        cy.apollo({ mutation: clearTokens, variables: { username: LOGIN_USER, siteKey: null } })
        tokenLogin(LOGIN_USER, 'any-token-value-000000000000').its('body').should('contain', UNAUTHORIZED_BODY)
    })
})
