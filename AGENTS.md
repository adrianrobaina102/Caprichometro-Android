# Guía del Agente — Caprichómetro Android

Este repositorio independiente contiene la aplicación Android nativa de **Caprichómetro**.

## Documentación compartida y obligatoria

Antes de realizar cambios en la arquitectura, datos o modelos, consulta la documentación compartida ubicada en el submódulo `shared-docs/`:

1. **Guía común del agente:**
   - [shared-docs/agent-guides/common.md](shared-docs/agent-guides/common.md)

2. **Guía específica de Android:**
   - `shared-docs/agent-guides/android.md`
   *(Nota: Actualmente este archivo no existe todavía en el repositorio de documentación compartida; no inventes su contenido y consulta con el equipo cuando sea necesario).*

3. **Contrato de datos de Firebase:**
   - [shared-docs/architecture/firebase-data-contract.md](shared-docs/architecture/firebase-data-contract.md)
   *(Cualquier modificación en las colecciones `users`, `usernames`, `friendships` o `caprices` debe respetar estrictamente este contrato para mantener la sincronización con iOS).*

4. **Flujo Git y Ramas:**
   - [shared-docs/development/git-workflow.md](shared-docs/development/git-workflow.md)
   *(Describe las cinco ramas funcionales acordadas: `feature/authentication`, `feature/friendships`, `feature/caprices`, `feature/voting`, `feature/profile`, el formato de commits en inglés con Conventional Commits y el procedimiento para actualizar el submódulo).*

---

## Reglas de desarrollo en Android

1. **Arquitectura Hexagonal (Ports & Adapters) estricta:**
   - `domain`: Modelos inmutables y puertos (interfaces de repositorio). Prohibido importar librerías de Android o Firebase en esta capa.
   - `application`: Casos de uso con responsabilidad única (`execute`).
   - `infrastructure`: Adaptadores de Firebase (`data`, `auth`) y vistas en Jetpack Compose con ViewModels (`ui`).
2. **Interfaz de usuario:**
   - Jetpack Compose con paleta Dark Theme (`DarkBackground = #121212`, `DarkSurface = #1E1E1E`, `LimeGreen = #D4FF00`).
   - Flujo unidireccional de datos (UDF) mediante `StateFlow<UiState>` inmutable.
3. **Credenciales y Configuración Local:**
   - `app/google-services.json` y `local.properties` están estrictamente excluidos de Git por `.gitignore` y deben mantenerse seguros localmente.
