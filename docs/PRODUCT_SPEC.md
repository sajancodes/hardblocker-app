You are a senior Android/Kotlin engineer. Build a production-quality Android application whose purpose is strict digital self-control.

The core philosophy is:

**Restrictions may become stricter immediately, but they must not become weaker while a commitment is active.**

This is NOT a normal screen-time tracker. It is a commitment-based application blocker designed so that once the user voluntarily activates a restriction, they cannot impulsively weaken that restriction until the commitment expires.

Build the application in Kotlin using modern native Android architecture.

# 1. PRODUCT GOAL

The app lets the user select installed applications such as:

- Instagram
- TikTok
- YouTube
- Facebook
- Reddit
- games
- browsers
- any other non-essential app

The user can create strict policies such as:

### Daily Limit

Example:

Instagram:
30 minutes/day

After activating this restriction:

Allowed:

30 → 25 minutes
30 → 15 minutes
30 → 5 minutes

NOT allowed:

30 → 45 minutes
30 → unlimited
disable restriction
remove Instagram from the policy

The restriction resets at the configured daily reset time.

Example:

00:00 local time.

---

# 2. MONOTONIC POLICY SYSTEM

This is the most important architectural rule.

Every active policy must obey:

`newRestriction >= oldRestriction in strictness`

In other words:

A policy can be made MORE restrictive.

It cannot be made LESS restrictive until the commitment period has expired.

Examples:

Daily usage:

30 min → 20 min = ALLOWED
30 min → 60 min = DENIED

Blocked-until time:

Blocked until 8 PM → blocked until 10 PM = ALLOWED
Blocked until 8 PM → blocked until 6 PM = DENIED

Blocked apps:

Instagram + TikTok
→ Instagram + TikTok + YouTube = ALLOWED

Instagram + TikTok
→ Instagram only = DENIED

Focus session:

3 hours → 5 hours = ALLOWED
3 hours → 1 hour = DENIED

Implement this logic centrally in a PolicyEngine.

Do NOT scatter this validation around UI components.

Something conceptually similar to:

```kotlin
interface PolicyEngine {
    fun canApplyChange(
        current: RestrictionPolicy,
        proposed: RestrictionPolicy
    ): PolicyDecision
}
```

The PolicyEngine must be unit-tested thoroughly.

---

# 3. COMMITMENT PERIODS

When activating restrictions, allow the user to select:

- Until end of today
- 24 hours
- 3 days
- 7 days
- 14 days
- 30 days
- 90 days
- Until custom date/time
- Indefinitely with delayed relaxation

Store:

- createdAt
- activatedAt
- expiresAt
- original configuration
- current stricter configuration
- policy history

Once activated, the original commitment cannot be weakened until expiration.

---

# 4. DELAYED RELAXATION

Do NOT make restrictions permanently irreversible.

That would eventually make the device impractical.

Instead implement delayed relaxation.

Example:

Current Instagram allowance:

30 minutes/day

User requests:

45 minutes/day

Do NOT apply immediately.

Create:

RelaxationRequest(
    oldValue = 30,
    requestedValue = 45,
    requestedAt = ...
    availableAt = requestedAt + cooldown
)

Default cooldown:

72 hours.

Possible cooldown options before activating the policy:

- 24 hours
- 72 hours
- 7 days
- 30 days

Once the commitment is active, the cooldown itself cannot be shortened.

It CAN be increased.

Example:

72 hours → 7 days = allowed

72 hours → 24 hours = denied.

The user should be able to CANCEL a pending relaxation request at any time.

Cancellation makes the restriction remain strict.

---

# 5. DAILY APP TIMER

Implement per-application daily limits.

Example dashboard:

Instagram

Used:
13m 24s

Remaining:
16m 36s

Daily Limit:
30m

Reset:
12:00 AM

When remaining reaches zero:

Instagram must become blocked.

Show a blocking screen:

"Daily limit reached"

Instagram

30 minutes used today.

Available again:
Tomorrow at 12:00 AM

Buttons:

[Close]
[Make limit stricter]

There must NOT be:

"5 more minutes"
"Snooze"
"Ignore"
"Use anyway"

Do not create loophole buttons.

---

# 6. USAGE TRACKING

Use Android's UsageStatsManager / usage access APIs for application usage information.

Build a UsageTracker abstraction.

Example:

```kotlin
interface UsageTracker {
    suspend fun getUsageToday(packageName: String): Duration
    suspend fun getForegroundPackage(): String?
}
```

Do not tie UsageStatsManager directly to ViewModels.

Repository/service layers should handle Android APIs.

Persist enough information locally to prevent simple process restarts from resetting counters.

Handle:

- device reboot
- app process death
- midnight reset
- timezone changes
- daylight-saving changes where applicable
- app updates
- package reinstall where detectable
- phone restart

Use wall clock and monotonic timing appropriately.

Do not simply depend on an in-memory countdown timer.

---

# 7. FOCUS / LOCKDOWN MODE

Create a Focus Mode.

Example:

FOCUS MODE

Duration:
3 hours

Blocked:

Instagram
TikTok
YouTube
Reddit
Games

Allowed:

Phone
Messages
Camera
Authenticator
Maps

Once started:

The user CAN:

- extend the duration
- add another blocked app
- make restrictions stricter

The user CANNOT:

- shorten duration
- remove blocked applications
- stop the session
- reduce strictness

Example:

3h → 5h = allowed

3h → 30m = denied.

---

# 8. SCHEDULED LOCKS

Allow scheduled restrictions.

Examples:

Social Media

Blocked:

10:00 PM – 8:00 AM

Study Mode

Blocked:

6:00 PM – 9:00 PM
Monday–Friday

Sleep Mode

Blocked:

11:00 PM – 7:00 AM
Every day

Once a committed schedule becomes active, its current commitment period cannot be weakened.

---

# 9. TWO ENFORCEMENT LEVELS

Architect the application around two enforcement implementations.

Use an interface such as:

```kotlin
interface AppEnforcer {
    suspend fun block(packageName: String)
    suspend fun unblock(packageName: String)
    suspend fun isBlocked(packageName: String): Boolean
}
```

Implement:

### StandardEnforcer

For normal APK installation.

Use the strongest legitimate Android mechanisms available without pretending they are impossible to bypass.

Possible components:

- UsageStatsManager
- foreground-app detection
- AccessibilityService where technically justified
- blocking activity / overlay where permitted
- foreground service only when genuinely required

Clearly disclose during onboarding that Standard Mode can ultimately be bypassed through Android system settings.

Do NOT fake security.

---

# 10. EXTREME MODE

Create a separate:

DeviceOwnerEnforcer

This is intended for users who intentionally provision the application as Android Device Owner / Device Policy Controller.

Use DevicePolicyManager where supported.

For blocked applications, prefer actual package suspension through:

`DevicePolicyManager.setPackagesSuspended(...)`

when the app has the required Device Owner privileges.

The architecture must automatically detect:

```kotlin
devicePolicyManager.isDeviceOwnerApp(packageName)
```

and expose:

EnforcementMode.STANDARD

or

EnforcementMode.DEVICE_OWNER

Do not assume Device Owner privileges exist.

Gracefully fall back to Standard Mode.

---

# 11. EXTREME MODE SETUP

Create a dedicated onboarding page:

EXTREME MODE

Explain clearly:

"Extreme Mode gives this app stronger device-management privileges. It requires special Android provisioning and cannot be enabled through a normal permission dialog."

Provide a guided setup flow.

For developer/testing installation, show the appropriate ADB provisioning instructions based on official current Android documentation.

Never execute destructive DevicePolicyManager operations.

Do NOT implement:

- factory reset
- wipeData()
- remote wipe
- credential destruction

Those capabilities are completely unnecessary for this product.

Only use Device Policy APIs relevant to application restriction and tamper resistance.

---

# 12. ANTI-BYPASS DESIGN

Within Android's legitimate APIs, make committed restrictions resistant to impulsive bypassing.

Detect and clearly warn if required permissions disappear.

Example:

"Protection interrupted"

Usage Access was disabled.

Restrictions cannot currently be enforced.

Provide instructions for restoring protection.

When running as Device Owner, use available DevicePolicyManager capabilities where appropriate to strengthen enforcement.

Do NOT exploit Android vulnerabilities.

Do NOT use root.

Do NOT hide malicious services.

Do NOT interfere with Android security protections.

Do NOT pretend an ordinary Android application can make itself literally impossible for the device owner to remove.

Use supported APIs only.

---

# 13. PROTECTED ESSENTIAL APPS

Some applications must NEVER be blockable.

Maintain a ProtectedPackagesResolver.

Protect things such as:

- system UI
- device Settings where blocking would cause unsafe lockout
- current launcher
- package installer where required
- permission controller
- default dialer
- emergency calling
- authenticator apps selected by user
- accessibility-related critical services
- this application's required system dependencies

The app must never prevent emergency calling.

Before activating a policy, perform a lockout safety check.

Example:

```kotlin
SafetyCheckResult validatePolicy(RestrictionPolicy policy)
```

Reject configurations that could leave the user without essential functionality.

---

# 14. LOCAL-FIRST PRIVACY

This application does NOT need an account.

Default architecture:

100% local.

Do not send:

- usage data
- installed app list
- focus sessions
- policies
- app names

to external servers.

Use Room database.

Recommended entities:

AppInfoEntity

RestrictionPolicyEntity

DailyUsageEntity

FocusSessionEntity

ScheduleEntity

RelaxationRequestEntity

PolicyHistoryEntity

AppExceptionEntity

SettingsEntity

Store timestamps in a robust format.

Use encrypted local storage for security-sensitive configuration where appropriate.

---

# 15. DATABASE INTEGRITY

Do not allow accidental policy weakening through database bugs.

All policy mutations MUST go through repositories and PolicyEngine.

UI components must never directly modify policy database rows.

Flow:

UI
↓
ViewModel
↓
PolicyRepository
↓
PolicyEngine validation
↓
Database transaction
↓
EnforcementCoordinator

Create an append-only policy history where practical.

Record:

- old restriction
- proposed restriction
- decision
- timestamp
- reason
- enforcement result

---

# 16. TECH STACK

Use:

- Kotlin
- Jetpack Compose
- Material 3
- MVVM
- Clean Architecture where useful without overengineering
- Coroutines
- Flow / StateFlow
- Room
- DataStore
- Hilt
- WorkManager for appropriate persistent scheduled work
- AndroidX Lifecycle
- Navigation Compose

Use the latest stable mutually compatible versions available at implementation time.

Do not blindly use outdated version numbers from this prompt.

Verify current official Android documentation before choosing SDK or dependency versions.

---

# 17. PROJECT ARCHITECTURE

Prefer something similar to:

```text
app/
├── data/
│   ├── database/
│   ├── dao/
│   ├── entities/
│   ├── repository/
│   └── datastore/
│
├── domain/
│   ├── model/
│   ├── repository/
│   ├── policy/
│   └── usecase/
│
├── enforcement/
│   ├── AppEnforcer.kt
│   ├── StandardEnforcer.kt
│   ├── DeviceOwnerEnforcer.kt
│   ├── EnforcementCoordinator.kt
│   └── PackageSafetyManager.kt
│
├── usage/
│   ├── UsageTracker.kt
│   ├── UsageStatsTracker.kt
│   └── UsageMonitor.kt
│
├── admin/
│   ├── AppDeviceAdminReceiver.kt
│   └── DeviceOwnerManager.kt
│
├── service/
│   └── ...
│
├── scheduler/
│   └── ...
│
├── ui/
│   ├── onboarding/
│   ├── dashboard/
│   ├── apps/
│   ├── policy/
│   ├── focus/
│   ├── schedules/
│   ├── blocked/
│   ├── settings/
│   └── extreme/
│
└── di/
```

Adapt where necessary.

Do not create meaningless abstraction layers.

---

# 18. UI DESIGN

Design a modern minimal dark interface.

Visual direction:

- near-black background
- clean typography
- restrained accent colors
- large timer numbers
- smooth Compose animations
- rounded cards
- minimal clutter
- serious/productive feeling
- no childish gamification

Dashboard example:

```text
GOOD AFTERNOON

Today's Focus
2h 41m

────────────────────

Instagram
██████████████░░░

23m / 30m

7m remaining

────────────────────

YouTube
████████░░░░░░░░

18m / 45m

27m remaining

────────────────────

FOCUS MODE

Start strict session
```

---

# 19. APP SELECTION SCREEN

Display installed launchable applications.

Each row:

Icon
Application name
Package name
Current restriction

Allow search.

Allow categories where practical.

Do not show hundreds of irrelevant Android internal packages by default.

---

# 20. POLICY CREATION FLOW

Example:

Select Instagram

→ Restriction Type

Daily Limit
Scheduled Block
Always Blocked
Focus Only

→ Daily Limit

30 minutes

→ Commitment

7 days

→ Relaxation Delay

72 hours

→ Confirmation

Show:

"You are about to commit to:

Instagram
30 minutes/day
7 days

Until:
October XX at XX:XX

For the next 7 days:

✓ You can decrease this limit
✕ You cannot increase it
✕ You cannot disable the restriction
✕ You cannot remove Instagram

After your commitment expires, normal editing becomes available."

Require an explicit final confirmation.

After confirmation, activate immediately.

---

# 21. BLOCK SCREEN

Create a dedicated polished blocked-app screen.

Example:

```text
                 🔒

             INSTAGRAM

         DAILY LIMIT REACHED

          30m / 30m used

       Available again in

             07:42:18


       [ CLOSE INSTAGRAM ]


       Limit: 30 minutes

       Make restriction stricter →
```

Do not provide temporary bypasses.

---

# 22. WARNING BEFORE COMMITMENT

The strict behavior must be communicated BEFORE activation.

Do not surprise the user.

Show exactly:

- what will be blocked
- how long
- when the policy expires
- when the limit resets
- which changes remain possible
- which changes become unavailable
- enforcement strength being used

Example:

Protection:

EXTREME
Device Owner active

or:

STANDARD
Can potentially be bypassed through Android system settings

---

# 23. RELAXATION REQUEST UI

If user requests a weaker rule:

Current:

30 minutes/day

Requested:

45 minutes/day

Show:

```text
RELAXATION REQUEST

Current limit:
30 minutes

Requested:
45 minutes

Available in:
2d 18h 41m

This delay cannot be shortened.

[Cancel Request]
```

Never provide:

"Apply now."

Once available, the user may explicitly accept it.

Do NOT automatically weaken restrictions merely because the cooldown expired.

Expiration only makes the change eligible.

Require another deliberate confirmation.

---

# 24. NOTIFICATIONS

Useful notifications only.

Examples:

Instagram
5 minutes remaining

Instagram
1 minute remaining

Instagram
Daily limit reached

Focus Mode
2 hours remaining

Relaxation Request
Your requested change is now available.

Do not spam.

Allow warning thresholds to be configured before commitment.

---

# 25. USAGE ACCOUNTING

Be precise.

Do not count an application simply because its process exists.

Try to measure meaningful foreground usage.

Prevent obvious double counting.

Handle rapid switching:

Instagram → Home → Instagram

Correctly.

Usage should survive:

- screen rotation
- process restart
- reboot
- app restart

Daily limit calculations must be deterministic and testable.

---

# 26. REBOOT HANDLING

After reboot:

restore active policies.

Recalculate current state.

Reapply enforcement.

If a focus session was scheduled to end at 18:00 and the device rebooted at 17:30, it must remain active until 18:00.

Never reset restrictions because the process died.

---

# 27. TIME MANIPULATION

Design reasonable protection against simple clock manipulation.

Store:

- wall clock timestamps
- elapsed realtime checkpoints
- boot information where useful

Detect suspicious backwards clock changes.

Do not accidentally give additional usage because the user changed the system clock backwards.

Do not create dangerous permanent lockouts either.

Create a TimeIntegrityManager with testable logic.

---

# 28. STATES

Model policies explicitly.

Example:

```kotlin
enum class PolicyState {
    DRAFT,
    ACTIVE,
    EXPIRED,
    RELAXATION_PENDING,
    DISABLED
}
```

Model enforcement separately from policy state.

Example:

```kotlin
enum class EnforcementState {
    ENFORCED,
    PENDING,
    INTERRUPTED,
    UNSUPPORTED
}
```

Do not conflate "policy exists" with "Android successfully enforced policy."

---

# 29. PERMISSION ONBOARDING

Guide user through required permissions step by step.

Examples:

Usage Access

Accessibility, only if actually needed by Standard Mode implementation

Notification permission where applicable

Device Owner setup for Extreme Mode

Each screen must explain WHY the permission is needed.

Detect whether it has actually been granted.

Do not simply assume because the settings screen was opened.

---

# 30. TESTING

Create extensive tests.

Especially test PolicyEngine.

Cases:

30m → 20m
expected ALLOW

30m → 30m
expected ALLOW / NO_CHANGE

30m → 45m
expected DENY or RELAXATION_REQUIRED

blockUntil 20:00 → 22:00
ALLOW

blockUntil 20:00 → 18:00
DENY

7-day commitment → 30-day
ALLOW if interpreted as stricter

7-day commitment → 1-day
DENY

blockedApps + YouTube
ALLOW

blockedApps - Instagram
DENY

72h cooldown → 7-day cooldown
ALLOW

72h cooldown → 24h cooldown
DENY

Also test:

- midnight reset
- timezone change
- reboot
- policy expiration
- pending relaxation
- cancellation
- app uninstall
- package unavailable
- permissions revoked
- Device Owner unavailable
- corrupted persistence edge cases

---

# 31. DEBUG SCREEN

For development builds, create an internal debug screen showing:

Current foreground package

Current enforcement mode

Device Owner:
true/false

Usage Access:
true/false

Accessibility:
true/false

Active policies

Remaining usage

Policy expiration timestamps

Scheduled jobs

Last enforcement event

Database state

Do NOT include this screen in normal user navigation.

---

# 32. LOGGING

Use structured logging.

Important events:

POLICY_CREATED

POLICY_ACTIVATED

POLICY_STRICTENED

POLICY_WEAKEN_ATTEMPTED

RELAXATION_REQUESTED

RELAXATION_CANCELLED

RELAXATION_AVAILABLE

APP_BLOCKED

APP_UNBLOCKED

LIMIT_REACHED

PERMISSION_REVOKED

ENFORCEMENT_INTERRUPTED

DEVICE_REBOOT

Do not log private user data unnecessarily.

---

# 33. PERFORMANCE

The application must not constantly burn CPU by polling every few milliseconds.

Design monitoring intelligently.

Optimize battery usage.

Do not run an unnecessary permanent busy loop.

Avoid memory leaks.

Profile background services.

---

# 34. FIRST BUILD PRIORITY

Do NOT try to implement everything simultaneously.

Build vertically in phases.

PHASE 1:

Project structure
Compose UI
Room
installed app listing
Usage Access onboarding
UsageStatsManager tracking
daily usage display

PHASE 2:

PolicyEngine
daily limits
persistent policies
countdown
blocked screen
midnight reset

PHASE 3:

Focus Mode
scheduled restrictions
WorkManager/scheduling
reboot recovery

PHASE 4:

StandardEnforcer hardening

PHASE 5:

DeviceAdminReceiver
Device Owner detection
DeviceOwnerEnforcer
package suspension

PHASE 6:

Delayed relaxation system
time-integrity protection
anti-bypass improvements

PHASE 7:

UI polish
animations
tests
battery optimization
edge cases

Each phase must leave the project runnable.

---

# 35. DEVELOPMENT RULES

You are working on the actual project, not merely explaining how it could be built.

When coding:

1. Inspect existing files first.
2. Understand the current architecture.
3. Do not replace working functionality unnecessarily.
4. Implement one coherent milestone at a time.
5. Build after meaningful changes.
6. Run tests.
7. Fix compilation errors before moving on.
8. Do not leave placeholder implementations unless explicitly documented.
9. Do not claim something works without testing it.
10. If Android prevents a requested capability, explain the limitation and implement the strongest legitimate alternative.
11. Prefer official Android APIs.
12. Do not use root.
13. Do not exploit vulnerabilities.
14. Never introduce destructive DevicePolicyManager capabilities.
15. Maintain a running TODO/STATUS document.

---

# 36. FIRST TASK

Start by:

1. Inspecting the repository.
2. Identifying the current Android/Kotlin/Gradle setup.
3. Checking the latest official Android APIs relevant to this implementation.
4. Creating or correcting the base architecture.
5. Getting a minimal application running.
6. Implementing installed-app discovery.
7. Implementing Usage Access onboarding.
8. Implementing UsageStatsManager-based daily usage tracking.
9. Creating the initial dashboard.
10. Creating comprehensive unit tests for the first PolicyEngine implementation.

Do not start Device Owner functionality until the normal policy/usage architecture works.

At the end of each implementation stage give me:

- what you changed
- files created
- files modified
- tests run
- build result
- what currently works
- known limitations
- exact next milestone

Most importantly:

**Never weaken an active commitment merely because the UI requests it. The domain-level PolicyEngine is the authority.**