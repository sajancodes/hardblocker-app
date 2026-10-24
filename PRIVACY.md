# Privacy

HardBlock works locally and needs no account. It does not transmit installed applications, usage history, policies or focus sessions and includes no analytics SDK. Internet access is used only when you manually check GitHub Releases or download an update. GitHub receives normal connection information (including your IP address) and an app-version User-Agent; no usage or policy data is attached. There are no automatic background update checks.

Usage Access reads Android's foreground-activity events. The Accessibility service receives window-change package names to identify restricted applications and displays a blocking overlay. It declares `canRetrieveWindowContent=false`: it cannot use the accessibility window-content API to read screen text, passwords or typed messages.

Room stores application inventory, observed usage intervals, daily totals, commitments, requests and decision history inside the application's private directory. Usage intervals/daily snapshots retain eight previous days. Policy history remains on the device for auditability. DataStore stores onboarding acknowledgment. Backup and device transfer are excluded in the Android manifest and extraction rules.

Notifications may show application names and commitment state on the lock screen according to your Android notification settings. Opening the documentation link in your browser uses that browser's network connection. HardBlock itself does not upload data.

Updates are downloaded into private cache and checked for the same application, a higher version code and matching signing identity. The optional GitHub SHA-256 asset digest is checked when supplied. Android's installer performs final signature verification and requires your confirmation. The install-packages permission is used only for this update flow; the app cannot silently install packages. Installing a valid update preserves existing local data.

Clearing app storage or uninstalling deletes this local data. Standard Mode cannot prevent the device owner from doing so. Device Owner privileges are voluntary and require special provisioning; no wipe/reset capability is requested or implemented.
