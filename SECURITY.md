# Security

This is a synthetic-data portfolio implementation, not a certified clinical system. The code includes defensive controls and regression tests; the Spring/MySQL and active HTTP tests still need to execute in CI or a suitable local environment. No penetration-test pass or compliance claim is made.

## Implemented boundaries

Public signup accepts only name, email and password and creates PATIENT. Unknown JSON properties (including role) fail validation. Only ADMIN provisions staff; doctors need specialty and room. BCrypt cost 12 hashes passwords; UTF-8 password input is capped at 72 bytes, with a minimum of 12 characters for account creation. Login failures use generic responses and a timing-padding hash for nonexistent accounts. There is no password-reset, email verification or MFA workflow in this scope.

Spring Security validates HS256 signature, issuer, audience and expiry. Every authenticated request looks up the live role, enabled account and non-revoked auth session. Access tokens expire after 15 minutes and are kept in browser memory. Refresh tokens use 256 bits of cryptographic randomness, SHA-256 storage hashes, absolute seven-day session expiry and single-use rotation. Replay revokes the session in a transaction that commits before returning 401. Logout revokes access immediately. A Secure HttpOnly SameSite=Strict cookie carries refresh tokens, and refresh requires an exact allowlisted Origin. Same-origin proxying supports Vercel. Cross-tab refresh uses Web Locks and BroadcastChannel; browsers without Web Locks have only per-tab refresh coordination and may require re-login after a simultaneous refresh.

Service-layer ownership checks cover patient appointments/reservations and assigned-doctor queues. Bed availability carries no identity; WARD_STAFF/ADMIN can read reservation identities. DTOs, JSON rejection of unknown properties, parameterized JDBC, and a 16 KiB body limit (including unknown-length/chunked input) constrain writes. React escapes text; there is no raw HTML rendering. Frontend CSP blocks external scripts, framing and object content; no remote fonts or images are required. Backend errors omit SQL messages, passwords and stack traces.

Transactional row locks and generated unique indexes enforce active slot, active bed, patient and consultation invariants. The API returns conflicts without claiming success. Audit rows record successful sensitive changes, login failures, refresh replay, staff reads of identified data and handled denials. Tokens, passwords, request bodies and clinical notes are absent from audit payloads. Application endpoints cannot alter the audit trail. Database administrators remain trusted and may modify records; this is not a cryptographically tamper-evident log.

## Rate limits and resource bounds

MySQL fixed windows provide shared per-minute limits: login 8 per canonical email, registration 3 per email, anonymous API traffic 60 per observed peer, authenticated traffic 300 per user. Hashed bucket keys expire after a short retention window and are cleaned every minute. Retry-After is returned for handled 429s. Forwarded IP headers are ignored; a proxy may aggregate peer addresses. JWT verification precedes this application's per-user limiter, so production ingress also needs a request/IP limit and body/read timeout. SSE is capped at four streams per user and 256 total, expires with access tokens, heartbeats every 15 seconds and checks session revocation. Snapshot reconciliation runs every 20 seconds.

## Security work and proof

| Finding in supplied code / review | Change | Regression evidence |
|---|---|---|
| Plain-text passwords and default JWT key | BCrypt and required random environment secret | Auth integration tests; configuration fails on short key |
| Signup accepted caller's role | Patient-only registration; admin-only provisioning | Public role-escalation test + active probe |
| Booking and queue writes were unauthenticated | Security filter and service ownership checks | Missing-token, owner and assigned-doctor tests |
| JSON-file writes could lose updates / duplicate bookings | InnoDB transactions, locks and active unique indexes | Bed/slot parallel Testcontainers tests |
| Token rotation could roll back replay revocation | Commit the revocation, then return 401 outside the transaction | Replayed token invalidates newly issued access test |
| Cookie refresh could be driven cross-site | Exact Origin allowlist and Strict HttpOnly cookie | Missing/cross-site Origin tests and probe |
| A modal conflict could be hidden behind its backdrop | Dialog displays action errors from workspace feedback | Frontend error propagation/render checks |
| OPD wait time had an unexplained 0.65 multiplier | Count-based estimate; separate labelled simulation | Simulation source/results; no clinical improvement claim |

Frontend tests and static source checks were run in this environment. The backend regression tests and live probes are provided but were not run because Maven downloads, Docker/MySQL and browser execution were unavailable. The CI pipeline starts the real stack and runs the active probe. `python3 scripts/security-probe.py` targets only your isolated deployment and writes the actual observations to `results/security-probe.json`; it does not synthesize a pass. See [verification ledger](docs/VERIFICATION.md).

CodeQL scans Java and TypeScript. OWASP dependency-check fails at CVSS 7; npm audit fails at high severity. These workflows are configured, not already executed. Review generated reports and fix findings before calling the project deployment-ready. NVD updates need network access and may benefit from NVD_API_KEY. Dependabot proposes dependency and action updates weekly.

## Residual scope

No real patient data, multi-hospital tenancy, insurance, payments, clinical triage, password recovery, MFA or comprehensive operational resilience is implemented. Queue events are process-local; multiple API replicas need an outbox/shared broker for immediate cross-instance fanout. Retention/deletion of historical bookings and audit data is an operator policy, not automated. Audit exports are bounded. Managed TLS, database grants, backup restoration, secrets management and ingress limits must be configured on the actual host.

Report suspected issues privately to the repository owner's chosen security contact before publishing exploit details. No monitored security mailbox is invented for this project.
