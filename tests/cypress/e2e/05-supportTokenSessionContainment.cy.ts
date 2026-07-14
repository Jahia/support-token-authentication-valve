import { DocumentNode } from 'graphql'
import { createUser, deleteUser, grantRoles } from '@jahia/cypress'

/**
 * Gap G2 / spec S12 / undocumented behaviour U15, doc-fact D1 — privilege containment.
 *
 * A session established BY a support token must not be able to manage tokens, even when the
 * impersonated user would otherwise be authorised. This is the control that stops an impersonator
 * from pivoting — minting fresh tokens, enumerating a user's tokens, or wiping history — from
 * inside a borrowed session.
 *
 * The subtlety that makes this test meaningful: the impersonated user is granted the
 * `support-token-authentication-valve-administrator` role, so the `@GraphQLRequiresPermission`
 * gate PASSES. What refuses the operation is the separate session-containment guard
 * (`isTokenAuthenticatedSession`) which keys off the `supportTokenAuth` session attribute the valve
 * sets on a token login. So this proves containment independent of the permission gate:
 *   - listTokens ⇒ empty list   (SupportTokenQuery)
 *   - create     ⇒ null         (SupportTokenMutation)
 *   - clearAll   ⇒ false        (SupportTokenMutation)
 *
 * The management calls MUST ride the token-authenticated session cookie, so they are issued with
 * raw `cy.request` against `/modules/graphql` using the cookie jar from the `/cms/login`, NOT via
 * `cy.apollo` (which uses its own root client).
 *
 * CHARACTERIZATION — pins an existing, correct control. Resolves D1 (README right; AGENTS.md stale
 * in claiming the session block was removed).
 */
describe('Support Token — token-authed session is contained', () => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const createToken: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/createToken.graphql')
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const clearTokens: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/clearTokens.graphql')

    const ROLE_NAME = 'support-token-authentication-valve-administrator'
    // Impersonation target that DOES hold supportTokenAdmin, so only the containment guard can refuse.
    const CONTAINED_USER = 'stContainedUser'
    const USER_PASSWORD = 'StContain9PwdTest'
    const RECIPIENT = 'support-containment-test@jahia.com'

    // Perform a token login and keep the resulting session cookie in the jar.
    const tokenLogin = (username: string, token: string) =>
        cy.request({
            method: 'POST',
            url: '/cms/login',
            form: true,
            failOnStatusCode: false,
            body: { username, password: token, doLogin: 'true', restMode: 'true' },
        })

    // Issue a GraphQL operation on the CURRENT session cookie (the token-authed one).
    const gqlAsSession = (query: string, variables: Record<string, unknown>) =>
        cy.request({
            method: 'POST',
            url: '/modules/graphql',
            failOnStatusCode: false,
            headers: { 'Content-Type': 'application/json' },
            body: { query, variables },
        })

    const LIST = `query($u: String!, $s: String) { supportToken { listTokens(username: $u, siteKey: $s) { createdDate } } }`
    const CREATE = `mutation($u: String!, $r: String!) { supportToken { create(username: $u, recipient: $r) } }`
    const CLEAR = `mutation($u: String!) { supportToken { clearAll(username: $u) } }`

    before(() => {
        cy.login()
        createUser(CONTAINED_USER, USER_PASSWORD)
        grantRoles('/', [ROLE_NAME], CONTAINED_USER, 'USER')
    })

    after(() => {
        cy.login()
        cy.apollo({ mutation: clearTokens, variables: { username: CONTAINED_USER, siteKey: null } })
        deleteUser(CONTAINED_USER)
    })

    beforeEach(() => {
        cy.clearCookies()
    })

    it('refuses listTokens / create / clearAll from a token-authenticated session', () => {
        // Mint a token for the (admin-capable) target as root, then log in with it via the valve.
        cy.apollo({ mutation: clearTokens, variables: { username: CONTAINED_USER, siteKey: null } })
        cy.apollo({
            mutation: createToken,
            variables: {
                username: CONTAINED_USER,
                siteKey: null,
                recipient: RECIPIENT,
                description: 'containment seed',
                expiration: 60,
            },
        })
            .its('data.supportToken.create')
            .then((token: string) => {
                expect(token, 'seed token').to.be.a('string').and.not.be.empty
                // Establish the token-authed session (drop the root apollo cookies first).
                cy.clearCookies()
                tokenLogin(CONTAINED_USER, token).its('body').should('not.contain', 'unauthorized')

                // listTokens ⇒ empty list, despite a token existing for this user.
                gqlAsSession(LIST, { u: CONTAINED_USER, s: null })
                    .its('body.data.supportToken.listTokens')
                    .should('deep.eq', [])

                // create ⇒ null (cannot mint further tokens from a borrowed session).
                gqlAsSession(CREATE, { u: CONTAINED_USER, r: RECIPIENT })
                    .its('body.data.supportToken.create')
                    .should('be.null')

                // clearAll ⇒ false (cannot wipe history from a borrowed session).
                gqlAsSession(CLEAR, { u: CONTAINED_USER }).its('body.data.supportToken.clearAll').should('eq', false)
            })
    })
})
