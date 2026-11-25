# Savart — app Android

App nativa (Capacitor 6) de Savart. La parte web **no vive aquí**: en cada compilación
GitHub baja la PWA actual de [erkdevcr/savart](https://github.com/erkdevcr/savart)
(solo lectura) y la mete en la app.

## Bajar el APK
Pestaña **Releases → latest → `savart.apk`**.

## Compilar
- Automático en cada cambio a `main`.
- A mano: **Actions → Build APK → Run workflow** (por ejemplo, después de publicar una versión nueva de la PWA).

## Secretos (Settings → Secrets and variables → Actions)
- `DEBUG_KEYSTORE_B64` — llave de firma de la Mac (`~/.android/debug.keystore`) en base64.
  Con ella el login nativo de Google sigue funcionando y el APK se instala encima del anterior.
- `WEB_REPO_TOKEN` — solo si `erkdevcr/savart` es privado (token de solo lectura).
