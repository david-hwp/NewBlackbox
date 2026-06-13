# Phase 16: 主 App 登录态数据中心重构 - Research

## Current Implementation Summary

The current app already separates the installed engine APK from the Main App, but login-state business decisions are mixed into HomeActivity flow.

Main App currently:

- loads shop cards from the backend
- asks the engine to ensure clone users and prepare launches
- asks the server for authorization tokens
- invokes engine login-state export and restore
- invokes engine shop-info extraction
- uploads and downloads login-state through `ShopRepository`

Engine currently:

- owns clone user mapping and authorization metadata
- resolves scoped runtime directories via `BEnvironment`
- exports and restores platform-specific login-state files
- extracts shop information from clone-local platform data

Backend currently:

- stores login-state blob and metadata on `Shop`
- exposes upload/download endpoints by system shop ID
- derives `hasLoginState` from stored blob metadata
- stores shop information reported by devices

## Problem

The current data flow can make normal open, refresh, repair, server state, and engine-local files conflict:

- Main App may download/restore server login-state during normal open.
- Login-state export/upload can happen even though the shop-info collection sequence is not the single source of truth.
- Server `hasLoginState` can cause a restore even when the local clone may already contain newer live data.
- Engine directories contain the live clone data, but Main App does not have a durable local index of which login-state artifact it trusts.

## Target Pattern

Use the engine as a capability provider and Main App as the orchestration/data owner:

```text
User action
  -> Main App decides intent
  -> Engine performs one atomic local capability
  -> Engine returns data/status
  -> Main App stores local metadata/artifact
  -> Main App decides server upload/download/repair
```

This mirrors the current Retrofit repository pattern in the Main App and keeps server calls out of the engine.

## Server Backup Role

The server is a backup and cross-device recovery store, not the normal runtime source for shop card state or login-state when the device already has usable local data.

During normal use:

- devices report extracted shop information to the server
- devices upload newer login-state artifacts to the server
- server validates system shop ID ownership, freshness, checksum, and package/profile compatibility
- server stores the latest trusted backup metadata/blob

During recovery only:

- new device or local-absent clone state may pull server shop information and login-state to bootstrap local state
- explicit repair may pull server shop information and login-state to rebuild the clone directory

The server should not push runtime replacement decisions into the Main App. Main App asks for backup data only when it has decided that the current flow is one of the two recovery cases.

## Local-First Runtime Rule

The live clone on the user's phone is the source of truth during normal use. Server login-state is a cloud backup, not an automatic runtime authority.

Server login-state can overwrite/release into the local clone only in two scenarios:

- the phone has no usable local clone login-state, such as first use on a new device
- the user explicitly triggers repair shop

When the phone already has a usable local clone login-state, the Main App must not restore or release server login-state during:

- normal shop open
- pull-to-refresh
- background sync
- shop-info collection
- login-state export/upload

This keeps an already logged-in phone from being overwritten by an older or mismatched server backup while still allowing new-device restore and explicit repair.

## Storage and Cleanup Recommendation

Use a small local repository in the Main App for metadata and bounded staging:

```text
files/login-state/shops/<systemShopId>/
  manifest.json
  metadata.json
  staging/<timestamp>-<sha256>.zip
```

Raw zip/blob files are temporary:

- engine returns them through AIDL and does not persist extra copies
- Main App writes them only to staging while upload/recovery is in progress
- Main App deletes staged raw files immediately after successful upload/report
- Main App keeps failed uploads only in a bounded retry queue
- repair/new-device downloads are staged only until restore completes or terminally fails

Use a queryable metadata layer for long-term state:

- either Room/SQLite if the app is ready to add Room
- or a small JSON index managed through atomic file writes if avoiding a new dependency is preferred

The metadata must include:

- system shop ID
- packageName
- cloneInstanceId
- virtualUserId
- profileId
- platform
- extractedPlatformShopId
- extractedShopName
- artifactPath
- artifactSha256
- artifactSize
- artifactCreatedAt
- lastShopInfoCollectedAt
- lastUploadedAt
- serverUpdatedAt
- dirty flag
- raw artifact retention state such as `STAGED_FOR_UPLOAD`, `UPLOADED_DELETED`, `DOWNLOAD_FOR_RESTORE`, `RESTORED_DELETED`, or `EXPIRED_DELETED`

## AIDL Boundary

Keep AIDL methods as atomic capabilities:

- `triggerShopIdExtract(packageName, userId): ShopInfo?`
- `exportLoginState(packageName, userId, profileId): ByteArray?`
- `restoreLoginState(packageName, userId, profileId, artifact): Boolean`
- `defaultLoginStateProfile(packageName): String?`
- `clearClonePackageData(cloneInstanceId, packageName, serverUserId, userId): Boolean`
- `prepareLaunch(...)`
- clone mapping queries and authorization writes

Avoid adding server-specific or business-decision APIs to the engine.

## Server Rules

Server should continue storing login-state by system shop ID. It should additionally reject stale uploads when the incoming artifact creation/export time is older than the stored login-state update/export time.

If the existing DB only has `loginStateUpdatedAt`, add `loginStateArtifactCreatedAt` or parse/store a manifest timestamp so server freshness does not depend only on receipt time.

## Migration Strategy

1. Add Main App repository and save newly exported artifacts locally.
2. Add cleanup so successfully uploaded raw artifacts are deleted immediately and only metadata remains.
3. Change normal open and refresh so they do not restore login-state.
4. Change shop-info collection success path to export, stage, upload, then delete raw local staging on success.
5. Change upload to use Main App staging metadata and server freshness rules.
6. Change repair to explicitly choose current live local clone, bounded staging, or server backup, then call engine restore.
7. Add a one-time backfill path: for shops that have a working local clone but no Main App metadata, collect shop info then export/upload/delete staging.

## Verification Focus

- A normal shop open must not overwrite existing local clone data from server.
- Pull-to-refresh must not restore login-state.
- Shop-info extraction failure must not export/upload login-state.
- Repair must restore only when user chooses repair and must use system shop ID.
- Existing Phase 13 platform profiles must continue to restore on Xiaomi real device.
