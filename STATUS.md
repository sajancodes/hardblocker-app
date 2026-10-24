# Implementation status

Updated 2026-10-24 for the 0.2.0 public functional pre-release.

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
- Manual in-app GitHub Releases updates with notes/progress, bounded HTTPS downloads, digest checks when supplied, package/version/signing checks and Android-confirmed installation. No policy data is sent to GitHub.

## Verification

- Previous tracking milestone: clean lint, 71 domain tests and six emulator tests passed; first APK installed/launched on API 36.
- Current domain tests: 90 passing (50 monotonic-policy, 13 lifecycle/schedule/time-integrity, 21 usage accounting and six semantic-release-version tests).
- Debug app, instrumentation APK and signed/minified release assembled successfully. Debug and release lint passed with warnings treated as errors. The final local gate completed in 6m33s.
- Release APK verified by Android SDK apksigner: APK Signature Scheme v2, RSA 4096-bit certificate SHA-256 `4131ad43f7d1b5d32db16da0e706f557183b32e270b4099eb9caaad6858f78fd`.
- [Fresh API 35 Android CI baseline](https://github.com/sajancodes/hardblocker-app/actions/runs/40085913613) passed all ten device tests, including a live Chrome-blocking/Settings-safety probe, request cancellation/eligible acceptance/replay rejection, essentials/corruption, Room and UI flows.
- [Updater verification run](https://github.com/sajancodes/hardblocker-app/actions/runs/40086758132), code commit `fc5960c`, passed the build/domain-test/debug-lint job and all 12 Android instrumentation tests. This includes corrupt/same-version APK rejection; no empty results are accepted.
- The local Windows emulator encountered system/launcher ANRs, and one earlier Gradle run produced an empty result. Those attempts are not counted as passes. Fresh Linux/KVM CI requires positive nonempty instrumentation results.

## Release

Public source: https://github.com/sajancodes/hardblocker-app. Release: https://github.com/sajancodes/hardblocker-app/releases/tag/v0.2.0. Assets are the signed APK, SHA256SUMS.txt and SIGNING.txt. APK SHA-256: `87335427d10793f236d709f7a9fcc95ed7eea420a60cbd76ef32eadf5ca8c108`. Follow docs/RELEASING.md to publish future versions using the same key and increased versionCode/versionName.

## Known remaining validation

- Device Owner provisioning/suspension has not yet been exercised on a separately provisioned device.
- Broad OEM/physical-device testing, split-screen/PiP accounting, full battery profiling, localization and comprehensive accessibility review remain open.
- Full corruption-recovery UX and stronger offline clock/reboot tamper resistance need further hardening. Essential system access is preserved for safety.
- The first public release includes the updater, but a full previous-official-release → newer-official-release installation/policy-retention exercise still needs a second published version. The updater's rejection paths and version ordering are tested; Android performs final APK signature verification.
- See SECURITY.md and PRIVACY.md for enforcement/privacy boundaries. This is an early functional release, not a guarantee of production hardening.

## Architecture and files

New functional layers: app/data/repository/PolicyRepository.kt and PolicyCodec.kt, app/data/database/PolicyStorage.kt, app/enforcement, app/service/ProtectionService.kt, app/scheduler, app/admin and ui/CommitmentsScreen.kt. The original tracking/UI/DI files were extended. Domain lifecycle/time logic and tests are in core/src. Instrumentation tests are in app/src/androidTest.

Next milestone after this release: independent Device Owner test device, broader restart/permission/OEM matrix, corruption recovery, battery measurements and localization/accessibility review.
