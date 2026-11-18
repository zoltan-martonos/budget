# Personal spending budget

A locally hosted Spring Boot app for tracking expenses against a €30 daily allowance. Unspent allowance carries forward, and balances can go below zero when spending exceeds the available amount.

## Run locally

Requires Java 21 and Maven. From this directory, run:

```sh
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). On first use, choose the date the allowance should start; the €30 allowance is counted from that date, inclusive. The date and expenses are saved in the local H2 database at `./data/personal-budget`, retained between restarts. The existing database has been copied here so your current budget data is preserved.

The app does not connect to an external database or hosting service. To run the tests, use `mvn test`.

## Local AI assistant

The assistant uses Spring AI with a local Ollama server; no hosted model or API key is configured. Install Ollama from [ollama.com](https://ollama.com), start the server, and download a tool-capable model:

```sh
ollama serve
ollama pull llama3.3
```

By default the app connects to `http://localhost:11434` and uses `llama3.3`. Override these with `OLLAMA_BASE_URL` and `OLLAMA_MODEL` if needed. The app does not download the model automatically. Start the app with `mvn spring-boot:run` after Ollama is running.

After signing in, send a JSON `POST` to `/api/assistant/query`, for example:

```json
{"question":"How much did I spend yesterday?"}
```

The endpoint accepts only a question, not a user ID. Spending tools use the signed-in account; calendar weeks run Monday through Sunday, with the current week ending today. Rolling periods such as “last 3 weeks” use an inclusive 21-day window ending today and are clipped to the budget start date when necessary. Questions about the highest-spending day consider recorded expenses from budget start through today and include ties. The endpoint requires the authenticated session and the usual CSRF token for POST requests.

Lifetime, all-time, and “since inception” spending questions are measured from the signed-in user's budget start date through today. The app does not store a separate account-creation date.

Successful assistant question/answer exchanges are stored in the local H2 database and associated with the signed-in account. Failed or empty model responses are not saved. This currently provides persistence only; no conversation-history API or UI is exposed.

## Local MCP server

The app also exposes its five read-only spending tools over Streamable HTTP at
`http://localhost:8080/mcp`: daily, weekly, and recent spending totals, spending since
the budget start date, and highest-spending date(s). The endpoint requires an
`Authorization: Bearer <token>` header and accepts no user ID or email arguments.
Browser UI and assistant requests continue to use the signed-in Google OIDC session
and CSRF protection; MCP uses a separate, revocable per-user token.
The earlier `/mcp` 403 was the expected CSRF rejection for Copilot's request without
the browser's CSRF header; this setup does not disable CSRF globally.

For localhost testing with Copilot:

1. Start the app and sign in with Google in a browser.
2. While signed in, use `POST /api/mcp-token` with the session's CSRF token. For
   example, in the signed-in browser's developer console:

   ```js
   const { csrfToken } = await fetch("/api/auth/status").then(response => response.json());
   const { token } = await fetch("/api/mcp-token", {
     method: "POST",
     headers: { "X-CSRF-TOKEN": csrfToken }
   }).then(response => response.json());
   console.log(token);
   ```

   The response contains the plaintext token only once. The endpoint rotates any
   existing token for that account; the server stores only its SHA-256 hash.
3. Paste the token into Copilot's private MCP configuration or secret setting as
   `BUDGET_MCP_TOKEN`. Configure the server URL as `http://localhost:8080/mcp` and
   the header as `Authorization: Bearer ${BUDGET_MCP_TOKEN}`. `.mcp.json` shows this
   environment-variable form; do not put the token itself in a committed config.
4. If the token is exposed, sign in again and issue another `POST /api/mcp-token` to
   rotate it, or send a CSRF-protected `DELETE /api/mcp-token` to revoke it.

This bearer-token setup is for localhost testing only, not a public deployment
authentication strategy. Never commit or share the token, and do not expose the app
publicly without a separately designed and secured deployment.

### Connect GitHub Copilot CLI

The repository's `.mcp.json` configures the `budget` server at
`http://localhost:8080/mcp`. Copilot CLI reads workspace `.mcp.json` files, so run
it from this repository after starting the app, signing in, and creating a token
using the steps above. Set the token in the same terminal before launching Copilot:

```sh
export BUDGET_MCP_TOKEN="paste-the-token-here"
copilot
```

For example, ask Copilot:

```text
How much did I spend yesterday?
```

Copilot uses the budget server to answer for the account associated with the token.
In Copilot CLI, use `/mcp` to check the server, or run `copilot mcp list` in a
terminal. If the server is unavailable, confirm the app is running and that
`BUDGET_MCP_TOKEN` is set in the environment where Copilot CLI was started. Do not
commit the token or add it directly to `.mcp.json`.

### Connect Codex

Codex can connect to the same Streamable HTTP endpoint. First start the app, sign in
with Google, and create a token using the steps above. Set the token in the shell
where you will launch Codex, then register the server:

```sh
export BUDGET_MCP_TOKEN="paste-the-token-here"
codex mcp add budget --url http://localhost:8080/mcp --bearer-token-env-var BUDGET_MCP_TOKEN
```

The token stays in your shell environment; do not paste it directly into the Codex
command or commit it in a configuration file. Verify the connection with:

```sh
codex mcp get budget
```

The project `.mcp.json` contains the equivalent HTTP server URL and bearer header
configuration for clients that read that file. For Codex, the CLI command above
stores the server in Codex's user configuration. Rotate or revoke the token using
the endpoints described above if it is exposed.

Once connected, ask Codex a question that uses one of the spending tools, for
example:

```text
Using the Budget MCP server, how much did I spend this week?
```

## Google sign-in

The app requires Google sign-in by default. Any Google account with a verified email can create and access its own private budget; accounts do not share expenses or settings. Account ownership is keyed by Google's stable identity subject, not by email. The app reads settings from environment variables or an optional `.env` file in the working directory. If OAuth credentials are not configured, it still starts but displays setup instructions and blocks access to the budget.

After sign-in, the local app session is retained in a persistent, HTTP-only browser cookie for up to 14 days and expires after 14 days of inactivity. Logging out clears the session. Sessions are held in memory, so restarting the app requires signing in again.

1. In Google Cloud Console, open the existing OAuth client. It must be a **Web application** client.
2. Add `http://localhost:8080` as an authorized JavaScript origin and `http://localhost:8080/login/oauth2/code/google` as an authorized redirect URI.
3. Set the client ID and secret in the shell that will start the app:

   ```sh
   export BUDGET_GOOGLE_CLIENT_ID="your-client-id"
   export BUDGET_GOOGLE_CLIENT_SECRET="your-client-secret"
   ```

4. If upgrading a database created by the single-account version, keep its existing `BUDGET_ALLOWED_EMAIL` configured for the first sign-in after upgrade. The app migrates the existing settings and expenses to a reserved legacy account; only a sign-in with that same verified email can bind and retain the existing budget. Other verified Google accounts receive new, separate budgets. This setting is only used for the one-time legacy-account binding; it does not restrict new sign-ins.

5. If the OAuth consent screen is in Testing mode, add each account as a test user. Start the app from this directory using `mvn spring-boot:run`, then open [http://localhost:8080](http://localhost:8080) and choose **Continue with Google**.

Alternatively, put the same `KEY=value` entries in a `.env` file in the app's working directory; the app imports it automatically. Keep the client secret out of source control and do not share it. Environment variables take precedence over values in `.env`. For HTTPS deployments, set `BUDGET_COOKIE_SECURE=true` and register the matching HTTPS callback URI in Google Cloud Console. Do not expose this local app to the public internet without HTTPS and an appropriately configured OAuth client.
