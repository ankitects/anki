> [!IMPORTANT]
> GitHub does not auto-update apps


## For regular users

Install `arm64-v8a` from the **Assets** section below. If it fails to install, use `Parallel.A`.

`Parallel` builds install side-by-side with the main APK, allowing you to use different settings and profiles (via the `AnkiDroid directory` advanced setting and a different AnkiWeb login).


## For testers

The builds with `full`, `play` and `amazon` are useful for testing our builds for different app stores:

- **`full`**: F-Droid & GitHub `Parallel` apks
- **`play`**: Google Play - does not have `MANAGE_EXTERNAL_STORAGE` permission as it is forbidden by Google, on uninstall the user is invited to delete data.
- **`amazon`**: Amazon - currently same as full, historically removed `CAMERA` permission


## ABI variants

We perform ABI splits to reduce APK size. In rare cases, a phone may not be using the `arm64-v8a` ABI. You can find your phone's ABI using [kamgurgul/cpu-info](https://github.com/kamgurgul/cpu-info). If disk space isn't an issue, use the `full` apk.