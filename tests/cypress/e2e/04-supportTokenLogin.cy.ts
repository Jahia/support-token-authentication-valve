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

    // Gap G10: identity confirmation. `not.contain 'unauthorized'` only proves the valve did not
    // refuse — it never proves *which* user the session belongs to. This whoami rides the login
    // cookie jar (same session cy.request just established) and asks the DXM GraphQL API who the
    // current user is, so we can positively assert the valve authenticated the intended target and
    // not some other identity.
    //
    // Stage-6 confirmed against this DXM (graphql-dxm-provider 3.8.0): the field IS `currentUser { name }`,
    // and it resolves correctly over the token-login session cookie — BUT only for a privileged
    // identity. Jahia's GraphQL API security denies `currentUser` (GqlAccessDeniedException) to a
    // guest and to an unprivileged user over a session cookie, independently of this module (proven:
    // guest and a plain user are both denied, root succeeds; a user holding `supportTokenAdmin` can
    // still call the module's own gated ops over the same session — see spec 05). So this whoami is
    // used for the ROOT regression, where it doubles as the strongest identity proof; the plain-user
    // happy path below proves identity a different, reachable way.
    const assertCurrentUserIs = (expectedUsername: string) =>
        cy
            .request({
                method: 'POST',
                url: '/modules/graphql',
                failOnStatusCode: false,
                // Jahia's GraphQL SecurityFilter rejects requests without a same-origin `Origin`
                // header (CSRF protection) with a GqlAccessDeniedException — `cy.apollo` sets this
                // automatically, but a raw `cy.request` does not. Send it explicitly so the call
                // authenticates on the token-login session cookie.
                headers: { 'Content-Type': 'application/json', Origin: Cypress.config('baseUrl') as string },
                body: { query: '{ currentUser { name } }' },
            })
            .its('body.data.currentUser.name')
            .should('eq', expectedUsername)

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
            // G10 (identity, plain-user path): the valve authenticates a token ONLY against the
            // token history of the *submitted* username (verifyPassword looks up that user and
            // iterates that user's own tokenHistory). So Jahia's authenticated `restMode` marker —
            // the literal body `OK` — returned for `username = LOGIN_USER` is a positive binding
            // that the established session belongs to LOGIN_USER, not some other identity: a foreign
            // or wrong token yields `unauthorized` (see the negative cases below). A GraphQL
            // `currentUser` whoami is not authorizable for an unprivileged user over a session cookie
            // (Jahia GraphQL API security — see the note on assertCurrentUserIs); the root regression
            // below asserts identity positively via `currentUser` where the platform permits it.
            tokenLogin(LOGIN_USER, token).its('body').should('eq', 'OK')
        })
    })

    // Regression: root's user node is ACL-restricted, so reading its token history requires a
    // system session. A valid token for root must authenticate just like any other user.
    // CHARACTERIZATION: root impersonation is intended for a support tool; the module currently
    // places no privilege ceiling on it. Stage-7 decision, not a bug — keep this assertion.
    it('authenticates the root user with a valid token', () => {
        createTokenFor('root', 60, 'root login regression').then((token: string) => {
            expect(token, 'generated token').to.be.a('string').and.not.be.empty
            tokenLogin('root', token).its('body').should('not.contain', UNAUTHORIZED_BODY)
            // G10: confirm the impersonated identity really is root.
            assertCurrentUserIs('root')
        })
    })

    // Gap G9 (U2 / D7) — CHARACTERIZATION, Stage-7 DESIGN DECISION.
    // The valve never consumes the token on use (SupportTokenAuthenticationValve
    // handleSuccessfulLogin never touches the token node), so a single minted token is replayable
    // until it expires. This pins that current behaviour: the SAME token authenticates twice across
    // two independent (cookie-cleared) sessions. Docs imply "single-use-style"; a future single-use
    // implementation would flip the second assertion. Do NOT treat as an assume-bug.
    it('accepts the same token more than once (token is not single-use)', () => {
        createTokenFor(LOGIN_USER, 60, 'replay characterization').then((token: string) => {
            expect(token, 'generated token').to.be.a('string').and.not.be.empty
            tokenLogin(LOGIN_USER, token).its('body').should('not.contain', UNAUTHORIZED_BODY)
            cy.clearCookies()
            // Second, independent session with the very same raw token still succeeds.
            tokenLogin(LOGIN_USER, token).its('body').should('not.contain', UNAUTHORIZED_BODY)
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
