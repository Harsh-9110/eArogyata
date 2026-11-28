# Threat model

## Assets and trust boundaries

Protect account credentials, refresh/session state, patient identity in bookings, active occupancy/queue integrity and audit history. The browser and all submitted IDs are untrusted. The API is the authorization boundary; MySQL is authoritative for concurrency and revocation. The Vercel proxy/API TLS link and database connection form deployment boundaries. Database operators and deployment-secret administrators are trusted. This is one hospital; there is no tenant-selection claim.

| Threat | Concrete attempt | Control and test |
|---|---|---|
| Spoofing | Edit JWT role/subject or use a logged-out token | Signature/issuer/audience/expiry verification plus live DB session check; auth tests |
| Privilege escalation | Register role ADMIN or invoke staff creation as patient | Reject unknown fields, fixed PATIENT registration, ADMIN method authorization; auth tests/probe |
| IDOR | Cancel another patient's visit or operate another doctor's queue | Service ownership/assignment checks; queue/bed integration tests |
| Race / tampering | Submit parallel reservations for one bed or slot | Row locks and generated unique indexes; separate MySQL connections and barriers |
| Refresh theft/reuse | Reuse an old refresh token after rotation | Hashed single-use tokens; replay revokes the entire session and commits; test/probe |
| CSRF | Cross-origin refresh using an ambient cookie | Strict cookie, exact Origin check; test/probe |
| Injection / XSS | SQL fragments in email or markup in a name | JDBC binding, input constraints, React escaping, CSP; input regression/probe and static review |
| Information disclosure | Request global bed inventory to learn patients | No patient fields in bed DTO; reservations guarded; view and API tests |
| Repudiation | Discharge or read identified queue without evidence | Transactional audit of changes, staff-read audit and denial audit; no mutation API for audit |
| Credential abuse / DoS | Guess passwords or open unlimited streams | Shared DB limiter, BCrypt, limits on request bodies, slots, result sizes and SSE connections; ingress limits required |
| Supply chain | Vulnerable Maven/npm artifact or action | Dependency-check, npm audit, CodeQL and Dependabot; inspect actual reports after execution |

## Attack plan

`security-probe.py` actively attempts privilege escalation, unauthenticated access, invalid JWT use, invalid input, CSRF, refresh replay, logged-out access and login brute-force. It creates only synthetic accounts and logs only verdict/status metadata. Run it after the real stack starts. The Testcontainers suite adds ownership and concurrency attacks that need a real transactional database. Frontend unit tests do not prove server authorization.

## Accepted limitations

There is no clinical prioritization or emergency queue, no identity proofing/email verification, no MFA, no account recovery and no external audit sink. SSE invalidations may reveal that a workflow changed, but contain neither patient data nor resource IDs. Exact wait-time guarantees are impossible from a configurable mean; the UI labels estimates. Production ingress must rate-limit invalid-token traffic before verification. A backend pod/process has a finite stream cap, and large deployments need a broker and outbox. No active security verdict is claimed for this build environment.
