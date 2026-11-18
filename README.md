# Gamelth (Android)

Real per-app screen time from the phone, daily limits, and a block screen when a limit is reached.

## Build without Android Studio (GitHub)
1. Create a free account at github.com and make a new repository.
2. Upload everything in this folder (including the hidden-looking `.github` folder) to the repository.
   If `.github` doesn't upload, use "Add file > Create new file", name it `.github/workflows/build.yml`
   and paste in the contents of that file.
3. Open the repository's Actions tab. The "Build APK" run starts by itself (about 5 minutes).
4. Open the finished run, download the `Gamelth-apk` artifact, unzip it, and copy `app-debug.apk` to your phone.
5. Install it (allow "install unknown apps" when asked), open Gamelth, grant the two permissions,
   then tap "Start protection".
