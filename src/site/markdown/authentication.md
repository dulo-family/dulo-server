# Authentication

Dulo does not store passwords. Users sign in with an identity provider you already use, such as
Google, Microsoft or a self-hosted one like Pocket ID. Dulo then issues its own tokens to its own
clients.

## The server's three roles

1. **A client of the identity providers.** Dulo has its own client ID, and usually a secret, at
   each provider in `dulo.identity.providers`. It signs users in with the OpenID Connect
   authorization code flow. On the first sign-in it creates a Dulo user, and on every sign-in it
   maps the provider's roles claim to Dulo roles.
2. **An authorization server** for Dulo's clients: the web client, tools like Postman, and later
   MCP clients. It issues JWT access tokens whose subject (`sub`) is the Dulo user id.
   - Access tokens last 15 minutes.
   - Refresh tokens last 30 days and are replaced on every use.
   - Authorizations and refresh tokens are kept in memory, so after a restart users sign in again.
     If the provider's session is still alive, that needs no password.
3. **A resource server.** Every API call needs one of Dulo's access tokens. Dulo checks the token
   with its own key, without calling the provider. What the user may do comes from Dulo's
   database, not from the token, so a role change takes effect on the next call.

The key that signs the tokens is created on the first start and stored in the database. It is
published at `/oauth2/jwks`.

## Sign-in flow

<pre class="mermaid">
sequenceDiagram
    autonumber
    actor B as Browser (web client)
    participant AS as Dulo authorization server
    participant IdP as Identity provider
    participant API as Dulo API (resource server)
    B->>AS: GET /oauth2/authorize?client_id=dulo-web&code_challenge=…
    AS-->>B: 302 to the web client's sign-in page (no Dulo session yet)
    B->>API: GET /api/v1/identity-providers
    B->>AS: GET /oauth2/authorization/{providerId} (the user picks a provider)
    AS-->>B: 302 to the provider's authorize endpoint (Dulo's client ID there)
    B->>IdP: The user signs in
    IdP-->>B: 302 /login/oauth2/code/{providerId}?code=…
    B->>AS: Follows the redirect
    AS->>IdP: POST token endpoint (code + Dulo's client secret)
    IdP-->>AS: ID token (sub, email, name, groups)
    AS->>AS: Finds or creates the Dulo user, maps claims to roles
    AS-->>B: 302 back to /oauth2/authorize, now with a Dulo session
    AS-->>B: 302 redirect_uri?code=…
    B->>AS: POST /oauth2/token (code + code_verifier)
    AS-->>B: Access token (JWT, sub = Dulo user id, 15 min) + refresh token
    B->>API: GET /api/v1/tree, Authorization: Bearer …
    API->>API: Checks the JWT with Dulo's key, then the user's status, roles and features
    API-->>B: 200, or a 401/403 problem
</pre>

The server skips the web client's sign-in page (step 2) when the choice is clear: only one
provider is configured, or the authorization request names one with `idp=<providerId>`.

## Users and roles

- New users are **active** when `dulo.registration.mode` is `auto`, or when a role mapping matches
  their claims. Otherwise they are **pending** until an administrator approves them.
- A pending user becomes active as soon as a later sign-in matches a role mapping.
- A user's roles are the roles mapped from the provider's claims, which are recomputed at every
  sign-in, plus the roles an administrator assigns. Users whose claims match no mapping get the
  provider's `default-role`.
- Google sends no group claims, so Google users depend on the default role and on roles assigned
  by hand.

## Testing with Postman

Postman supports the authorization code flow with PKCE.

1. Start a local identity provider that accepts any user name, the same one the integration tests
   use:

   ```sh
   docker run --rm -p 9090:8080 -e JSON_CONFIG='{"interactiveLogin": true}' \
     ghcr.io/navikt/mock-oauth2-server:6.1.5
   ```

2. Start the server with the development configuration
   [`deploy/dev/dulo.dev.yaml`](https://github.com/rdlf0/dulo-server/blob/main/deploy/dev/dulo.dev.yaml):

   ```sh
   DULO_CONFIG_FILE=deploy/dev/dulo.dev.yaml java -jar dulo-service/target/dulo-server-*.jar
   ```

   In IntelliJ, the shared run configuration **Dulo local with mock IdP** does steps 1 and 2.

3. In Postman, open the request's **Authorization** tab and choose **OAuth 2.0**:

   | Setting | Value |
   |---|---|
   | Grant type | Authorization Code (With PKCE) |
   | Callback URL | `https://oauth.pstmn.io/v1/callback`, with **Authorize using browser** on |
   | Auth URL | `http://localhost:8080/oauth2/authorize` |
   | Access Token URL | `http://localhost:8080/oauth2/token` |
   | Client ID | `dulo-web` |
   | Client Secret | leave empty |
   | Code Challenge Method | SHA-256 |
   | Scope | `openid` |
   | Client Authentication | Send client credentials in body |

   With several providers configured, add `idp` = `<providerId>` under **Advanced → Auth Request**.

4. Click **Get New Access Token**. The browser shows the mock provider's form. Enter any user name
   and, for an administrator, the claims `{"groups": ["dulo-admins"]}`. Postman receives the
   tokens.

5. Call the API, for example `GET http://localhost:8080/api/v1/me`.

6. Access tokens last 15 minutes. Postman refreshes them with the refresh token (turn on
   **Auto-refresh access token**, or click **Refresh** in the token manager). Refresh tokens are
   kept in the server's memory, so after a server restart click **Get New Access Token** again.

curl works too: open the authorize URL in a browser, take the `code` from the redirect, and post it
with the `code_verifier` to `/oauth2/token`.
