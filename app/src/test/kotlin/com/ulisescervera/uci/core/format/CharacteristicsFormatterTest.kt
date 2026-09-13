package com.ulisescervera.uci.core.format

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.domain.model.EnergyCertification
import com.ulisescervera.uci.domain.model.EnergyRating
import com.ulisescervera.uci.domain.model.Floor
import com.ulisescervera.uci.domain.model.PropertyCharacteristics
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es")
class CharacteristicsFormatterTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val propertyFormatter = PropertyFormatter(context)
    private val formatter = CharacteristicsFormatter(
        context = context,
        propertyFormatter = propertyFormatter,
        dateFormatter = UciDateFormatter(context),
    )

    @Test
    fun `an empty characteristics block produces no rows at all`() {
        // A table full of "Sin datos" is worse than a short table: it implies we
        // asked and got a negative answer.
        val rows = formatter.rows(PropertyCharacteristics.Empty, energyCertification = null)

        assertThat(rows).isEmpty()
    }

    @Test
    fun `only reported fields become rows`() {
        val rows = formatter.rows(
            PropertyCharacteristics(hasLift = true, communityCostsPerMonth = 330.0),
            energyCertification = null,
        )

        assertThat(rows.map { it.label }).containsExactly("Ascensor", "Gastos de comunidad")
    }

    @Test
    fun `booleans render as yes and no, not as true and false`() {
        val rows = formatter.rows(
            PropertyCharacteristics(hasLift = true, hasBoxRoom = false),
            energyCertification = null,
        )

        assertThat(rows.first { it.label == "Ascensor" }.value).isEqualTo("Sí")
        assertThat(rows.first { it.label == "Trastero" }.value).isEqualTo("No")
    }

    @Test
    fun `backend enums are localised`() {
        val rows = formatter.rows(
            PropertyCharacteristics(flatLocation = "internal", status = "renew"),
            energyCertification = null,
        )

        assertThat(rows.first { it.label == "Situación en el edificio" }.value).isEqualTo("Interior")
        assertThat(rows.first { it.label == "Estado" }.value).isEqualTo("A reformar")
    }

    @Test
    fun `an unknown backend enum degrades to its raw value instead of vanishing`() {
        // Visible degradation beats a silently missing row.
        val rows = formatter.rows(
            PropertyCharacteristics(status = "pendingDemolition"),
            energyCertification = null,
        )

        assertThat(rows.single().value).isEqualTo("pendingDemolition")
    }

    @Test
    fun `zero community costs are not shown as a cost`() {
        val rows = formatter.rows(
            PropertyCharacteristics(communityCostsPerMonth = 0.0),
            energyCertification = null,
        )

        assertThat(rows).isEmpty()
    }

    @Test
    fun `the energy certificate becomes one row with both ratings`() {
        val rows = formatter.rows(
            PropertyCharacteristics.Empty,
            EnergyCertification("Certificado energético", EnergyRating.E, EnergyRating.C),
        )

        assertThat(rows.single().label).isEqualTo("Certificado energético")
        assertThat(rows.single().value).isEqualTo("Consumo E · Emisiones C")
    }

    @Test
    fun `a floor is rendered through the same formatter the list uses`() {
        val rows = formatter.rows(
            PropertyCharacteristics(floor = Floor.Ground),
            energyCertification = null,
        )

        assertThat(rows.single().value).isEqualTo("Planta baja")
    }

    @Test
    fun `a bank advertiser is only mentioned when true`() {
        val shown = formatter.rows(PropertyCharacteristics(agencyIsABank = true), null)
        val hidden = formatter.rows(PropertyCharacteristics(agencyIsABank = false), null)

        assertThat(shown.map { it.label }).contains("Anunciado por una entidad bancaria")
        assertThat(hidden).isEmpty()
    }
}
