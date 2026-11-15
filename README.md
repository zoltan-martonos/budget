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
ollama pull llama3.2
```

By default the app connects to `http://localhost:11434` and uses `llama3.2`. Override these with `OLLAMA_BASE_URL` and `OLLAMA_MODEL` if needed. The app does not download the model automatically. Start the app with `mvn spring-boot:run` after Ollama is running.

After signing in, send a JSON `POST` to `/api/assistant/query`, for example:

```json
{"question":"How much did I spend yesterday?"}
```

The endpoint accepts only a question, not a user ID. Spending tools use the signed-in account; calendar weeks run Monday through Sunday, with the current week ending today. Rolling periods such as “last 3 weeks” use an inclusive 21-day window ending today and are clipped to the budget start date when necessary. Questions about the highest-spending day consider recorded expenses from budget start through today and include ties. The endpoint requires the authenticated session and the usual CSRF token for POST requests.

Lifetime, all-time, and “since inception” spending questions are measured from the signed-in user's budget start date through today. The app does not store a separate account-creation date.

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
