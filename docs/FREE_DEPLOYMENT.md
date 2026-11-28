# Free deployment: Northflank Sandbox + MySQL

Checked against official provider documentation on 28 November 2026. This is a prepared deployment route, not a completed deployment. No live URL has been created or tested.

The React production build and Spring Boot API run together in one container. A separate Northflank MySQL addon stores the data. Both are in the same project; only the app's HTTPS endpoint is public. This preserves the Spring Boot / React / TypeScript / MySQL stack on your resume.

## 1. Select the free hosting plan

Open https://app.northflank.com/ and sign up or sign in. Choose **Sandbox** on **Northflank Cloud**. The current pricing page advertises up to $20/month of included resources across 2 services, 2 jobs, and 1 database, with no sleeping. A valid payment method is required for abuse prevention. Keep the account on Sandbox and confirm that the resources below are included and the amount payable is $0 before creating them. BYOC uses your own paid cloud infrastructure and is not this route.

If the account dashboard requires a paid upgrade, do not select it for this completely free deployment. Provider limits and eligibility can change.

## 2. Put this version in your GitHub repository

Extract the deployment ZIP. Upload the contents of `eArogyata-main/` to your repository root, or commit them with Git. The root must contain `Dockerfile`, `backend/`, `frontend/`, `deploy/` and `.github/` directly, without another enclosing project directory. Do not upload an actual `.env`, passwords or database certificates.

The root Dockerfile builds the frontend and backend on the host; you do not need local Java, Maven, Node or Docker for these browser steps. GitHub Actions is configured for the complete backend/frontend checks; inspect the actual run results. Configuration alone does not establish that a test passed.

## 3. Create the project and private database

1. Create a Northflank Cloud project named `earogyata` in an available Sandbox region.
2. Select **Create new → Addon → MySQL**. Name it `hospital-db`.
3. Explicitly choose **MySQL 8.4**, rather than the newest major version.
4. Enable **TLS** at creation. MySQL's TLS setting cannot be changed later.
5. Keep **public access disabled** and use one instance, without high availability.
6. Select a Sandbox-included resource size with **512 MiB memory** and the smallest included persistent disk. A starting target is 0.2 vCPU and 512 MiB for each of the database and app. Confirm the combined allocation fits the free allowance; do not choose a paid size to continue.
7. Create the addon and wait for it to become ready.
8. Under its connection details, locate the actual `HOST`, `PORT`, `DATABASE`, `USERNAME` and `PASSWORD`. Use the ordinary application account, not `_ADMIN` credentials.

The database must stay in the same Northflank project as the application. The browser never connects directly to MySQL.

## 4. Create the application service

Create a **Combined service** connected to the GitHub repository and intended branch.

| Setting | Value |
|---|---|
| Name | `earogyata-web` |
| Build method | Dockerfile |
| Dockerfile | `/Dockerfile` at repository root |
| Build context | `/` at repository root |
| Runtime command/entrypoint | Use image default |
| Instances | 1 |
| Resources | Sandbox-included 512 MiB size; confirm $0 payable |
| Port | 8080 |
| Port protocol/access | HTTP, public |
| Public domain | Use the generated HTTPS `code.run` address |

Use the root Dockerfile, which contains the React build. `backend/Dockerfile` alone does not include the website. No separate Vercel frontend is needed for this route.

## 5. Enter runtime variables

These belong in the service's **runtime environment**, not in build arguments or React `VITE_` variables.

| Key | Value |
|---|---|
| `PORT` | `8080` |
| `DB_URL` | `jdbc:mysql://HOST:PORT/DATABASE`, substituting the actual addon values; no query string and no embedded credentials |
| `DB_USER` | The addon's normal `USERNAME` |
| `DB_PASSWORD` | The addon's `PASSWORD`; keep secret |
| `JWT_SECRET` | Northflank expression `${fn.randomSecret(48)}`; evaluated and stored as a secret when saved |
| `COOKIE_SECURE` | `true` |
| `HOSPITAL_TIMEZONE` | `Asia/Kolkata` |
| `BOOTSTRAP_ADMIN_EMAIL` | Your chosen administrator sign-in email |
| `BOOTSTRAP_ADMIN_PASSWORD` | A unique password of at least 12 characters and no more than 72 UTF-8 bytes; keep secret |
| `SPRING_PROFILES_ACTIVE` | Leave unset for the normal configuration |
| `ALLOWED_ORIGINS` | Usually leave unset; the startup script derives exact HTTPS origins from Northflank's `NF_HOSTS`. For an override, enter the exact public HTTPS origin, without a trailing slash. |
| `DB_CA_PEM` | Usually leave unset for Northflank's publicly trusted database certificate. Set only when your actual provider supplies a custom CA. |

You can link `USERNAME` and `PASSWORD` through a runtime secret group with aliases `DB_USER` and `DB_PASSWORD`. Construct `DB_URL` from the actual host, port and database. Do not paste a complete provider JDBC URI containing its own query parameters; the startup script adds mandatory TLS verification and UTC settings.

The database connection always uses `sslMode=VERIFY_IDENTITY`. If certificate verification fails, check the addon's TLS setting, hostname and CA. Do not fix it by disabling TLS or certificate verification.

For a browser-only JWT alternative, generate a random secret locally in a trusted password manager and paste it as a secret value. Never use a tutorial example as the real key.

## 6. Configure health checks and deploy

Add an **HTTP startup check** on port `8080`, path `/actuator/health`. A practical initial setting is 5-second initial delay, 10-second interval, 5-second timeout and 30 failures allowed. Set readiness to the same endpoint. This gives the small instance time to connect, migrate the database and start Spring Boot.

Save the service and deploy. Inspect build and runtime logs. Flyway creates the schema; the bootstrap step creates the first administrator. Successful startup should show the service healthy and `/actuator/health` should return `{"status":"UP"}` at the actual public URL.

When deployment succeeds, copy the real generated HTTPS URL. No example URL in this document is a live deployment.

## 7. Prepare a useful portfolio demo

1. Sign in with the bootstrap administrator account.
2. Create a doctor, ward staff and synthetic patient using **People & access**. Publish a future OPD session.
3. Test the patient booking/check-in/cancel workflow and the assigned doctor's queue actions in separate browser sessions.
4. Test bed reservation, admission, discharge and marking the bed ready with the appropriate roles.
5. Remove **both** `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` from runtime variables and redeploy. The database preserves the administrator account. Do not remove just the password.

For a deliberately public synthetic demonstration, `SPRING_PROFILES_ACTIVE=demo` seeds the documented demo accounts and sessions at startup. Those credentials are public, including the administrator account, so anyone can change synthetic demo data. The normal setup above keeps staff passwords private. Existing demo sessions are not refreshed continuously; publish future sessions through the admin UI when needed.

## 8. Verify before adding a Live Demo link to your resume

- Open the HTTPS root URL and sign in. Confirm a reload restores the session and sign-out ends it.
- In browser developer tools, verify the refresh cookie is Secure, HttpOnly, SameSite=Strict and scoped to `/api/v1/auth`.
- Check the four roles and confirm staff actions are denied to a patient.
- Open two sessions and check queue/bed updates. The authenticated SSE stream has a heartbeat and reconnect logic; polling reconciles snapshots every 20 seconds. Keep one app replica for immediate local event fanout.
- Run `mvn -B verify` in `backend/` on a Docker-capable machine, or inspect the GitHub Actions backend run. This exercises real MySQL concurrency through Testcontainers.
- Run `npm ci`, `npm run build` and `npm test` in `frontend/`, or inspect the frontend CI run.
- Against an isolated synthetic deployment, run `python3 scripts/security-probe.py --api https://YOUR-ACTUAL-HOST/api/v1 --origin https://YOUR-ACTUAL-HOST`.

Only add the verified real URL to README and the resume. Backend integration, live cookie/SSE behavior and container startup were not runnable in the preparation environment.

## Troubleshooting

| Symptom | Check |
|---|---|
| Startup asks for origins | Expose public HTTP 8080 so `NF_HOSTS` exists, or set exact `ALLOWED_ORIGINS` |
| Startup rejects `DB_URL` | Use a bare `jdbc:mysql://HOST:PORT/DATABASE` address; no existing `?` options |
| Database connection/TLS fails | Addon ready, same project, actual hostname/credentials, TLS enabled, MySQL 8.4 |
| Restart loop before health check succeeds | Read runtime logs; check memory, database readiness and startup probe allowance |
| Refresh/login gets 403 | Use the same actual HTTPS host and exact origin allowlist; never wildcard it |
| Login works but no sessions appear | Admin must publish a future OPD session; demo seeding happens only on startup |
| Website is missing | Build the root Dockerfile, not the backend-only Dockerfile |
| Many visitors get rate-limited together | The current proxy setup can group anonymous requests by proxy IP; this portfolio limit needs trusted-proxy configuration before larger use |

## Why the previous free instructions were replaced

Aiven stopped accepting new free MySQL services on 6 November 2026 and says remaining free MySQL instances and backups will be deleted on 21 December 2026. The older Aiven route cannot meet this deployment's free requirement.

## Official references

- Pricing and current account requirements: https://northflank.com/pricing
- Dockerfile builds: https://northflank.com/docs/v1/application/build/build-with-a-dockerfile
- MySQL addon: https://northflank.com/docs/v1/application/databases-and-persistence/deploy-databases-on-northflank/deploy-mysql-on-northflank
- Database access and certificate trust: https://northflank.com/docs/v1/application/databases-and-persistence/access-a-database
- Environment, `NF_HOSTS` and random secrets: https://northflank.com/docs/v1/application/secure/inject-secrets
- Health checks: https://northflank.com/docs/v1/application/observe/configure-health-checks
- Aiven free MySQL retirement: https://aiven.io/changelog/507f8b1b-dd8f-4841-ac4a-940ac492b688
