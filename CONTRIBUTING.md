# Contributing

Thanks for helping improve Android TV Home Switcher. Device-specific reports and documentation corrections are especially useful.

## Before changing code

1. Search existing issues and pull requests. For a device bug, include the TV model, Android version/API level, launcher package, command used, and a redacted error message.
2. Do not post ADB authorization keys, public IP addresses, backup contents, or personal location data.
3. Make a small, focused change. Keep stock launcher recovery working and avoid changing backup behavior without testing the restore path.

## Develop and verify

- Install JDK 17 and Android SDK 34.
- Build with `./build.sh glass`, `./build.sh glass-widgets`, or `./build.sh all`.
- Run JVM tests with `cd launcher && ./gradlew :app:testDebugUnitTest`.
- For shell changes, run `bash -n switch.sh backup.sh restore.sh build.sh` and use `--dry-run` on a real device before a live switch.
- For documentation, check that links and images render and that command examples match `--help`.

`build.sh` changes `launcher/app/src/main/res/values/config.xml` while selecting variants. Review and restore unintended generated changes before committing. Do not commit `.env`, backups, local SDK paths, or signing keys.

## Commit hooks

Run `npm install` once to install the repository's Husky hooks and development tools. The pre-commit hook formats supported staged documentation/configuration files with Prettier. The commit-msg hook checks [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) with commitlint.

Examples:

```text
docs: clarify recovery steps
fix(launcher): restore Home selection on resume
feat(widgets): add a new dashboard card
```

Use a short imperative summary. Breaking changes should use `!` after the type or a `BREAKING CHANGE:` footer. The hooks do not replace a Gradle build or device test.

## Pull requests

Explain the user-visible change, list the checks you ran, and add screenshots when changing the UI. Describe any device-specific behavior and how to recover if a switch fails. Keep third-party copyright notices with imported assets.
