# Deploy e-AROGYATA free: Render + Aiven

This route hosts the React website and Java API together on **one Render Free web service**, with a persistent **Aiven Free MySQL database**. You do not need Docker Desktop, Java, Node, Maven, or Python on your computer to follow the browser setup below.

The deployment configuration is prepared; **the app has not been published and has no live URL yet**. Account creation and deployment happen in your own accounts. Direct Render deployment from this chat is disabled by the workspace administrator.

## 1. Create the free database

1. Open <https://console.aiven.io/> and create an account.
2. Create a **MySQL** service and select the **Free** tier. Confirm the summary is free, rather than a paid plan covered temporarily by trial credits.
3. Wait until the service says **Running**.
4. On its **Overview / Connection information** or **Quick connect** page, find the host, port, database name, username and password. The default database is usually `defaultdb`, and the username is usually `avnadmin`. Use your actual displayed values.
5. Download the service's **CA certificate** (`ca.pem`). Open it in Notepad or another plain-text editor. Keep the entire text, including `-----BEGIN CERTIFICATE-----` and `-----END CERTIFICATE-----`, ready to paste into Render.

Keep the credentials private. They go into Render's environment fields, never into GitHub source code or this chat. You do not need to import SQL manually: Flyway creates the app's tables at first startup. Use a fresh database for this app.

## 2. Upload the project to GitHub

1. Extract the updated ZIP and open its `e-arogyata` folder.
2. Visit <https://github.com/new>. Create a repository called `e-arogyata` (private is fine).
3. Select **uploading an existing file**, or **Add file → Upload files** in an existing repository.
4. Drag the **contents inside** the extracted `e-arogyata` folder into the upload area. Include the dotfiles and `.github` folder. Do not upload the ZIP itself.
5. Commit the files to the default branch.

At the top level of GitHub you should see `Dockerfile`, `render.yaml`, `README.md`, `backend`, `frontend`, `deploy`, `docs`, and `scripts`. If everything is inside another `e-arogyata` folder, correct that before continuing. Do not include `.env`, downloaded database credentials, `node_modules`, or build output.

## 3. Create the free Render deployment

1. Open <https://dashboard.render.com/> and sign up or sign in.
2. Select **New → Blueprint** and connect the GitHub repository. Grant access to just this repository when possible.
3. Select the branch containing your upload. Render finds the root `render.yaml` automatically.
4. Name the Blueprint `e-arogyata`. Review the proposed service: it must be a **Free web service**. This file does not create a Render database, disk, worker or other paid service.
5. Enter the six prompted values below.

| Prompt | What to enter |
| --- | --- |
| `DB_URL` | `jdbc:mysql://YOUR_AIVEN_HOST:YOUR_AIVEN_PORT/defaultdb` — substitute the actual host, port and database. Start with `jdbc:mysql://`, and add **no question mark or connection options**. |
| `DB_USER` | Username shown by Aiven, usually `avnadmin`. |
| `DB_PASSWORD` | Password shown by Aiven. |
| `DB_CA_PEM` | Paste the complete downloaded CA certificate, including BEGIN/END lines and line breaks. |
| `BOOTSTRAP_ADMIN_EMAIL` | The email address you want to use for your first administrator account. |
| `BOOTSTRAP_ADMIN_PASSWORD` | A strong, unique password of at least 12 characters, no more than 72 UTF-8 bytes. Save it in your password manager. |

6. Select **Deploy Blueprint / Apply**. Allow several minutes for the first build. Render compiles the frontend and backend in the cloud.
7. Wait for **Live** and open the service's assigned `https://...onrender.com` URL. Use the URL Render actually shows; the name may have a suffix.

The Blueprint generates `JWT_SECRET` automatically. The startup script automatically reads Render's actual URL to configure the permitted browser origin. It installs Aiven's public CA in a temporary truststore and enforces `sslMode=VERIFY_IDENTITY`. Website requests and refresh cookies use the same origin.

Do not add `VITE_API_BASE_URL`, `ALLOWED_ORIGINS` or a Vercel rewrite for this route. `frontend/vercel.json` is only used by the older separate-frontend option. If you later add a custom domain, set `ALLOWED_ORIGINS` explicitly to that exact HTTPS origin (no trailing slash), or comma-separated exact origins if retaining the original URL too.

If you use **New → Web Service** instead of Blueprint, choose **Docker**, leave Root Directory blank, set Dockerfile Path to `./Dockerfile`, choose **Free**, and set Health Check Path to `/actuator/health`. Add the same six values plus a freshly generated `JWT_SECRET` of at least 32 random characters using Render's Generate feature. The Blueprint path is simpler.

## 4. Sign in and verify

1. Open `https://YOUR_ACTUAL_RENDER_HOST/actuator/health`. It should return `{"status":"UP"}`.
2. Open the root website. Sign in using the administrator email and password you supplied.
3. Create a doctor and ward staff account from the administrator's user management view, and publish a future OPD session.
4. In another browser/private window, create a synthetic patient account and check that sessions and beds load.
5. Reload after signing in and confirm you remain signed in. Test logout too.
6. After confirming administrator access, go to **Render service → Environment** and remove **both** `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`; save and redeploy. The administrator account stays in MySQL. Removing only the password while leaving the email would make the next startup fail.

The default setup starts without public demo credentials and without pre-created patient/staff accounts. Wards and beds are seeded by migrations. Use invented patient information for this portfolio deployment.

## 5. Common errors

| Symptom | What to check |
| --- | --- |
| Blueprint cannot find `render.yaml` | The file must be at the GitHub repository root on the selected branch. |
| Dockerfile missing | Use the **root** `Dockerfile`, not `backend/Dockerfile` or `frontend/Dockerfile`, for this one-URL deployment. |
| `DB_URL must start with jdbc:mysql://` | Build a JDBC URL from Aiven's displayed host/port/database; do not paste a `mysql://user:password@...` service URI. |
| `DB_URL must have no query string` | Remove everything beginning with `?`. The startup script adds verified TLS and timezone settings. |
| `DB_CA_PEM is not a valid certificate` | Paste the certificate file contents, not its filename or download link. Preserve BEGIN/END lines and line breaks. |
| `PKIX`, TLS or hostname verification failure | Confirm host, port and certificate all come from the **same** Aiven service. Re-download the current CA if it changed. Keep certificate verification enabled. |
| `Access denied for user` | Re-copy Aiven's current database username and password into Render. |
| `Communications link failure` / DB timeout | Check Aiven is Running and host/port are correct. If you enabled IP restrictions, allow the Render region's outbound ranges shown in its dashboard. |
| `Unsupported Database` from Flyway | Check the actual MySQL version in Aiven against the Flyway version resolved by Maven. The app's original integration suite targets MySQL 8.4. Share the error text without credentials for a precise fix. |
| Bootstrap password error after cleanup | Remove **both** bootstrap fields; leaving the email still enables the password check. |
| Login or refresh returns `403` | For the default Render URL, remove a stale custom `ALLOWED_ORIGINS` value so it is derived automatically. For a custom domain, set its exact HTTPS origin and redeploy. |
| Blank page or `/` returns `401` | Confirm you deployed this updated project using its root Dockerfile, which includes public static-file access. Check build logs and browser console. |
| `429 Too Many Requests` | Wait a minute and retry. Rate limiting stays enabled. |
| Service wakes slowly | Render Free sleeps after 15 minutes without incoming traffic. Allow about a minute for wake-up and retry. |
| Out of memory / exit `137` | These settings cap Java heap at 224 MiB and reduce threads/connections for the 512 MB free instance. Check logs and traffic; memory use has not been measured on a live Render instance. |

For build failures, open **Render → service → Logs / Events**, copy the first error and the following lines, and remove any secrets before sharing. A successful build and passing health check still need the browser login checks above.

## Cost and limits

Checked against official documentation on **21 October 2026**:

- Render Free provides 512 MB RAM and a small CPU allocation. It sleeps after 15 idle minutes, normally taking about one minute to wake. Its 750 free instance hours are shared across your workspace each calendar month. Build minutes and bandwidth have limits too.
- On Render, keep the service on **Free** and avoid adding a payment method if you want usage exhaustion to suspend free services or builds rather than incur usage charges. Check the final billing summary before creation. Account verification requirements may vary.
- Aiven Free MySQL currently provides 1 GB storage, 1 GB RAM and backups, with no card required or scheduled expiry. It may be powered off after inactivity; you can restart it from Aiven. There is no uptime SLA.
- The app's database stays in Aiven across Render deploys or sleep. Files written inside the Render container do not persist.
- This is a portfolio/demo deployment with synthetic data. Free tiers have availability and capacity limits; no real hospital operating capacity is claimed.

Provider terms can change. Official references:

- <https://render.com/docs/free>
- <https://render.com/docs/compute-plans>
- <https://render.com/docs/docker>
- <https://render.com/docs/blueprint-spec>
- <https://render.com/docs/environment-variables>
- <https://aiven.io/docs/products/mysql/concepts/mysql-free-tier>
- <https://aiven.io/docs/products/mysql/get-started>
- <https://aiven.io/docs/platform/concepts/tls-ssl-certificates>
- <https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html>

## What was checked before packaging

The deployment preparation adds a root multi-stage Dockerfile, free-only Render Blueprint, certificate-verifying startup script, public GET access for the built UI, and a self-only browser content security policy. It also adds focused security-chain tests.

This workspace blocks npm and Maven artifact downloads with HTTP 403, and has no Docker or Maven executable. A full frontend/backend/container build and live deployment **could not be run here**. Java syntax checks, project structural checks, and real-certificate startup-script checks are recorded in `results/free-deployment-checks.txt`. The Dockerfile runs the new database-free security-chain tests and existing workflow unit tests when Render builds it. The complete MySQL integration suite remains in the existing GitHub Actions workflow.
