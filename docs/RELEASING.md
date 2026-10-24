# Publishing updates

The in-app updater reads public releases from `sajancodes/hardblocker-app`. Keep this repository address stable, or ship an update pointing to its replacement before moving it.

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`. Use a semantic version such as `0.2.1`; the GitHub tag must match, optionally prefixed with `v`.
2. Keep the original private release signing key safely backed up outside Git. A different key cannot update an existing installation. Never upload the keystore or signing properties.
3. Run domain tests, debug/release lint, assemble the signed/minified release, and require passing GitHub Android instrumentation checks. Follow the build commands in README.md. Do not count empty instrumentation results as a pass.
4. Verify the release APK with Android SDK `apksigner verify --verbose --print-certs`. Check the package is `dev.sajancodes.hardblock`, the version code increased and the certificate matches the previous release. Publish a SHA-256 checksum and certificate fingerprint alongside it.
5. Create a GitHub Release with the matching version tag, readable release notes and one universal signed `.apk` asset. Mark experimental builds as pre-releases; the updater labels them and requires an explicit download. Drafts and tags without a published release are ignored.
6. Check the published release and asset URL. On a device with the previous official signed version, open Protection → Check for updates → Download → Install update. Grant Android's per-source installation permission if requested, return, and press Install update again. Confirm installation in Android's installer.
7. Verify existing commitments, queued requests and usage history survive. Verify protection reconnects after package replacement. Do not uninstall or clear app storage during update testing.

GitHub's SHA-256 asset digest is checked when available. Independently, the updater rejects another package, the same/older version code and a different signing identity. Android performs final APK signature verification. Debug installations use their own certificate and cannot switch to the official signed release through this flow.

Checks and downloads are manual. There is no background polling, silent installation, forced update or policy reset.
