# Verification ledger

This document distinguishes executed checks from implementation awaiting an execution environment. No backend benchmark, database race-test pass, browser comparison, deployment, or recording is claimed.

## Executed in this build environment

- Installed the exact locked frontend dependency set from an existing offline package cache.
- Ran strict TypeScript checking and `vite build`; production JS and CSS bundles were generated.
- Ran 13 Node tests: refresh coordination, refresh failure, operation URL/body, server conflicts, no-content logout, fragmented SSE handling, patient/ward/doctor controls, empty states and data-derived counts. These use mocked HTTP responses or server rendering, not a live backend/browser.
- Parsed Java source with the installed JDK compiler module in syntax-only mode. External Spring/Testcontainers symbols were not resolved.
- Compiled and ran WorkflowRules and a standalone smoke harness: 9 assertions passed. No JDBC/Spring behavior was exercised.
- Checked OpenAPI references/operation IDs/path parameters and served-copy equality, migration invariant names, POM XML and JSON syntax. YAML was parsed locally. These are structural checks, not an OpenAPI conformance validator or MySQL SQL execution.
- Ran the queue-arrival simulation. Its seed, assumptions and numerical output are saved in `results/queue-simulation.json` and explicitly labelled simulated.

## Not executed here

Maven is absent; external Maven downloads return HTTP 403 under the current network rules. Docker, MySQL and k6 executables are absent. Playwright's package is present, but the Chromium executable is absent. Vercel/GitHub/backend-host integrations are disabled by the workspace admin. These constraints prevent full compilation, Testcontainers, active API probes, container startup, external dependency scans, real API load numbers, browser UI checks, recording and publishing in this session.

## Reproduce the checks that do not need Spring dependencies

```bash
python3 scripts/check-project.py
java -m jdk.compiler/com.sun.tools.javac.Main -d /tmp/earogyata-qa scripts/ParseJava.java scripts/WorkflowSmoke.java backend/src/main/java/com/earogyata/core/WorkflowRules.java
java -cp /tmp/earogyata-qa ParseJava backend/src
java -cp /tmp/earogyata-qa WorkflowSmoke
```

On a normal JDK installation, `javac` may replace the compiler-module command. Full required checks are `mvn verify`, `npm ci && npm run build && npm test`, Compose startup, the active probe, security workflows, real k6 workload and browser walkthrough. Their commands are in README. CI is wired to run them where appropriate; it has not been pushed or observed passing.
