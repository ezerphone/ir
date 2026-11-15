# Offline IR Remote (Tiqiaa USB-C dongle)

A fully **offline** universal IR remote for Android that drives the **Tiqiaa USB-C IR
dongle** (the one the ZaZa Remote app uses). No account, no internet, no ads — the IR code
database ships inside the app.

> Personal interoperability project for hardware you own. The repo is **private** because
> it bundles ZaZa's proprietary native libraries (see *Legal* below). Don't publish it.

## How it works

The dongle is a **Silicon Labs CP210x USB-UART bridge**, `VID 0x10C4` (or `0x045E`),
`PID 0x8468`. Android talks to it over the USB Host API: interface 0, two bulk endpoints
(IN `0x80`, OUT). The IR encoding and the dongle framing live in two native libraries
lifted from ZaZa Remote 5.4.0:

| Library | Role |
|---|---|
| `liblocalirdb.so` | The offline IR database **and** the match/encode engine. Opened with `irdbOpen(ctx, null)` — the data is baked into the `.so`, nothing is downloaded. |
| `libtiqiaadev.so` | The dongle driver: builds/sends IR, decodes responses. |
| `libtiqiaautil.so` | Support lib for the two above. |

These are bound through small JNI "shim" classes whose package/class/field/method names
match exactly what the native code looks up:

- `com.tiqiaa.local.LocalIrDb` (+ nested `DbRemote/DbBrand/DbIrKey/DbInfrared/MachineTypeInfo`)
- `com.icontrol.dev.TiqiaaUsbController`, `IControlIRData`, `IrData`

**Do not rename anything in those classes** or JNI binding breaks. `proguard-rules.pro`
keeps them under R8.

## Code coverage (4 layers)

1. **Embedded ZaZa DB** (`liblocalirdb.so`) — offline, native byte codes, sent verbatim.
2. **Bundled open DBs** in `assets/smartir/` — **SmartIR**: 337 air-conditioner models
   (121 brands) + 52 TVs. This is what fixes the sparse-AC-offline problem. Codes are
   Broadlink/Pronto encoded and decoded to raw microsecond signals.
3. **Cache-on-use from the cloud** (`CloudCacheSource`, scaffolded) — fetch a missing
   device once, then store it forever. Grows the offline library with real use.
4. **Learn mode** — point the physical remote at the dongle; captures the exact native
   code. The guaranteed path for anything missing, including any AC.

Everything the user adds/learns is saved in app storage and can be **exported/imported**
as a JSON backup (main screen ⋮ menu).

## Known calibration point

Codes from the **embedded DB** and from **learn** are already in Tiqiaa's native byte
format and send as-is (known-good). Codes from **SmartIR** are raw microsecond timings and
must be re-encoded into that native format before `TiqiaaUsbController.t()`. That single
conversion is isolated in `TiqiaaTx.ForeignEncoder` and marked as the calibration point.

To confirm it on a device: learn any button → read its native bytes `B` and
`T = IrData.p(ctx,0,0,B)` (bytes→microseconds) → verify the encoder reproduces `B` from
`T`, adjusting until the round-trip matches. **Until calibrated, use Learn for SmartIR
devices that don't fire.** AC coverage via Learn is 100% offline and always works.

## Project layout

```
app/src/main/
  java/com/tiqiaa/local/LocalIrDb.java        JNI: offline DB
  java/com/icontrol/dev/*.java                JNI: dongle + IR format
  java/com/offlineremote/app/
    usb/DongleManager.java                    discover/open/send/learn
    ir/*.java                                 IrSignal, Broadlink/Pronto codecs, TiqiaaTx
    sources/*.java                            LocalIrdbSource, SmartIrSource, CloudCacheSource
    model/*.java, store/RemoteStore.java      saved remotes + export/import
    ui/*.java                                 Main / AddDevice / Remote / Learn
  jniLibs/{arm64-v8a,armeabi-v7a}/*.so         bundled native libs
  assets/smartir/                              open AC/TV database + index.json
```

## Building

CI builds the APK automatically — push to `main` and grab it from the **Actions** run's
`offline-ir-remote-debug` artifact. Locally: `./gradlew assembleDebug` (JDK 17, Android
SDK). Min Android 5.0 (API 21); needs a phone with USB Host/OTG.

## Legal

`liblocalirdb.so` / `libtiqiaadev.so` / `libtiqiaautil.so` are Tiqiaa/ZaZa property,
included here only for private interoperability with a dongle you own. SmartIR data is
under its own open-source license (see the SmartIR project). Keep this repository private
and do not redistribute.
