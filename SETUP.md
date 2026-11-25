# Savart Android — Guía de Build

Savart Android es la versión nativa de Savart para Android, construida con
**Capacitor 6** sobre el mismo código web existente. La autenticación con Google
se realiza a través del SDK nativo de Android en lugar de popups del navegador.

---

## Requisitos previos

| Herramienta | Versión mínima | Instalación |
|-------------|---------------|-------------|
| Node.js | 18+ | https://nodejs.org |
| Java JDK | 17+ | https://adoptium.net |
| Android Studio | Hedgehog (2023.1) o superior | https://developer.android.com/studio |
| Android SDK | API 34 (Android 14) | Instalar desde Android Studio → SDK Manager |

Verifica que Android Studio tenga instalado:
- Android SDK Platform 34
- Android SDK Build-Tools 34.x.x
- Android Emulator (opcional, para pruebas)

---

## Paso 1 — Instalar dependencias

```bash
cd savart-android
npm install
```

Esto instala Capacitor y el plugin de Google Auth.

---

## Paso 2 — Configurar Google Cloud Console

### 2a. Credenciales Android (necesarias para Sign-In nativo)

1. Ve a [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Selecciona el mismo proyecto que usa la web app
3. Click **"Create Credentials" → "OAuth client ID"**
4. Tipo: **Android**
5. Package name: `com.savart.app`
6. SHA-1 fingerprint: (ver instrucciones abajo)
7. Guarda el cliente OAuth Android

### 2b. Obtener el SHA-1 del keystore de debug

Para desarrollo, usa el keystore de debug de Android:

```bash
# En Mac/Linux:
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android

# En Windows:
keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore ^
  -alias androiddebugkey -storepass android -keypass android
```

Copia el valor **SHA1** y pégalo en Google Cloud Console.

### 2c. Descargar google-services.json

1. En [Firebase Console](https://console.firebase.google.com) o Google Cloud Console
2. Descarga el archivo `google-services.json`
3. Colócalo en `android/app/google-services.json`

> **Sin Firebase:** Puedes generar un `google-services.json` directamente desde
> Google Cloud Console sin activar Firebase. Ve a:
> APIs & Services → Credentials → tu proyecto Android → Download JSON

---

## Paso 3 — Agregar la plataforma Android

```bash
npx cap add android
```

Esto genera la carpeta `android/` completa.

---

## Paso 4 — Aplicar configuraciones de Android

### 4a. Copiar google-services.json

```bash
cp /ruta/a/tu/google-services.json android/app/google-services.json
```

### 4b. Agregar Google Services plugin al build.gradle raíz

Edita `android/build.gradle` y agrega dentro de `buildscript.dependencies`:

```groovy
classpath 'com.google.gms:google-services:4.4.1'
```

(Ver el template en `android-config/build.gradle`)

### 4c. Aplicar Google Services plugin al app/build.gradle

Edita `android/app/build.gradle`:

1. Agrega al inicio del archivo:
   ```groovy
   apply plugin: 'com.google.gms.google-services'
   ```

2. En `android.defaultConfig` cambia `minSdkVersion` a **22**:
   ```groovy
   minSdkVersion 22
   ```

3. En `dependencies` agrega:
   ```groovy
   implementation 'com.google.android.gms:play-services-auth:21.0.0'
   ```

(Ver el template completo en `android-config/app-build.gradle`)

### 4d. Actualizar strings.xml

Edita `android/app/src/main/res/values/strings.xml` y agrega:

```xml
<string name="server_client_id">409671846168-u60nj8ib48se183sarosn0sicu8g4vvb.apps.googleusercontent.com</string>
```

---

## Paso 5 — Sincronizar y abrir en Android Studio

```bash
# Sincroniza el contenido de www/ con el proyecto Android
npx cap sync android

# Abre el proyecto en Android Studio
npx cap open android
```

---

## Paso 6 — Build desde Android Studio

1. Android Studio abrirá el proyecto automáticamente
2. Espera a que Gradle sincronice (primera vez tarda ~5 min)
3. Selecciona un dispositivo (emulador o físico conectado por USB)
4. Presiona ▶ **Run** o usa **Build → Build Bundle(s) / APK(s)**

### Para generar APK de release:

```
Build → Generate Signed Bundle / APK → APK
```

Necesitarás un keystore de release. Para crear uno:

```bash
keytool -genkey -v -keystore savart-release.keystore \
  -alias savart -keyalg RSA -keysize 2048 -validity 10000
```

> ⚠️ Recuerda registrar el SHA-1 del keystore de **release** también en
> Google Cloud Console, o el Sign-In no funcionará en producción.

---

## Actualizar el contenido web

Después de modificar archivos en `www/`, sincroniza con:

```bash
npm run sync
# equivale a: npx cap sync android
```

No necesitas abrir Android Studio para cambios solo en HTML/JS/CSS.

---

## Estructura del proyecto

```
savart-android/
├── www/                   ← Contenido web (HTML, CSS, JS)
│   ├── index.html         ← Modificado para Capacitor (sin GIS en nativo)
│   ├── styles.css
│   ├── sw.js
│   └── js/
│       ├── auth.js        ← Modificado: soporte nativo Android
│       └── ... (resto igual que la web app)
├── android/               ← Proyecto Android (generado por Capacitor)
│   └── app/
│       └── google-services.json  ← TÚ DEBES AGREGAR ESTE ARCHIVO
├── android-config/        ← Templates y configs de referencia
├── capacitor.config.json  ← Configuración de Capacitor y plugins
├── package.json
└── SETUP.md
```

---

## Diferencias técnicas: web vs Android nativo

| Aspecto | Web (PWA) | Android (Capacitor) |
|---------|-----------|---------------------|
| **Auth** | Google Identity Services (popup) | `@codetrix-studio/capacitor-google-auth` (nativo) |
| **Token refresh** | GIS silencioso + banner de renovación | `GoogleAuth.refresh()` en background automático |
| **Service Worker** | Activo (cache del shell) | Desactivado (innecesario, archivos son locales) |
| **Drive API** | Fetch normal con Bearer token | Igual — no cambia nada |
| **IndexedDB** | Estándar del navegador | Funciona igual en Android WebView |
| **Audio** | Web Audio API | Igual — Android WebView soporta MP3/FLAC/etc |

---

## Resolución de problemas

### "Sign in failed" / Error 12501
El usuario canceló el diálogo. No es un error real — es el comportamiento normal.

### "Sign in failed" / Error 10 (DEVELOPER_ERROR)
El SHA-1 del keystore no está registrado en Google Cloud Console, o el
`package_name` en `google-services.json` no coincide con `com.savart.app`.

### "GoogleAuth plugin not available"
Asegúrate de haber corrido `npm install` y `npx cap sync` antes de abrir
Android Studio.

### El audio no reproduce / CORS errors
El WebView de Android debería poder reproducir MP3, FLAC, AAC, OGG y WAV.
Si ves errores de CORS con la Drive API, verifica que el token esté vigente
(el banner de renovación debería aparecer automáticamente).

### Fuentes no cargan en release
En build de release con minificación, asegúrate de que la política de red
permita las fuentes de Google. Alternativa: descarga DM Sans y Baskervville
y colócalos en `www/fonts/` para servir localmente.

---

## Plugins usados

| Plugin | Versión | Uso |
|--------|---------|-----|
| `@capacitor/core` | ^6.0.0 | Runtime de Capacitor |
| `@capacitor/android` | ^6.0.0 | Plataforma Android |
| `@codetrix-studio/capacitor-google-auth` | ^3.4.0 | Google Sign-In nativo |

---

*Savart v1.7.63 — Android build vía Capacitor 6*
