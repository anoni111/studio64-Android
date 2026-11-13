#!/usr/bin/env bash
# Descarga nodejs-mobile (Node 18 para Android) y deja solo arm64-v8a + cabeceras en app/libnode.
# Solo hace falta si app/libnode/bin/arm64-v8a/libnode.so no esta en el proyecto.
set -euo pipefail
cd "$(dirname "$0")/.."
URL="https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v18.20.4/nodejs-mobile-v18.20.4-android.zip"
TMP="$(mktemp -d)"
curl -fSL "$URL" -o "$TMP/nm.zip"
unzip -q "$TMP/nm.zip" 'bin/arm64-v8a/*' 'include/*' -d "$TMP/x"
mkdir -p app/libnode/bin/arm64-v8a
cp "$TMP/x/bin/arm64-v8a/libnode.so" app/libnode/bin/arm64-v8a/
rm -rf app/libnode/include
cp -r "$TMP/x/include" app/libnode/include
rm -rf "$TMP"
echo "libnode listo en app/libnode"
