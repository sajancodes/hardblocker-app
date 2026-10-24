# HardBlocker App

HardBlock is a local-first Android commitment blocker. Restrictions can become stricter immediately; weaker changes require commitment eligibility and deliberate acceptance.

## Features

- Search installed launchable applications, inspect daily foreground usage and protect essential/authenticator apps.
- Create daily allowances, fixed focus sessions, always-blocked commitments and weekly schedules, including overnight windows.
- Commit until the end of today, for 1/3/7/14/30/90 days, until a custom local date/time, or indefinitely with delayed relaxation.
- Tighten limits, add applications, extend commitments or increase cooldowns. The central PolicyEngine rejects active weakening in every dimension.
- Queue a weaker configuration or disabling request. The rule remains unchanged until cooldown/finite commitment eligibility and a second explicit confirmation. Requests can be canceled, are invalidated by policy edits, and cannot be replayed after acceptance.
- Standard Mode blocks through a disclosed Accessibility overlay. The service reads window package changes, not screen contents or typed text.
- Device Owner detection automatically selects supported Android package suspension when provisioned. No destructive device-management APIs are included.
- Room stores immutable originals, current revisions, audit history, usage intervals and requests. WorkManager reconciles after reboot/update and reports interrupted protection.
- Persisted elapsed-time/boot checkpoints detect clock changes. Daily rules use pinned reset times and zones. Daily totals survive app restarts and cannot decrease within the same window.
- Configurable daily warning thresholds and one-time notifications for protection interruption and eligible relaxation.

**Standard Mode can be bypassed through Android system settings.** This is a voluntary self-control aid, not a security boundary against the phone owner. See [SECURITY.md](SECURITY.md) for precise limits and [PRIVACY.md](PRIVACY.md) for local data handling.

## Install and use

Download the signed APK from [GitHub Releases](https://github.com/sajancodes/hardblocker-app/releases). Android 10/API 29 or newer is required. Verify its SHA-256 against the release checksum file.

1. Open Protection and grant Usage Access.
2. Read the Accessibility disclosure and voluntarily enable HardBlock in Android Accessibility Settings. Return to the app and check status.
   If Android restricts Accessibility for a sideloaded APK, review App info → the menu → Allow restricted settings, where offered by Android. HardBlock cannot enable this system setting for you.
3. Optionally permit notifications. In Applications, mark additional authenticators essential before activating commitments.
4. Open Commitments, choose apps and a restriction, set commitment/cooldown/reset details, review the exact warning and explicitly confirm.
5. To make changes, review the policy. Stronger edits apply immediately. Weaker changes must be requested and later explicitly accepted; they never apply automatically.

The blocking screen provides Close app and Make restriction stricter. It offers no snooze or temporary bypass. Emergency calling, Settings, launchers and other protected components remain available.

## Extreme Mode

Device Owner requires special Android provisioning; it is not a normal permission dialog. Use a separate unprovisioned test device with no accounts/other owner, install the APK, then provision deliberately:

```text
adb shell dpm set-device-owner dev.sajancodes.hardblock/.admin.AppDeviceAdminReceiver
adb shell dpm list-owners
```

Reopen HardBlock and inspect its detected mode. Usage Access and Accessibility are still required for timely daily-limit and schedule monitoring. The app requests no wipe/reset policies. Do not erase your personal device to enable this feature. Follow the [official dedicated-device documentation](https://developer.android.com/work/dpc/dedicated-devices) for prerequisites.

## Build and test

Open in Android Studio with JDK 21 and Android SDK 37. Let Gradle install missing licensed SDK components. No backend or API key is needed.

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug --console=plain --max-workers=2
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain --max-workers=2
.\scripts\verify-device.ps1 -Serial emulator-5554
```

The core module is Android independent. The app module uses Compose Material 3, Navigation Compose, MVVM/StateFlow, Hilt, Room, DataStore and WorkManager. All policy writes go through PolicyRepository, PolicyEngine and a Room transaction; enforcement is reconciled separately. Original configuration and decision history are retained.

Release signing reads HARDBLOCK_SIGNING_PROPERTIES or the local user-profile file .android/hardblocker-signing.properties. Properties: storeFile, storePassword, keyAlias, keyPassword. Supply your own private signing identity for local release builds; signing material is excluded from Git. Run :app:assembleRelease to build. APKs signed with a different key cannot update the published APK in place.

## Verification and limitations

See [STATUS.md](STATUS.md) for the exact tested build and remaining validation. Android UsageStats is an estimate: OEM event retention/delivery, split-screen and picture-in-picture require device-specific testing. The service pauses monitoring when the screen is off and uses deadline-aware checks while interactive; WorkManager background recovery is best-effort, not an exact alarm. Clock protection cannot defeat root or all clock/reboot combinations offline.

This is an early functional release. Broad physical-device/OEM, accessibility/localization and battery profiling remain ongoing. Do not treat an emulator pass as proof of complete production hardening or Device Owner provisioning on your device.

## Official references

- [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)
- [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work)
- [AGP and built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
