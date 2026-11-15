package com.example.caprichometro.caprice

import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.NearbyCellGrid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NearbyCellGridTest {

    @Test
    fun `cellId coincide exactamente con la fórmula interoperable de iOS`() {
        val madrid = ApproximateLocation(latitude = 40.4168, longitude = -3.7038)
        // floor((40.4168 + 90) / 0.05) = 2608; floor((-3.7038 + 180) / 0.05) = 3525
        assertThat(NearbyCellGrid.cellId(madrid)).isEqualTo("v1_2608_3525")
    }

    @Test
    fun `cellId normaliza polos y antimeridiano en rango valido`() {
        val northPole = ApproximateLocation(latitude = 90.0, longitude = 180.0)
        val southPole = ApproximateLocation(latitude = -90.0, longitude = -180.0)

        assertThat(NearbyCellGrid.cellId(northPole)).isEqualTo("v1_3599_0")
        assertThat(NearbyCellGrid.cellId(southPole)).isEqualTo("v1_0_0")
    }

    @Test
    fun `centerLocation decodifica el centro exacto de la celda y rechaza formatos invalidos`() {
        val center = NearbyCellGrid.centerLocation("v1_2608_3525")
        assertThat(center).isNotNull
        assertThat(center!!.latitude).isCloseTo(40.425, org.assertj.core.data.Offset.offset(0.0001))
        assertThat(center.longitude).isCloseTo(-3.725, org.assertj.core.data.Offset.offset(0.0001))

        assertThat(NearbyCellGrid.centerLocation("v2_2608_3525")).isNull()
        assertThat(NearbyCellGrid.centerLocation("v1_3600_0")).isNull()
        assertThat(NearbyCellGrid.centerLocation("v1_0_7200")).isNull()
        assertThat(NearbyCellGrid.centerLocation("v1_-1_10")).isNull()
        assertThat(NearbyCellGrid.centerLocation("invalido")).isNull()
    }

    @Test
    fun `distanceInMeters calcula distancia Haversine sin tope maximo`() {
        val madrid = ApproximateLocation(latitude = 40.4168, longitude = -3.7038)
        val ownCell = NearbyCellGrid.cellId(madrid)
        val barcelonaCell = NearbyCellGrid.cellId(ApproximateLocation(41.3874, 2.1686))
        val tokyoCell = NearbyCellGrid.cellId(ApproximateLocation(35.6762, 139.6503))

        val sameZoneMeters = NearbyCellGrid.distanceInMeters(madrid, ownCell)!!
        val barcelonaMeters = NearbyCellGrid.distanceInMeters(madrid, barcelonaCell)!!
        val tokyoMeters = NearbyCellGrid.distanceInMeters(madrid, tokyoCell)!!

        assertThat(sameZoneMeters).isLessThan(3_000.0)
        assertThat(barcelonaMeters).isBetween(495_000.0, 515_000.0)
        assertThat(tokyoMeters).isGreaterThan(10_000_000.0)
    }

    @Test
    fun `formatApproximateDistanceMeters formatea zona local o multiplos de 5 km sin coordenadas`() {
        assertThat(NearbyCellGrid.formatApproximateDistanceMeters(0.0)).isEqualTo("En tu zona")
        assertThat(NearbyCellGrid.formatApproximateDistanceMeters(2_499.0)).isEqualTo("En tu zona")
        assertThat(NearbyCellGrid.formatApproximateDistanceMeters(2_500.0)).isEqualTo("Aprox. 5 km")
        assertThat(NearbyCellGrid.formatApproximateDistanceMeters(7_400.0)).isEqualTo("Aprox. 5 km")
        assertThat(NearbyCellGrid.formatApproximateDistanceMeters(7_600.0)).isEqualTo("Aprox. 10 km")
        assertThat(NearbyCellGrid.formatApproximateDistanceMeters(505_000.0)).isEqualTo("Aprox. 505 km")
    }

    @Test
    fun `ApproximateLocation rechaza coordenadas no finitas o fuera de rango`() {
        assertThrows<IllegalArgumentException> { ApproximateLocation(Double.NaN, 0.0) }
        assertThrows<IllegalArgumentException> { ApproximateLocation(90.1, 0.0) }
        assertThrows<IllegalArgumentException> { ApproximateLocation(0.0, -180.1) }
    }
}
