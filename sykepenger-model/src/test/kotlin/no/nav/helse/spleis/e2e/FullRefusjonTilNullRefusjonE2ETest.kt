package no.nav.helse.spleis.e2e

import no.nav.helse.Toggle
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.testhelpers.februar
import no.nav.helse.testhelpers.januar
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Isolated

@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class FullRefusjonTilNullRefusjonE2ETest : AbstractEndToEndTest() {

    @BeforeAll
    fun setup() {
        Toggle.LageBrukerutbetaling.enable()
    }

    @AfterAll
    fun teardown() {
        Toggle.LageBrukerutbetaling.pop()
    }

    @Test
    fun `starter med ingen refusjon`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(0.daglig, null))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(2.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        assertFalse(inspektør.utbetaling(0).arbeidsgiverOppdrag().harUtbetalinger())
        assertTrue(inspektør.utbetaling(0).personOppdrag().harUtbetalinger())
        assertEquals(17.januar til 31.januar, Oppdrag.periode(inspektør.utbetaling(0).personOppdrag()))

        assertFalse(inspektør.utbetaling(1).arbeidsgiverOppdrag().harUtbetalinger())
        assertTrue(inspektør.utbetaling(1).personOppdrag().harUtbetalinger())
        assertEquals(17.januar til 28.februar, Oppdrag.periode(inspektør.utbetaling(1).personOppdrag()))
    }

    @Test
    fun `starter med refusjon, så ingen refusjon`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, 31.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(2.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        assertFalse(inspektør.utbetaling(1).arbeidsgiverOppdrag().harUtbetalinger())
        assertEquals(17.januar til 31.januar, Oppdrag.periode(inspektør.utbetaling(1).arbeidsgiverOppdrag()))
        assertTrue(inspektør.utbetaling(1).personOppdrag().harUtbetalinger())
        assertEquals(1.februar til 28.februar, Oppdrag.periode(inspektør.utbetaling(1).personOppdrag()))
    }

    @Test
    fun `starter med refusjon som opphører i neste periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, 3.februar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode)) { "refusjonen opphører i den andre perioden, som betyr at vi skal betale ut penger i to oppdrag samtidig" }
    }

    @Test
    fun `starter med refusjon, så korrigeres refusjonen til ingenting`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, 31.januar))

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(2.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        assertFalse(inspektør.utbetaling(1).arbeidsgiverOppdrag().harUtbetalinger())
        assertEquals(17.januar til 31.januar, Oppdrag.periode(inspektør.utbetaling(1).arbeidsgiverOppdrag()))
        assertTrue(inspektør.utbetaling(1).personOppdrag().harUtbetalinger())
        assertEquals(1.februar til 28.februar, Oppdrag.periode(inspektør.utbetaling(1).personOppdrag()))
    }

    // Tester strengt talt et ugyldig case siden refusjon ikke får lov å gå fra null til full, men den treffer uansett en probemstilling i modellen
    @Test
    fun `starter med ingen refusjon, så korrigeres refusjonen til full`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar, refusjon = Inntektsmelding.Refusjon(0.daglig, null))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(1.vedtaksperiode, Oppdragstatus.AKSEPTERT)

        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)

        assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode)) {
            "Perioden forkastes pga. feature toggle om delvis refusjon." +
                "Den dagen delvis refusjon støttes vil koden under være riktig asserts -- med mindre man stopper dette caset på en funksjonelt nivå"
        }

        /*håndterSimulering(2.vedtaksperiode)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true)
        håndterUtbetalt(2.vedtaksperiode, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertFalse(inspektør.utbetaling(0).arbeidsgiverOppdrag().harUtbetalinger())
        assertTrue(inspektør.utbetaling(0).personOppdrag().harUtbetalinger())
        assertEquals(17.januar til 31.januar, Oppdrag.periode(inspektør.utbetaling(0).personOppdrag()))

        assertTrue(inspektør.utbetaling(1).arbeidsgiverOppdrag().harUtbetalinger())
        assertTrue(inspektør.utbetaling(1).personOppdrag().harUtbetalinger())
        assertTrue(inspektør.utbetaling(1).personOppdrag()[0].erOpphør())
        assertEquals(17.januar til 31.januar, Oppdrag.periode(inspektør.utbetaling(1).arbeidsgiverOppdrag()))*/
    }
}