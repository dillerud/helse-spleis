package no.nav.helse.spleis.e2e

import no.nav.helse.assertForventetFeil
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.utbetalingslinjer.Utbetaling.GodkjentUtenUtbetaling
import no.nav.helse.utbetalingslinjer.Utbetaling.Sendt
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SamletSykdomsgradE2ETest: AbstractEndToEndTest() {

    @Test
    fun `avviser dager under 20 prosent`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar, 19.prosent))
        håndterSøknad(Sykdom(1.januar, 20.januar, 19.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar, 16.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        assertEquals(GodkjentUtenUtbetaling, inspektør.utbetalingtilstand(0))
        val utbetalingstidslinje = inspektør.utbetalingUtbetalingstidslinje(0)
        assertTrue(utbetalingstidslinje[17.januar] is Utbetalingstidslinje.Utbetalingsdag.AvvistDag)
        assertTrue(utbetalingstidslinje[18.januar] is Utbetalingstidslinje.Utbetalingsdag.AvvistDag)
        assertTrue(utbetalingstidslinje[19.januar] is Utbetalingstidslinje.Utbetalingsdag.AvvistDag)
        assertEquals(3, utbetalingstidslinje.inspektør.avvistDagTeller)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_GODKJENNING,
            AVSLUTTET
        )
    }

    @Test
    fun `avviser dager under 20 prosent på forlengelser`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar, 19.prosent))
        håndterSøknad(Sykdom(1.januar, 20.januar, 19.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar, 16.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterSykmelding(Sykmeldingsperiode(21.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(21.januar, 31.januar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        val utbetalingstidslinje = inspektør.utbetalingUtbetalingstidslinje(1)
        assertTrue(utbetalingstidslinje[17.januar] is Utbetalingstidslinje.Utbetalingsdag.AvvistDag)
        assertTrue(utbetalingstidslinje[18.januar] is Utbetalingstidslinje.Utbetalingsdag.AvvistDag)
        assertTrue(utbetalingstidslinje[19.januar] is Utbetalingstidslinje.Utbetalingsdag.AvvistDag)
        assertEquals(3, utbetalingstidslinje.inspektør.avvistDagTeller)
        assertEquals(Sendt, inspektør.utbetalingtilstand(1))
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING
        )
    }

    @Test
    fun `opprinnelig søknad med 100 prosent arbeidshelse blir korrigert slik at sykdomsgraden blir 100 prosent `() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent, 100.prosent)) // 100 prosent arbeidshelse => 0 prosent syk
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar, 16.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent)) // korrigert søknad med 0 prosent arbeidshelse => 100 prosent syk
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_GODKJENNING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )

        assertForventetFeil(
            forklaring = "når vi mottar korrigert søknad ligger det igjen warnings fra før som ikke lengre gjelder",
            nå = { assertWarning("Minst én dag uten utbetaling på grunn av sykdomsgrad under 20 %. Vurder å sende vedtaksbrev fra Infotrygd") },
            ønsket = { assertNoWarnings() }
        )
    }

    @Test
    fun `ny periode med egen arbeidsgiverperiode skal ikke ha warning pga sykdomsgrad som gjelder forrige periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar, 19.prosent))
        håndterSøknad(Sykdom(1.januar, 20.januar, 19.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.januar, 16.januar)))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.mars, 20.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 20.mars, 100.prosent))
        håndterInntektsmeldingMedValidering(1.vedtaksperiode, listOf(Periode(1.mars, 16.mars)))
        håndterYtelser(2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode, INNTEKT)
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode)
        assertWarning("Minst én dag uten utbetaling på grunn av sykdomsgrad under 20 %. Vurder å sende vedtaksbrev fra Infotrygd", 1.vedtaksperiode.filter())
        assertNoWarnings(2.vedtaksperiode.filter())
    }
}
