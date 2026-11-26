package com.example.caprichometro.infrastructure.ui.app

import androidx.compose.runtime.saveable.Saver

/**
 * Rutas tipadas de navegación principal de Caprichómetro.
 */
sealed interface AppRoute {
    data object Home : AppRoute
    data object FriendPoll : AppRoute
    data object Create : AppRoute
    data object ActivePoll : AppRoute
    data object Result : AppRoute
    data object AddFriend : AppRoute
    data object Profile : AppRoute

    companion object {
        val Saver: Saver<AppRoute, String> = Saver(
            save = { route ->
                when (route) {
                    Home -> "home"
                    FriendPoll -> "friend_poll"
                    Create -> "create"
                    ActivePoll -> "active"
                    Result -> "result"
                    AddFriend -> "add_friend"
                    Profile -> "profile"
                }
            },
            restore = { key ->
                when (key) {
                    "friend_poll" -> FriendPoll
                    "create" -> Create
                    "active" -> ActivePoll
                    "result" -> Result
                    "add_friend" -> AddFriend
                    "profile" -> Profile
                    else -> Home
                }
            }
        )
    }
}
