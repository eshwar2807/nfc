# NFC Wallet

An Android app for your Pixel that **captures an NFC card and re-uses it from the phone**
via Host Card Emulation (HCE).

## ⚠️ Read this first — what a phone can and cannot do

Android's HCE and NFC reader mode have hard limits that no app can work around
on a **non-rooted** phone:

| Your card type | Can the phone replace it? |
|---|---|
| **ISO-DEP / Type-4** (APDU-based smartcards, many NDEF tags, some transit/badge systems) | **Yes** — this app emulates it. |
| **NDEF tags** (URL / text / contact) | **Yes** — the phone re-presents the same NDEF data. |
| **MIFARE Classic / UID-based** (most office & apartment fobs, gym cards, cheap hotel keys) | **No.** These are read by the card's fixed UID, and a stock phone **cannot change its NFC UID**. No app can clone them; that needs special hardware (a "magic" card, Proxmark, or Flipper Zero). |
| **Bank / credit / EMV cards** | **No** — cryptographically protected, and cloning them is illegal. |

**So the first thing to do is scan your card and see what type it is.** The app
tells you directly whether emulation is supported. Only capture and use cards
**you are authorized to use.**

## Using the app

1. Install the APK and grant nothing special — only the NFC permission is used.
2. Open **NFC Wallet**, hold your card to the back of the phone.
3. The app shows the UID, technologies, and whether it can be emulated. Name it and **Save**.
4. Tap **Use this card** to make it active.
5. To use it: unlock the phone and tap it to the reader (with the app installed,
   the OS routes matching taps to it). For NDEF cards this works out of the box.

### Making a specific reader accept the phone (ISO-DEP)

Readers select an application by its **AID**. Edit
`app/src/main/res/xml/apduservice.xml` and add the AID your reader expects, then
rebuild. The default includes the standard NDEF app AID (`D2760000850101`).

## Building

Already built for you as a debug APK, and CI rebuilds it on every push.

**Locally (Android Studio):** open the project and Run, or:

```bash
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

**Via GitHub Actions:** every push runs `.github/workflows/android.yml`, which
builds the APK and uploads it as the artifact **nfc-wallet-debug-apk**
(Actions tab → latest run → Artifacts).

Requirements to build: JDK 17, Android SDK (platform 35, build-tools 35.0.0).
`minSdk` is 26; `targetSdk`/`compileSdk` 35.

## Project layout

```
app/src/main/java/com/example/nfcwallet/
  MainActivity.kt            Scans cards (NFC reader mode), saves/lists profiles
  CardEmulationService.kt    HCE service: NDEF Type-4 emulation + APDU replay
  CardProfile.kt             Captured-card model (+ emulationSupported())
  ProfileStore.kt            Persists profiles in SharedPreferences
  ProfileAdapter.kt          Saved-cards list
  Hex.kt                     Hex encode/decode
app/src/main/res/xml/apduservice.xml   HCE AIDs the OS routes to this app
```

## Legal / ethical note

Use this only with cards you own or are explicitly authorized to use. Cloning
access credentials you don't have permission for, or payment cards, is illegal
in most jurisdictions.
