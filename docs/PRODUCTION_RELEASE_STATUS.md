# Non-payment release status

The production code and automated regression checks are complete for recovery, durable push and shared messaging. This is separate from configuring and verifying live infrastructure. Razorpay configuration and physical-device testing are deliberately deferred by the owner.

## Confirmed now

- Public API `/health` returned `{"status":"UP"}` on 2026-10-27. This proves database reachability, not which commit or migration is deployed.
- Existing branches contain the tested implementation: backend `production-changes`; both mobile apps `ariyaan-changes`.
- Render template now lists JWT, Maps, public API URL, SMTP, push and shared-messaging settings. It does not contain secret values or change live services by itself.
- Four configuration-preflight tests verify missing/unsafe settings are detected without printing credentials. Razorpay keys are intentionally not required; payments must remain disabled until separately configured.
- The frontend example files no longer suggest enabling an obsolete JWT-login bypass. Actual password login remains unchanged.

## Requires connected accounts / owner decisions

- Connect the actual existing Render service before editing anything. Match its repository, branch, service ID and public URL; do not create a duplicate from the template merely because its example name differs.
- Read live variable presence securely, preserve existing working JWT/database/SMTP values and public URLs, and configure missing non-payment settings. Do not rotate JWT keys implicitly: that would sign out users. Never paste credentials into chat or commit them.
- The template still selects `free` for testing. Render free web services can sleep and are not suitable for production workers. An always-on plan needs an owner-approved cost/plan choice; no paid resources are created by this change.
- Link the two existing Expo/EAS projects and verify their package/bundle IDs, FCM/APNs credentials, restricted Maps keys and build environment. Having credentials is not the same as confirming these branches use them. Native builds may require account authorization and build credits.
- The owner confirmed Neon project Rider Booking APP, branch render-test, as the live database. A no-compute recovery branch is saved and verified ready; see [working recovery baseline](RENDER_WORKING_BASELINE_2026-10-27.md). An independent export, restore rehearsal, retention policy and staged migration verification are still outstanding. Local migration tests are not a production restore rehearsal.
- Configure alert delivery (recipient/channel), uptime/readiness checks, worker FAILED/queue-age alerts, database resource/storage alerts and a backup retention policy in the chosen provider. No external monitoring subscription or recurring task has been created.
- Load-test a staging deployment with a known target concurrency/traffic level, not the live ride API. Shared PostgreSQL fanout is implemented, but does not prove a particular production capacity. Configure affinity if multiple nodes permit SockJS HTTP-polling fallback.
- Apply migrations and deploy the verified commit only after backup/staging checks, preserving existing functionality. Send a real reset email to an owner-approved test account. Do not send mail to arbitrary customer accounts as a health probe.

## Secret-safe configuration check

With Node 22 and a securely stored environment file (not committed), run:

```sh
node --env-file=/absolute/secure/backend.env scripts/production-preflight.mjs backend
node --env-file=/absolute/secure/admin.env scripts/production-preflight.mjs admin
node --env-file=/absolute/secure/rider.env scripts/production-preflight.mjs rider
```

Only variable names and pass/fail are printed. A pass does not validate SMTP/Maps/Expo credentials, prove external delivery, check the live hosting plan, or replace staging/provider testing. The mobile preflight expects `EXPO_PUBLIC_EAS_PROJECT_ID`; if a project ID is stored in `extra.eas.projectId` instead, supply that same public value to the checker.

The complete implementation/setup reference remains [Recovery and notifications](RECOVERY_AND_NOTIFICATIONS.md). Do not mark this release production-ready until the connected-account work is confirmed, even if all code tests pass.
