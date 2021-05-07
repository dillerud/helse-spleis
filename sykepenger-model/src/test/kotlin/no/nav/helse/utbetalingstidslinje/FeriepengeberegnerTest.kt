package no.nav.helse.utbetalingstidslinje

import no.nav.helse.Toggles
import no.nav.helse.hendelser.*
import no.nav.helse.person.infotrygdhistorikk.Infotrygdperiode
import no.nav.helse.person.infotrygdhistorikk.Utbetalingsperiode
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.testhelpers.*
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Year
import java.util.*

internal class FeriepengeberegnerTest : AbstractEndToEndTest() {
    private companion object {
        private val alder = Alder(UNG_PERSON_FNR_2018)
        private const val a1 = "456789123"
        private const val a2 = "789456213"
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 48 sammenhengende utbetalingsdager i IT fra første januar`() {
        val historikk = utbetalingshistorikkForFeriepenger(
            listOf(Utbetalingsperiode(ORGNUMMER, 1.januar, 7.mars, 100.prosent, 1000.månedlig))
        )

        val beregner = Feriepengeberegner(historikk, person, alder)

        assertEquals(48, beregner.count())
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 49 sammenhengende utbetalingsdager i IT fra første januar`() {
        val historikk = utbetalingshistorikkForFeriepenger(
            listOf(Utbetalingsperiode(ORGNUMMER, 1.januar, 8.mars, 100.prosent, 1000.månedlig))
        )

        val beregner = Feriepengeberegner(historikk, person, alder)

        assertEquals(48, beregner.count())
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 47 sammenhengende utbetalingsdager i IT fra første januar`() {
        val historikk = utbetalingshistorikkForFeriepenger(
            listOf(Utbetalingsperiode(ORGNUMMER, 1.januar, 6.mars, 100.prosent, 1000.månedlig))
        )

        val beregner = Feriepengeberegner(historikk, person, alder)

        assertEquals(47, beregner.count())
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 48 sammenhengende utbetalingsdager i Oppdrag fra første januar`() {
        byggPerson(
            arbeidsgiverperiode = 16.desember(2017) til 31.desember(2017),
            syktil = 7.mars(2018)
        )

        val beregner = Feriepengeberegner(utbetalingshistorikkForFeriepenger(), person, alder)

        assertEquals(48, beregner.count())
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 49 sammenhengende utbetalingsdager i Oppdrag fra første januar`() {
        byggPerson(
            arbeidsgiverperiode = 16.desember(2017) til 31.desember(2017),
            syktil = 8.mars(2018)
        )

        val beregner = Feriepengeberegner(utbetalingshistorikkForFeriepenger(), person, alder)

        assertEquals(48, beregner.count())
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 47 sammenhengende utbetalingsdager i Oppdrag fra første januar`() {
        byggPerson(
            arbeidsgiverperiode = 16.desember(2017) til 31.desember(2017),
            syktil = 6.mars(2018)
        )

        val beregner = Feriepengeberegner(utbetalingshistorikkForFeriepenger(), person, alder)

        assertEquals(47, beregner.count())
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 48 sammenhengende utbetalingsdager i Oppdrag fra niende mai`() {
        byggPerson(
            arbeidsgiverperiode = 23.april(2018) til 8.mai(2018),
            syktil = 13.juli(2018)
        )

        val beregner = Feriepengeberegner(utbetalingshistorikkForFeriepenger(), person, alder)

        assertEquals(48, beregner.count())
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 47 ikke-sammenhengende utbetalingsdager i Oppdrag`() {
        byggPerson(
            arbeidsgiverperiode = 16.desember(2017) til 31.desember(2017),
            syktil = 22.januar(2018)
        )
        byggPerson(
            arbeidsgiverperiode = 1.mars(2018) til 16.mars(2018),
            syktil = 28.mars(2018)
        )
        byggPerson(
            arbeidsgiverperiode = 1.mai(2018) til 16.mai(2018),
            syktil = 12.juni(2018)
        )
        byggPerson(
            arbeidsgiverperiode = 1.juli(2018) til 16.juli(2018),
            syktil = 21.juli(2018)
        )

        val beregner = Feriepengeberegner(utbetalingshistorikkForFeriepenger(), person, alder)

        assertEquals(47, beregner.count())
    }

    @Test
    fun `Finner datoer for feriepengeberegning med 48 ikke-sammenhengende utbetalingsdager i Oppdrag`() {
        byggPerson(
            arbeidsgiverperiode = 16.desember(2017) til 31.desember(2017),
            syktil = 22.januar(2018)
        )
        byggPerson(
            arbeidsgiverperiode = 1.mars(2018) til 16.mars(2018),
            syktil = 28.mars(2018)
        )
        byggPerson(
            arbeidsgiverperiode = 1.mai(2018) til 16.mai(2018),
            syktil = 12.juni(2018)
        )
        byggPerson(
            arbeidsgiverperiode = 1.juli(2018) til 16.juli(2018),
            syktil = 23.juli(2018)
        )

        val beregner = Feriepengeberegner(utbetalingshistorikkForFeriepenger(), person, alder)

        assertEquals(48, beregner.count())
    }

    @Test
    fun `Finner datoer for feriepengeberegning med to helt overlappende Oppdrag`() = Toggles.FlereArbeidsgivereFørstegangsbehandling.enable {
        byggPersonToParallelle(
            arbeidsgiverperiode = 23.april(2018) til 8.mai(2018),
            syktil = 13.juli(2018)
        )

        val beregner = Feriepengeberegner(utbetalingshistorikkForFeriepenger(), person, alder)

        assertEquals(48, beregner.count())
    }

    @Test
    fun `Finner datoer for feriepengeberegning med to ikke-overlappende utbetalingstidslinjer`() = Toggles.FlereArbeidsgivereFørstegangsbehandling.enable {
        byggPerson(
            arbeidsgiverperiode = 1.januar(2018) til 16.januar(2018),
            syktil = 15.februar(2018),
            orgnummer = a1
        )
        byggPerson(
            arbeidsgiverperiode = 1.juli(2018) til 16.juli(2018),
            syktil = 15.august(2018),
            orgnummer = a2
        )

        val beregner = Feriepengeberegner(utbetalingshistorikkForFeriepenger(), person, alder)

        assertEquals(44, beregner.count())
    }

    private fun utbetalingshistorikkForFeriepenger(utbetalinger: List<Infotrygdperiode> = emptyList()) =
        UtbetalingshistorikkForFeriepenger(
            UUID.randomUUID(),
            AKTØRID,
            ORGNUMMER,
            utbetalinger,
            emptyList(),
            emptyList(),
            false,
            emptyMap(),
            Year.of(2020)
        )

    private fun byggPerson(
        arbeidsgiverperiode: Periode = 1.januar til 16.januar,
        syktil: LocalDate = 31.januar,
        orgnummer: String = ORGNUMMER
    ) {
        håndterSykmelding(Sykmeldingsperiode(arbeidsgiverperiode.start, syktil, 100.prosent), orgnummer = orgnummer)
        håndterSøknadMedValidering(
            observatør.sisteVedtaksperiode(),
            Søknad.Søknadsperiode.Sykdom(arbeidsgiverperiode.start, syktil, 100.prosent),
            orgnummer = orgnummer
        )
        håndterUtbetalingshistorikk(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterInntektsmelding(listOf(arbeidsgiverperiode), orgnummer = orgnummer)
        håndterYtelser(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterVilkårsgrunnlag(observatør.sisteVedtaksperiode(), inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioder {
                inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
                arbeidsgiverperiode.start.minusYears(1) til arbeidsgiverperiode.start.withDayOfMonth(1).minusMonths(1) inntekter {
                    orgnummer inntekt INNTEKT
                }
            }
        ), orgnummer = orgnummer)
        håndterYtelser(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterSimulering(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterUtbetalingsgodkjenning(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
        håndterUtbetalt(observatør.sisteVedtaksperiode(), orgnummer = orgnummer)
    }

    private fun byggPersonToParallelle(
        arbeidsgiverperiode: Periode = 1.januar til 16.januar,
        syktil: LocalDate = 31.januar
    ) {
        håndterSykmelding(Sykmeldingsperiode(arbeidsgiverperiode.start, syktil, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(arbeidsgiverperiode.start, syktil, 100.prosent), orgnummer = a2)
        håndterSøknadMedValidering(1.vedtaksperiode(a1), Søknad.Søknadsperiode.Sykdom(arbeidsgiverperiode.start, syktil, 100.prosent), orgnummer = a1)
        håndterSøknadMedValidering(1.vedtaksperiode(a2), Søknad.Søknadsperiode.Sykdom(arbeidsgiverperiode.start, syktil, 100.prosent), orgnummer = a2)
        håndterUtbetalingshistorikk(1.vedtaksperiode(a1), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode(a2), orgnummer = a2)
        håndterInntektsmelding(listOf(arbeidsgiverperiode), orgnummer = a1)
        håndterInntektsmelding(listOf(arbeidsgiverperiode), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode(a1), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode(a1), inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioder {
                inntektsgrunnlag = Inntektsvurdering.Inntektsgrunnlag.SAMMENLIGNINGSGRUNNLAG
                arbeidsgiverperiode.start.minusYears(1) til arbeidsgiverperiode.start.withDayOfMonth(1).minusMonths(1) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt INNTEKT
                }
            }
        ), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode(a1), orgnummer = a1)
        håndterSimulering(1.vedtaksperiode(a1), orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode(a1), orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode(a1), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode(a2), orgnummer = a2)
        håndterSimulering(1.vedtaksperiode(a2), orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode(a2), orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode(a2), orgnummer = a2)
    }
}