# Savart — app Android

App nativa (Capacitor 6) de Savart. La parte web **no vive aquí**: en cada compilación
GitHub baja la PWA actual de [erkdevcr/savart](https://github.com/erkdevcr/savart)
(solo lectura) y la mete en la app.

## Bajar el APK
Pestaña **Releases → latest → `savart-<versión>.apk`**.

## Compilar
- Automático en cada cambio a `main`.
- Solo: una vez por semana (lunes) revisa si `erkdevcr/savart` tiene un commit nuevo; si lo tiene, compila (sin token).
- A mano: **Actions → Build APK → Run workflow**.

## Firma
`signing/debug.keystore` es la llave fija de la app (alias `androiddebugkey`, contraseña estándar `android`).
Todas las compilaciones firman con ella, así cada APK se instala encima del anterior.
Su huella SHA-1 debe estar registrada en Google Cloud (cliente OAuth Android de `com.savart.app`)
para que funcione el inicio de sesión nativo de Google:

`18:0D:48:8D:EA:DB:9B:EC:C0:81:1A:85:06:8C:49:8E:AB:4B:54:D2`

Opcional: el secreto `DEBUG_KEYSTORE_B64` (llave en base64) tiene prioridad sobre el archivo.
