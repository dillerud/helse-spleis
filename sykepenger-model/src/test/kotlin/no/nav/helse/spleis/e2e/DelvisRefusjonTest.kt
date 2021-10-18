package no.nav.helse.spleis.e2e

import no.nav.helse.Toggles
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.TilstandType.*
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.testhelpers.februar
import no.nav.helse.testhelpers.januar
import no.nav.helse.testhelpers.mars
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class DelvisRefusjonTest : AbstractEndToEndTest() {


    @Test
    fun `Full refusjon til en arbeidsgiver med RefusjonPerDag på`() = Toggles.RefusjonPerDag.enable {
        nyttVedtak(1.januar, 31.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, null, emptyList()))

        assertTrue(inspektør.utbetalinger.last().arbeidsgiverOppdrag().isNotEmpty())
        inspektør.utbetalinger.last().arbeidsgiverOppdrag().forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().personOppdrag().isEmpty())
        assertUtbetalingsbeløp(1.vedtaksperiode, 1431, 1431)
    }

    @Test
    fun `Full refusjon til en arbeidsgiver med RefusjonPerDag av`() = Toggles.RefusjonPerDag.disable {
        nyttVedtak(1.januar, 31.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, null, emptyList()))

        assertTrue(inspektør.utbetalinger.last().arbeidsgiverOppdrag().isNotEmpty())
        inspektør.utbetalinger.last().arbeidsgiverOppdrag().forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().personOppdrag().isEmpty())

        inspektør.utbetalingstidslinjer(1.vedtaksperiode).forEach {
            it.økonomi.medAvrundetData { _, arbeidsgiverRefusjonsbeløp, _, _, arbeidsgiverbeløp, personbeløp, _ ->
                val forventetArbeidsgiverbeløp = if (it is Utbetalingstidslinje.Utbetalingsdag.NavDag) 1431 else 0
                assertEquals(forventetArbeidsgiverbeløp, arbeidsgiverbeløp)
                assertEquals(null, arbeidsgiverRefusjonsbeløp)
                assertEquals(0, personbeløp)
            }
        }
    }

    @Test
    fun `Arbeidsgiverperiode tilstøter Infotrygd`() =
        Toggles.RefusjonPerDag.enable { //TODO (Holder det å sjekke 1 dag tilbake i tid isteden for 16? [RefusjonsHistorikk.kt:45])
            håndterInntektsmelding(listOf(1.januar til 16.januar))

            håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
            håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
            håndterUtbetalingshistorikk(
                1.vedtaksperiode, ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 31.januar, 100.prosent, INNTEKT), inntektshistorikk = listOf(
                    Inntektsopplysning(ORGNUMMER, 17.januar, INNTEKT, true)
                )
            )
            håndterYtelser(1.vedtaksperiode)
            håndterSimulering(1.vedtaksperiode)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode)
            håndterUtbetalt(1.vedtaksperiode)
            assertFalse(inspektør.warnings.contains("Fant ikke refusjon for perioden. Defaulter til full refusjon."))
        }


    @Test
    fun `Finner refusjon ved forlengelse fra Infotrygd`() = Toggles.RefusjonPerDag.enable {
        håndterInntektsmelding(listOf(1.januar til 16.januar))

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 31.januar, 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.februar, 28.februar, 100.prosent, INNTEKT),
            inntektshistorikk = listOf(
                Inntektsopplysning(ORGNUMMER, 17.januar, INNTEKT, true)
            )
        )
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt(1.vedtaksperiode)
        assertFalse(inspektør.warnings.contains("Fant ikke refusjon for perioden. Defaulter til full refusjon."))
    }

    @Disabled
    @Test
    fun `ikke kast ut vedtaksperiode når tidligere vedtaksperiode har opphør i refusjon`() = Toggles.RefusjonPerDag.enable {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, 20.januar, emptyList())
        )
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            TIL_INFOTRYGD
        )

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(
            listOf(1.mars til 16.mars)
        )
        håndterYtelser(2.vedtaksperiode)
        håndterVilkårsgrunnlag(2.vedtaksperiode)
        håndterYtelser(2.vedtaksperiode)
        assertTilstander(
            2.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )


        assertTrue(inspektør.utbetalinger.last().arbeidsgiverOppdrag().isNotEmpty())
        inspektør.utbetalinger.last().arbeidsgiverOppdrag().forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().personOppdrag().isEmpty())
        assertUtbetalingsbeløp(2.vedtaksperiode, 1431, 1431)

    }

    @Test
    fun `ikke kast ut vedtaksperiode ved ferie i slutten av perioden`() = Toggles.RefusjonPerDag.enable {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), Ferie(25.januar, 31.januar))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, 24.januar, emptyList())
        )
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )

        assertTrue(inspektør.utbetalinger.last().arbeidsgiverOppdrag().isNotEmpty())
        inspektør.utbetalinger.last().arbeidsgiverOppdrag().forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().personOppdrag().isEmpty())

        assertUtbetalingsbeløp(1.vedtaksperiode, 1431, 1431, 1.januar til 24.januar)
        assertUtbetalingsbeløp(1.vedtaksperiode, 1431, 0, 25.januar til 31.januar)
    }

    private fun assertUtbetalingsbeløp(
        vedtaksperiodeIdInnhenter: IdInnhenter,
        forventetArbeidsgiverbeløp: Int,
        forventetArbeidsgiverRefusjonsbeløp: Int,
        subset: Periode? = null,
    ) {
        val utbetalingstidslinje = inspektør.utbetalingstidslinjer(vedtaksperiodeIdInnhenter).let { subset?.let(it::subset) ?: it }

        utbetalingstidslinje.forEach {
            it.økonomi.medAvrundetData { _, arbeidsgiverRefusjonsbeløp, _, _, arbeidsgiverbeløp, personbeløp, _ ->
                val forventetArbeidsgiverbeløpForDagtype = if (it is Utbetalingstidslinje.Utbetalingsdag.NavDag) forventetArbeidsgiverbeløp else 0
                assertEquals(forventetArbeidsgiverbeløpForDagtype, arbeidsgiverbeløp)
                assertEquals(forventetArbeidsgiverRefusjonsbeløp, arbeidsgiverRefusjonsbeløp)
                assertEquals(0, personbeløp)
            }
        }
    }
}