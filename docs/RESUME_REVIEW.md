# Resume wording review

Reviewed against the uploaded source on 28 November 2026.

## Suggested entry

**e-AROGYATA | Spring Boot, React, TypeScript, MySQL | GitHub**  
**May–Jul 2024 · Enhanced Nov 2026** (use actual enhancement dates)

- Led a six-member team to build a hospital management platform for SIH 2024.
- Implemented OPD booking and bed reservation workflows using Spring Boot and MySQL, with transactional row locks and unique constraints enforcing one active bed reservation per patient.
- Implemented access controls for four roles using Spring Security, JWT authentication, BCrypt, and rotating refresh tokens with replay detection.
- Added 13 automated frontend tests covering authentication refresh, role-based views, API error handling, and SSE event parsing.

Keep the team size, leadership and SIH association only if they describe your actual contribution. Code cannot verify those historical facts. The README describes a rebuilt portfolio project; recent improvements should not appear to have all been implemented in 2024.

Add **Live Demo** only after a real deployment is running and its core flows have been checked. Do not claim backend concurrency tests passed, a production deployment, real hospital usage, or measured business improvements without evidence.

## Evidence and limits

| Claim | Code evidence |
|---|---|
| Locks for bed reservation | `backend/src/main/java/com/earogyata/beds/BedService.java`: transactions and `FOR UPDATE` |
| One active bed per patient | `backend/src/main/resources/db/migration/V3__beds.sql`: generated keys and unique constraints |
| Four roles | `V1__identity_and_audit.sql`: PATIENT, DOCTOR, WARD_STAFF, ADMIN |
| BCrypt/JWT/Spring Security | `backend/src/main/java/com/earogyata/auth/SecurityConfig.java` |
| Refresh rotation and replay handling | `backend/src/main/java/com/earogyata/auth/AuthService.java` |
| 13 frontend tests | `frontend/tests/`: 5 client tests, 1 SSE test, 7 view tests |

The uploaded `results/frontend-tests.txt` records 13 passes and zero failures. These tests use mocked HTTP or server rendering, not an actual browser or a live Spring Boot/MySQL service. A fresh `npm ci` attempt in this review failed with HTTP 403 downloading Vite, so the 13 passes are historical evidence from the uploaded archive, not a new run.

Maven, Docker and MySQL were unavailable during this review; external Maven bootstrap downloads also returned HTTP 403. The dependency-free workflow harness passed 9 assertions and Java syntax parsing passed, but those checks do not verify Spring compilation or SQL concurrency. Run the complete backend CI suite before describing the locking behavior as integration-tested.
