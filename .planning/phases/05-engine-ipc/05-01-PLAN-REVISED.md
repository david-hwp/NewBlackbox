---
phase: 05-engine-ipc
plan: 01-revised
type: execute
wave: 1
depends_on: []
files_modified:
  - Bcore/build.gradle
  - Bcore/src/main/AndroidManifest.xml
  - Bcore/src/main/java/top/niunaijun/blackbox/engine/EngineApp.kt
  - Bcore/src/main/java/top/niunaijun/blackbox/engine/BlackBoxEngineService.kt
  - engine-aidl/build.gradle
  - engine-aidl/src/main/aidl/top/niunaijun/blackbox/engine/IBlackBoxEngine.aidl
  - engine-aidl/src/main/java/top/niunaijun/blackbox/entity/ (纯化后的实体类)
  - settings.gradle
autonomous: true
requirements:
  - IPC-01
  - IPC-02
  - IPC-03
must_haves:
  truths:
    - "Bcore is currently an Android Library module; will be transformed to Application"
    - "Entity classes have framework dependencies that must be separated from data layer"
    - "engine-aidl will contain ONLY AIDL interfaces + pure entity classes (no framework deps)"
    - "Bcore will depend on engine-aidl for compile-time AIDL and entity types"
    - "No new engine-plugin module; all engine code stays in Bcore"
  artifacts:
    - path: "Bcore/build.gradle"
      provides: "Engine APK build configuration (application plugin)"
      exports: ["Bcore application module"]
    - path: "Bcore/src/main/AndroidManifest.xml"
      provides: "Engine manifest with Service and proxy components"
      exports: ["BlackBoxEngineService"]
    - path: "engine-aidl/src/main/aidl/top/niunaijun/blackbox/engine/IBlackBoxEngine.aidl"
      provides: "Unified AIDL interface for all Engine capabilities"
      exports: ["IBlackBoxEngine"]
    - path: "engine-aidl/src/main/java/top/niunaijun/blackbox/entity/"
      provides: "Pure entity classes (Parcelable only, no framework deps)"
      exports: ["InstallResult", "BLocation", "BUserInfo", "ShopInfo", etc.]
  key_links:
    - from: "engine-aidl"
      to: "Bcore source"
      via: "Bcore depends on engine-aidl for AIDL and entity types"
      pattern: "Shared compile-time contract layer"
    - from: "IBlackBoxEngine"
      to: "IBPackageManagerService"
      via: "getPackageManager() returns IBPackageManagerService"
      pattern: "AIDL interface composition"
---

<objective>
Transform :Bcore from an Android Library into a standalone Android Application (APK) that packages the entire engine. Create :engine-aidl as a shared library containing unified AIDL entry point IBlackBoxEngine and pure entity classes (no framework dependencies). :Bcore depends on :engine-aidl for compile-time AIDL and entity types. No new :engine-plugin module is created.

Purpose: This is the foundational layer that all IPC communication depends on. The Engine APK must be installable as a separate application, expose its capabilities exclusively via AIDL, and initialize all native hooks within its own process.

Output: A buildable :Bcore application module that produces a signed APK containing the complete engine runtime, plus a lightweight :engine-aidl shared library.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/05-engine-ipc/05-CONTEXT.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Pure entity classes migration to engine-aidl</name>
  <files>
    engine-aidl/src/main/java/top/niunaijun/blackbox/entity/
    engine-aidl/src/main/java/top/niunaijun/blackbox/core/system/user/BUserInfo.java
  </files>
  <description>
    1. Identify all entity classes used in AIDL interfaces:
       - BLocation, BCell, BLocationConfig (location)
       - InstallResult, InstallOption, InstalledPackage, ShopInfo (pm)
       - AppConfig, UnbindRecord (base)
       - PendingResultData, ReceiverData, RunningAppProcessInfo, RunningServiceInfo (am)
       - BUserInfo (user)
    2. For each entity class, analyze framework dependencies:
       - SAFE (no deps): BLocation, BCell, BLocationConfig, InstallOption, ShopInfo, BUserInfo, AppConfig, UnbindRecord, ReceiverData, RunningAppProcessInfo, RunningServiceInfo
       - NEEDS PUREFICATION: InstallResult (uses Slog), InstalledPackage (uses BlackBoxCore), PendingResultData (uses BR* reflection + BuildCompat)
    3. For SAFE classes: copy directly to engine-aidl/src/main/java/
    4. For NEEDS PUREFICATION classes:
       - Remove framework-specific methods (build(), toPackage(), etc.)
       - Keep ONLY: fields, getters/setters, Parcelable implementation
       - Move removed business logic to utility classes in Bcore
    5. Ensure all copied classes compile without :black-reflection or :compiler dependencies
  </description>
</task>

<task type="auto">
  <name>Task 2: Create IBlackBoxEngine.aidl in engine-aidl</name>
  <files>
    engine-aidl/src/main/aidl/top/niunaijun/blackbox/engine/IBlackBoxEngine.aidl
  </files>
  <description>
    1. Define IBlackBoxEngine.aidl with all core methods (same as current implementation)
    2. Include imports for all service interfaces and entity types
    3. Keep ONLY AIDL files needed by :app:
       - IBlackBoxEngine.aidl
       - IBPackageManagerService.aidl
       - IBActivityManagerService.aidl
       - IBLocationManagerService.aidl
       - IBUserManagerService.aidl
       - All entity parcelable AIDL files
    4. Remove internal service AIDL files not exposed via IBlackBoxEngine:
       - IBPackageInstallerService.aidl
       - IBAccountManagerService.aidl
       - IBJobManagerService.aidl
       - IBNotificationManagerService.aidl
       - IBStorageManagerService.aidl
  </description>
</task>

<task type="auto">
  <name>Task 3: Configure engine-aidl build.gradle</name>
  <files>
    engine-aidl/build.gradle
    settings.gradle
  </files>
  <description>
    1. Keep com.android.library plugin
    2. Enable aidl build feature
    3. Include Java sources (entity classes)
    4. Minimal dependencies (androidx.annotation only)
    5. NO :black-reflection or :compiler dependencies
    6. Ensure Java compilation succeeds for all entity classes
  </description>
</task>

<task type="auto">
  <name>Task 4: Transform Bcore from Library to Application</name>
  <files>
    Bcore/build.gradle
    Bcore/src/main/AndroidManifest.xml
  </files>
  <description>
    1. Change plugin from com.android.library to com.android.application
    2. Add applicationId "top.niunaijun.blackbox.engine"
    3. Keep compileSdk, minSdk, targetSdk, NDK config
    4. Remove library-specific configs (consumerProguardFiles, aidlPackagedList)
    5. Add applicationVariants output naming for APK
    6. Add dependency on :engine-aidl
    7. Update AndroidManifest.xml:
       - Add <application android:name=".engine.EngineApp">
       - Add BlackBoxEngineService declaration
       - Keep all proxy Activity/Service/Receiver/Provider stubs
       - Add BIND_ENGINE permission
  </description>
</task>

<task type="auto">
  <name>Task 5: Create Engine Application class in Bcore</name>
  <files>
    Bcore/src/main/java/top/niunaijun/blackbox/engine/EngineApp.kt
  </files>
  <description>
    1. Create EngineApp extending Application in Bcore
    2. attachBaseContext(): Call BlackBoxCore.get().doAttachBaseContext()
    3. onCreate(): Call BlackBoxCore.get().doCreate(), start BlackBoxEngineService
    4. Default ClientConfiguration with isEnableLauncherActivity() = false
    5. Remove all Activity lifecycle hooks (Engine has no UI)
  </description>
</task>

<task type="auto">
  <name>Task 6: Create BlackBoxEngineService in Bcore</name>
  <files>
    Bcore/src/main/java/top/niunaijun/blackbox/engine/BlackBoxEngineService.kt
  </files>
  <description>
    1. Implement BlackBoxEngineService extending Service
    2. onBind(): Verify caller package name + signature fingerprint
    3. Return IBlackBoxEngine.Stub implementation
    4. Delegate all AIDL methods to existing Bcore services
    5. TRUSTED_HOST_PACKAGE = "top.niunaijun.blackbox"
  </description>
</task>

<task type="auto">
  <name>Task 7: Adapt NativeCore for dynamic loading</name>
  <files>
    Bcore/src/main/java/top/niunaijun/blackbox/core/NativeCore.java
  </files>
  <description>
    1. Add loadNative(String absolutePath) alongside existing loadNative()
    2. Thread-safe synchronized loading
    3. When absolutePath provided: System.load(path)
    4. When null: fall back to System.loadLibrary("blackbox")
  </description>
</task>

<task type="auto">
  <name>Task 8: Remove duplicate entity classes from Bcore</name>
  <files>
    Bcore/src/main/java/top/niunaijun/blackbox/entity/
  </files>
  <description>
    1. Delete all entity Java classes that were migrated to engine-aidl
    2. Update imports in Bcore source files to use engine-aidl entities
    3. Bcore now depends on engine-aidl for entity types
    4. Keep ONLY business logic utility methods that were separated from entities
  </description>
</task>

<task type="auto">
  <name>Task 9: Delete engine-plugin module</name>
  <files>
    settings.gradle
  </files>
  <description>
    1. Remove include ':engine-plugin' from settings.gradle
    2. Delete entire engine-plugin/ directory
    3. engine-plugin is no longer needed; all its code is now in Bcore
  </description>
</task>

<task type="auto">
  <name>Task 10: Build and verify</name>
  <files>
    Bcore/build.gradle
    engine-aidl/build.gradle
  </files>
  <description>
    1. Build engine-aidl: ./gradlew :engine-aidl:build
    2. Build Bcore: ./gradlew :Bcore:assembleDebug
    3. Verify Bcore APK:
       - Package name: top.niunaijun.blackbox.engine
       - No LAUNCHER Activity
       - Contains BlackBoxEngineService
       - Contains all native libraries
    4. Verify APK does NOT contain entity classes (they're in engine-aidl)
  </description>
</task>

</tasks>

<acceptance_criteria>
- [ ] engine-aidl builds successfully as a library with AIDL + pure entity classes
- [ ] Bcore builds successfully as an application producing a signed APK
- [ ] Bcore APK has no LAUNCHER Activity
- [ ] IBlackBoxEngine.aidl is defined with all core methods
- [ ] BlackBoxEngineService binds and returns valid IBinder
- [ ] NativeCore supports both System.loadLibrary() and System.load(path)
- [ ] engine-plugin module is fully deleted
- [ ] Zero code duplication between Bcore and engine-aidl
- [ ] All entity classes in engine-aidl have no framework dependencies
</acceptance_criteria>

<success_metrics>
- Bcore APK builds in under 3 minutes
- APK size is within 20% of original Bcore AAR size
- engine-aidl AAR is under 100KB
</success_metrics>
