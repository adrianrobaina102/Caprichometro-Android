# 🟢 Caprichómetro (Android)

> Tus caprichos, tus dudas y tus planes resueltos con tus amigos en **5 minutos**.

Aplicación nativa de **Android** desarrollada en **Kotlin** y **Jetpack Compose (Material 3)** sobre **Firebase Authentication**, **Cloud Firestore** y **Firebase App Check**, diseñada bajo **Arquitectura Hexagonal (Ports & Adapters)**, **UDF reactivo (`StateFlow`)** y patrones de diseño clásicos.

---

## ✨ Funcionalidades Principales

* **Autenticación segura y `@username` único:** Registro e inicio de sesión con email/contraseña o **Google Sign-In** (`Credential Manager`), validación de `@username` (`^[a-z0-9_]{3,20}$`), reserva atómica por transacción (`SetOptions.merge()`) y protección **Firebase App Check** (Debug / Play Integrity).
* **Tu gente, bloqueos e invitaciones VIP:** Búsqueda por `@username`, envío/respuesta de solicitudes, bloqueos bidireccionales, enlaces de invitación (`https://adrianrobaina102.github.io/Caprichometro-Android/invite/?u=...` y `caprichometro://invite/...`) con extracción automática al pegar texto y tarjeta VIP interactiva.
* **Feeds *Amigos* y *General* (*cache-first* + ubicación aproximada):**
  * **Amigos:** carga inmediata apoyada en caché local (`Source.CACHE` -> `5 -> 10 -> 15 -> 20` con paginación de `20` en `20`), distancia aproximada por celda de `0.05°` (`locationCell` con respaldo de perfil `users/{uid}.locationCell` sin abrir visibilidad pública) y secciones `< 50 km` / `Caprichos más lejanos > 50 km`.
  * **General:** descubrimiento público optativo (`nearbyCell`) ordenado por cercanía (`ACCESS_COARSE_LOCATION` sin coordenadas exactas), paginación de `20` en `20` y hoja segura de autor (`GeneralAuthorProfileSheet`).
* **Creación y fotos WebP a coste cero:** Formulario validado (`Chain of Responsibility` + `Caprice.Builder`), audiencia selectiva (`visibleToUserIds`), compresión local WebP (`800 px` en caprichos / `300 px` en avatar al `75%`) y renderizado optimizado con `BoundedImageByteCache` (`4 MiB` máx. en RAM) + visor ampliable `FullScreenImageViewer`.
* **Votaciones en tiempo real de 5 minutos (`PollTiming`):** Voto idempotente mediante transacción (`db.runTransaction` en `caprices/{id}/votes/{voterId}`), porcentajes en vivo, máquina de estados (`PollState`), estrategia de veredicto (`StandardVerdictStrategy`), confirmación y **rectificación de decisión final** (`finalDecision`) y píldora de zona (`CapriceLocationPill` + `Geocoder`).
* **Temporizador y notificaciones:** Alarma de cierre a +5 minutos (`AlarmManager` / `PollExpiredReceiver`), escucha unificada en vivo por amigo (altas + decisiones finales en 1 listener), recuperación al abrir la app y deep-link directo por `capriceId`.
* **Perfil, Recuerdos y Estadísticas:** Cuadrícula de recuerdos con distintivos reales (activo, ✅/❌ ganador, sello `Verified` en empates y `Pendiente`), gráfico de dona (`DecisionDonutChart`) + 4 categorías, edición de bio/avatar, ajustes nativos de notificaciones y borrado en cascada seguro tras validar sesión reciente.

---

## 🏗 Arquitectura Hexagonal y Organización

Las dependencias apuntan hacia el núcleo puro (`domain` <- `application` <- `infrastructure`):

```text
app/src/
├── main/java/com/example/caprichometro/
│   ├── domain/                  # Núcleo puro (0 imports de Android o Firebase)
│   │   ├── model/               # Caprice + Builder, CapriceFeedSections, PollTiming, PollState, VerdictStrategy, NearbyCellGrid, InviteDeepLink, UserProfile...
│   │   ├── validation/          # Validator, BaseValidator, CapriceValidators (Chain of Responsibility)
│   │   ├── friendship/          # Friendship, FriendshipStatus, FriendshipRepository
│   │   └── repository/          # Puertos (CapriceRepository, VoteRepository, UserProfileRepository, DeviceLocationRepository...)
│   ├── application/usecase/     # Casos de uso de acción única (auth/, friendship/, profile/, voting/ y caprices/avisos)
│   └── infrastructure/          # Adaptadores externos y UI
│       ├── auth/                # FirebaseAuthRepository (Email + Credential Manager Google)
│       ├── data/                # Firestore*Repository, Base64ImageStorageRepository, FirebaseStorageImageRepository, SharedPreferences*
│       ├── factory/             # ViewModelFactory (DI manual + ViewModelProvider.Factory + invalidación de cachés)
│       ├── location/            # AndroidLocationRepository y AndroidApproximatePlaceNameRepository (Geocoder)
│       ├── notification/        # PollNotificationHelper, PollNotificationScheduler, PollExpiredReceiver
│       ├── security/            # AppCheckInstaller (variantes src/debug y src/release)
│       ├── util/                # ImageCompressor (WebP) e ImageModelHelper (BoundedImageByteCache 4 MiB)
│       └── ui/                  # Compose Screens + ViewModels + subpaquetes modulares (auth/, caprice/, components/, friendship/, home/, profile/)
├── androidTest/                 # Smoke tests Compose UI (CapriceUiSmokeTest)
└── test/                        # 32 suites unitarias (147 tests JUnit 5 + MockK + Coroutines Test + AssertJ)
```

### Requisitos técnicos y Build
- **SDK:** `compileSdk = 37` · `minSdk = 26` · `targetSdk = 37` · Java 11 · Kotlin `2.2.10` · AGP `9.4.1` · Compose BOM `2026.02.01`
- **Build Release:** `isMinifyEnabled = true` + `isShrinkResources = true` + reglas R8/ProGuard en [app/proguard-rules.pro](app/proguard-rules.pro).

---

## 🚀 Apertura, Configuración y Verificación

1. **Clonar con submódulo compartido:**
   ```bash
   git clone --recurse-submodules https://github.com/adrianrobaina102/Caprichometro-Android.git
   ```
   *(Si ya estaba clonado: `git submodule update --init --recursive`.)*

2. **Configuración local de Firebase y Google Sign-In:**
   - Añade tu `app/google-services.json` local (excluido por `.gitignore`).
   - Opcional para Google Sign-In en `local.properties`: `WEB_CLIENT_ID=<tu_web_client_id>`.

3. **Ejecutar tests, Lint y compilaciones (CI en [android-ci.yml](.github/workflows/android-ci.yml)):**
   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease compileDebugAndroidTestKotlin
   ```
   *(En Windows PowerShell: sustituye `./gradlew` por `.\gradlew.bat`.)*

---

## 📚 Documentación Compartida (`shared-docs/`)

El diseño funcional y técnico compartido entre Android e iOS vive en el submódulo [`shared-docs/`](shared-docs/README.md):

- [AGENTS.md](AGENTS.md) y [Guía específica de Android](shared-docs/agent-guides/android.md)
- [Arquitectura Hexagonal y Patrones](shared-docs/architecture/modules/hexagonal-architecture.md)
- [Usuarios, Amigos y Perfil](shared-docs/architecture/modules/users-and-profile.md)
- [Creación, Fotos WebP y Feeds](shared-docs/architecture/modules/caprices.md)
- [Votaciones, Veredicto y Decisión Final](shared-docs/architecture/modules/voting.md)
- [Temporizador y Notificaciones](shared-docs/architecture/modules/notifications.md)
- [Contrato de Datos de Firebase](shared-docs/architecture/firebase-data-contract.md)
- [Decisiones Técnicas (ADRs)](shared-docs/architecture/decisions/README.md)
- [Hoja de Ruta y Auditoría Técnica](shared-docs/roadmaps/ROADMAP.md)
