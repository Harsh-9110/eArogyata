# Two-minute demo

No recording was produced in the build environment: Playwright is present there, but its browser executable is missing and downloads are restricted. The script below records the actual application after it is running; it does not render a mock or fabricate a demo.

Start a fresh local demo with Docker Compose, then:

```bash
cd qa
npm install
npx playwright install chromium
npm run record
```

The recorder creates unique synthetic patient and doctor accounts through the real API, publishes a session for today, then drives one browser through admin, patient, doctor and ward-staff workflows. It records a roughly two-minute silent 1440 × 960 WebM in `qa/output/`. It requires an available bed, the default local demo accounts, and at least 40 minutes before hospital midnight. `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `WARD_EMAIL`, `WARD_PASSWORD`, `API_BASE`, `WEB_ORIGIN`, and `HOSPITAL_TIMEZONE` may override the local defaults. Use only an isolated deployment; histories and synthetic accounts are retained.

Optional MP4 conversion, after a real recording exists:

```bash
ffmpeg -i qa/output/ACTUAL_RECORDING.webm -c:v libx264 -pix_fmt yuv420p -an e-arogyata-demo.mp4
```

| Time | Show | Say |
|---|---|---|
| 0:00–0:15 | Overview, current counters, People & access | “This is a focused hospital workspace for OPD and beds, with four server-enforced roles.” |
| 0:15–0:45 | Patient books a slot, checks in, sees ticket, reserves a bed | “A slot has one owner. Check-in joins the live FIFO queue; estimates are labelled and may change.” |
| 0:45–1:10 | Assigned doctor calls and completes the patient | “The doctor can operate only their own queue. SSE invalidations refresh authorized snapshots.” |
| 1:10–1:35 | Ward staff admits, discharges, marks bed ready | “Discharge moves the bed to cleaning. A row lock and database unique keys prevent concurrent occupancy.” |
| 1:35–2:00 | Admin audit trail; show test report separately if available | “Sensitive actions are audited. Parallel MySQL tests check the booking invariant. Performance claims come only from recorded k6 output; wait-time comparisons are simulated.” |

Manual browser checklist: test desktop and 390px mobile layout; keyboard access and dialog focus; all four roles; refresh and logout; two browser tabs; book and cancel; check in and complete; admit, discharge and mark ready; SSE reconnect after restarting API; clear errors on a 409 conflict; verify refresh-cookie Secure/HttpOnly/SameSite flags on the HTTPS deployment.
