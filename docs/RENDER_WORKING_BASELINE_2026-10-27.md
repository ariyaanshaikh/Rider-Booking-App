# Working Render baseline — 2026-10-27

This record preserves the identified working version before production preparation. It is **not a full infrastructure/database backup**. No deploy, restart, environment-variable update, branch change or plan change was performed.

## Confirmed working deployment

- Workspace: Ariyaan's workspace (`tea-d9ktchm1egvs738b2tt0`), explicitly confirmed by the owner.
- Service: Rider-Booking-App (`srv-d9l3u8bl550s73fhv8f0`).
- Dashboard: https://dashboard.render.com/web/srv-d9l3u8bl550s73fhv8f0
- API: https://rider-booking-app.onrender.com
- Live deployment: `dep-dbe9evfb9u1s738tpol0`, status `live`.
- Source commit: `c7c43a7e31fc508db15543b6024f8c52de5502d3` (Secure rider self-profile creation).
- Deployment completed: 2026-10-24 11:37:03 UTC (17:07:03 India time).
- Repository: https://github.com/ariyaanshaikh/Rider-Booking-App
- Linked branch: `main`; auto-deploy on commit is enabled. Do not push/merge into main during staging preparation.
- Runtime: Docker; context `.`; Dockerfile `./Dockerfile`; no Docker command override.
- Region: Oregon. One instance on the free compute plan; not suspended.
- Render HTTP health-check path: empty (the service is not configured to use `/health` for its platform health checks).
- An independent GET `/health` returned HTTP success and `{"status":"UP"}` during this inspection.

## Source recovery copies created

- GitHub annotated tag: [`render-working-2026-10-27`](https://github.com/ariyaanshaikh/Rider-Booking-App/tree/render-working-2026-10-27), pointing to the exact live source commit above. Pushing this tag did not change the linked main branch.
- Local self-contained Git bundle: `.git/render-backups/render-working-2026-10-27.bundle` in the backend checkout. `git bundle verify` confirmed it contains complete history for the backup tag.
- Bundle SHA-256: `587abaf02efe751075329a00475710b335b673694053d1cfb2aa9ddc20cfbe64`.
- The bundle contains Git-tracked source/history, not ignored local secrets, a running container image, database contents or deployed environment values. Treat history archives as private repository data. The local bundle is not an off-device backup; the GitHub tag is the additional remote source reference.

## Recovery safeguards and outstanding backup work

Render's current rollback documentation says retained build artifacts can be reused, but retention is limited and no artifact was exported or pinned by this task. The current live deploy is recorded, not guaranteed indefinitely available. A source tag permits identifying/rebuilding that code, but rebuilding later may resolve different external dependencies/base images. It is not an exact container snapshot.

Render rollback reuses the target deploy's build artifact, start command, environment values and certain other settings, subject to documented environment-group behavior. It does not roll back database contents/migrations, disks or the compute plan. Confirm artifact availability and database compatibility before any rollback. No rollback has been triggered.

The confirmed workspace lists **no Render-managed PostgreSQL instances**. The actual application database provider/host and a secure connection or provider backup route must be identified before a consistent database export and isolated restoration rehearsal can be completed. A healthy API is not evidence that a database backup exists.

The current connection exposes service/deployment metadata but no environment-variable read/export capability. No complete secret configuration export has been created. Preserve/export the existing effective variables securely through the owner-authorized Dashboard/provider workflow before replacing them; never put values into this document, Git or chat.

**Do not change the working instance, run production migrations or switch it to production-changes until database recovery and the existing secret configuration are secured.** Continue staging work separately after the owner confirms the database and any additional hosting costs.

References verified during inspection:

- [Render rollbacks](https://render.com/docs/rollbacks)
- [Render PostgreSQL backups](https://render.com/docs/postgresql-backups)
- [Render deployment behavior](https://render.com/docs/deploys)
