# 🟢 Caprichómetro (Android)

> Tus caprichos, tus dudas y tus planes en 5 minutos.

Este repositorio contiene la aplicación nativa de **Android** para Caprichómetro, desarrollada en Kotlin con Jetpack Compose y construida bajo estrictos estándares de código limpio, separación de responsabilidades y **Arquitectura Hexagonal**.

---

## 🚀 Ubicación y Apertura del Proyecto Independiente

Este es el proyecto standalone oficial para la plataforma Android. Para abrirlo y ejecutarlo:

1. **Clonación del Repositorio:**
   Clona el repositorio inicializando automáticamente el submódulo de documentación compartida (`shared-docs/`):
   ```bash
   git clone --recurse-submodules https://github.com/adrianrobaina102/Caprichometro-Android.git
   ```
   Si ya lo clonaste sin submódulos, inicialízalo con:
   ```bash
   git submodule update --init --recursive
   ```

2. **Apertura en Android Studio:**
   - Abre Android Studio.
   - Selecciona **File > Open...** y elige la carpeta raíz `Caprichometro-Android`.
   - Espera a que Gradle sincronice el proyecto.

3. **Configuración de Firebase:**
   - Coloca tu archivo `google-services.json` dentro del módulo `app/` (`app/google-services.json`).
   - *Nota de seguridad:* Este archivo contiene credenciales locales y está explícitamente ignorado por `.gitignore`, por lo que nunca debe subirse al control de versiones.

4. **Ejecución de Pruebas Unitarias:**
   Las pruebas unitarias se ejecutan localmente en la máquina sin necesidad de emulador:
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 🏗 Arquitectura y Estructura del Proyecto

El proyecto aísla la lógica de negocio de los frameworks externos dividiéndose en tres capas principales:

- **Dominio (`domain`):** Modelos puros de negocio (`Caprice`, `Friendship`, `User`, `UserProfile`, `UsernameRules`) y puertos (interfaces de repositorio como `CapriceRepository`, `FriendshipRepository`, `UserProfileRepository`, `AuthRepository`). Sin dependencias de Android ni Firebase.
- **Aplicación (`application`):** Casos de uso con responsabilidad única que orquestan las operaciones (`SaveCapriceUseCase`, `GetCapricesUseCase`, `SignInUseCase`, `RegisterUseCase`, `SignInWithGoogleUseCase`, `SignOutUseCase`, `DeleteAccountUseCase`, `GetIncomingFriendRequestsUseCase`, `RespondToFriendRequestUseCase`, `GetAcceptedFriendsUseCase`, etc.).
- **Infraestructura (`infrastructure`):** Adaptadores tecnológicos y UI.
    - **Data / Auth (`infrastructure/data`, `infrastructure/auth`):** Implementaciones reales contra Firebase Firestore y Firebase Auth.
    - **UI (`infrastructure/ui`):** Pantallas construidas 100% con Jetpack Compose (`CapriceScreen`, `ActivePollScreen`, `ResultScreen`, `AuthScreen`, `AddFriendScreen`), ViewModels reactivos (`CapriceViewModel`, `AuthViewModel`, `FriendshipViewModel`) y navegación basada en estados locales.

---

## 📚 Documentación Compartida y Reglas de Desarrollo

- Consulta [AGENTS.md](AGENTS.md) para conocer las pautas de desarrollo para agentes y colaboradores.
- Consulta el submódulo `shared-docs/` para el contrato de datos compartido de Firebase (`shared-docs/architecture/firebase-data-contract.md`) y el flujo de trabajo con Git (`shared-docs/development/git-workflow.md`).
