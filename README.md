# OccasioNet — Algerian Second-Hand Marketplace (Dual Android Apps + Supabase)

**OccasioNet** is a dual-application Android project written in **Kotlin** and **Jetpack Compose (Material 3)** backed by a single **Supabase** project with strict **Row Level Security (RLS)**, atomic PostgreSQL RPC transactions, and a manual **500 DZD per-listing payment verification workflow** (CCP & BaridiMob).

## Project Structure

- `userApp/` (`dz.ocasionet.user`): Standalone Android application for buyers, sellers, and visitors (`OccasioNet-User-debug.apk`).
- `adminApp/` (`dz.ocasionet.admin`): Standalone Android application for verified administrators (`OccasioNet-Admin-debug.apk`).
- `core/` (`dz.ocasionet.core`): Shared non-sensitive library containing models, the official 58 Algerian Wilayas & Communes catalog, Supabase REST/Auth/Storage/RPC client, and RTL Material 3 theme.
- `supabase/migrations/`: Idempotent PostgreSQL schema, functions, RLS policies, Storage buckets, and Algerian reference data.
- `supabase/functions/verify-admin-action/`: Supabase Edge Function for server-side admin verification and short-lived signed receipt URLs.
- `.github/workflows/android-build.yml`: Automated CI pipeline building both APKs and running the 15 security & unit test scenarios.

For full Arabic documentation and the 20-step deployment guide, see [README_AR.md](./README_AR.md) and the `docs/` directory:
- [docs/SECURITY.md](./docs/SECURITY.md)
- [docs/DATABASE.md](./docs/DATABASE.md)
- [docs/DEPLOYMENT.md](./docs/DEPLOYMENT.md)
- [docs/TESTING.md](./docs/TESTING.md)
