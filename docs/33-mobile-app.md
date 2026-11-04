# Android app (SutraVidya)

The phone app is a second client of the same gateway. It lives in `mobile/` (Flutter), which is **its own git repository** (`rohanrk2810/ITAndro`) and is ignored by this one.

## Design

- **Sign in is native** (email or phone, password, Cloudflare Turnstile in a small WebView, forgot password, forced password change). The logo is `desktop/build/logo-full.png`, copied to `mobile/assets`.
- **After sign in the app is the website.** `SiteScreen` opens `/app` of the web app full screen, so every page, every role, the customize settings and every later feature are in the app without a second implementation. The earlier hand-built Flutter screens covered about half of the web and drifted from it; that is why this was replaced.
- **Same person, no second login.** The app gives the site its refresh token (written to the site's `localStorage` before the page boots). The site rotates that token as it runs, so the app reads it back every 5 seconds and keeps the newest copy in secure storage; the app does not call `/api/auth/refresh` itself while the site is open (`AuthService.webHolds`), because a reused refresh token would end the session. If the site goes to `/login` (signed out or expired), the app signs out too.
- **What a browser tab cannot do, the app does:** it asks the phone for camera and microphone when the site asks (live classes, face checks, video-lesson monitoring); it opens the phone's file picker for uploads (resume, photo, assignments); it saves and opens files made in the page (Excel, PDF, CSV, certificates, ID cards: `blob:` links are caught by a script injected on every page and handed to the app); links to other sites (Meet, Zoom) open outside the app; Android back goes back in the site, then leaves the app.
- **Not in the app:** the Windows secure-test lock-down (screen pinning, remote-tool scan) stays on the desktop app ([09](09-test-portal.md)).
- Older native screens (`lib/screens/*`: dashboard, courses, quiz, live room and others) are still in the repository but are no longer reachable from the menu.

## Branding

App name and launcher icon are SutraVidya (`mobile/android/app/src/main/AndroidManifest.xml`, launcher icons made from `desktop/build/icon.png`). The name and logo inside the site come from the institute branding the admin sets on **Customize**.

## Build

```bash
cd mobile
flutter pub get
flutter build apk --release --dart-define=API_BASE_URL=https://api.<SERVER_IP>.sslip.io
# build/app/outputs/flutter-apk/app-release.apk
```

`APP_ORIGIN` defaults to the API address with `api.` turned into `app.`. A release build needs `https`. Without a keystore the build is signed with the debug key, which is fine for trying it and not for the Play Store ([mobile/README.md](../mobile/README.md) has the signing steps). Build quirks of the Windows machine used so far are in that README too.

## Needs the server to

Serve the site over HTTPS (camera and microphone need it, [07](07-deployment-guide.md)) and keep `FRONTEND_URL` equal to the address the app opens.
