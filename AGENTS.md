# support-token-authentication-valve

Jahia OSGi module that manages temporary support tokens: a support engineer creates a short-lived token for a specific user, receives it by email, and uses it to log in without knowing the real password. Admin UI at `/jahia/administration/supportTokenAdmin`.

## Key Facts

- **artifactId**: `support-token-authentication-valve` | **version**: `3.1.2-SNAPSHOT` (Maven `pom.xml` — the source of truth). Note: `package.json` still reads `2.0.8-SNAPSHOT`; this front-end/back-end version drift is cosmetic (the OSGi bundle version comes from the pom) but should be reconciled.
- **groupId**: `org.jahia.community`
- **Java package**: `org.jahia.community.token`
- **jahia-depends**: `default,graphql-dxm-provider`
- No Blueprint/Spring — pure OSGi DS
- Karaf commands: `support-token:create`, `support-token:list`, `support-token:clear`

## Architecture

| Class | Role |
|-------|------|
| `valve/SupportTokenAuthenticationValve` | `@Component(service=Valve.class, immediate=true)`; inserts itself before `LoginEngineAuthValve` via `@Reference(target="(type=authentication)")`; verifies hashed token against JCR and checks expiry |
| `SupportTokenConstants` | String constants for JCR node types, node names, and property names |
| `SupportTokenUtils` | Static helpers: `addToken`, `clearAllTokens`, `listUserTokens`, `generateRandomToken`; each wraps a `JCRTemplate` system session |
| `command/CreateCommand` | Karaf `@Command` — generates and stores a token, prints the raw value |
| `command/ListCommand` | Karaf `@Command` — prints formatted token list for a user |
| `command/ClearCommand` | Karaf `@Command` — removes all tokens for a user |
| `graphql/SupportTokenGraphQLExtensionsProvider` | `DXGraphQLExtensionsProvider` marker; triggers discovery of `@GraphQLTypeExtension` classes |
| `graphql/SupportTokenQueryExtension` | Query field: `supportTokenListTokens(username, siteKey)` → `[SupportTokenInfo]` |
| `graphql/SupportTokenMutationExtension` | Mutation fields: `supportTokenCreate(...)` → `String` (token), `supportTokenClearAll(...)` → `Boolean` |

## JCR Data Model

Token data is stored under the user node:

```
/users/<username>
  jmix:supportTokenUser  (mixin)
  └── tokenHistory  (jnt:supportTokenHistory)
      └── <yyyy-MM-dd-HH-mm-ss>  (jnt:supportToken)
          ├── token       (string, bcrypt hash)
          ├── expiration  (long, minutes from creation)
          ├── description (string)
          └── recipient   (string, email)
```

The raw token is **never** stored. Only its bcrypt hash (via `PasswordService`) is persisted. The raw token is returned once by the GraphQL mutation and is never retrievable again.

## Authentication Flow

`SupportTokenAuthenticationValve` runs before `LoginEngineAuthValve` in the auth pipeline. On a login request it:

1. Looks up the user by `username` + optional `site` parameter.
2. Iterates the user's `tokenHistory` children.
3. For each child, calls `PasswordService.matches(rawToken, storedHash)`.
4. On match, checks expiry: `creationDate + expiration_minutes > now`.
5. If valid: sets the current user, fires `LoginEvent` + OSGi `org/jahia/usersgroups/login/LOGIN` event, returns (does not fall through to next valve).
6. On any failure: sets the appropriate `VALVE_RESULT` attribute and calls `invokeNext`.

There is no longer a `main.jsp`. Access control for the admin UI is handled by the React `adminRoute` registry entries via `requiredPermission`; both routes now gate on `supportTokenAdmin` (D5 — aligned with the GraphQL resolver permission).

**Session containment IS present** (correcting an earlier stale note that claimed the session block was removed). On a successful token login the valve sets the HTTP session attribute `supportTokenAuth=true` (`SupportTokenConstants.SESSION_SUPPORT_TOKEN_AUTH`). Every GraphQL resolver checks this via `isTokenAuthenticatedSession(...)` and refuses service to a token-authenticated caller — `listTokens` ⇒ empty list, `create` ⇒ `null`, `clearAll` ⇒ `false` — so an impersonated session cannot pivot to mint, enumerate, or wipe tokens even if the impersonated user holds `supportTokenAdmin`.

**Audit logging (U18).** The module emits `[AUDIT]`-prefixed `INFO` log lines at three points so impersonation activity is traceable: the token **minter** identity when `supportToken.create` is invoked, the **target** user when a token is actually minted (`SupportTokenUtils.addToken`), and a distinct line tagging each successful token **login** in the valve (so it is distinguishable from an ordinary password login).

## GraphQL API

All operations require the `supportTokenAdmin` permission (via `@GraphQLRequiresPermission("supportTokenAdmin")`) — **not** the generic `admin` permission. In addition, every resolver refuses callers whose HTTP session was established by the support-token valve (see "Session containment" below).

**The operations are namespaced**, not flat root fields. `Query` and `Mutation` each expose a single `supportToken` field (see `SupportTokenQueryExtension` / `SupportTokenMutationExtension`) that returns the `SupportTokenQuery` / `SupportTokenMutation` container; the actual operations are nested under it:

```graphql
query    { supportToken { listTokens(username: "...", siteKey: "...") { createdDate recipient expiration description } } }
mutation { supportToken { create(username: "...", recipient: "...", expiration: 60) } }
mutation { supportToken { clearAll(username: "...") } }
```

### Queries

| Name | Parameters | Returns | Notes |
|------|-----------|---------|-------|
| `supportToken.listTokens` | `username: String!`, `siteKey: String` | `[SupportTokenInfo]` | Returns `null` if user not found; empty list if no tokens. Returns empty list for a token-authed session. |

`SupportTokenInfo` fields: `createdDate` (ISO string), `recipient`, `expiration` (Long, minutes), `description`.

### Mutations

| Name | Parameters | Returns | Notes |
|------|-----------|---------|-------|
| `supportToken.create` | `username: String!`, `siteKey: String`, `recipient: String!`, `description: String`, `expiration: Long` | `String` | Raw token (UUID); `null` on failure, missing required params, or a token-authed session. Sends email via `MailService` if configured. Default expiration: 60 min. |
| `supportToken.clearAll` | `username: String!`, `siteKey: String` | `Boolean` | Removes the entire `tokenHistory` node; `false` for a token-authed session |

## React Admin UI

- **Entry**: `src/javascript/index.js` → `init.js` → `SupportToken/register.jsx`
- **Routes**:
  - `administration-server-usersAndRoles:10` — requires `adminUsers`
  - `administration-sites:10` — requires `siteAdminUsers`
- **Component**: `SupportToken/SupportToken.jsx`
  - Username + site key search → calls `supportTokenListTokens`
  - Token table (created date, recipient, description, expiration)
  - Create form (recipient required, description optional, expiration default 60)
  - After creation: generated token displayed in a copy box (shown once)
  - Clear All button (disabled when no tokens exist)
- **CSS prefix**: `st_`
- **i18n namespace**: `support-token-authentication-valve`
- **GQL file**: `SupportToken/SupportToken.gql.js` — `LIST_TOKENS`, `CREATE_TOKEN`, `CLEAR_ALL_TOKENS`
- **Button ids**: `st-search`, `st-create-token`, `st-clear-all` (for Cypress test selectors)

## Cypress Tests

| File | Scope |
|------|-------|
| `01-supportTokenAPI.cy.ts` | GraphQL API shape, field types, create/list/clear round-trips, email verification (namespaced `supportToken.*` ops) |
| `02-supportTokenUI.cy.ts` | Admin UI page structure, search, token creation, clear flow, email verification |
| `03-supportToken-Permissions.cy.ts` | `supportTokenAdmin` permission gate on the GraphQL operations |
| `04-supportTokenLogin.cy.ts` | End-to-end token login via `/cms/login`, incl. the **root-login regression** and identity whoami (`currentUser { name }`); negative fail-closed cases; **U2 replay characterization** |
| `05-supportTokenSessionContainment.cy.ts` | Token-authed session is contained: list⇒`[]`, create⇒`null`, clearAll⇒`false` even when the impersonated user holds `supportTokenAdmin` |

### Characterization notes (pinned, not fixed — maintainer decision)

- **U2 token replay** — a minted token is reusable until expiry (the valve never consumes it on use). Docs imply single-use; `04` pins the current replayable behaviour. Flip that assertion if single-use is ever implemented.
- **Root impersonation** — a valid token for `root` authenticates via the system session by design (root's node is ACL-restricted). No privilege ceiling is placed on the target. Kept as a regression, not treated as a bug.
- **U3 no rate-limiting** — LOW severity; tokens are 122-bit random UUIDs, so brute force is impractical.

CSS Module selectors use `[class*="st_..."]` pattern. Uses **mailpit** (`cypress-mailpit`) for email verification testing.

## Build

```bash
mvn clean install       # builds Java + runs yarn build:production
yarn build              # webpack development build only
yarn build:production   # webpack production build
yarn lint               # ESLint on src/javascript
```

- Node: `v22.6.0` | Yarn: `v1.22.21` (installed by `frontend-maven-plugin`; `.yarnrc.yml` sets `nodeLinker: node-modules`)
- Output bundle: `src/main/resources/javascript/apps/support-token-authentication-valve.bundle.js`
- **Lint config**: `.eslintrc.json` (extends `@jahia`), `babel.config.js` (preset-react + preset-env)

## Gotchas

- The valve iterates **all** token children on every login attempt. For users with many tokens this is O(n) but acceptable given typical token counts (< 10).
- Expired tokens are **not** automatically removed from JCR. They remain in the history as audit records and simply fail the expiry check. Use `supportTokenClearAll` to purge.
- `PasswordService.matches` is bcrypt — it is intentionally slow. Avoid calling it in tight loops.
- `supportTokenListTokens` returns `null` (not empty list) when the user does not exist. The React component uses this distinction to show "user not found" vs "no tokens".
- The `@Reference(service=Pipeline.class, target="(type=authentication)")` on the valve means the component will not activate until Jahia's auth pipeline OSGi service is published. If the module starts before Jahia core is fully up, DS will hold the component pending until the dependency is satisfied.
- The `SpringContextSingleton.getInstance().publishEvent(new LoginEvent(...))` call in the valve is intentional: it fires the standard Jahia `BaseLoginEvent` consumed by integrations like JExperience. The separate `FrameworkService.sendEvent` call publishes the same event on the OSGi event bus for integrations listening there.
