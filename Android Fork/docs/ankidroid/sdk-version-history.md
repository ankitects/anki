# SDK Version History

Use https://apilevels.com/ to lookup Android version numbers/codenames.

See https://docs.ankidroid.org/changelog.html for user-facing changelogs.

| Version                                                                                      | minSdk | targetSdk   |
|----------------------------------------------------------------------------------------------|--------|-------------|
| [v2.23](https://github.com/ankidroid/Anki-Android/blob/v2.23.0/gradle/libs.versions.toml#L9) | 24     | 35          |
| [v2.22](https://github.com/ankidroid/Anki-Android/blob/v2.22.1/gradle/libs.versions.toml#L9) | 24     | 35          |
| [v2.21](https://github.com/ankidroid/Anki-Android/blob/v2.21.0/gradle/libs.versions.toml#L9) | 24     | 35          |
| [v2.20](https://github.com/ankidroid/Anki-Android/blob/v2.20.0/gradle/libs.versions.toml#L3) | 24     | 34          |
| [v2.19](https://github.com/ankidroid/Anki-Android/blob/v2.19.0/gradle/libs.versions.toml#L3) | 23     | 34          |
| [v2.18](https://github.com/ankidroid/Anki-Android/blob/v2.18.0/AnkiDroid/build.gradle#L79)   | 23     | 33          |
| [v2.17](https://github.com/ankidroid/Anki-Android/blob/v2.17.0/AnkiDroid/build.gradle#L79)   | 23     | 33          |
| [v2.16](https://github.com/ankidroid/Anki-Android/blob/v2.16.1/AnkiDroid/build.gradle#L71)   | 21     | 31          |
| [v2.15](https://github.com/ankidroid/Anki-Android/blob/v2.15.0/AnkiDroid/build.gradle#L55)   | 21     | 29          |
| [v2.14](https://github.com/ankidroid/Anki-Android/blob/v2.14.0/AnkiDroid/build.gradle#L46)   | 16     | 29          |
| [v2.13](https://github.com/ankidroid/Anki-Android/blob/v2.13.0/AnkiDroid/build.gradle#L46)   | 16     | 28          |
| [v2.12](https://github.com/ankidroid/Anki-Android/blob/v2.12.0/AnkiDroid/build.gradle#L46)   | 16     | 28          |
| [v2.11](https://github.com/ankidroid/Anki-Android/blob/v2.11.0/AnkiDroid/build.gradle#L20)   | 16     | 28          |
| [v2.10](https://github.com/ankidroid/Anki-Android/blob/v2.10/AnkiDroid/build.gradle#L20)     | 15     | 28          |
| [v2.9](https://github.com/ankidroid/Anki-Android/blob/v2.9/AnkiDroid/build.gradle#L23)       | 15     | 28          |
| [v2.8](https://github.com/ankidroid/Anki-Android/blob/v2.8/AnkiDroid/build.gradle#L10)       | 10     | 25          |
| [v2.7](https://github.com/ankidroid/Anki-Android/blob/v2.7/AnkiDroid/build.gradle#L10)       | 10     | 24          |
| [v2.6](https://github.com/ankidroid/Anki-Android/blob/v2.6/AnkiDroid/build.gradle#L10)       | 10     | 23          |
| [v2.5](https://github.com/ankidroid/Anki-Android/blob/v2.5/AnkiDroid/build.gradle#L10)       | 10     | 23          |
| [v2.4](https://github.com/ankidroid/Anki-Android/blob/v2.4/AnkiDroid/build.gradle#L9)        | 7      | 19          |
| [v2.3](https://github.com/ankidroid/Anki-Android/blob/v2.3/AndroidManifest.xml#L57)          | 7      | 19          |
| [v2.2](https://github.com/ankidroid/Anki-Android/blob/v2.2/AndroidManifest.xml#L57)          | 7      | 19          |
| [v2.1](https://github.com/ankidroid/Anki-Android/blob/v2.1/AndroidManifest.xml#L56)          | 7      | 18          |
| [v2.0](https://github.com/ankidroid/Anki-Android/blob/v2.0/AndroidManifest.xml#L33)          | 4      | 11          |
| [v1.1](https://github.com/ankidroid/Anki-Android/blob/v1.1/AndroidManifest.xml#L163)         | 3      | 11          |
| [v1.0](https://github.com/ankidroid/Anki-Android/blob/v1.0/AndroidManifest.xml#L143)         | 5      | 11          |
| [v0.7](https://github.com/ankidroid/Anki-Android/blob/v0.7/AndroidManifest.xml#L126)         | 3      | 11          |
| [v0.6](https://github.com/ankidroid/Anki-Android/blob/v0.6/AndroidManifest.xml#L124)         | 3      | _(not set)_ |
| [v0.5](https://github.com/ankidroid/Anki-Android/blob/v0.5/AndroidManifest.xml#L108)         | 3      | _(not set)_ |
| [v0.4](https://github.com/ankidroid/Anki-Android/blob/v0.4/AndroidManifest.xml#L58)          | 3      | _(not set)_ |
| [v0.3](https://github.com/ankidroid/Anki-Android/blob/v0.3/AndroidManifest.xml#L17)          | 3      | _(not set)_ |

## Notes

* v0.3-v0.6 had no `targetSdkVersion` set (defaults to `minSdkVersion` at runtime)
* v1.0 raised minSdk to 5 ([c44c089b74](https://github.com/ankidroid/Anki-Android/commit/c44c089b7438b0e4ea4cbbdb0d82074e55886b5d)); v1.1 was tagged from a branch that kept minSdk at 3
* The build system migrated from `AndroidManifest.xml` to `build.gradle` at v2.4 ([1fbf16e693](https://github.com/ankidroid/Anki-Android/commit/1fbf16e693e6c8787cb00f6a8b00e88f1f76be46))
* The build system migrated from `build.gradle` to `libs.versions.toml` at v2.19 ([dcb10769b1](https://github.com/ankidroid/Anki-Android/commit/dcb10769b16c72f52c4191d35f36a494be46fc3a))
