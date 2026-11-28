package com.example.caprichometro.infrastructure.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.example.caprichometro.R

/**
 * Traduce en presentación los mensajes de error o validación estándar emitidos por
 * casos de uso, repositorios y ViewModels usando los recursos activos de idioma (ES/EN),
 * manteniendo intactas las cadenas internas usadas por pruebas y reglas de negocio.
 */
internal object UiErrorTranslator {

    private val exactMessages: Map<String, Int> = mapOf(
        // Auth & Registro
        "El email y la contraseña son obligatorios" to R.string.err_auth_email_password_required,
        "Por favor, introduce un correo electrónico válido" to R.string.err_auth_invalid_email,
        "Error al iniciar sesión" to R.string.err_auth_sign_in_failed,
        "Todos los campos, incluida la fecha, son obligatorios" to R.string.err_auth_all_fields_required,
        "La fecha de nacimiento no puede ser en el futuro" to R.string.err_auth_birthday_future,
        "La contraseña debe tener al menos 6 caracteres" to R.string.err_auth_password_min_length,
        "Error al registrar la cuenta" to R.string.err_auth_register_failed,
        "Error al iniciar sesión con Google" to R.string.err_auth_google_failed,
        "Error al borrar la cuenta" to R.string.err_auth_delete_account_failed,
        "Credencial no válida o cancelada" to R.string.err_auth_google_invalid_credential,
        "Inicio de sesión con Google cancelado" to R.string.err_auth_google_cancelled,
        "No hay cuentas de Google disponibles en este dispositivo" to R.string.err_auth_google_no_accounts,
        "No hay usuario autenticado" to R.string.err_auth_not_signed_in,
        "No hay sesión activa" to R.string.err_auth_no_active_session,
        "Error al obtener el usuario de Firebase" to R.string.err_auth_firebase_user,
        "Error al crear el usuario de Firebase" to R.string.err_auth_firebase_create,
        "Error al obtener usuario de Firebase" to R.string.err_auth_firebase_user,
        "Error al obtener el usuario registrado" to R.string.err_auth_firebase_user,
        "Error al guardar el perfil del usuario" to R.string.err_auth_save_profile,
        "Error al comprobar el perfil existente" to R.string.err_auth_check_profile,
        "Error al registrar el perfil de Google en la base de datos" to R.string.err_auth_save_profile,
        "Por seguridad, cierra sesión y vuelve a iniciar sesión antes de eliminar tu cuenta." to R.string.err_auth_requires_recent_login,

        // Creación y validación de caprichos
        "La pregunta no puede estar vacía" to R.string.err_create_question_empty,
        "Las dos opciones de respuesta deben ser diferentes" to R.string.err_create_options_different,
        "La pregunta supera el máximo de 200 caracteres" to R.string.err_create_question_too_long,
        "Las opciones superan el máximo de 60 caracteres" to R.string.err_create_options_too_long,
        "Precio inválido" to R.string.create_price_invalid_error,
        "No se pudo subir la imagen del capricho" to R.string.err_create_image_upload,
        "No se pudo decodificar la imagen seleccionada" to R.string.err_create_image_decode,
        "Ubicación no disponible en este dispositivo." to R.string.err_location_unavailable,
        "No se pudo obtener tu ubicación aproximada." to R.string.err_location_approx_failed,
        "No se pudo obtener tu ubicación aproximada en este momento." to R.string.err_location_approx_failed,
        "Necesitas permitir la ubicación aproximada para publicar también en General." to R.string.err_create_general_permission_needed,

        // Feeds
        "No se pudo cargar el feed de amigos." to R.string.err_feed_friends_load,
        "No se pudo cargar General." to R.string.err_feed_general_load,
        "No se pudieron cargar más caprichos" to R.string.err_feed_load_more,

        // Amistades y bloqueos
        "No se puede enviar una solicitud a un usuario bloqueado." to R.string.err_friend_blocked_target,
        "Ya existe una relación entre estos usuarios." to R.string.err_friend_relation_exists,
        "No se ha encontrado ningún usuario con ese nombre." to R.string.err_friend_user_not_found,
        "Error al buscar usuario." to R.string.err_friend_search_failed,
        "Error al enviar la solicitud." to R.string.err_friend_send_request_failed,
        "No se pudo enviar la solicitud." to R.string.err_friend_send_request_failed,
        "No se pudo eliminar la amistad." to R.string.err_friend_remove_failed,
        "No se pudo bloquear al usuario." to R.string.err_friend_block_failed,
        "Error al responder a la solicitud" to R.string.err_friend_respond_failed,
        "Error al cargar usuarios bloqueados" to R.string.err_friend_load_blocked_failed,

        // Perfil
        "La biografía no puede superar los 160 caracteres." to R.string.err_profile_bio_too_long,
        "No se pudieron cargar más recuerdos" to R.string.err_profile_load_more_failed,
        "No se pudo actualizar la biografía." to R.string.err_profile_bio_update_failed,
        "No se pudo actualizar la foto de perfil." to R.string.err_profile_photo_update_failed,
        "No se pudo desbloquear al usuario" to R.string.err_profile_unblock_failed,
        "Error al eliminar la cuenta" to R.string.err_auth_delete_account_failed,
        "Error al eliminar amigo" to R.string.err_friend_remove_failed,
        "Error al bloquear usuario" to R.string.err_friend_block_failed,

        // Votaciones y veredicto
        "Opción de voto inválida" to R.string.err_vote_invalid_option,
        "El capricho ya no existe" to R.string.err_vote_caprice_missing,
        "El capricho no es válido" to R.string.err_vote_caprice_invalid,
        "No puedes votar en tu propio capricho" to R.string.err_vote_own_caprice,
        "No tienes permiso para votar en este capricho" to R.string.err_vote_no_permission,
        "La votación ya ha cerrado (límite de 5 minutos alcanzado)" to R.string.err_vote_poll_closed,
        "Ya has votado en este capricho" to R.string.err_vote_already_voted,
        "Solo los amigos del autor pueden votar en este capricho" to R.string.err_vote_friends_only,
        "No puedes votar en este capricho por un bloqueo entre ambos usuarios" to R.string.err_vote_blocked,
        "No se pudo comprobar tu voto anterior" to R.string.err_vote_check_previous_failed,
        "No se pudo comprobar el estado de bloqueo" to R.string.err_vote_check_block_failed,
        "No se pudieron sincronizar los votos" to R.string.err_vote_sync_failed,
        "No se pudo emitir el voto" to R.string.err_vote_cast_failed,
        "No se pudo guardar tu decisión final" to R.string.err_vote_final_decision_failed
    )

    private val minAgeRegex = Regex("^Debes tener al menos (\\d+) años para registrarte$")
    private val minLengthRegex = Regex("^(.+) debe tener al menos (\\d+) caracteres$")
    private val maxLengthRegex = Regex("^(.+) no puede superar los (\\d+) caracteres$")
    private val usernameTakenRegex = Regex("^El nombre de usuario @(.+) ya está en uso\\.$")
    private val incomingReqErrRegex = Regex("^Error al recibir solicitudes: (.*)$")
    private val friendsListErrRegex = Regex("^Error al recibir amigos: (.*)$")

    fun translate(rawMessage: String, resources: Resources): String {
        val trimmed = rawMessage.trim()
        if (trimmed.isEmpty()) return rawMessage

        exactMessages[trimmed]?.let { resId ->
            return resources.getString(resId)
        }
        minAgeRegex.matchEntire(trimmed)?.let { match ->
            val years = match.groupValues[1].toIntOrNull() ?: return rawMessage
            return resources.getString(R.string.err_auth_min_age, years)
        }
        minLengthRegex.matchEntire(trimmed)?.let { match ->
            val fieldLabel = translateFieldLabel(match.groupValues[1], resources)
            val min = match.groupValues[2].toIntOrNull() ?: return rawMessage
            return resources.getString(R.string.err_validation_min_length, fieldLabel, min)
        }
        maxLengthRegex.matchEntire(trimmed)?.let { match ->
            val fieldLabel = translateFieldLabel(match.groupValues[1], resources)
            val max = match.groupValues[2].toIntOrNull() ?: return rawMessage
            return resources.getString(R.string.err_validation_max_length, fieldLabel, max)
        }
        usernameTakenRegex.matchEntire(trimmed)?.let { match ->
            return resources.getString(R.string.err_auth_username_taken, match.groupValues[1])
        }
        incomingReqErrRegex.matchEntire(trimmed)?.let { match ->
            return resources.getString(R.string.err_friend_listen_requests_failed, match.groupValues[1])
        }
        friendsListErrRegex.matchEntire(trimmed)?.let { match ->
            return resources.getString(R.string.err_friend_listen_friends_failed, match.groupValues[1])
        }
        return rawMessage
    }

    private fun translateFieldLabel(rawField: String, resources: Resources): String = when (rawField) {
        "La pregunta" -> resources.getString(R.string.err_field_question)
        "Cada opción" -> resources.getString(R.string.err_field_each_option)
        else -> rawField
    }
}

@Composable
internal fun localizedUiMessage(rawMessage: String): String {
    val resources = LocalResources.current
    return UiErrorTranslator.translate(rawMessage, resources)
}

@Suppress("unused")
@StringRes
private fun keepResRef(): Int = R.string.err_auth_sign_in_failed
