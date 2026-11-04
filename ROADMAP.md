# 🗺️ Hoja de Ruta y Guía de Pruebas — Caprichómetro (Android e iOS)

Estado del proyecto, comparativa entre ambas plataformas, esquemas de arquitectura y guía de pruebas tras la actualización del **4 de noviembre de 2026**.

---

## 📊 1. Estado Actual vs iOS

| Funcionalidad | iOS (Daniel) | Android (Adrián) | Estado |
| :--- | :--- | :--- | :---: |
| **Optimización WebP (800 px / 75%)** | ✅ `SDWebImageWebPCoder` | ✅ `ImageCompressor` | Paridad total |
| **Almacenamiento Base64 (0€ / Sin tarjeta)** | ✅ Guardado en `imageUrl` | ✅ `Base64ImageStorageRepository` | Paridad total |
| **Visualización fluida (Anti-parpadeo)** | ✅ `CapriceImageView` estable | ✅ `ImageModelHelper` (`LruCache`) + `remember` | Paridad total |
| **Feed Social de Inicio (`Home`)** | ✅ `CapriceFeedView` | ✅ `HomeScreen` + `GetFriendsCapricesUseCase` | Paridad total |
| **Refresco y Resiliencia en Red** | ⚠️ Consulta estándar | ✅ Forzado `Source.SERVER` + Fallback sin índices compuestos | 🚀 Android más robusto |
| **Cámara Nativa (Disparo directo)** | ✅ Selector Cámara / Galería | ⚠️ Solo PhotoPicker de galería | 🔴 Pendiente en Android |
| **Avisos de amigos con app abierta** | ✅ `ObserveFriendCapricesUseCase` | ⚠️ Solo avisos de duda propia | 🔴 Pendiente en Android |
| **Pantalla de Perfil (`ProfileScreen`)** | ⏳ Pendiente en iOS | ✅ Recuerdos (grid 3 columnas) + Estadísticas Canvas | 🚀 **Android por delante** |
| **Estadísticas de Votación en Canvas** | ⏳ Pendiente en iOS | ✅ `DecisionDonutChart` (% "hice caso") + 4 Categorías | 🚀 **Android por delante** |
| **Visualización de Perfil de Amigos** | ⏳ Pendiente en iOS | ✅ Navegación a perfil ajeno con protección de privacidad | 🚀 **Android por delante** |
| **Ajustes de Cuenta y Privacidad** | ⏳ Pendiente en iOS | ✅ `ModalBottomSheet` (Edición, Notif, Idioma, Borrado) | 🚀 **Android por delante** |
| **Desbloqueo de Usuarios Bloqueados** | ⏳ Pendiente en iOS | ✅ Gestión completa en Perfil > Ajustes > Privacidad | 🚀 **Android por delante** |
| **Pantalla de Veredicto (`ResultScreen`)** | ⏳ Pendiente en iOS | ✅ Pantalla interactiva tras expirar los 5 minutos | 🚀 **Android por delante** |
| **Alarma con pantalla bloqueada / App cerrada** | ⏳ Solo `UserNotifications` | ✅ `AlarmManager` RTC_WAKEUP + `BroadcastReceiver` | 🚀 **Android por delante** |
| **Compartir Invitación con Share Sheet** | ⏳ Pendiente en iOS | ✅ Intent nativo con `@username` en "Tu gente" | 🚀 **Android por delante** |
| **Validación por Chain of Responsibility** | ⚠️ En ViewModel | ✅ Cadena desacoplada en dominio + Patrón `Builder` | 🚀 **Android por delante** |

---

## ⚡ 2. Esquema de Arquitectura del Feed de Inicio

```text
[ 🏠 HomeScreen.kt — Pantalla de Inicio; recalcula cada 60s "Ahora/Xm/Xh" y "Activa/Cerrada" ]
       │
       ▼ (1. Al abrir Inicio o pulsar el botón de refresco 🔄)
[ ⚙️ CapriceViewModel.kt — Activa isFeedLoading = true (spinner en botón) y gestiona el Job ]
       │
       ▼ (2. Ejecuta la lógica de negocio)
[ 🧠 GetFriendsCapricesUseCase.kt — Descarga las dudas de tus amigos ]
       ├── GetAcceptedFriendsUseCase.kt (obtiene amigos aceptados y sin bloqueo)
       ├── FirestoreCapriceRepository.kt (intenta consulta indexada a servidor; si falta índice,
       │                                  hace fallback a consulta simple y ordena en memoria)
       └── CapriceFeedItem.kt (une cada Caprice con el @username y avatar del amigo)
       │
       ▼ (3. Actualiza CapriceState: isFeedLoading = false, feedItems = lista ordenada)
[ 🃏 CapriceFeedCard (en HomeScreen.kt) — Dibuja @username, tipo, foto WebP y opciones A/B ]
       │
       ▼ (4. Al tocar una tarjeta del feed)
[ ⏱️ ActivePollScreen.kt (con authorUsername) — Detalle: "Duda de @amigo" y cuenta atrás real ]
```

---

## 🧪 3. Checklist de Pruebas y Verificación

### A. Feed de Inicio (`HomeScreen`)
- [x] **Carga real de dudas de amigos desde Firestore:** Verificado en emulador con 14 caprichos de amigos obtenidos y ordenados cronológicamente.
- [x] **Botón de refresco (`🔄`):** Animación de carga con `CircularProgressIndicator` en el botón y barra de progreso superior mientras descarga los datos más frescos del servidor.
- [x] **Resiliencia ante falta de índices:** Funciona automáticamente incluso si Firestore no tiene configurado el índice compuesto de `userId` + `timestamp`.
- [ ] **Comprobar visualmente en tu pantalla:** Ver que las tarjetas de tus amigos se muestran con su inicial en círculo lima, `@username`, etiqueta de categoría (*Compra* / *Plan*), foto (si tiene) y las opciones A y B.
- [ ] **Antigüedad y estado de la duda:**
  - Si la duda tiene menos de 5 minutos: Insignia verde **«Activa»** y tiempo relativo (*"Ahora"*, *"2m"*).
  - Si han pasado más de 5 minutos: Insignia gris **«Cerrada»** y tiempo relativo (*"1h"*, *"2d"*).
- [ ] **Navegación al detalle:** Al tocar la tarjeta de un amigo, se abre la cuenta atrás con el título *"Duda de @amigo"* sin activar notificaciones locales de autor en tu dispositivo.

### B. Fotos y Rendimiento
- [x] **Compresión WebP y Base64:** Las fotos se comprimen en memoria a ~40-60 KB sin necesidad de Firebase Storage de pago (plan Blaze).
- [x] **Eliminación de parpadeo (Anti-flicker):** Con la caché `LruCache` en `ImageModelHelper` y el `remember(imageUrl)`, la imagen se mantiene completamente fija durante el tic-tac de la cuenta atrás.
- [ ] **Interoperabilidad:** Comprobar que una duda con foto publicada desde Android se ve en el iPhone de Daniel, y viceversa.

---

## 🎯 4. Próximos Pasos en el Desarrollo (Paridad Android)

### Prioridad 1: 📷 Disparo directo con la Cámara Nativa (`TakePicture`)
Actualmente solo se puede elegir de la galería. Para alcanzar paridad en captura:
1. Declarar `FileProvider` en `AndroidManifest.xml` con `res/xml/file_paths.xml`.
2. Crear diálogo selector al pulsar *"Añadir foto"* en `CapriceScreen.kt`:
   - *"Hacer foto con la cámara"* (`ActivityResultContracts.TakePicture()`).
   - *"Elegir de la galería"* (`ActivityResultContracts.PickVisualMedia()`).
3. Enrutar el resultado de la cámara por el mismo compresor `ImageCompressor` a WebP.

### Prioridad 2: 🔔 Avisos de dudas de amigos con la app abierta (Sección 5)
Implementar la observación de amigos y recuperación de pendientes según la especificación de la Sección 5.

### Prioridad 3: 🗳️ Módulo de Votaciones en Tiempo Real (Siguiente fase del proyecto)
- Persistencia de votos individuales (`caprices/{id}/votes/{userId}`).
- Cierre definitivo de votaciones y cálculo de resultados en vivo (común para Android e iOS).

---

## 5. Ampliación técnica de paridad con iOS — añadida por Codex para Daniel

**Autoría de esta ampliación:** Codex, a petición de Daniel (iOS), el 4 de noviembre de 2026. Todo el contenido anterior a este separador es una copia íntegra del `ROADMAP.md` original de Android; esta sección se añade debajo y no sustituye sus apartados ni sus verificaciones.

### 5.1 Referencias y alcance de la comparación

- Android consultado: rama `develop`, commit `b4cfb1dfc13e52f180b359c1787a1dc91579c77d`. [Roadmap original en esa revisión](https://github.com/adrianrobaina102/Caprichometro-Android/blob/b4cfb1dfc13e52f180b359c1787a1dc91579c77d/ROADMAP.md).
- Objetivo iOS: commit `8cf06bd0a1845e23e677cc51c81982dcc80e81bd`, `feat: add caprice alerts and open them from notifications`. [Código iOS de referencia](https://github.com/DanielLuzardo/caprichometro_ios/tree/8cf06bd0a1845e23e677cc51c81982dcc80e81bd).
- Se excluyen los cambios locales de iOS posteriores a ese commit. No deben convertirse en tareas de este roadmap.
- Referencias compartidas: [contrato de Firebase](../architecture/firebase-data-contract.md), [guía iOS](../agent-guides/ios.md), [notificaciones](../architecture/modules/notifications.md) y [flujo Git](../development/git-workflow.md). No introducir colecciones, campos ni validaciones fuera de ese contrato.

### 5.2 Diferencias verificadas y puntos de integración en Android

| Bloque | Android consultado | Objetivo de paridad iOS |
| --- | --- | --- |
| Cámara | `CapriceScreen.kt` solo registra `PickVisualMedia`; el manifest no declara `FileProvider`. | Completar la prioridad 1 original, conservando el mismo flujo WebP/Base64 y la foto anterior si se cancela. |
| Avisos de amigos | Los helpers actuales emiten avisos propios de publicación/cierre. No hay observación de caprichos para avisar al receptor. | Detectar publicaciones nuevas de amigos mientras la app del receptor esté abierta y emitir un aviso local en ese teléfono. |
| Recuperación al volver | No hay checkpoint persistente de avisos por cuenta/amigo. | Consultar solo el último capricho de cada amigo y avisar una vez si aún no estaba notificado, incluso si ya cerró. |
| Pulsación | `MainActivity.onCreate` solo interpreta el destino `result`; `onNewIntent` solo hace `setIntent`. Las pantallas `active/result` pueden usar `latestCaprices.firstOrNull()`. | Conservar una ruta pendiente y cargar el documento exacto del aviso por ID, tanto en arranque en frío como con la Activity existente. |
| Tiempo visible | `HomeScreen.kt` muestra antigüedad y `Activa/Cerrada`; el detalle ya tiene cuenta atrás. | Añadir minutos restantes o tiempo desde cierre en el feed y la fecha/hora original de cierre en el detalle. |

### 5.3 Puertos, casos de uso y dependencias propuestos para Android

| Responsabilidad | Puerto o caso propuesto | Adaptación Android |
| --- | --- | --- |
| Eventos de publicaciones | `CapriceObservationRepository.observeNewCaprices(friendId, sinceTimestamp): Flow<List<Caprice>>` y `ObserveFriendCapricesUseCase`. | Adaptador Firestore con `callbackFlow`; emitir las altas válidas y retirar `ListenerRegistration` mediante `awaitClose`. |
| Lectura exacta | `CapriceDetailsRepository.getCaprice(id): Result<Caprice?>` y `GetNotificationCapriceUseCase`. | Lectura `caprices/{id}`; resolver identidad y disponibilidad del autor mediante los casos existentes de amigos/perfiles/bloqueos. |
| Publicación de aviso local | `FriendCapriceNotificationRepository` y `NotifyFriendCapriceUseCase`. | Adaptador de `NotificationManagerCompat` con resultado explícito: publicado, omitido por permiso/configuración o error. |
| Historial | `CapriceNotificationHistoryRepository` con lectura y guardado del último `(timestamp, capriceId)` por cuenta/amigo. | Persistencia local con DataStore o SharedPreferences detrás del puerto. No necesita Firestore. |
| Coordinación de sesión | Un ViewModel/coordinador de avisos con `StateFlow` para estado/error y trabajos cancelables. | Inyectar desde `ViewModelFactory` y conectar al ciclo de vida de la sesión en la raíz de la app, no solo a `HomeScreen`. |

---

## 6. Funcionalidades e Interfaces completadas en Android — Pendientes de paridad en iOS

**Autoría de esta sección:** Adrián (Android), 4 de noviembre de 2026. Recoge las interfaces, pantallas completas y módulos técnicos ya operativos en la aplicación Android que actualmente **no existen o están pendientes en iOS**, para que el equipo de iOS pueda planificar su equiparación.

### 6.1 👤 Pantalla de Perfil e Historial propio (`ProfileScreen.kt` + `ProfileViewModel.kt`)

Android cuenta con una pantalla de perfil completa accesible desde la pestaña 3 de la barra de navegación inferior, con tres subsistemas:

1. **Pestaña de Recuerdos (Memories Grid):**
   - Cuadrícula de 3 columnas que muestra el historial de todos los caprichos publicados por el usuario.
   - Renderizado con foto WebP optimizada (usando la caché `ImageModelHelper`) o tarjeta degradada temática si no tiene foto.
   - Insignias de veredicto sobre cada tarjeta: icono de tick verde o aspa roja según si hizo caso a los amigos o no, y badge interactivo con el recuento de opciones.
   - Al pulsar cualquier recuerdo, se abre el detalle de la votación con su foto y opciones.
2. **Pestaña de Estadísticas en Canvas (`DecisionDonutChart`):**
   - Gráfico de dona (donut) dibujado nativamente en Canvas con porcentaje central y desglose métrico: *"Hice caso de mis amigos en X de Y dudas"*.
   - Tarjeta *"Mis categorías"* con desglose porcentual distribuido en 4 tipos: *Compras*, *Planes*, *Ropa* y *Otros*.
   - Métricas en tiempo real en la cabecera: número total de **dudas**, dudas **resueltas** y porcentaje global de **"hice caso"**.
3. **Visualización de Perfil de Amigos:**
   - Al tocar a un amigo en la pantalla "Tu gente" (`AddFriendScreen.kt`), la app abre `ProfileScreen` en modo visitante mostrando su bio, sus métricas y sus recuerdos públicos.
   - Muestra un botón de retroceso (`<`) para volver a la lista y oculta automáticamente las opciones privadas de ajustes.
4. **Hoja inferior de Ajustes (`ModalBottomSheet`):**
   - Acceso desde el icono de engranaje (`⚙`) en la cabecera del perfil propio.
   - **Editar perfil:** Diálogo para actualizar bio y datos de presentación.
   - **Amigos:** Contador reactivo que navega a la gestión de amistades.
   - **Notificaciones:** Intent explícito hacia los ajustes nativos del sistema de la app (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`).
   - **Privacidad:** Lista de usuarios bloqueados con botón para desbloquear en tiempo real y botón de **Eliminación permanente de cuenta** (ejecuta `DeleteAccountUseCase` borrando atómicamente el perfil en `users/{uid}`, liberando el `usernames/{username}`, limpiando amistades en `friendships` y borrando la cuenta en Firebase Auth).
   - **Idioma** y **Cerrar sesión** (`signOutUseCase`).

### 6.2 🏁 Pantalla de Veredicto / Resultados (`ResultScreen.kt`)

En iOS todavía no existe interfaz de resultados al vencer la votación. En Android:
- Se navega automáticamente a `ResultScreen` al expirar la cuenta atrás de 5 minutos o al pulsar la notificación emergente de cierre.
- Muestra el veredicto final destacado (*"¡Tus amigos han hablado!"*), la pregunta, la foto del capricho, las barras de resultados con porcentajes por opción y el botón para volver al Inicio.

### 6.3 ⏰ Alarma en segundo plano con pantalla apagada (`AlarmManager` + `BroadcastReceiver`)

A diferencia de los avisos que dependen de que la app permanezca abierta:
- **`PollNotificationScheduler.kt`**: Programa la alarma con `alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)`. Esta API garantiza que el procesador del móvil se despierte a los **5 minutos exactos** (`300.000 ms`), incluso en modo de reposo profundo (*Doze mode*) o con la app completamente cerrada.
- **`PollExpiredReceiver.kt`**: `BroadcastReceiver` no exportado (`android:exported="false"`) que se activa en el instante exacto del cierre sin consumir recursos en segundo plano, construye la notificación de alta prioridad y la despacha al usuario.
- **Canal de alta importancia:** `PollNotificationHelper.createNotificationChannel` registra el canal `caprice_poll_channel` con sonido y vibración.
- **Permisos en tiempo de ejecución:** Gestión automática de `Manifest.permission.POST_NOTIFICATIONS` para Android 13+ (API 33+).

### 6.4 🤝 Gestión avanzada de Amigos e Invitaciones (`AddFriendScreen.kt`)

1. **Compartir invitación nativa (`Share Sheet`):** Botón *"Invitar a un amigo"* que abre el selector del sistema operativo compartiendo un mensaje personalizado con el `@username` del usuario para agregarse directamente.
2. **Desbloqueo de usuarios bloqueados:** Lista en tiempo real de usuarios bloqueados en Ajustes > Privacidad, con botón de desbloqueo que elimina el registro en `userBlocks/{userId}/blockedUsers/{blockedId}` sin reactivar la amistad de forma automática (cumpliendo el contrato de Firebase). iOS actualmente carece de pantalla o método de desbloqueo.

### 6.5 🏛️ Patrones de Diseño del Dominio y Validación

- **Chain of Responsibility (`Validator<T>`):** Validación desacoplada de formularios en `domain/validation/` (`NotBlankValidator` ➔ `MinLengthValidator` ➔ `DifferentOptionsValidator`). En iOS las validaciones están acopladas dentro del `ViewModel`.
- **Builder (`Caprice.Builder`):** Construcción fluida e inmutable de la entidad `Caprice` garantizando invariantes antes de crear la instancia en el dominio puro.
