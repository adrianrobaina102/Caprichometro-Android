# 🗺️ Hoja de Ruta y Guía de Pruebas — Caprichómetro Android

Estado del proyecto, comparativa con iOS, esquema de arquitectura y guía de pruebas tras la actualización del **4 de noviembre de 2026**.

---

## 📊 1. Estado Actual vs iOS

| Funcionalidad | iOS (Daniel) | Android (Adrián) | Estado |
| :--- | :--- | :--- | :---: |
| **Optimización WebP (800 px / 75%)** | ✅ `SDWebImageWebPCoder` | ✅ `ImageCompressor` | Paridad total |
| **Almacenamiento Base64 (0€ / Sin tarjeta)** | ✅ Guardado en `imageUrl` | ✅ `Base64ImageStorageRepository` | Paridad total |
| **Visualización fluida (Anti-parpadeo)** | ✅ `CapriceImageView` estable | ✅ `ImageModelHelper` (`LruCache`) + `remember` | Paridad total |
| **Feed Social de Inicio (`Home`)** | ✅ `CapriceFeedView` | ✅ `HomeScreen` + `GetFriendsCapricesUseCase` | Paridad total |
| **Refresco y Resiliencia en Red** | ⚠️ Consulta estándar | ✅ Forzado `Source.SERVER` + Fallback sin índices compuestos | 🚀 Android más robusto |
| **Recuerdos en Perfil con Fotos** | ⏳ Pendiente en iOS | ✅ Cuadrícula de fotos en `ProfileScreen` | 🚀 Android por delante |
| **Cámara Nativa (Disparo directo)** | ✅ Selector Cámara / Galería | ⚠️ Solo PhotoPicker de galería | 🔴 **ÚNICA PENDIENTE DE PARIDAD** |

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

## 🎯 4. Próximos Pasos en el Desarrollo

### Prioridad 1: 📷 Disparo directo con la Cámara Nativa (`TakePicture`)
Actualmente solo se puede elegir de la galería. Para alcanzar **100% de paridad con iOS** en este módulo:
1. Declarar `FileProvider` en `AndroidManifest.xml` con `res/xml/file_paths.xml`.
2. Crear diálogo selector al pulsar *"Añadir foto"* en `CapriceScreen.kt`:
   - *"Hacer foto con la cámara"* (`ActivityResultContracts.TakePicture()`).
   - *"Elegir de la galería"* (`ActivityResultContracts.PickVisualMedia()`).
3. Enrutar el resultado de la cámara por el mismo compresor `ImageCompressor` a WebP.

### Prioridad 2: 🗳️ Módulo de Votaciones en Tiempo Real (Siguiente fase del proyecto)
- Persistencia de votos individuales (`caprices/{id}/votes/{userId}`).
- Cierre definitivo de votaciones y cálculo de resultados en vivo.
