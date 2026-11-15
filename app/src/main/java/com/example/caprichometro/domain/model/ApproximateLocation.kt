package com.example.caprichometro.domain.model

data class ApproximateLocation(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude.isFinite() && longitude.isFinite()) {
            "Las coordenadas aproximadas deben ser finitas."
        }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "Las coordenadas aproximadas están fuera de rango."
        }
    }
}

sealed class DeviceLocationException(message: String) : Exception(message) {
    class PermissionDenied(
        message: String = "Permiso de ubicación aproximada denegado."
    ) : DeviceLocationException(message)

    class LocationServicesDisabled(
        message: String = "Activa los servicios de ubicación del dispositivo para usar General."
    ) : DeviceLocationException(message)

    class LocationUnavailable(
        message: String = "No se pudo obtener tu ubicación aproximada en este momento."
    ) : DeviceLocationException(message)

    class RequestInProgress(
        message: String = "Ya hay una solicitud de ubicación en curso."
    ) : DeviceLocationException(message)

    class InvalidLocation(
        message: String = "La ubicación obtenida no es válida."
    ) : DeviceLocationException(message)
}
