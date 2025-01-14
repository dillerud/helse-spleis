package no.nav.helse.spleis.e2e

import no.nav.helse.EnableToggle
import no.nav.helse.Toggle
import no.nav.helse.assertForventetFeil
import no.nav.helse.februar
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Arbeid
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_BLOKKERENDE_PERIODE
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NyTilstandsflytEnArbeidsgiverTest : AbstractEndToEndTest() {

    @Test
    fun `drawio -- misc -- oppvarming`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        håndterInntektsmelding(listOf(1.januar til 16.januar))

        utbetalPeriode(1.vedtaksperiode)

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )

        utbetalPeriodeEtterVilkårsprøving(2.vedtaksperiode)

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Én arbeidsgiver - førstegangsbehandling`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
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
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
    }

    @Test
    fun `Forlengelse av en avsluttet periode går til AvventerHistorikk`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `Førstegangsbehandling går ikke videre dersom vi har en tidligere uferdig periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars))

        assertTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
        assertTilstander(2.vedtaksperiode, START, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `Førstegangsbehandling går videre etter at en tidligere uferdig periode er ferdig`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars))

        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertTilstand(1.vedtaksperiode, AVSLUTTET)
        assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
    }

    @Test
    fun `Kort periode går til AvsluttetUtenUtbetaling, pusher neste periode til AvventerHistorikk`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 16.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(17.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode)

        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterSøknad(Sykdom(17.januar, 31.januar, 100.prosent))

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVSLUTTET_UTEN_UTBETALING,
            AVSLUTTET_UTEN_UTBETALING
        )
        assertTilstander(2.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE, AVVENTER_HISTORIKK)
    }

    @Test
    fun `Inntektsmelding kommer før søknad - vi kommer oss videre til AvventerHistorikk pga replay`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmeldingReplay(inntektsmeldingId, 1.vedtaksperiode.id(ORGNUMMER))
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_BLOKKERENDE_PERIODE,
            AVVENTER_HISTORIKK
        )
    }

    @Test
    fun `Kort periode skal ikke blokkeres av mangelende søknad`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(21.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))

        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)
    }

    @Test
    fun `Går til AvventerInntektsmelding ved gap`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 22.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(25.januar, 17.februar, 100.prosent))

        håndterSøknad(Sykdom(1.januar, 22.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))

        håndterSøknad(Sykdom(25.januar, 17.februar, 100.prosent))

        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `drawio -- Out of order`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar))

        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterInntektsmeldingReplay(inntektsmeldingId, 1.vedtaksperiode.id(ORGNUMMER))
        assertTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmeldingReplay(inntektsmeldingId, 2.vedtaksperiode.id(ORGNUMMER))
        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)

        utbetalPeriode(2.vedtaksperiode)
        assertTilstand(2.vedtaksperiode, AVSLUTTET)

        utbetalPeriodeEtterVilkårsprøving(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `drawio -- Avsluttet uten utbetaling`() = Toggle.GjenopptaAvsluttetUtenUtbetaling.enable {
        håndterSykmelding(Sykmeldingsperiode(5.januar, 19.januar, 100.prosent))
        håndterSøknad(Sykdom(5.januar, 19.januar, 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        håndterInntektsmelding(listOf(1.januar til 16.januar))
        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)

        utbetalPeriode(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVSLUTTET)
    }

    @Test
    fun `Periode i AvsluttetUtenUtbetaling blir truffet av en IM slik at den får en utbetaling - går ikke videre siden tidligere periode må behandles først`() {
        Toggle.GjenopptaAvsluttetUtenUtbetaling.enable {
            håndterSykmelding(Sykmeldingsperiode(1.november, 30.november, 100.prosent))
            håndterSøknad(Sykdom(1.november, 30.november, 100.prosent))
            håndterUtbetalingshistorikk(1.vedtaksperiode)

            håndterSykmelding(Sykmeldingsperiode(5.januar, 19.januar, 100.prosent))
            håndterSøknad(Sykdom(5.januar, 19.januar, 100.prosent))
            håndterUtbetalingshistorikk(2.vedtaksperiode)
            assertTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

            håndterInntektsmelding(listOf(1.januar til 16.januar))
            assertTilstander(
                2.vedtaksperiode,
                START,
                AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
                AVSLUTTET_UTEN_UTBETALING,
                AVSLUTTET_UTEN_UTBETALING,
                AVVENTER_BLOKKERENDE_PERIODE,
                AVVENTER_HISTORIKK
            )
        }
    }

    @Test
    fun `Periode i AvsluttetUtenUtbetaling blir truffet av en IM slik at den får en utbetaling - senere perioder må trekkes tilbake til ventetilstand`() = Toggle.GjenopptaAvsluttetUtenUtbetaling.enable {
        håndterSykmelding(Sykmeldingsperiode(5.januar, 19.januar, 100.prosent))
        håndterSøknad(Sykdom(5.januar, 19.januar, 100.prosent))
        håndterUtbetalingshistorikk(1.vedtaksperiode)
        assertTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterUtbetalingshistorikk(2.vedtaksperiode)
        håndterInntektsmelding(listOf(1.mars til 16.mars))

        håndterInntektsmelding(listOf(1.januar til 16.januar))

        assertForventetFeil(
            forklaring = "Skal trekke tilbake tidligere periode til en ventetilstand",
            nå = {
                assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)
                assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
            },
            ønsket = {
                assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)
                assertTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)

                utbetalPeriode(1.vedtaksperiode)
                assertTilstand(1.vedtaksperiode, AVSLUTTET)

                utbetalPeriode(2.vedtaksperiode)
                assertTilstand(2.vedtaksperiode, AVSLUTTET)
            }
        )
    }

    @Test
    fun `går tilbake til AvventerTidligereEllerOverlappende dersom vi får en tilbakedatert søknad før periode i AvventerGodkjenning`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `går tilbake til AvventerTidligereEllerOverlappende dersom vi får en tilbakedatert søknad før periode i AvventerHistorikk`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `går tilbake til AvventerTidligereEllerOverlappende dersom vi får en tilbakedatert søknad før periode i AvventerVilkårsprøving`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars))
        håndterYtelser(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `går tilbake til AvventerTidligereEllerOverlappende dersom vi får en tilbakedatert søknad før periode i AvventerSimulering`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `blir i AvventerInntektsmeldingEllerHistorikk dersom vi får en out-of-order søknad forran`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        assertTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `blir i AvventerTidligereEllerOverlappende dersom vi får en out-of-order søknad forran`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.mars til 16.mars))

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `hopper ikke videre fra AvventerInntektsmeldingEllerHistorikk dersom vi får en out-of-order søknad foran og IM kommer på den seneste vedtaksperioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        håndterInntektsmelding(listOf(1.mars til 16.mars))
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
        assertTilstand(1.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `Ber om inntektsmelding i AvventerInntektsmeldingEllerHistorikk og sier ifra at vi ikke trenger når vi forlater tilstanden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        assertEquals(1, observatør.manglendeInntektsmeldingVedtaksperioder.size)
        assertEquals(0, observatør.trengerIkkeInntektsmeldingVedtaksperioder.size)
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        assertEquals(1, observatør.trengerIkkeInntektsmeldingVedtaksperioder.size)
    }

    @Test
    fun `To perioder med gap, den siste venter på at den første skal bli ferdig - dersom den første blir forkastet skal den siste perioden gå videre`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(1.mai, 31.mai, 100.prosent))

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.mai, 31.mai, 100.prosent))

        håndterInntektsmelding(listOf(1.mai til 16.mai))
        håndterPåminnelse(1.vedtaksperiode, påminnetTilstand = AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, tilstandsendringstidspunkt = 5.februar.atStartOfDay())

        assertTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
        assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK)
    }

    @Test
    fun `Kort periode med en tidligere kort periode som har lagret inntekt for første fraværsdag`() {
        /* skal ikke gå videre til AVVENTER_HISTORIKK siden perioden ikke går forbi AGP */
        håndterSykmelding(Sykmeldingsperiode(1.januar, 2.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(10.januar, 11.januar, 100.prosent))

        håndterSøknad(Sykdom(1.januar, 2.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 2.januar, 10.januar til 23.januar), førsteFraværsdag = 10.januar)
        håndterSøknad(Sykdom(10.januar, 11.januar, 100.prosent))

        assertTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
    }

    @Test
    fun `Periode skal ha utbetaling grunnet inntektsmelding vi mottok før søknad`() {
        håndterSykmelding(Sykmeldingsperiode(11.januar, 26.januar, 100.prosent))
        val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterSøknad(Sykdom(11.januar, 26.januar, 100.prosent))
        håndterInntektsmeldingReplay(inntektsmeldingId, 1.vedtaksperiode.id(ORGNUMMER))

        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK)
    }

    @Test
    fun `Skal ikke forkaste periode som mottar to identiske søknader`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
    }

    @Test
    fun `Skal ikke forkaste periode som mottar to identiske søknader for forlengelse i AvventerTidligereEllerOverlappendePerioder`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        assertTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING)
        assertTilstand(2.vedtaksperiode, AVVENTER_BLOKKERENDE_PERIODE)
    }

    @Test
    fun `overlappende søknad fører til gap til neste periode -- skal kaste ut alle sammenhengende perioder`() {
        // Dette burde det være mye lettere å støtte med ny tilstandsmaskin
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.mars, 31.mars, 100.prosent))
        håndterSøknad(Sykdom(1.mars, 31.mars, 100.prosent))

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Arbeid(20.februar, 28.februar))

        assertError("Mottatt flere søknader for perioden - siste søknad inneholder arbeidsdag", 2.vedtaksperiode.filter())

        assertTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
        assertTilstand(2.vedtaksperiode, TIL_INFOTRYGD)
        assertTilstand(1.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `ferie strekker seg ut over tidligere vedtaksperiode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        utbetalPeriode(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(20.januar, 31.januar))
        assertWarning("Det er oppgitt ny informasjon om ferie i søknaden som det ikke har blitt opplyst om tidligere. Tidligere periode må revurderes.", 2.vedtaksperiode.filter())
    }

    @Test
    fun `ikke opprett ny vedtaksperiode dersom vi tidligere har forkastet en i samme periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        person.invaliderAllePerioder(hendelselogg, null)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        assertTrue(inspektør.periodeErForkastet(1.vedtaksperiode))
        assertTrue(inspektør.periodeErForkastet(2.vedtaksperiode))
    }

    @Test
    fun `Dersom alle perioder forkastes skal ingen av dem pokes videre fra gjenopptaBehandling`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        person.invaliderAllePerioder(hendelselogg, null)

        assertForkastetPeriodeTilstander(2.vedtaksperiode, START, AVVENTER_BLOKKERENDE_PERIODE, TIL_INFOTRYGD)
    }

    @Test
    // TODO: https://trello.com/c/9qxVRTpM
    fun `Infotrygdhistorikk fører til at en senere periode ikke trenger ny AGP - må vente på infotrygdhistorikk før vi bestemmer om vi skal til AUU`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        utbetalPeriode(1.vedtaksperiode)

        val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 20.februar)
        håndterSykmelding(Sykmeldingsperiode(20.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(20.februar, 28.februar, 100.prosent))
        håndterInntektsmeldingReplay(inntektsmeldingId, 2.vedtaksperiode.id(ORGNUMMER))
        håndterUtbetalingshistorikk(
            2.vedtaksperiode,
            utbetalinger = arrayOf(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.februar, 10.februar, 100.prosent, INNTEKT)),
            inntektshistorikk = listOf(Inntektsopplysning(ORGNUMMER, 1.februar, INNTEKT, true))
        )

        assertForventetFeil(
            forklaring = "Skal vente på infotrygdhistorikk før den går til AvsluttetUtenUtbetaling",
            nå = {
                assertTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING)
            },
            ønsket = {
                assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK)
            }
        )
    }

    @Test
    fun `sender trenger_ikke_inntektsmelding ved forkastelse av vedtaksperiode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        person.invaliderAllePerioder(hendelselogg, null)
        assertEquals(listOf(1.vedtaksperiode.id(ORGNUMMER)), observatør.trengerIkkeInntektsmeldingVedtaksperioder)
    }

    @Test
    fun `sender hendelse_ikke_håndtert ved søknad som treffer utbetalt periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        utbetalPeriode(1.vedtaksperiode)

        val søknadId = håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        assertNotNull(observatør.hendelseIkkeHåndtert(søknadId))
    }

    private fun utbetalPeriodeEtterVilkårsprøving(vedtaksperiode: IdInnhenter) {
        håndterYtelser(vedtaksperiode)
        håndterSimulering(vedtaksperiode)
        håndterUtbetalingsgodkjenning(vedtaksperiode)
        håndterUtbetalt()
    }

    private fun utbetalPeriode(vedtaksperiode: IdInnhenter) {
        håndterYtelser(vedtaksperiode)
        håndterVilkårsgrunnlag(vedtaksperiode)
        utbetalPeriodeEtterVilkårsprøving(vedtaksperiode)
    }
}