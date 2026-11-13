# Studio 64 para Android (arm64-v8a)

Adaptación de [supermochi64/studio64](https://github.com/supermochi64/studio64) **v0.2.4-beta** a una APK.

## Cómo funciona

- La app lleva **Node 18 embebido** ([nodejs-mobile](https://github.com/nodejs-mobile/nodejs-mobile), solo `arm64-v8a`) y ejecuta el servidor original de Studio 64 en `127.0.0.1:8064`.
- La interfaz es un **WebView** a pantalla completa que carga ese servidor.
- Está todo el código JS del servidor y de la web (`studio64-app/`), con sus dependencias (`ws`, `marked`, `dompurify`, `zod`).

### Importante: el motor del juego NO se puede compilar en el teléfono

Studio 64 compila Super Mario 64 a WebAssembly **desde tu propia ROM** con Emscripten, Python, Zig, make… (≈1 GB de herramientas de escritorio). Eso no corre dentro de una app Android, y yo no puedo incluir el motor porque contiene código y assets de Nintendo.

Por eso hay que generar el motor **una vez en un PC** y pasarlo a la app:

1. En un PC (Windows/macOS/Linux) descarga `studio64-0.2.4-beta.zip` de la [release](https://github.com/supermochi64/studio64/releases/tag/v0.2.4-beta), ábrelo con el instalador y completa el setup con tu ROM US de SM64.
2. Dentro de la carpeta de Studio 64, **comprime las carpetas `web` y `server`** en un único `.zip` (la app solo toma `web/engine`, `web/data`, `web/thumbs`, `server/data` y `server/samples`; lo demás lo ignora).
3. Pasa ese `.zip` al teléfono, abre la app y pulsa **Elegir .zip del motor**.

Es de uso privado: no publiques ese zip (contiene código y assets de Nintendo).

## Uso y límites

- **Controles:** el editor está pensado para ratón y teclado. Funciona con teclado/ratón/mando Bluetooth o USB. Sin ellos, el botón ⌨ (abajo a la derecha) muestra una barra de teclas en pantalla (Tab, WASD, flechas, Espacio, Shift/Ctrl, 1-5…). No lo probé en un dispositivo real, así que el manejo táctil de la cámara puede ser limitado.
- **Proyectos** y ajustes se guardan en la memoria interna de la app. Las descargas/exportaciones van a `Descargas/Studio64`.
- **Exportar ROM `.z64` / parche `.bps`:** funciona sin compilar, pero necesita tu ROM de SM64 US: botón ⚙ → «Importar ROM…» (acepta .z64/.v64/.n64 o un .zip). La primera exportación tarda un poco más (prepara la ROM base a partir de tu ROM). Los archivos se guardan en `Descargas/Studio64`.
- **No funciona en Android:** las imágenes/muestras que genera el setup (se traen del PC en el zip) y el asistente de IA vía suscripción de Claude. El asistente con API key de otros proveedores sí está disponible.
- Pantalla: la web se muestra con un viewport fijo de 1280 px (layout de escritorio) escalado a la pantalla; para cambiarlo edita `width=1280` en los `<meta viewport>` de `studio64-app/web/*.html`.
- Rendimiento: usa WebGL 2 y ~256 MB de memoria WebAssembly; en un Snapdragon 8 Elite debería ir bien.
- Si cambias el contenido de `studio64-app/`, sube `APP_VERSION` en `Installer.kt` para que la app vuelva a descomprimirlo.

## Licencias

Código de Studio 64: MIT (supermochi64). Basado en sm64ex y la decompilación de SM64 (términos propios). nodejs-mobile: MIT/Node.js. Fan project, sin relación con Nintendo.
