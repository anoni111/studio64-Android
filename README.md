# Studio 64 for Android (arm64-v8a)

An Android APK adaptation of [supermochi64/studio64](https://github.com/supermochi64/studio64), based on version `0.2.4-beta`.

## How it works

- The app bundles Node 18 through [nodejs-mobile](https://github.com/nodejs-mobile/nodejs-mobile) for `arm64-v8a` and runs Studio 64's local server at `127.0.0.1:8064`.
- A full-screen Android WebView loads the editor from that local server.
- The APK contains the JavaScript server, web interface, and dependencies. It does **not** contain the compiled Super Mario 64 WebAssembly engine or a game ROM; prepare the engine on a desktop and import it as described below.

## Android requirements

- Android 8.0 (API 26) or later on an `arm64-v8a` device. Other CPU architectures are not supported by this APK.
- The editor uses WebGL 2 and approximately 256 MB of WebAssembly memory. Performance depends on the phone's GPU and available memory.
- A keyboard, mouse, or Bluetooth/USB game controller is recommended. The on-screen keyboard button (`⌨`, at the bottom right) provides keys such as Tab, WASD, arrows, Space, Shift/Ctrl, and 1–5. Touch camera controls may be limited; they have not been tested on a physical device.

## Prepare and import the game engine

The Super Mario 64 WebAssembly engine must be built once on a Windows, macOS, or Linux computer. It cannot be compiled inside the Android app. The desktop setup uses tools such as Emscripten, Python, Zig, and make, and may download about 1 GB of toolchain files.

1. On a computer, download the [Studio 64 v0.2.4-beta desktop release](https://github.com/supermochi64/studio64/releases/tag/v0.2.4-beta), run its installer, and complete setup with your own original, unmodified U.S. (NTSC) Super Mario 64 ROM.
2. In the configured Studio 64 folder, create one ZIP containing the `web/` and `server/` folders. Preserve their folder names and contents. The ZIP must include `web/engine/sm64.us.f3dex2e.wasm` and `web/engine/sm64.us.f3dex2e.js`; the Android importer also brings in `web/data/`, `web/thumbs/`, `server/data/`, and `server/samples/`.
3. Transfer the ZIP to your phone. Launch the APK and, when prompted, tap **“Elegir .zip del motor”** (“Choose engine ZIP”). Keep the app open while it imports the files.

If the ZIP is missing either required `.wasm` or `.js` engine file, the import will fail. The engine ZIP and the phone's ROM import below are separate steps: the engine ZIP is needed to load the game engine; importing a ROM with the gear is for ROM/BPS export.

## Import a ROM on the phone

To enable ROM and BPS exports, tap the **⚙** button at the bottom right, then choose **“Importar ROM…”** (“Import ROM…”). Select your own original, unmodified U.S. (NTSC) Super Mario 64 ROM as a `.z64`, `.v64`, or `.n64` file, or a `.zip` containing one. The ROM must be 8 MiB. The app normalizes `.v64` and `.n64` byte order and checks the N64 header, game title, and U.S. region before saving it in the app's private data.

No game ROM is included. Use the original U.S. ROM compatible with the ROM-export patch. A file can pass the importer's basic size/region checks and still be rejected later by the patch if it is a different revision, modified, or a bad dump. The first ROM/BPS export may take longer while Studio 64 prepares the ROM base.

The ⚙ menu also includes **“Reimportar motor (.zip)…”** (“Re-import engine ZIP…”) if you need to replace the installed engine files.

## Usage and limitations

- Projects and settings are stored in the app's internal storage. On Android 10 or later, exports are saved to `Downloads/Studio64`; on Android 8 and 9, they are saved in the app-specific Downloads folder.
- Setup-generated images and samples need to be included in the ZIP transferred from the desktop. The Claude subscription-based AI assistant is not available in this Android build; AI providers configured with their own API key are supported.
- The web editor uses a fixed 1280 px desktop viewport scaled to the phone screen. To change it, edit `width=1280` in the `<meta name="viewport">` tags in `studio64-app/web/*.html`.
- If you change the contents of `studio64-app/`, increment `APP_VERSION` in `app/src/main/java/com/studio64/android/Installer.kt` so the app unpacks the updated files after installation.

## Build notes

The GitHub Release APK is a debug-signed test build for `arm64-v8a`, not a production-signed package.

## Licenses

Studio 64 is MIT-licensed by `supermochi64`. It is based on `sm64ex` and the Super Mario 64 decompilation under their respective terms. `nodejs-mobile` and Node.js are MIT-licensed. This is a fan project and is not affiliated with Nintendo.
