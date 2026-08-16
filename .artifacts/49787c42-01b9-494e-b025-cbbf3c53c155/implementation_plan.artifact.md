# Implementation Plan - Update SDK and Dependencies to Latest Recommended Versions

This plan updates the project to target Android 17 (API 37), which is the latest recommended SDK version as of August 2026. It also updates the Android Gradle Plugin (AGP) and key libraries to their latest stable versions.

## User Review Required

> [!IMPORTANT]
> **Android 17 (API 37) Changes**: Targeting API 37 introduces new platform behaviors. For example, apps can no longer lock their orientation or block resizing on large screens (tablets and foldables). Please verify if your app relies on fixed orientations.

> [!NOTE]
> **minSdkVersion Update**: I am proposing to update the `minSdkVersion` from 21 to 24. API 24 (Android 7.0) is the recommended baseline for 2026 to ensure compatibility with modern security features and reduce maintenance overhead for legacy versions.

## Proposed Changes

### Build Configuration

#### [MODIFY] [root build.gradle](file:///Users/martinmarinov/Projects/Public/Android/rtl_tcp_andro-/build.gradle)
- Update Android Gradle Plugin (AGP) from `8.13.2` to `9.3.1`.

#### [MODIFY] [app/build.gradle](file:///Users/martinmarinov/Projects/Public/Android/rtl_tcp_andro-/app/build.gradle)
- Update `compileSdk` to `37`.
- Update `targetSdk` to `37`.
- Update `minSdk` to `24`.
- Update `appcompat` dependency to `1.8.0`.
- Update `material` dependency to `1.14.0`.

#### [MODIFY] [hackrf/build.gradle](file:///Users/martinmarinov/Projects/Public/Android/rtl_tcp_andro-/hackrf/build.gradle)
- Update `compileSdk` to `37`.
- Update `targetSdk` to `37`.
- Update `minSdk` to `24`.
- Update dependencies to latest versions.

#### [MODIFY] [rtlsdr/build.gradle](file:///Users/martinmarinov/Projects/Public/Android/rtl_tcp_andro-/rtlsdr/build.gradle)
- Update `compileSdk` to `37`.
- Update `targetSdk` to `37`.
- Update `minSdk` to `24`.
- Update dependencies to latest versions.

#### [MODIFY] [sdrdrivertools/build.gradle](file:///Users/martinmarinov/Projects/Public/Android/rtl_tcp_andro-/sdrdrivertools/build.gradle)
- Update `compileSdk` to `37`.
- Update `targetSdk` to `37`.
- Update `minSdk` to `24`.
- Update dependencies to latest versions.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure the project builds with the new SDK and AGP versions.
- Run unit tests: `./gradlew test`.

### Manual Verification
- Deploy the app to an Android 17 emulator or device to verify basic functionality.
- Check for any UI regressions caused by the `minSdk` bump or library updates.
