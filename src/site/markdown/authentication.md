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
   - Access tokens last 15 minutes. They are JWT access tokens as
     [RFC 9068](https://www.rfc-editor.org/rfc/rfc9068) defines them: type `at+jwt`, a
     `client_id` claim, and Dulo's own URL as the audience (`aud`).
   - Refresh tokens last 30 days and are replaced on every use.
   - Authorizations and refresh tokens are kept in memory, so after a restart users sign in again.
     If the provider's session is still alive, that needs no password.
3. **A resource server.** Every API call needs one of Dulo's access tokens. Dulo checks the token
   with its own key, without calling the provider, and accepts only access tokens: an ID token
   from the same sign-in has the same user and signature, but is rejected with 401. What the user may do comes from Dulo's
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

The web client is the public client `dulo-web`. By default it runs under the public URL, as in the
Docker image: its sign-in page is `<public-url>/sign-in` and its redirect URI
`<public-url>/auth/callback`. A web client served elsewhere needs `dulo.clients.web.redirect-uris`,
`allowed-origins` and `sign-in-url`; `deploy/dev/dulo.dev.yaml` shows them for local development.

## Users and roles

- New users are **active** when `dulo.registration.mode` is `auto`, or when a role mapping matches
  their claims. Otherwise they are **pending** until an administrator approves them.
- A pending user becomes active as soon as a later sign-in matches a role mapping.
- `role-mappings` maps each value of the provider's roles claim to one Dulo role, e.g.
  `dulo-admins: admin`. A user with several matching values gets each of their roles. A group
  that needs more permissions maps to a role that has them. A list of roles per value is
  rejected at startup.
- A user's roles are the roles mapped from the provider's claims, which are recomputed at every
  sign-in, plus the roles an administrator assigns. Users whose claims match no mapping get the
  provider's `default-role`.
- Google sends no group claims, so Google users depend on the default role and on roles assigned
  by hand.
- Administrators (edit access to the `administration` feature, which the built-in admin role has)
  approve, disable and reactivate users and assign roles with `PATCH /api/v1/admin/users/{id}`,
  and manage roles at `/api/v1/admin/roles`. Nobody can change their own status or the root
  user's. Only root can change the built-in roles, and nobody can delete them. A role that users
  have or that an identity provider maps to can't be deleted.
- **Partial administrators stay partial.** Unless they're root, administrators can only grant
  what they may do themselves:
  - every role they assign must grant no more than their own roles do;
  - they can only change users who may do no more than they may;
  - they can only create, change or delete roles within their own permissions.

  The reason: without these rules, a role meant only to approve new users (for example
  `administration: [view, edit]`) could give its holder the admin role, or widen itself to every
  permission, which would make any administration access the same as full administration. With
  them, the built-in admin role, which has every permission, works as before, and a narrower role
  stays as narrow as root made it. To let a moderator hand out a role, give the moderator's role at
  least that role's permissions.
- Changes send the version they're based on in `If-Match` (the `ETag` of the last read), so a
  change to an outdated copy fails with 412 instead of overwriting someone else's change.

## Initial setup

A new server has no tree, no roles and no administrator. Until the setup is done, every API call
except the setup itself, `/api/v1/identity-providers`, `/api/v1/me` and the API description
answers **503** with the problem type `urn:dulo:problem:setup-required`.

The setup is protected by a **setup token**: the value of `dulo.setup.token`, or a random token
the server generates and logs on every start while the setup is pending. Whoever completes the
setup must be signed in and know the token, and becomes the **root** user, who may do everything,
whatever their roles.

<pre class="mermaid">
sequenceDiagram
    autonumber
    actor A as Administrator (web client)
    participant API as Dulo API
    participant Log as Server log
    API->>Log: Initial setup pending, setup token …
    A->>API: GET /api/v1/setup/status
    API-->>A: {"pending": true}
    A->>API: Signs in (see the sign-in flow)
    A->>API: GET /api/v1/setup/defaults, X-Dulo-Setup-Token: …
    API-->>A: Built-in roles, features, providers and their role mappings
    A->>API: GET /api/v1/setup/config, X-Dulo-Setup-Token: …
    API-->>A: The merged configuration, secrets masked
    A->>API: POST /api/v1/setup, Bearer …, X-Dulo-Setup-Token: … (tree, features, roles)
    API->>API: Creates the tree and the roles, makes the caller root (one transaction)
    API-->>A: 201, the tree
</pre>

- `GET /api/v1/setup/status` is public. The defaults and the configuration need the setup token.
- `POST /api/v1/setup` needs an access token and the setup token. Its body has the tree's name,
  description and enabled features, and optionally roles. Built-in roles that the body leaves out
  get their default permissions. Every role an identity provider's `role-mappings` or
  `default-role` names must exist, or the request is rejected with 400.
- The setup works once. Afterwards the setup endpoints answer 409 `urn:dulo:problem:setup-completed`.

## Testing with Postman

Postman supports the authorization code flow with PKCE.

1. Start a local identity provider that accepts any user name, the same one the integration tests
   use:

   ```sh
   docker run --rm -p 9090:8080 -e JSON_CONFIG='{"interactiveLogin": true}' \
     ghcr.io/navikt/mock-oauth2-server:6.1.5
   ```

2. Start the server with the development configuration
   [`deploy/dev/dulo.dev.yaml`](https://github.com/dulo-family/dulo-server/blob/main/deploy/dev/dulo.dev.yaml):

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

   On a new server, complete the setup first: `POST http://localhost:8080/api/v1/setup` with the
   header `X-Dulo-Setup-Token: dev-setup-token` (set in the development configuration) and the
   body

   ```json
   {"name": "The Smiths", "enabledFeatures": ["people", "families", "tree-view"]}
   ```

   You become the root user. Until then, other calls such as `GET /api/v1/tree` answer 503.

6. Access tokens last 15 minutes. Postman refreshes them with the refresh token (turn on
   **Auto-refresh access token**, or click **Refresh** in the token manager). Refresh tokens are
   kept in the server's memory, so after a server restart click **Get New Access Token** again.

curl works too: open the authorize URL in a browser, take the `code` from the redirect, and post it
with the `code_verifier` to `/oauth2/token`.
