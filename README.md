# Support Token Authentication Valve

Jahia OSGi module that lets a support engineer create a short-lived token for any user, receive it by email, and use it to log in without knowing the real password.

## Features

- **Token-based authentication valve** — plugs into Jahia's auth pipeline before the standard login valve; verifies hashed tokens and checks expiry.
- **GraphQL API** — create, list, and clear tokens programmatically.
- **React admin UI** — accessible from both server settings (all users) and site settings (site users).
- **Karaf shell commands** — `support-token:create`, `support-token:list`, `support-token:clear`.
- **Email notification** — sends the raw token to the designated recipient and an audit email (without the token) to the Jahia notification address when a token is created (requires a configured mail server).

## Requirements

- Jahia 8.2+
- `graphql-dxm-provider` module
- Mail server configured in Jahia server settings (optional — token creation still works without it, but no email is sent)

## Installation

1. In Jahia, go to **Administration → Server settings → System components → Modules**.
2. Upload `support-token-authentication-valve-X.X.X.jar`.
3. Verify the module status is **Started**.

## Usage

### Admin UI

Navigate to **Administration → Users and Roles → Support Token Authentication** (server level) or the equivalent entry under site settings.

1. Enter a username (and optionally a site key for site-scoped users) and click **Search**.
2. Existing tokens are listed with their creation date, recipient, description, and expiration.
3. Fill in **Recipient email**, optional **Description**, and **Expiration (minutes)**, then click **Create Token**.
4. The generated token is shown once in a copy box — copy it before navigating away.
5. Use **Clear All Tokens** to remove all tokens for the user.

### Karaf Shell

#### `support-token:create`

Creates a token for a user.

| Option | Alias | Required | Default | Description |
|--------|-------|:--------:|---------|-------------|
| `-u` | `--username` | ✓ | — | Username |
| `-r` | `--recipient` | ✓ | — | Recipient email address |
| `-s` | `--site-key` | | `null` | Site key (global users if omitted) |
| `-d` | `--description` | | `Access for Jahia Support` | Purpose of the token |
| `-e` | `--expiration` | | `60` | Expiration in minutes |

```
support-token:create -u root -r support@jahia.com -e 120 -d "Bug JAHIA-1234"
```

#### `support-token:list`

Lists all tokens for a user (metadata only — the raw token is never stored or shown).

```
support-token:list -u root
```

#### `support-token:clear`

Removes all tokens for a user.

```
support-token:clear -u root
```

### GraphQL API

All operations require `admin` permission.

#### Query — list tokens

```graphql
query {
    supportTokenListTokens(username: "root") {
        createdDate
        recipient
        expiration
        description
    }
}
```

Returns `null` if the user does not exist, or an empty array if the user has no tokens.

#### Mutation — create token

```graphql
mutation {
    supportTokenCreate(
        username: "root"
        recipient: "support@jahia.com"
        description: "Bug JAHIA-1234"
        expiration: 120
    )
}
```

Returns the raw token string on success, or `null` on failure. **Store it immediately** — it cannot be retrieved again.

#### Mutation — clear all tokens

```graphql
mutation {
    supportTokenClearAll(username: "root")
}
```

Returns `true` on success.

## Authentication

Once a token is created, use it to log in via the standard Jahia login form or HTTP POST:

```
POST /cms/login
username=<user>&password=<token>&site=<siteKey>&doLogin=true
```

The token is valid for `expiration` minutes from its creation time.

## Development

### Build

```bash
mvn clean install
```

The frontend-maven-plugin installs Node/Yarn and builds the React bundle automatically.

For frontend-only development:

```bash
yarn install
yarn build          # development build
yarn build:production
yarn lint
```

### Tests

Tests run inside Docker. From the `tests/` directory:

```bash
cp .env.example .env
# Edit .env: set JAHIA_IMAGE, JAHIA_LICENSE, SUPER_USER_PASSWORD
docker compose up --abort-on-container-exit
```

Cypress results are written to `tests/results/`.

## Security Notes

- Raw tokens are **never** persisted. Only their bcrypt hash is stored in JCR.
- Expired tokens are kept in JCR as audit records but rejected at login. Use **Clear All Tokens** to purge them.
- The token management UI is inaccessible to sessions that are themselves authenticated with a support token (blocked at the auth valve level via the session attribute).
