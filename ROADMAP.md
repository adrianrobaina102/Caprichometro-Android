# 🗺️ Hoja de Ruta y Guía de Pruebas — Caprichómetro Android

Estado del proyecto, esquema resumido de arquitectura y checklist de pruebas tras la actualización del **4 de noviembre de 2026**.

---

## 📊 1. Estado Actual vs iOS

| Funcionalidad | iOS (Daniel) | Android (Adrián) | Estado |
| :--- | :--- | :--- | :---: |
| **Optimización WebP (800 px / 75%)** | ✅ `SDWebImageWebPCoder` | ✅ `ImageCompressor` | Paridad total |
| **Almacenamiento Base64 (0€ / Sin tarjeta)** | ✅ Guardado en `imageUrl` | ✅ `Base64ImageStorageRepository` | Paridad total |
| **Visualización fluida (Anti-parpadeo)** | ✅ `CapriceImageView` estable | ✅ `ImageModelHelper` (`LruCache`) + `remember` | Paridad total |
| **Feed Social de Inicio (`Home`)** | ✅ `CapriceFeedView` | ✅ `HomeScreen` + `GetFriendsCapricesUseCase` | Paridad total |
| **Recuerdos en Perfil con Fotos** | ⏳ Pendiente en iOS | ✅ Cuadrícula de fotos en `ProfileScreen` | 🚀 Android por delante |
| **Cámara Nativa (Disparo directo)** | ✅ Selector Cámara / Galería | ⚠️ Solo PhotoPicker de galería | 🔴 **PENDIENTE EN ANDROID** |

---

## ⚡ 2. Esquema Resumido de Clases (Feed e Imágenes)

```text
[ 🏠 HomeScreen.kt — Pantalla de Inicio; recalcula cada 60s "Ahora/Xm/Xh" y "Activa/Cerrada" ]
       │
       ▼ (1. Al abrir Inicio o pulsar 'Actualizar')
[ ⚙️ CapriceViewModel.kt — Pone isFeedLoading = true (ruedita de carga) y pide el feed ]
       │
       ▼ (2. Ejecuta la lógica de negocio)
[ 🧠 GetFriendsCapricesUseCase.kt — Descarga las dudas de tus amigos ]
       ├── GetAcceptedFriendsUseCase.kt (filtra amigos aceptados y sin bloqueo)
       ├── FirestoreCapriceRepository.kt (descarga en paralelo hasta 20 dudas por amigo)
       └── CapriceFeedItem.kt (une cada Caprice con el @username de su autor)
       │
       ▼ (3. Actualiza CapriceState: isFeedLoading = false, feedItems = top 20)
[ 🃏 CapriceFeedCard (en HomeScreen.kt) — Dibuja @username, foto WebP sin parpadeo y opciones A/B ]
       │
       ▼ (4. Al tocar una tarjeta de un amigo)
[ ⏱️ ActivePollScreen.kt (con authorUsername) — Muestra "Duda de @amigo" y su cuenta atrás ]
```

---

## 🧪 3. Roadmap de Pruebas (Qué debemos probar en el emulador / móvil)

### A. Pruebas del Feed de Inicio (`HomeScreen`)
- [ ] **Estado vacío (sin amigos o sin dudas):** Entrar a la pestaña de Inicio (`🏠`) y comprobar que aparece *"Nada por aquí todavía"* con los botones **«Actualizar»** y **«Buscar amigos»**.
- [ ] **Ver dudas de un amigo:** Con una cuenta que tenga al menos un amigo aceptado que haya publicado caprichos (desde Android o desde el iPhone de Daniel), pulsar el botón de refresco (`🔄`) arriba a la derecha y verificar que aparecen sus tarjetas ordenadas de más reciente a más antigua.
- [ ] **Insignia Activa vs Cerrada:**
  - Publicar una duda desde la cuenta de un amigo y comprobar que en el feed aparece en verde lima como **«Activa»** y tiempo **«Ahora»** / **«1m»**.
  - En dudas de hace más de 5 minutos, comprobar que aparece en gris como **«Cerrada»**.
- [ ] **Detalle de duda de un amigo (`ActivePollScreen`):**
  - Pulsar sobre una tarjeta del feed y comprobar que se abre la pantalla con el título **«Duda de @amigo»**, muestra la foto (si tiene), las opciones **A** y **B**, y el contador en tiempo real (o `00:00` / *«Votación cerrada»* si ya expiró).
  - Verificar que **no** salta notificación local de autor en tu móvil por la duda de un amigo.

### B. Pruebas de Fotos y Anti-Parpadeo (Android ↔ iOS)
- [ ] **Publicar duda con foto desde galería:** En la pestaña central (`📷`), elegir una foto con *Añadir foto*, comprobar la miniatura y el botón `✖` para quitarla, escribir pregunta + 2 opciones distintas y pulsar **Publicar**.
- [ ] **Sin parpadeo en cuenta atrás:** Verificar en `ActivePollScreen` que mientras el reloj baja segundo a segundo (`04:59`, `04:58`...), la imagen permanece completamente fija sin parpadear.
- [ ] **Compatibilidad cruzada con iOS:** Comprobar que una foto subida por Daniel desde iOS se ve en tu `HomeScreen` de Android, y que una foto subida por ti desde Android se ve en el iOS de Daniel.
- [ ] **Cuadrícula de Recuerdos en Perfil:** Entrar a la pestaña de Perfil (`👤`) y comprobar que la foto aparece en la cuadrícula de recuerdos y al abrir el detalle.

---

## 🛠️ 4. Única Tarea de Desarrollo Pendiente (Paridad con iOS)

### 📷 Captura con la Cámara Nativa (`TakePicture`)
- [ ] Declarar `FileProvider` en `app/src/main/AndroidManifest.xml` y crear `app/src/main/res/xml/file_paths.xml`.
- [ ] En `CapriceScreen.kt`, al pulsar *"Añadir foto"*, mostrar un selector:
  - **"Hacer foto con la cámara"** (`ActivityResultContracts.TakePicture()`).
  - **"Elegir de la galería"** (`ActivityResultContracts.PickVisualMedia()`).
- [ ] Pasar la foto capturada por `ImageCompressor` (WebP 800 px, 75%).
