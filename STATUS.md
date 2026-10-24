# Implementation status

Updated 2026-10-24 for the 0.2.0 functional release work.

## Implemented

- Kotlin/JVM domain module and Compose Material 3 Android app; Room, Hilt, DataStore, Navigation Compose and WorkManager.
- Launchable-app discovery, icons/search, daily foreground usage, Usage Access onboarding and permission rechecks.
- Daily limits, focus sessions, weekly/overnight schedules, always-blocked rules, finite/custom/indefinite commitments and confirmation UI.
- Transactional PolicyRepository, central monotonic PolicyEngine, immutable original configuration, revision checks and append-only audit entries.
- Queued/cancelable delayed relaxation with explicit eligibility revalidation and acceptance; no automatic weakening.
- Essential-package resolver and user-protected authenticators; no exemption of an actively committed app through the UI.
- Accessibility window-package monitoring and blocking overlay; emergency/Settings/launcher safety; screen-off monitoring pause.
- Automatic Device Owner detection and package-suspension implementation; voluntary setup guidance and supported uninstall/time controls. No destructive policies.
- Room schema 1-to-2 automatic migration, usage high-water persistence, clock/elapsed/boot checkpoints and restart reconciliation.
- Configurable daily warning thresholds, interruption/eligibility notifications and public CI workflow.
- Separate private release-signing identity outside the repository.

## Verification

- Previous tracking milestone: clean lint, 71 domain tests and six emulator tests passed; first APK installed/launched on API 36.
- Current domain tests: 84 passing (50 monotonic-policy, 13 lifecycle/schedule/time-integrity, 21 usage accounting).
- Current updated debug application: assembled successfully after fixing cross-module smart casts and using supported public APIs.
- Full current instrumentation/lint run is pending. A lint analyzer failed when a new test file arrived during its analysis; the full stable-source rerun is in progress. No checks or tests were disabled.
- New on-device tests cover request cancellation/eligible acceptance/replay rejection, essentials/corruption and a live Chrome-blocking/Settings-safety probe.

## Before publishing the release

1. Finish the stable-source lint/instrumentation run; fix genuine failures.
2. Exercise the real blocking path and inspect updated screens.
3. Build and verify the signed/minified release APK, publish checksums/certificate fingerprint, push source and create the GitHub Release.
4. Check public CI and record its result.

## Known remaining validation

- Device Owner provisioning/suspension has not yet been exercised on a separately provisioned device.
- Broad OEM/physical-device testing, split-screen/PiP accounting, full battery profiling, localization and comprehensive accessibility review remain open.
- Full corruption-recovery UX and stronger offline clock/reboot tamper resistance need further hardening. Essential system access is preserved for safety.
- See SECURITY.md and PRIVACY.md for enforcement/privacy boundaries. This is an early functional release, not a guarantee of production hardening.

## Architecture and files

New functional layers: app/data/repository/PolicyRepository.kt and PolicyCodec.kt, app/data/database/PolicyStorage.kt, app/enforcement, app/service/ProtectionService.kt, app/scheduler, app/admin and ui/CommitmentsScreen.kt. The original tracking/UI/DI files were extended. Domain lifecycle/time logic and tests are in core/src. Instrumentation tests are in app/src/androidTest.

Next milestone after this release: independent Device Owner test device, broader restart/permission/OEM matrix, corruption recovery, battery measurements and localization/accessibility review.
