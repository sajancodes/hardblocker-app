# Enforcement boundaries

Standard Mode is a voluntary commitment aid, not a security boundary against the phone's owner. Disabling Accessibility, revoking Usage Access, force-stopping, clearing data, uninstalling, booting safe mode or using developer/root tools can interrupt or bypass it. Protection status and notifications report detected interruptions. Do not rely on it for parental controls, enterprise containment or an emergency workflow.

Device Owner mode uses supported Android package suspension. While commitments are active it also prevents uninstalling HardBlock through supported device-policy controls and disables manual date/time configuration. These controls are cleared when no commitments remain. Android may refuse suspension for protected packages. Provision only a separate test/device-management device you intend to manage. Never erase a personal device merely to enable this app.

Emergency calling, Settings, the launcher, permission/installer components, enabled accessibility services, common authenticators and user-protected essential applications cannot be selected for blocking. Dynamic essential roles override blocking for safety. Mark other authenticators essential before activation. An actively committed app cannot be newly exempted through the app UI.

Clock integrity uses persisted wall/elapsed/boot checkpoints. Same-boot policy deadlines advance using elapsed realtime, not a changed wall clock. A backwards floor survives reboot. A clock discrepancy blocks daily-limited apps until the clock is restored or commitment eligibility is reached, preventing a fresh allowance from a simple clock change. This is offline protection: a forward clock change combined with reboot, root or database tampering is outside its guarantee.

UsageStats is an Android estimate with limited event retention and OEM behavior. Split-screen, picture-in-picture, event delays, overlays, rapid activity changes and process/reboot gaps need broader device validation. Standard Mode may show the restricted application briefly before the blocking screen appears. Background WorkManager reconciliation is best-effort, not an exact alarm; active Accessibility monitoring maintains usage/schedule deadlines while the device is interactive.

If persistence is unreadable, the app reports interrupted protection and rejects unsafe mutations. It never silently replaces the database using a destructive migration. Release builds are signed with a private key stored outside the source repository. APK checksums and certificate fingerprint are published with each release.

To report a defect, open a GitHub issue without posting private app usage, signing keys or device backups.
