# Eye – one-finger screen magnifier handle for Android

English | [简体中文](README.zh-CN.md)

Eye puts a small floating handle on your screen. With it you can magnify **any** screen (apps, web pages, settings, lists) and pan around the zoomed view with one finger, without using the system's triple-tap or two-finger gestures.

It is built for people who find the built-in magnification gestures hard to use: low vision, tremor, limited dexterity, or anyone who mostly uses the phone one-handed.

> **Status:** early version, built and tested by one person on two phones (Android 10 on an EMUI device, Android 16). Other phones may behave differently. Feedback is very welcome, see the bottom of this page.

## What it does

| Gesture | Result |
|---|---|
| Tap the handle | Opens a quick settings panel |
| Drag the handle (quickly) | Moves the handle |
| **Press and hold, then move** | Zooms in smoothly at your finger. Keep moving and the view follows your finger. **Release to go back to normal.** |
| **Double-tap** | Zooms in smoothly and **keeps** the zoom |
| While zoomed: press the handle and move | Pans the view. Release and the zoom stays |
| Double-tap again | Zooms back out |

Details that matter in daily use:

- **Relative panning with adjustable sensitivity.** The view moves according to how far your finger moves, not where it is. Turn the sensitivity up and a tiny finger movement scrolls all the way to the top or bottom, so you never have to reach for the screen edge.
- **Your other hand stays free.** While one finger holds the handle, the other hand can scroll lists or tap things in the app underneath.
- **The handle hides under your finger** while you hold it and reappears in place when you let go, so there is no flicker on top of the zoom.
- The handle fades out when idle (optional) and can be hidden completely with a switch in the app.

## Settings (open the Eye app icon)

- **Status**: shows whether the accessibility service is really running, with a step-by-step guide to turn it on.
- **Floating ball switch**: hide or show the handle without turning the service off.
- **Restore**: "Bring back the handle" resets its position, makes it fully visible, blinks a blue ring so you can find it, and leaves zoom. "Reset all settings" restores defaults.
- **Adjust**: zoom level (1.5x to 8x), follow sensitivity (0.1 to 20), idle time before fading, faded opacity, handle size.
- **Keep Eye alive**: shortcuts for battery optimization and your phone maker's auto-start page, plus per-brand instructions (Xiaomi, Huawei/Honor, OPPO/realme/OnePlus, vivo/iQOO, Samsung). Many phones kill background apps, and then the handle disappears.
- Optional self-repair: run this once from a computer and Eye can turn itself back on when you open the app, even if the system stopped it:
  `adb shell pm grant com.eye.zoom android.permission.WRITE_SECURE_SETTINGS`

The app is in English by default and also includes Simplified and Traditional Chinese.

## Install and set up

1. Download the APK from the [Releases](../../releases) page and install it (you may need to allow installs from your browser or file manager).
2. Open **Eye**. The status card shows what to do next.
3. Tap **Open accessibility settings**, find **Eye**, turn it on and confirm.
4. Android 13 and newer: if the switch is greyed out, go to *Settings > Apps > Eye > ⋮ (top right) > Allow restricted settings*, then try again.
5. **Turn off the system's own magnification** (*Accessibility > Magnification* and its shortcuts). If both are on they fight over your touches.
6. Open the **Keep Eye alive** section and follow it for your phone brand.

## Privacy and permissions

- Eye has **no internet permission** and collects nothing.
- It uses the Accessibility Service API only to control the system's full-screen magnification and to draw the handle. It does not read screen content and does not record anything.
- It also declares: a boot receiver that does nothing (some phones only list apps in their auto-start manager if they declare one), a button to ask for battery optimization exemption, and an optional `WRITE_SECURE_SETTINGS` that only works if you grant it yourself over adb.

## Known limitations

- **The orange border** around the screen while zoomed is drawn by Android itself. Apps and accessibility services have no way to hide it.
- **Auto-start / background restrictions differ for every phone brand.** Eye tries to open your brand's page and shows manual steps and the reason if it cannot. Some brands do not let third-party apps open that page at all.
- The handle is not labelled for screen readers yet, so Eye is aimed at **low-vision** users rather than fully blind TalkBack users.
- Tested only on Android 10 and Android 16 so far.

## Build it yourself

The project is plain Java with no dependencies (min Android 8.0, target Android 14).

- **Locally:** open it in Android Studio, or run `gradle assembleRelease`.
- **On GitHub:** push the repository and the included workflow `Build APK` builds it. Download the `Eye-apk` artifact from the finished run.

Note: the workflow signs with a debug key that is generated for every run, so each build has a different signature. To install a new build over an old one you may have to uninstall the old one first.

## Feedback wanted

If you try it, it would help a lot to know:

- Your phone brand, model and Android version
- Whether the zoom-in feels smooth and the panning feels natural
- Which sensitivity and zoom level you ended up using
- Whether the handle was easy to find and reach
- What was confusing during setup

Please open an issue on GitHub, or reply where you found this.
