# ClosetAI Android release engineering — 2026-09-07

Baseline: `1f36139` in `vaibhavnaik2/ClosetAI`. Draft PR: https://github.com/vaibhavnaik2/ClosetAI/pull/1 . Android builds are development release candidates, not store-signed commercial releases.

## Verified Android packages

Android source commit: `c59d09936cdf52c90c494d9335354dda44d517ad`.
GitHub CI run: https://github.com/vaibhavnaik2/ClosetAI/actions/runs/34099141729 . Build/test/lint and Android 16 emulator installation, launch, sign-in screen detection, restart and crash-buffer checks passed. Seven local Robolectric/API tests also passed. These are smoke/regression tests, not complete end-to-end UI coverage.

Downloaded CI artifact archive SHA-256 independently verified:
`78ff344b838d50429352c578dc35c96f2c953ab479902f18781cf30859937147`.

- Debug APK: `4d361bcd00dcaa8b03a81c4193abe019d08406df08774910b9f1993e5ff552c8`
- Unsigned release AAB: `8dc0c0a7677e9ce530d72702e39a24b1d85809060deebf843701ba9be4465e40`

Both package checksums match the CI manifest. APK signature, ZIP integrity, 16 KB ZIP alignment and bundletool AAB validation pass. A ZIP-alignment check alone does not establish native-library ELF compatibility on every device.

Android targets API 36 using AGP 8.10.1, Gradle 8.11.1 and JDK 17. Google Play's current requirement: https://developer.android.com/google/play/requirements/target-sdk . The Gradle distribution checksum is pinned. No release signing key has been generated or committed.

## Android improvements

- Missing launcher activity and warm-intent handling added.
- Checksummed Gradle wrapper and independent build/test/lint/device workflow.
- Bounded image streams and dimensions before bitmap allocation; sampled decoding; bounded folder traversal off the UI thread.
- Cancellable HTTP calls, bounded response reads, and generic errors that exclude backend response bodies.
- Serialized token refresh on the main dispatcher; cancellation propagation; account-state cleanup; server sign-out attempt; realtime token payload and structured event matching.
- Visible auth errors; scrollable login; system theme; responsive grid density.
- Cloud-backed saved looks, collections/membership, packing membership/packed state, wear logging/history, and filter presets.
- Paginated wardrobe and utility reads. Empty preference updates fail rather than claiming a save.

## Deployed backend fixes and evidence

Five migration files match the live migration history:

- `20260907080639_fix_duplicate_account_initialization`: two AFTER INSERT triggers previously collided on profile creation. The legacy initializer is now idempotent. Tests confirm profile/preferences/entitlement creation and preserved terms metadata.
- `20260907080654_harden_rate_limit_counters`: client counter writes revoked; the compatible invoker RPC delegates to a private, narrowly granted definer function with fixed quotas/windows. Tests reject quota inflation, unknown buckets, direct counter reset and deletion.
- `20260907081606_complete_account_export`: a STABLE invoker function exports a consistent RLS-scoped database snapshot without per-table REST row limits. Tests cover 1,001 items, packing and collection membership, cross-account isolation, and OAuth-token exclusion. The `account-export` endpoint now uses this function and fails explicitly on errors.
- `20260907082028_protect_account_deletion`: a private deletion lock adds a restrictive storage policy during deletion and rejects deleted-user tokens. The `delete-account` endpoint checks listing/removal errors, verifies empty storage, revokes sessions and removes the account in sequence. Failures do not return success.

- `20260907084002_wardrobe_reference_integrity`: restrictive reference checks prevent cross-account garments/outfits in wear, feedback, plans, outfits and import jobs. Atomic wear recording accepts an idempotency key and increments wear counts once. Rollback assertions pass after deployment.

Database assertions were run before deployment inside rolled-back transactions and repeated after deployment. Nine Deno handler tests and type checks pass. Backend dependencies are version-pinned with a lockfile for CI.

A disposable live account passed 13 HTTP checks: password sign-in, bootstrap, private upload, wardrobe save, collection and packing creation/membership, complete export, deletion, old-token storage denial and refresh-token rejection. A follow-up database query confirmed zero remaining users, storage objects, sessions or deletion locks for that fixture. No real user account was used and no emails were sent.

The security advisor reports one expected informational notice: RLS enabled with no policy on `private.closetai_account_deletions`. This is deliberately client-inaccessible and deny-by-default, with table grants revoked; narrowly scoped definer functions own access. Explanation: https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy . No warning/error-level advisor findings were returned. Advisors do not establish overall application security.

## macOS preservation

Successful RC3 Actions run: https://github.com/vaibhavnaik2/ClosetAI/actions/runs/34076805288 at commit `6509255b6a1db062f9fba32479a1bca93a61b7fe`.
The embedded ZIP was independently reconstructed and checked for ZIP integrity and SHA-256:
`d2fc651cd62ff000a398889d4a9b3defcdd387f2f9d9bd2c61a2841c6f75da27`.

The `.release` directory, existing macOS source and packaging workflows remain unchanged. The build reconstructs the archive and applies overrides before compiling each architecture. Reproducible source does not imply byte-identical signed DMGs across changing runner images. The existing release is ad-hoc signed, not Developer ID notarized. Shared backend fixes preserve the existing RPC and endpoint signatures.

## Remaining commercial-release gates

1. Owner-controlled Android upload/signing key and Play Console setup; store listing, approved terms/privacy documents, Google Play declarations and physical-device testing.
2. Google Drive OAuth credentials, redirect setup, connection/import/revocation acceptance tests. Current Drive functionality has not been verified with an owner's Drive account.
3. Password-recovery completion and approved policy links; existing recovery UI only requests an email.
4. AI model availability and real-garment classification/stylist acceptance tests; entitlement enforcement, server-side image/hash validation, prompt-injection tests and unavailable-garment exclusion. No claims of validated fashion accuracy.
5. URL-import hardening: current endpoint still buffers the response before checking its actual size, has DNS/IPv6/rebinding gaps, and does not fully sanitize metadata. A secure egress path is required before claiming a production import firewall.
6. Full filter/customization parity, offline import queues and all privacy/auto-analysis preference enforcement remain incomplete. Utility rename/delete, member removal, outfit planning and item name/status edits have been added; authenticated device interaction coverage is still needed.
7. Realtime reconnect/backoff and cross-account concurrency tests; stress testing of fixed quotas.
8. Deletion must still be stress-tested with concurrent in-flight uploads and very large libraries. The synchronous cleanup rejects more than 50,000 objects or excessive nesting, requiring a background cleanup job. Exports contain database records and storage paths, not a ZIP of original photos.
9. Full baseline backend migrations and all original Edge Functions still need to be brought under version control for a clean-room rebuild.

Retrieved legacy function snapshots under local `backend/reference/` are excluded from publication. They are review material, not hardened deployment sources.
