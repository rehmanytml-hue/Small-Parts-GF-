# Hourly Recorder for Android

An Android app that wraps the Hourly Recorder page and stores its data in your own Firebase database, so several phones share the same figures and the live view.

This project has not been compiled or run on a device yet. Expect to fix small issues on the first build.

## 1. Create the Firebase database (free, about 10 minutes)

1. Go to https://console.firebase.google.com and create a project.
2. Build > Firestore Database > Create database. Choose a nearby location and start in production mode.
3. In Firestore > Rules, paste the contents of `firestore.rules` from this folder and publish.
4. Build > Authentication > Get started > Sign-in method > enable **Anonymous**.
5. Project settings (gear icon) > Your apps > add a **Web app** (the `</>` icon). Copy the `firebaseConfig` values it shows.
6. Open `app/src/main/assets/www/firebase-config.js` and replace each `PASTE_...` value with yours.

## 2. Build the app

**Option A: Android Studio (on a computer)**

1. Install Android Studio and open this folder.
2. Wait for the sync to finish, then choose Build > Build Bundle(s) / APK(s) > Build APK(s).
3. The file is `app/build/outputs/apk/debug/app-debug.apk`. Send it to each phone and install it.

**Option B: GitHub (no Android Studio)**

1. Create a private GitHub repository and upload this whole folder to it.
2. Open the repository's Actions tab. The "Build APK" job runs on every upload.
3. When it finishes, download `HourlyRecorder-apk` from the job page. It contains the APK.

Phones must allow "Install unknown apps" to install an APK that is not from the Play Store.

## How it works

- The page lives in `app/src/main/assets/www/index.html`. To update the app's look or behaviour, replace that file and rebuild.
- `firebase-db.js` connects the page to Firestore. Data is stored under `days`, `settings`, `auth` and `live`.
- The Admin and Production counter passwords are set inside the app the first time each role is opened.
- "Share picture" opens the phone's own share sheet, so the graph can go straight to WhatsApp.

## Fingerprint or face login

On the login form for Admin or Production counter, tick **Use fingerprint or face next time** when you log in. After that, opening that role asks for the fingerprint instead of the password, and the app asks again each time it is reopened. The password still works. The option only appears on phones that have a fingerprint or face already set up. Changing a role's password turns the fingerprint off for that role.

## Limits to know

- The app needs an internet connection.
- The in-app passwords are a screen lock. Anyone who has the installed app can reach the database, so only give the APK to people you trust.
- Inside Claude, only the page owner could be Admin. In this app, anyone who knows the admin password can.
- The debug APK is fine for your own phones. Publishing on the Play Store needs a signed release build.
