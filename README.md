# Crossfox Drive — Dota Edition (v1.0, código fuente)

Launcher Android nativo (Kotlin + Jetpack Compose) para Samsung Galaxy Tab S6 Lite (SM-P620), VW Crossfox 2017 1.6 MSI.

## Compilar
**Opción A – Android Studio (recomendada)**
1. Android Studio (Ladybug o superior) → *Open* → carpeta `CrossfoxDrive`.
2. Deja que haga *Gradle Sync* (usa Gradle 8.9+ / JDK 17; descarga SDK 35 si lo pide).
3. *Build → Build APK(s)*. Resultado: `app/build/outputs/apk/debug/app-debug.apk`.

**Opción B – GitHub Actions (sin instalar nada)**
1. Crea un repositorio en GitHub y sube esta carpeta.
2. Pestaña *Actions* → *Build APK* → cuando termine, descarga el artefacto `crossfox-drive-debug-apk`.

**Opción C – línea de comandos**: `gradle wrapper --gradle-version 8.10.2 && ./gradlew :app:assembleDebug`

## Instalar
Copia `app-debug.apk` a la tablet → ábrelo → permite «instalar apps desconocidas» para el explorador usado.

## Launcher predeterminado
Dentro de la app: Ajustes → «Elegir app de inicio predeterminada», o en la tablet:
Ajustes → Aplicaciones → Elegir apps predeterminadas → Pantalla de inicio → *Crossfox Drive*.
Para volver al launcher original, elige One UI Home en la misma pantalla. La app no bloquea Inicio ni Ajustes.

## Música y notificaciones
Ajustes de Android → Notificaciones → Acceso a notificaciones → activar *Crossfox Drive*.
Sin esto no se muestran canción, controles ni avisos (el panel pide activarlo). Nada sale del dispositivo.

## Modo Taxi
- Botón INICIAR → panel con Waze, inDrive y YouTube Music (abren las apps reales).
- «Waze + inDrive en pantalla dividida» usa FLAG_ACTIVITY_LAUNCH_ADJACENT. Android solo lo respeta si la multiventana lo permite.
- Más fiable en Samsung: Panel Edge → Multiventana → crear «par de apps» (Waze + inDrive).

## Implementado
Launcher HOME; barra superior (hora, fecha, tipo de red); panel del vehículo; música (sesión de medios real, con permiso); navegación (abrir Waze/Maps y destino por intent); apps favoritas configurables y lista de todas las apps; aviso «No instalada»; mantenimiento de aceite con validación, barra, avisos y persistencia (DataStore); notificaciones (con permiso); modo noche (brillo de la ventana + atenuación, sin tocar ajustes globales); ajustes persistentes; Modo Taxi.

## No implementado / limitaciones
- Clima: no hay fuente real configurada, no se muestra. Combustible, temperatura, neumáticos: «Sin datos» (requiere OBD-II, pendiente).
- Ubicación y Bluetooth: no se piden permisos, no se muestran indicadores.
- La ilustración del auto es un dibujo vectorial simple y el logo es «CF» genérico; puedes reemplazarlos con arte propio.
- Tres apps simultáneas no están garantizadas por Android.
- Esta versión NO ha sido compilada ni probada en un dispositivo real.
