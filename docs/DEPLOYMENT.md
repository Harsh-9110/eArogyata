# Deployment: Vercel frontend + separate Java/MySQL host

Vercel serves the React/Vite frontend. Keep Spring Boot as a long-running Java container and MySQL as a persistent database on a container/VPS or managed backend provider. This repository does not attempt to run a Java server or persistent MySQL in Vercel functions.

No cloud resources were provisioned, no costs incurred, and no live URL was obtained during this build. Vercel/GitHub/deployment integrations are disabled by the current workspace admin. Use the following concrete configuration when publishing from your account.

## 1. Verify the code before publishing

Run `mvn verify` in `backend/`, then `npm ci && npm run build && npm test` in `frontend/`. Start the Compose stack and run `python3 scripts/security-probe.py`. GitHub Actions repeats these checks on a Docker-capable Linux runner. Review the actual results; configured CI does not mean it has already passed.

## 2. Host MySQL and the API

Provision MySQL 8.4 with a persistent volume or managed backups. Use a private connection where available and managed TLS in production. Do not expose a database port publicly for the browser.

Build from the repository root with Dockerfile `backend/Dockerfile`. The container listens on `PORT` (default 8080); health probe is `/actuator/health`. Allow roughly 768 MiB API memory initially and ensure the database permits the configured 12-connection Hikari pool. This is a starting configuration, not a load-tested capacity promise.

| API environment | Required value |
|---|---|
| DB_URL | Managed JDBC URL, e.g. `jdbc:mysql://HOST:3306/earogyata?sslMode=VERIFY_IDENTITY&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` |
| DB_USER / DB_PASSWORD | Database credentials from the provider |
| JWT_SECRET | At least 32 random bytes expressed as a secret string; `python3 -c "import secrets; print(secrets.token_urlsafe(40))"` |
| ALLOWED_ORIGINS | Exact frontend origin such as your actual `https://PROJECT.vercel.app`; no wildcard or trailing slash |
| COOKIE_SECURE | `true` |
| HOSPITAL_TIMEZONE | `Asia/Kolkata`, or the intended hospital's IANA timezone |
| BOOTSTRAP_ADMIN_EMAIL | Your chosen initial administrator email |
| BOOTSTRAP_ADMIN_PASSWORD | Your chosen strong initial password, at least 12 characters and at most 72 UTF-8 bytes |
| SPRING_PROFILES_ACTIVE | Empty / production configuration; do not enable `demo` for a private production instance |

Flyway runs before startup. First startup creates an administrator only if no ADMIN exists and bootstrap variables are provided. Remove bootstrap password from the deployment configuration afterward. No production credentials are checked into this repository. Reference wards/beds come from V3; user records are not inserted by migrations. With an empty production database, sign in as admin, create doctor/patient/staff accounts and publish future sessions.

For stricter database separation, run migrations in an authorized release job, then set `SPRING_FLYWAY_ENABLED=false` for the runtime and use restricted table grants. The application needs SELECT/INSERT/UPDATE on workflow tables, SELECT/INSERT on audit_events, and SELECT/INSERT/UPDATE/DELETE on rate_limit_buckets. It does not need UPDATE/DELETE on audit_events or runtime DDL. Default local startup intentionally uses one migration-capable account. Back up and verify restore procedures before real usage; this portfolio instance should use synthetic accounts.

## 3. Configure the Vercel rewrite

Once the API has a verified HTTPS origin:

```bash
node scripts/configure-vercel.mjs https://YOUR-ACTUAL-API-HOST
```

Replace the example argument with your real origin, without `/api` or credentials. The script rejects the checked-in placeholder host and writes the `/api/:path*` external rewrite in `frontend/vercel.json`. This keeps requests and refresh cookies on the same public origin as the frontend. Commit the actual non-secret rewrite target. No token is placed in configuration or a URL.

## 4. Import the frontend into Vercel

Push the repository to your own GitHub account and import it in Vercel. Use:

| Setting | Value |
|---|---|
| Root directory | `frontend` |
| Framework | Vite |
| Install | `npm ci` |
| Build | `npm run build:vercel` |
| Output | `dist` |
| Node.js | 24 |
| VITE_HOSPITAL_TIMEZONE | Same value as API HOSPITAL_TIMEZONE |
| VITE_DEMO_MODE | `false` (or explicitly `true` only for an intentionally public synthetic demo) |
| VITE_API_BASE_URL | Leave unset; browser uses `/api/v1` through the rewrite |

Generated API types are checked into source. The Vercel build needs only `frontend/`, so it works with that root-directory setting. Full repository builds and CI regenerate the types and reject drift. Do not place JWT_SECRET, DB_PASSWORD or staff passwords in VITE variables; Vite embeds them into public JS.

Use the exact resulting production frontend origin in the API's ALLOWED_ORIGINS, redeploy the API if needed, and retry sign-in. Vercel preview URLs require explicit separate allowlisting; do not use a broad wildcard. Preview deployments should use isolated synthetic API data or have login disabled.

## 5. Verify the deployed flow

Check API health over HTTPS and the frontend's Network panel. Confirm login/register set `earogyata_refresh` with Secure, HttpOnly, SameSite=Strict and Path=/api/v1/auth, with no Domain attribute. Reload the page and verify refresh rotation. Log out and confirm old access is rejected. Verify the browser's Origin reaches the API unchanged through the proxy. If refresh gets 403, correct the exact allowlist rather than weakening the origin check.

Check `/api/v1/events` as a streaming fetch with an Authorization header. Ensure the API host disables response buffering and allows long-lived requests. The frontend reconciles snapshots every 20 seconds if the hosting proxy closes the stream. Multi-replica API instances currently send immediate events only to their own clients; use a single API replica for the simplest portfolio demonstration, or document polling convergence. A broker/outbox is a future scaling extension.

Run the active security probes against the isolated deployed URL with `--api ACTUAL_FRONTEND_ORIGIN/api/v1 --origin ACTUAL_FRONTEND_ORIGIN`. Run k6 only against a dedicated synthetic instance, capture its raw output, then update README with the provided script. Record the real demo with `qa/record-demo.mjs`. Add the verified live URL to README only after it exists.

Public provider documentation used for the deployment design: [Vercel runtimes](https://vercel.com/docs/functions/runtimes), [Vite on Vercel](https://vercel.com/docs/frameworks/frontend/vite), [Vercel rewrites](https://vercel.com/docs/rewrites).
