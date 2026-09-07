# Android release engineering audit — 2026-09-07

Baseline: `1f36139` on `vaibhavnaik2/ClosetAI`. Changes are isolated to the Android branch. Two database fixes have been deployed and re-tested: duplicate account initialization and server-controlled rate limits. Edge Functions have not been changed.

## macOS preservation

The latest successful RC3 Actions run is https://github.com/vaibhavnaik2/ClosetAI/actions/runs/34076805288 at commit `6509255b6a1db062f9fba32479a1bca93a61b7fe`.
The embedded RC3 ZIP has been independently reconstructed, ZIP integrity checked, and SHA-256 verified as `d2fc651cd62ff000a398889d4a9b3defcdd387f2f9d9bd2c61a2841c6f75da27`.
The `.release` directory and existing macOS workflows are unchanged. The existing build reconstructs the archive and applies overrides before building each architecture. Reproducible source does not imply byte-identical signed DMGs on different runner images. Original release remains ad-hoc signed, not Developer ID notarized.

## Android changes

- Added the missing launcher activity and warm-intent handling.
- Added a checksummed Gradle wrapper and independent build/test/lint workflow.
- Bounded input streams and image dimensions before bitmap allocation; sampled image decoding; bounded folder traversal off the UI thread.
- Cancellable HTTP calls, bounded response reads, and generic errors that do not expose backend response bodies.
- Serialized refresh token exchange; cancellation propagation; account-scoped state cleanup; realtime token payload and structured event matching.
- Visible authentication errors; scrollable login; system theme support; responsive grid density.
- Added cloud-backed saved looks, collections and membership, packing membership and packed state, wear logging/history, and saved filter presets.
- Paginated wardrobe and utility reads. Empty preference updates fail instead of claiming a save.

## Live backend findings requiring release gates

The connected project is healthy. Supabase security advisor returned no notices; this is not a complete security certification. All public tables inspected have RLS enabled.

1. **Rate-limit bypass fixed:** counter writes are revoked for client roles. The compatible invoker RPC calls a private, narrowly granted definer function with fixed quotas and window. Live rollback-only regression tests confirm limits cannot be raised and counters cannot be reset/deleted. Unknown buckets are rejected. Parallel-load stress testing remains outstanding.
2. **Incomplete export:** deployed `account-export` silently skips query failures, uses non-paginated reads, and omits collection/packing membership and import assets. Export must fail on unexpected errors and include all relevant rows with stable pagination.
3. **Deletion errors:** deployed `delete-account` ignores storage listing/removal errors. It must stop and report failure, revoke sessions, and verify complete storage/account removal. Test with a disposable account, including storage failures and concurrent uploads.
4. **Link ingestion:** URL import reads the whole response before checking actual byte size. DNS checks have gaps (including IPv6 literals and DNS rebinding); MIME/signatures alone do not sanitize metadata. Require bounded streaming and an egress policy enforcing the resolved destination at connection time.
5. **Authentication:** duplicate account-creation triggers were discovered by live fixture testing and fixed with idempotent initialization. Post-deployment tests confirm one profile/preferences/entitlement record and preserved terms metadata. Password recovery currently requests an email but has no complete in-app recovery/password update route. Terms/privacy text needs actual approved policy documents and links. Production OAuth credentials and redirect configuration need verification.
6. **Feature completeness:** utility screens support initial creation and membership but do not yet include edit/delete flows, outfit scheduling, full filtering parity, offline retry queues, and all macOS customization options. Automatic analysis/privacy preferences need server enforcement, not only saved settings.
7. **Realtime:** needs reconnect/backoff and lifecycle tests, token expiration tests, and cross-account concurrency tests.
8. **AI:** deployed functions use unpinned major Supabase imports; verify configured model availability and real image/stylist responses. Uploaded hashes are trusted when syntactically valid. Validate bytes server-side, enforce entitlements, and test prompt injection and unavailable garment exclusion.
9. **Authorization relationships:** test that wear/outfit feedback/plan references cannot reference another user's objects, beyond simple `user_id` ownership.

`backend/reference/` contains retrieved function source for review and reproducibility work. It is a snapshot, not a new deployment, and must not be deployed as a claimed hardened backend.

## Distribution gates

The debug APK uses a development certificate. The release AAB is unsigned until an owner-controlled upload key is configured. Never commit signing keys, passwords, service role keys, OAuth secrets, or user sessions. Production readiness additionally requires physical-device/emulator end-to-end tests, store signing and policy review, and the backend fixes above.

## Verified build evidence

Seven local Robolectric/API tests, debug/release builds and lint pass with API 36 and Android Gradle Plugin 8.10.1. API 35 CI runs `34093094932` and `34093836338` passed; CI emulator launch/restart at commit `fe2256a0` also passed in runs `34097311081` and `34097306828`. Latest API 36 CI results will be recorded after the final run. Debug APK signature verification and AAB bundletool validation pass.

Android 16 target is required for new phone app submissions since August 31, 2026: https://developer.android.com/google/play/requirements/target-sdk .

Migrations `20260907080639_fix_duplicate_account_initialization` and `20260907080654_harden_rate_limit_counters` match the live migration history. Their assertions were tested before deployment in a rolled-back transaction, then repeated against the deployed functions. Security advisors returned zero notices after deployment.
