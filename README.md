# FileDude — PDF, Image, Video & Document Tools

**Tagline:** *Your Files. Ready When You Are.*  
**Application ID:** `com.aistudio.filedude.qrxmzk`  
**Architecture:** Native Android (Kotlin, Jetpack Compose, Material Design 3, Clean MVVM, Room, DataStore, WorkManager, Firebase Auth/Firestore, Firebase AI Logic + App Check, Media3 Transformer, PDFBox-Android, ML Kit OCR, Google Play Billing).

---

## Architecture & Core Design Decisions

- **Template 3 Light-First Visual System:** `#F7FAF8` background, `#FFFFFF` bordered cards (`#E5ECE7`), `#E3F2E8` pale mint accents, `#24845D` primary green actions, crisp white 5-tab `NavigationBar` (`Home`, `Tools`, `My Files`, `Reminders`, `Settings`) switching to `NavigationRail` on expanded screens (`WindowWidthSizeClass.Expanded`), and progressive disclosure ("More Options" / "View All") to keep every screen organized and uncluttered.
- **Official Splash Screen & Complete Navigation:**
  - Android 12+ (`values-v31` / `values-v33`) platform SplashScreen theme attributes paired with a lightweight animated Compose splash (`FileDudeSplashScreen`), smooth fade/scale transition, DataStore onboarding readiness gate, 5-tab state preservation, `BackHandler` support across sub-screens, and deep links (`filedude://...`, notification `navigate_tab` extras, `ACTION_VIEW` / `ACTION_SEND` for PDF, image, and video files).
- **Offline-First Local Engines + Secure Cloud AI:**
  - All PDF, Image, Video, OCR, and Smart File Ready transformations execute locally on-device using **PDFBox-Android**, **Android Bitmap / ExifInterface**, **Media3 Transformer**, and **ML Kit Text Recognition**. Originals are never overwritten; outputs save as new copies.
  - **FileDude AI Assistant** is powered by **Firebase AI Logic** (`Firebase.ai`, `gemini-3.8-flash`) secured with **Firebase App Check** (Debug provider in debug builds; reCAPTCHA Enterprise `recaptcha_site_key` in release). Zero client API keys exist in the codebase. Supports Chat with PDF (embedded text extraction + rendered page images for scans), Summarize & Plain Explanation, Structured Extraction (with one-tap "Add Expiry to Wallet"), Translation, Document Compare, and Workflow Advice with direct tool launches. Enforces daily usage quotas (`5/day` Free, `200/day` Premium), offline detection, 60s request timeouts, and streaming updates.
- **Play-Compliant Privacy & Minimal Permissions:**
  - Requests **zero** broad storage permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`). Media is imported exclusively via **Android Photo Picker** (`PickVisualMedia` / `PickMultipleVisualMedia`) and **Storage Access Framework** (`OpenDocument` / `CreateDocument`).
  - `POST_NOTIFICATIONS` is requested contextually in the Reminders / Document Wallet screen with clear user rationale.
- **Reserved Configurable Ad Containers (`AdPlacementManager` & `FileDudeAdContainer`):**
  - Rectangular ad containers are pre-designed for safe non-intrusive placements (`HOME_FEED`, `TOOLS_HUB`, `FILES_FOOTER`, `REMINDERS_FOOTER`), collapsing completely to `0.dp` for `PREMIUM` users and whenever `AdConfig.IS_ADMOB_CONFIGURED == false` (no fake ads or misleading boxes).

---

## External Setup & Configuration Required for Production Release

| Service | Current State in Project | Action Required Before Play Release |
|---|---|---|
| **Google Sign-In & Firestore** | Provisioned (`gen-lang-client-0710782195`, DB `ai-studio-android-filedude-5861cf48-b063-419f-aa8d-57f169986bd8`). Firestore rules verified via emulator (4/4 tests passed) & deployed. Debug keystore SHA-1/SHA-256 registered. | Add your **Play App Signing / Release keystore SHA-1 and SHA-256** fingerprints in Firebase Console before publishing release APK/AAB. |
| **Firebase AI Logic & App Check** | Provisioned (`ai_logic` enabled, `recaptcha_site_key` saved in `firebase_applet_config.xml`, `AppCheckInitializer` configured before `setContent`). | Verify Play Integrity / reCAPTCHA Enterprise enforcement in Firebase App Check console for release signing certificate. |
| **Phone OTP & Email/Password Auth** | UI explains Firebase provider status honestly instead of faking OTP/password logins. | Enable **Phone** (requires Play Integrity + Firebase Blaze plan) and **Email/Password** providers in Firebase Authentication Console, then wire `PhoneAuthProvider` / `signInWithEmailAndPassword`. |
| **Google Play Billing** | Real `BillingClient` v9.1.0 integrated (`filedude_premium_monthly`, `filedude_premium_annual`, `filedude_premium_lifetime`). Localized prices & entitlements query live from Play. | Create matching SKUs & base plans in Google Play Console and test with License Testers in closed testing. Optionally add server-side Play Developer API purchase verification. |
| **Google AdMob & UMP Consent** | `AdConfig.IS_ADMOB_CONFIGURED = false` (ad slots collapse cleanly; zero crash without App ID). | Add `play-services-ads` + `user-messaging-platform`, add `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" .../>` in `AndroidManifest.xml`, set real Banner/Native unit IDs, and flip `IS_ADMOB_CONFIGURED = true`. |
| **Release Upload Keystore** | `assembleDebug` APK and `bundleDebug` AAB build and verify cleanly. Release signing reads `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` env vars. | Supply your release upload keystore secrets when running `gradle assembleRelease` / `gradle bundleRelease`. |

---

## Master Requirement & Verification Checklist

Status categories:
- `Completed and tested` — Implemented and verified by automated JVM/Robolectric tests, Compose UI tests, or Firestore emulator rules tests.
- `Implemented but not tested` — Implemented and compiled cleanly in debug APK/AAB; requires physical Android device/emulator hardware or live network services for interactive verification.
- `Requires external configuration` — Requires external console credentials, SKUs, or account configuration prior to production launch.

| Area | Requirement | Status |
|---|---|---|
| **Brand & Design** | Template 3 `#F7FAF8` / `#24845D` theme, custom typography, light 5-tab bar, adaptive rail | `Completed and tested` |
| **Splash & App Icon** | Custom adaptive `ic_launcher` icons, Android 12+ SplashScreen theme + animated Compose splash, readiness gate | `Completed and tested` |
| **Navigation & Deep Links** | Type-safe 5-tab Navigation Compose, tab state preservation, `BackHandler`, `filedude://` + notification extras + `ACTION_VIEW`/`ACTION_SEND` | `Implemented but not tested` *(routes & `BackHandler` verified; OS intent dispatch requires device/emulator)* |
| **AI Assistant & PDF AI** | Firebase AI Logic + App Check, Chat with PDF, Summarize, Extract (+ Wallet expiry), Translate, Compare, Workflow Suggestions, Free/Premium limits, offline/limit UI | `Completed and tested` *(quota policy, DataStore persistence, PDF text extraction & LimitReached/quota UI tested in Robolectric; live Gemini streaming calls require device + Play Services)* |
| **Onboarding & Google Auth** | 3-page onboarding, Guest Mode, Google Sign-In (`CredentialManager`), Profile edit, Sign out, Account deletion | `Implemented but not tested` *(compiled; interactive Google account prompt requires device)* |
| **Phone OTP Auth** | Honest provider status UI + Firebase Console setup guidance | `Requires external configuration` |
| **Home Dashboard** | Greeting, search, bell badge, Smart File Ready hero, reorderable/expandable Quick Actions, tool studios, AI entry card, expiries, storage, recent files | `Completed and tested` |
| **Smart File Ready** | Built-in + custom DB-backed presets, live limits editor, disk-verified Pass/Fail validation engine, Save/Share | `Completed and tested` |
| **PDF Toolkit (27 tools)** | Viewer/Print, Compress, Merge, Split/Extract, Page Thumbnail Editor, Img↔PDF, Watermark/#, Annotate/Sign/AcroForms, OCR, Lock/Unlock, Metadata, Batch | `Completed and tested` *(Merge, Split, Metadata, Lock/Unlock, Img↔PDF & text extraction tested in Robolectric; Viewer/Print/OCR UI compiled)* |
| **Image Studio** | Undo/Redo, Before/After compare, Convert (JPG/PNG/WebP/BMP), Crop/Rotate/Flip, Resize/Passport px, Target KB compress, Filters/Adjustments, Matte background, Overlays, Collage, OCR, EXIF strip, Batch | `Completed and tested` *(Resize/Convert/KB Compress/Overlay tested in Robolectric; Collage/OCR UI compiled)* |
| **Video Studio** | ExoPlayer preview, Trim range, Compress/360p–1080p/Bitrate, Merge, Crop/Rotate, Speed, Watermark, Audio extract/mute/replace, Thumbnail, Info, Batch, Honest Video-to-GIF notice | `Implemented but not tested` *(compiled cleanly; Media3 codec requires device GPU/MediaCodec)* |
| **Document Wallet** | 9 categories, attachments, search/calendar grouping, 90/30/7/1 + custom alerts (`WorkManager`), `POST_NOTIFICATIONS` permission flow, Renewal history, Firestore sync | `Completed and tested` *(Room persistence & Firestore security rules 4/4 passed; notifications require Android 13+ device)* |
| **File Manager** | All/Recent/PDF/Images/Videos/Docs/Favourites/Folders/Downloads/Duplicates (SHA-256)/Large/Trash, Multi-select Batch ops, Storage Analyser, SAF & Photo Picker | `Completed and tested` |
| **Subscriptions & Ads** | Google Play Billing v9 client, Restore Purchases, Manage Play link, Configurable `AdPlacementManager` collapsing for Premium/Unconfigured | `Completed and tested` *(Ad slot gating & Billing integration tested/compiled; live checkout requires Play Console SKUs)* |
| **Settings & Legal** | Theme/tool defaults, Clear cache, Analytics opt-in, Permissions summary, Compliance dialog, Privacy Policy, Terms, Open-Source Licences, Feedback | `Implemented but not tested` |

---

## Google Play Compliance Audit Checklist

| Policy Requirement | Implementation Status | Evidence / Verification | External Action Needed |
|---|---|---|---|
| **Photo Picker & SAF (No Broad Storage)** | Compliant | `grep` confirms 0 occurrences of `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`. Only `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` in Manifest. | Declare Data Safety in Play Console. |
| **Contextual Runtime Permissions** | Compliant | `POST_NOTIFICATIONS` requested inside Document Wallet banner via `ActivityResultContracts.RequestPermission()`. | None. |
| **Account Deletion Policy** | Compliant | In-app `Delete Account & Cloud Data` button deletes Firestore user collections + Firebase Auth account while keeping local phone files safe. | Provide web account-deletion URL on Play Store listing. |
| **Subscriptions & Billing** | Compliant | Uses `BillingClient` 9.1.0, acknowledges purchases, shows Play localized pricing or honest pending notice, links to Play subscription management. | Publish SKUs in Play Console. |
| **AI Security & App Check** | Compliant | Zero client API keys (`GEMINI_API_KEY` absent); Firebase AI Logic requests protected via App Check (`AppCheckInitializer` runs prior to `setContent`). | Enforce App Check Play Integrity in Firebase Console for production release. |
| **Privacy Policy & Disclosures** | Compliant | Accessible in Settings -> Privacy Policy & Auth screen; discloses offline processing, optional Firestore sync, and Firebase AI document processing. | Host same Privacy Policy on public HTTPS URL for Play Console. |
| **App Icon & Store Listing** | Compliant | Custom FileDude adaptive icon & `app_name` = `"FileDude"` (<= 30 chars, no buzzwords). | Upload screenshots & feature graphic to Play Console. |

---

## Build & Verification Commands

```bash
gradle assembleDebug
gradle bundleDebug
gradle :app:generateSignedReleaseApk   # Requires KEYSTORE_PATH (or my-upload-key.jks), STORE_PASSWORD, KEY_PASSWORD
gradle :app:testDebugUnitTest
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

---

## Install on Your Android Phone Without USB Debugging

1. **Direct APK Export (Fastest):** Use the AI Studio **Settings menu -> Generate APK/AAB** (builds `app-debug.apk` signed with `debug.keystore`), transfer the `.apk` to your phone (or download directly in your mobile browser), tap the file, and allow **Install unknown apps** when Android prompts.
2. **GitHub Actions Automated Build (`.github/workflows/build-apk.yml`):**
   - Push this project to GitHub via AI Studio's **Push to GitHub** menu (or export as ZIP and push).
   - GitHub Actions automatically restores `debug.keystore` from `debug.keystore.base64`, runs `gradle assembleDebug testDebugUnitTest`, verifies the APK signature, and uploads **`FileDude-Debug-APK`**.
   - Open your GitHub repository -> **Actions** -> latest **Build FileDude Android APK** run -> **Artifacts** -> tap **FileDude-Debug-APK** to download the zip on your phone, extract `app-debug.apk`, and tap to install.
3. **Signed Release APK & Google Play Internal Testing:**
   - Generate your upload keystore (`keyAlias = "upload"`) once:
     `keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`
   - Configure `KEYSTORE_PATH` (optional if saved as `my-upload-key.jks` at project root), `STORE_PASSWORD`, and `KEY_PASSWORD` (and `RELEASE_KEYSTORE_BASE64` in GitHub Secrets).
   - Run `gradle :app:generateSignedReleaseApk` locally or push to GitHub to download **`FileDude-Release-APK`** (`app-release.apk`) and **`FileDude-Internal-Play-AAB`** (`app-release.aab`) without publishing publicly.
