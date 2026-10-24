# Privacy

HardBlock works locally and needs no account. It does not transmit installed applications, usage history, policies or focus sessions. It has no INTERNET permission or analytics SDK.

Usage Access reads Android's foreground-activity events. The Accessibility service receives window-change package names to identify restricted applications and displays a blocking overlay. It declares `canRetrieveWindowContent=false`: it cannot use the accessibility window-content API to read screen text, passwords or typed messages.

Room stores application inventory, observed usage intervals, daily totals, commitments, requests and decision history inside the application's private directory. Usage intervals/daily snapshots retain eight previous days. Policy history remains on the device for auditability. DataStore stores onboarding acknowledgment. Backup and device transfer are excluded in the Android manifest and extraction rules.

Notifications may show application names and commitment state on the lock screen according to your Android notification settings. Opening the documentation link in your browser uses that browser's network connection. HardBlock itself does not upload data.

Clearing app storage or uninstalling deletes this local data. Standard Mode cannot prevent the device owner from doing so. Device Owner privileges are voluntary and require special provisioning; no wipe/reset capability is requested or implemented.
