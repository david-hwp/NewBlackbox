---
phase: 09-clone-auth-billing
plan: 09
type: execute
status: completed
completed_at: "2026-06-08T03:46:30+08:00"
release: "1.2.3-release"
wave: 1
depends_on:
  - 08-02
  - 08-03
files_modified:
  - admin/backend/src/main/java/com/duodian/admin/entity/Shop.java
  - admin/backend/src/main/java/com/duodian/admin/entity/ComputeDeduction.java
  - admin/backend/src/main/java/com/duodian/admin/controller/ShopController.java
  - admin/backend/src/main/java/com/duodian/admin/controller/ShopReportController.java
  - admin/backend/src/main/java/com/duodian/admin/service/ComputeService.java
  - admin/backend/src/main/java/com/duodian/admin/service/CloneAuthorizationTokenService.java
  - admin/backend/src/main/resources/schema.sql
  - app/src/main/java/com/zhirang/zhanghaoguanjia/network/ApiService.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/data/ShopRepository.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeViewModel.kt
  - engine-aidl/src/main/aidl/top/niunaijun/blackbox/engine/IBlackBoxEngine.aidl
  - Bcore/src/main/java/top/niunaijun/blackbox/engine/BlackBoxEngineService.kt
  - Bcore/src/main/java/top/niunaijun/blackbox/engine/CloneInstanceStore.kt
  - Bcore/src/main/java/top/niunaijun/blackbox/engine/CloneAuthTokenVerifier.kt
autonomous: true
requirements:
  - CLONE-AUTH-01
  - CLONE-BILLING-01
  - CLONE-RENEW-01
  - ENGINE-TOKEN-01
must_haves:
  truths:
    - "店铺名称和店铺ID不参与新增扣费、续期扣费或打开授权"
    - "cloneInstanceId 是新增扣费、续期扣费、授权打开的唯一业务主键"
    - "服务端随机校验码不得返回 APP"
    - "服务端私钥不得进入 APP 或引擎"
    - "引擎只校验本地授权 token，不联网，不依赖 Android Keystore"
  artifacts:
    - path: "admin/backend/src/main/java/com/duodian/admin/service/CloneAuthorizationTokenService.java"
      provides: "服务端长期授权 token 签发"
      exports: ["authorizationToken"]
    - path: "Bcore/src/main/java/top/niunaijun/blackbox/engine/CloneAuthTokenVerifier.kt"
      provides: "引擎本地验签和授权放行"
      exports: ["verifyCloneAuth"]
    - path: "{BEnvironment.getSystemDir()}/clone-auth/{cloneInstanceId}/auth.token"
      provides: "引擎系统目录中的长期授权文件"
      exports: ["authorizationToken"]
---

<objective>
Implement clone-based billing and authorization for multi-shop management.

All create billing, renew billing, and clone launch authorization must be based on `cloneInstanceId`, not shop name or shop ID. The server signs long-lived authorization tokens, the APP passes them to the engine over AIDL, and the engine stores them in its own system directory before validating local tokens before launching a clone. Do not write clone meta or auth token files into the cloned app's visible data directory.
</objective>

<execution_context>
@$HOME/.codex/gsd-core/workflows/execute-phase.md
@$HOME/.codex/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/phases/09-clone-auth-billing/09-CONTEXT.md
@.planning/phases/09-clone-auth-billing/09-RESEARCH.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add backend clone identity and authorization schema</name>
  <files>
    admin/backend/src/main/java/com/duodian/admin/entity/Shop.java
    admin/backend/src/main/java/com/duodian/admin/entity/ComputeDeduction.java
    admin/backend/src/main/java/com/duodian/admin/repository/ComputeDeductionRepository.java
    admin/backend/src/main/resources/schema.sql
  </files>
  <description>
    1. Extend `shops` with:
       - `clone_instance_id VARCHAR(255) UNIQUE`
       - `clone_sequence INT`
       - `local_virtual_user_id INT`
       - `clone_validation_code VARCHAR(64)` hidden from JSON
       - `clone_validation_hash VARCHAR(128)` hidden from JSON
       - `credential_version INT DEFAULT 1`
       - `auth_start_at DATETIME`
       - `auth_expire_at DATETIME`
       - `authorization_jti VARCHAR(64)`
    2. Add `ComputeDeduction` table/entity:
       - `user_id`
       - `clone_instance_id`
       - `deduction_type` (`CREATE`, `RENEW`)
       - `operation_key`
       - `amount`
       - `transaction_log_id`
       - `created_at`
       - unique key `(user_id, clone_instance_id, deduction_type, operation_key)`
    3. Ensure soft-delete conventions remain intact.
    4. Keep `shopName` and `shopId` nullable rules compatible with existing UI while preserving `NEW-*` new-tag behavior.
  </description>
  <verify>
    - `admin/backend` tests compile.
    - Schema includes all new fields and idempotency table.
    - JSON serialization never returns `clone_validation_code`.
  </verify>
  <acceptance_criteria>
    - Existing shops can load after schema update.
    - New clone metadata fields are persisted.
    - Idempotency table prevents duplicate CREATE/RENEW entries.
  </acceptance_criteria>
</task>

<task type="auto">
  <name>Task 2: Implement server clone ID and authorization token service</name>
  <files>
    admin/backend/src/main/java/com/duodian/admin/service/CloneAuthorizationTokenService.java
    admin/backend/src/main/java/com/duodian/admin/config/CloneAuthorizationProperties.java
    admin/backend/src/main/java/com/duodian/admin/controller/dto/CloneShopCreateRequest.java
    admin/backend/src/main/java/com/duodian/admin/controller/dto/CloneShopCreateResponse.java
    admin/backend/src/main/java/com/duodian/admin/controller/dto/ShopRenewResponse.java
  </files>
  <description>
    1. Add server-side RSA signing service using `SHA256withRSA`.
    2. Load private key and `publicKeyId` from configuration.
    3. Generate readable clone ID:
       `CLN1-{phone}-{packageName}-N{cloneSequence}-U{localVirtualUserId}-R{randomDigest}`.
    4. Generate and store `clone_validation_code`; never include it in responses.
    5. Sign `authorizationToken` with claims:
       - `typ=clone_auth`
       - `serverUserId`
       - `phone`
       - `cloneInstanceId`
       - `packageName`
       - `localVirtualUserId`
       - `credentialVersion`
       - `authStartAt`
       - `authExpireAt`
       - `iat`
       - `exp`
       - `jti`
    6. Ensure token does not contain `shopName`, `shopId`, or `platformName`.
  </description>
  <verify>
    - Unit test token signature can be verified with public key.
    - Unit test token claims exclude shop display fields.
    - Unit test clone ID is parseable and includes phone/package/sequence/userId/random digest.
  </verify>
  <acceptance_criteria>
    - Server can sign a valid clone auth token.
    - Server random validation code is persisted but not returned.
    - Token expiry mirrors shop authorization expiry.
  </acceptance_criteria>
</task>

<task type="auto">
  <name>Task 3: Replace create and renew billing with clone-based idempotent flows</name>
  <files>
    admin/backend/src/main/java/com/duodian/admin/controller/ShopController.java
    admin/backend/src/main/java/com/duodian/admin/controller/ShopReportController.java
    admin/backend/src/main/java/com/duodian/admin/service/ComputeService.java
    admin/backend/src/main/java/com/duodian/admin/service/ShopService.java
    admin/backend/src/test/java/com/duodian/admin/controller/ShopControllerTest.java
    admin/backend/src/test/java/com/duodian/admin/controller/ShopReportControllerTest.java
  </files>
  <description>
    1. Add `POST /shops/clone/create`:
       - require login
       - accept `platform`, `platformName`, `packageName`, `localVirtualUserId`, `operationKey`
       - reject if same user/package already has a `NEW-*` shop
       - create server-signed `cloneInstanceId`
       - deduct CREATE by `(userId, cloneInstanceId, CREATE, operationKey)`
       - create `NEW-*` shop with `User[{localVirtualUserId}]-未知`
       - return shop, balance, `authorizationToken`, auth start/expire
    2. Deprecate or block legacy `/shops/pending` path so it cannot create a shop without clone-based billing.
    3. Change `POST /shops/{id}/renew`:
       - require cloneInstanceId
       - deduct RENEW by `(userId, cloneInstanceId, RENEW, operationKey)`
       - `authExpireAt = max(now, oldAuthExpireAt) + 30 days`
       - increment `credentialVersion`
       - return new `authorizationToken`
    4. Change `/shops/report` to only update `shopName` and `shopId`; no compute deduction and no auth token generation.
    5. Ensure `PUT /shops/{id}` can manually update `shopName` and `shopId` without billing.
  </description>
  <verify>
    - Backend tests prove CREATE duplicate operationKey does not double charge.
    - Backend tests prove RENEW duplicate operationKey does not double charge.
    - Backend tests prove `/shops/report` and `PUT /shops/{id}` never call compute deduction.
    - Backend tests prove transaction log remarks use cloneInstanceId, not shopId.
  </verify>
  <acceptance_criteria>
    - New shop create deducts exactly one point.
    - Renew deducts exactly one point per idempotent operation.
    - Shop info recognition and manual edit never affect balance.
  </acceptance_criteria>
</task>

<task type="auto">
  <name>Task 4: Add APK clone create, renew, and token persistence flow</name>
  <files>
    app/src/main/java/com/zhirang/zhanghaoguanjia/network/ApiService.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/data/ShopRepository.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/bean/dto/CloneShopCreateRequest.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/bean/dto/CloneShopCreateResult.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/bean/dto/ShopRenewResponse.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeViewModel.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/view/dialog/EditShopSheetFragment.kt
  </files>
  <description>
    1. Add DTOs for clone create and renew responses with `authorizationToken`.
    2. Add operation-key generation for create and renew.
    3. On add-shop confirm:
       - ask engine to create/ensure virtual user and data directory
       - install target platform package into virtual user
       - call `/shops/clone/create`
       - pass meta and token to engine via EngineProxy
       - engine stores meta and token in its own system directory, not the cloned app data directory
       - open clone after engine validation passes
    4. On renew:
       - call `/shops/{id}/renew` with operationKey
       - pass returned `authorizationToken` to engine
       - engine atomically replaces `auth.token` in its system directory
       - update local UI balance and expire data
    5. On report extracted shop info:
       - call report/update endpoint only with `shopName/shopId`
       - do not send or expect billing fields
    6. Edit sheet must allow manual `shopName` and `shopId` edits.
  </description>
  <verify>
    - MockWebServer tests or manual logs show clone create sends localVirtualUserId and operationKey.
    - Renew response causes engine to write a new token file before launching.
    - Manual edit does not call renew/create endpoints.
  </verify>
  <acceptance_criteria>
    - Add shop returns server cloneId and token, then app passes both to engine for system-dir storage.
    - Shop ID can be manually edited when recognition fails.
    - New tag disappears only after shop info report or manual shopId update makes the shop non-`NEW-*`.
  </acceptance_criteria>
</task>

<task type="auto">
  <name>Task 5: Implement engine metadata storage and local token launch gate</name>
  <files>
    engine-aidl/src/main/aidl/top/niunaijun/blackbox/engine/IBlackBoxEngine.aidl
    app/src/main/java/com/zhirang/zhanghaoguanjia/engine/EngineProxy.kt
    Bcore/src/main/java/top/niunaijun/blackbox/engine/BlackBoxEngineService.kt
    Bcore/src/main/java/top/niunaijun/blackbox/engine/CloneInstanceStore.kt
    Bcore/src/main/java/top/niunaijun/blackbox/engine/CloneAuthTokenVerifier.kt
  </files>
  <description>
    1. Add AIDL methods for:
       - ensuring clone user/data directory
       - writing clone meta into engine system directory
       - atomically writing `auth.token` into engine system directory
       - deleting clone user/directory and system-dir authorization record
       - launching clone with token validation
    2. `CloneInstanceStore` stores authorization records under:
       `{BEnvironment.getSystemDir()}/clone-auth/{cloneInstanceId}/`
       - `meta.json`
       - `auth.token.tmp` then rename to `auth.token`
       - do not add extra `serverUserId` or `packageName` path levels because `cloneInstanceId` is globally unique
    3. Do not write `.clone_meta.json`, `.clone_auth.token`, `meta.json`, `auth.token`,
       or any clone/token semantic file under:
       `data/user/{localVirtualUserId}/{packageName}/`
    4. Mapping recovery order:
       - read explicit clone mapping table
       - scan engine system-dir clone-auth records by cloneId or package/user
       - if missing, recreate a new local virtual user from server shop list and write a new system-dir auth record
    5. Implement `CloneAuthTokenVerifier`:
       - parse compact signed token
       - resolve public key by `publicKeyId`
       - verify `SHA256withRSA`
       - verify `typ`, phone/user/clone/package/localUserId, and auth time window
    6. Launch path refuses clone launch when token is missing, expired, malformed, or mismatched.
    7. Keep implementation independent of Android Keystore and system-version-specific crypto APIs beyond standard JCA RSA verification.
  </description>
  <verify>
    - Unit tests verify valid token passes and tampered token fails.
    - Engine launch fails when system-dir `auth.token` is missing.
    - Engine launch fails when token localVirtualUserId does not match requested userId.
    - Engine launch succeeds when meta and token are valid.
    - File-system check confirms engine system directory has `clone-auth/{cloneInstanceId}/meta.json` and `auth.token`.
    - File-system check confirms cloned app data directory has no `.clone_meta.json`, `.clone_auth.token`, `meta.json`, or `auth.token`.
  </verify>
  <acceptance_criteria>
    - Engine only validates token and opens clone; it does not perform billing or network calls.
    - `auth.token` can be replaced by renew flow without rewriting meta.
    - Delete shop removes clone directory and engine system-dir authorization record.
    - Target cloned app cannot observe clone meta or authorization token through its own data directory.
  </acceptance_criteria>
</task>

<task type="auto">
  <name>Task 6: Verification, migration, and rollout</name>
  <files>
    admin/backend/src/test/java/com/duodian/admin/controller/ShopControllerTest.java
    admin/backend/src/test/java/com/duodian/admin/service/CloneAuthorizationTokenServiceTest.java
    Bcore/src/test/java/top/niunaijun/blackbox/engine/CloneAuthTokenVerifierTest.kt
    CLAUDE.md
  </files>
  <description>
    1. Add backend tests for:
       - clone create
       - renew
       - idempotency
       - report/update non-billing
       - token claim exclusion of shop display fields
    2. Add engine token verifier tests.
    3. Add migration/backfill behavior:
       - existing shops with cloneId but no token can request an auth token if logged in and not expired
       - expired shops must renew before token issuance
    4. Manual test on Pixel 8:
       - add shop
       - open clone with valid token
       - corrupt token and confirm launch blocked
       - renew and confirm new token opens clone
       - manually edit shop name/shopId and confirm no balance change
       - inspect cloned app data directory and confirm no clone/token semantic files exist
    5. Document release and test steps in `CLAUDE.md`.
  </description>
  <verify>
    - `cd admin/backend && mvn test`
    - `./gradlew :Bcore:testDebugUnitTest` or closest available engine unit-test target
    - `./gradlew :app:assembleRelease`
    - Pixel 8 manual smoke test passes
  </verify>
  <acceptance_criteria>
    - All backend tests pass.
    - App builds.
    - Engine token verifier tests pass or manual token-tamper test is documented.
    - Release notes document clone auth and renew flow.
  </acceptance_criteria>
</task>

</tasks>

<verification>
1. Verify backend schema and repositories compile.
2. Verify RSA token signing and verification with unit tests.
3. Verify create and renew cannot double charge on retried operation keys.
4. Verify shop info report and manual edit do not change compute balance.
5. Verify `auth.token` is written as an independent file in engine system directory and renew only replaces that file.
6. Verify engine rejects missing/expired/tampered/mismatched token before launching.
7. Verify valid token launches the correct `packageName + localVirtualUserId` clone.
8. Verify cloned app visible data directory does not contain clone meta or authorization token files.
</verification>

<completion_summary>
Phase 09 was completed through the 1.2.x release train:

- `1.2.0-release`: initial cloneInstanceId billing and authorization rollout.
- `1.2.1-release`: platform-scoped restore caching and repeated-restore regression fixes.
- `1.2.2-release`: create/open progress UX, repair entry, and store recovery refinements.
- `1.2.3-release`: local-only repair behavior, no server-side repair mutation, and repair swipe regression closeout.

Final completion evidence:

- Release APK build passed with `./gradlew :app:assembleRelease --no-daemon`.
- Server-side主 APK版本记录和版本发布公告已发布。
- Xiaomi real-device smoke testing verified the repair swipe dialog path and confirmed the engine no-response regression was not reproduced after the 1.2.3 fix.
- Phase 09 implementation remained on `future-phase9` and was merged forward with this `.planning` completion record.
</completion_summary>

<success_criteria>
- [x] `cloneInstanceId` is server-generated and parseable as phone/package/sequence/localUserId/randomDigest.
- [x] Server stores clone validation random code but never returns it to APP.
- [x] `authorizationToken` contains phone and clone authorization data but no shopName/shopId.
- [x] Create billing and renew billing are keyed by cloneInstanceId.
- [x] Reported or manually edited shop info never triggers compute deduction.
- [x] Renew returns a new authorizationToken and APP passes it to engine for system-dir storage.
- [x] Engine validates token locally and refuses invalid launches.
- [x] Deleting a shop deletes the clone directory and removes engine system-dir token files.
- [x] Clone meta and token files are not written into the cloned app's visible data directory.
</success_criteria>

<rollback>
If Phase 9 validation fails:
1. Disable engine launch token gate behind a config flag.
2. Keep server clone records and compute deduction table intact.
3. Re-enable prior launch flow only for managed users while preserving shop info report behavior.
4. Do not revert idempotent compute deduction records.
</rollback>
