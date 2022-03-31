package no.nav.helse.spleis.e2e

import no.nav.helse.Toggle
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Inntektsmelding.Refusjon.EndringIRefusjon
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Ferie
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.november
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVVENTER_ARBEIDSGIVERE
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.MOTTATT_SYKMELDING_FERDIG_GAP
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DelvisRefusjonTest : AbstractEndToEndTest() {

    @Test
    fun `Full refusjon til en arbeidsgiver med RefusjonPerDag på`() {
        nyttVedtak(1.januar, 31.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, null, emptyList()))

        assertTrue(inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.isNotEmpty())
        inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().inspektør.personOppdrag.isEmpty())
        assertUtbetalingsbeløp(1.vedtaksperiode, 0, 1431, subset = 1.januar til 16.januar)
        assertUtbetalingsbeløp(1.vedtaksperiode, 1431, 1431, subset = 17.januar til 31.januar)
    }

    @Test
    fun `Full refusjon til en arbeidsgiver med forlengelse og opphørsdato treffer ferie`() {
        nyttVedtak(1.januar, 31.januar, refusjon = Inntektsmelding.Refusjon(INNTEKT, 27.februar, emptyList()))

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), Ferie(27.februar, 28.februar))
        håndterYtelser(2.vedtaksperiode)
        håndterSimulering(2.vedtaksperiode)
        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING
        )
        assertTrue(inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.isNotEmpty())
        inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().inspektør.personOppdrag.isEmpty())
        assertUtbetalingsbeløp(2.vedtaksperiode, 1431, 1431, subset = 1.februar til 26.februar)
        assertUtbetalingsbeløp(2.vedtaksperiode, 0, 1431, subset = 27.februar til 27.februar)
        assertUtbetalingsbeløp(2.vedtaksperiode, 0, 0, subset = 28.februar til 28.februar)
    }

    @Test
    fun `Refusjonsbeløpet er forskjellig fra beregnet inntekt i inntektsmeldingen`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = 30000.månedlig,
            refusjon = Inntektsmelding.Refusjon(25000.månedlig, null, emptyList())
        )
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `arbeidsgiver refunderer ikke`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            beregnetInntekt = INNTEKT,
            refusjon = Inntektsmelding.Refusjon(INGEN, null, emptyList())
        )
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `Arbeidsgiverperiode tilstøter Infotrygd`() {
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
        håndterUtbetalt()
        assertNoWarnings(1.vedtaksperiode.filter())
    }

    @Test
    fun `Arbeidsgiverperiode tilstøter ikke Infotrygd`() {
        håndterInntektsmelding(listOf(1.november(2017) til 16.november(2017)))

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
        håndterUtbetalt()
        assertWarning("Fant ikke refusjonsgrad for perioden. Undersøk oppgitt refusjon før du utbetaler.", 1.vedtaksperiode.filter())
    }

    @Test
    fun `Finner refusjon ved forlengelse fra Infotrygd`() {
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
        håndterUtbetalt()
        assertNoWarnings(1.vedtaksperiode.filter())
    }

    @Test
    fun `tidligere vedtaksperiode har opphør i refusjon`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, 20.januar, emptyList())
        )
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
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
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
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )


        assertTrue(inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.isNotEmpty())
        inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().inspektør.personOppdrag.isEmpty())
        assertUtbetalingsbeløp(2.vedtaksperiode, 0, 1431, subset = 1.mars til 16.mars)
        assertUtbetalingsbeløp(2.vedtaksperiode, 1431, 1431, subset = 17.mars til 31.mars)
    }

    @Test
    fun `kaster ikke ut vedtaksperiode når refusjonopphører`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, 20.januar, emptyList())
        )
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `ikke kast ut vedtaksperiode ved endring i refusjon`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null, listOf(EndringIRefusjon(15000.månedlig, 20.januar)))
        )
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `kaster ikke ut vedtaksperiode hvor endring i refusjon er etter perioden`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null, listOf(EndringIRefusjon(15000.månedlig, 1.februar)))
        )
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
        assertUtbetalingsbeløp(1.vedtaksperiode, 0, 1431, subset = 1.januar til 16.januar)
        assertUtbetalingsbeløp(1.vedtaksperiode, 1431, 1431, subset = 17.januar til 31.januar)
    }

    @Test
    fun `ikke kast ut vedtaksperiode ved ferie i slutten av perioden`() {
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
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )

        assertTrue(inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.isNotEmpty())
        inspektør.utbetalinger.last().inspektør.arbeidsgiverOppdrag.forEach { assertEquals(1431, it.beløp) }
        assertTrue(inspektør.utbetalinger.last().inspektør.personOppdrag.isEmpty())

        assertUtbetalingsbeløp(1.vedtaksperiode, 0, 1431, 1.januar til 16.januar)
        assertUtbetalingsbeløp(1.vedtaksperiode, 1431, 1431, 17.januar til 24.januar)
        assertUtbetalingsbeløp(1.vedtaksperiode, 0, 0, 25.januar til 31.januar)
    }

    @Test
    fun `to arbeidsgivere med ulik fom hvor den første har utbetalingsdager før arbeisdgiverperioden til den andre, ingen felles utbetalingsdager`() {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
            håndterSykmelding(Sykmeldingsperiode(21.januar, 10.februar, 100.prosent), orgnummer = a2)

            håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
            håndterSøknad(Sykdom(21.januar, 10.februar, 100.prosent), orgnummer = a2)

            håndterInntektsmelding(arbeidsgiverperioder = listOf(1.januar til 16.januar), orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a2)

            håndterInntektsmelding(arbeidsgiverperioder = listOf(21.januar til 5.februar), orgnummer = a2)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)

            håndterYtelser(1.vedtaksperiode, orgnummer = a1)
            håndterVilkårsgrunnlag(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                orgnummer = a1,
                inntektsvurdering = Inntektsvurdering(
                    listOf(
                        sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12)),
                        sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12))
                    )
                ),
                inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                    inntekter = listOf(
                        grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)),
                        grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3))
                    )
                , arbeidsforhold = emptyList())
            )
            håndterYtelser(1.vedtaksperiode, orgnummer = a1)
            håndterSimulering(1.vedtaksperiode, orgnummer = a1)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
            håndterUtbetalt(orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

            håndterYtelser(1.vedtaksperiode, orgnummer = a2)
            håndterSimulering(1.vedtaksperiode, orgnummer = a2)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
            håndterUtbetalt(orgnummer = a2)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)

            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 0,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 1.januar til 16.januar,
                orgnummer = a1
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 1081,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 17.januar til 31.januar,
                orgnummer = a1
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 0,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 1.februar til 10.februar,
                orgnummer = a1
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 0,
                forventetArbeidsgiverRefusjonsbeløp = 0,
                subset = 1.januar til 20.januar,
                orgnummer = a2
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 0,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 21.januar til 5.februar,
                orgnummer = a2
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 1081,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 6.februar til 10.februar,
                orgnummer = a2
            )
        }

    @Test
    fun `to arbeidsgivere med ulik fom hvor den første har utbetalingsdager før arbeisdgiverperioden til den andre`() {
            håndterSykmelding(Sykmeldingsperiode(1.januar, 10.februar, 100.prosent), orgnummer = a1)
            håndterSykmelding(Sykmeldingsperiode(21.januar, 10.februar, 100.prosent), orgnummer = a2)

            håndterSøknad(Sykdom(1.januar, 10.februar, 100.prosent), orgnummer = a1)
            håndterSøknad(Sykdom(21.januar, 10.februar, 100.prosent), orgnummer = a2)

            håndterInntektsmelding(arbeidsgiverperioder = listOf(1.januar til 16.januar), orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a2)

            håndterInntektsmelding(arbeidsgiverperioder = listOf(21.januar til 5.februar), orgnummer = a2)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)

            håndterYtelser(1.vedtaksperiode, orgnummer = a1)
            håndterVilkårsgrunnlag(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                orgnummer = a1,
                inntektsvurdering = Inntektsvurdering(
                    listOf(
                        sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12)),
                        sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12))
                    )
                ),
                inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                    inntekter = listOf(
                        grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)),
                        grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3))
                    )
                , arbeidsforhold = emptyList())
            )
            håndterYtelser(1.vedtaksperiode, orgnummer = a1)
            håndterSimulering(1.vedtaksperiode, orgnummer = a1)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
            håndterUtbetalt(orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

            håndterYtelser(1.vedtaksperiode, orgnummer = a2)
            håndterSimulering(1.vedtaksperiode, orgnummer = a2)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
            håndterUtbetalt(orgnummer = a2)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)

            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 0,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 1.januar til 16.januar,
                orgnummer = a1
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 1081,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 17.januar til 10.februar,
                orgnummer = a1
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 0,
                forventetArbeidsgiverRefusjonsbeløp = 0,
                subset = 1.januar til 20.januar,
                orgnummer = a2
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 0,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 21.januar til 5.februar,
                orgnummer = a2
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 1080,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 6.februar til 10.februar,
                orgnummer = a2
            )
        }

    @Test
    fun `to arbeidsgivere med ulik fom hvor den andre har utbetalingsdager før arbeidsgiverperioden til den første`() {
            håndterSykmelding(Sykmeldingsperiode(21.januar, 10.februar, 100.prosent), orgnummer = a1)
            håndterSykmelding(Sykmeldingsperiode(1.januar, 10.februar, 100.prosent), orgnummer = a2)

            håndterSøknad(Sykdom(21.januar, 10.februar, 100.prosent), orgnummer = a1)
            håndterSøknad(Sykdom(1.januar, 10.februar, 100.prosent), orgnummer = a2)

            håndterInntektsmelding(arbeidsgiverperioder = listOf(1.januar til 16.januar), orgnummer = a2)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a1)

            håndterInntektsmelding(arbeidsgiverperioder = listOf(21.januar til 5.februar), orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)

            håndterYtelser(1.vedtaksperiode, orgnummer = a1)
            håndterVilkårsgrunnlag(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                orgnummer = a1,
                inntektsvurdering = Inntektsvurdering(
                    listOf(
                        sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12)),
                        sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12))
                    )
                ),
                inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                    inntekter = listOf(
                        grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)),
                        grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3))
                    ), arbeidsforhold = emptyList()
                )
            )
            håndterYtelser(1.vedtaksperiode, orgnummer = a1)
            håndterSimulering(1.vedtaksperiode, orgnummer = a1)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
            håndterUtbetalt(orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

            håndterYtelser(1.vedtaksperiode, orgnummer = a2)
            håndterSimulering(1.vedtaksperiode, orgnummer = a2)
            håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
            håndterUtbetalt(orgnummer = a2)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
            assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)

            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 0,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 21.januar til 5.februar,
                orgnummer = a1
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 1081,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 6.februar til 10.februar,
                orgnummer = a1
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 0,
                forventetArbeidsgiverRefusjonsbeløp = 0,
                subset = 1.januar til 20.januar,
                orgnummer = a1
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 0,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 1.januar til 16.januar,
                orgnummer = a2
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 1081,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 17.januar til 20.januar,
                orgnummer = a2
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 1081,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 21.januar til 5.februar,
                orgnummer = a2
            )
            assertUtbetalingsbeløp(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                forventetArbeidsgiverbeløp = 1080,
                forventetArbeidsgiverRefusjonsbeløp = 1431,
                subset = 6.februar til 10.februar,
                orgnummer = a2
            )
        }

    @Test
    fun `gradert sykmelding med en arbeidsgiver`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 50.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT / 2, null, emptyList())
        )
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `korrigerende inntektsmelding endrer på refusjonsbeløp`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 50.prosent))
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null, emptyList())
        )
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT / 2, null, emptyList())
        )
        håndterSøknad(Sykdom(1.januar, 31.januar, 50.prosent))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `korrigerende inntektsmelding endrer på refusjonsbeløp med infotrygdforlengelse`() {
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null, emptyList())
        )
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT / 2, null, emptyList())
        )
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 50.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 50.prosent))
        håndterUtbetalingshistorikk(
            1.vedtaksperiode, ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 31.januar, 50.prosent, INNTEKT / 2),
            inntektshistorikk = listOf(
                Inntektsopplysning(ORGNUMMER, 17.januar, INNTEKT, true)
            )
        )
        håndterYtelser(1.vedtaksperiode)
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `korrigerende inntektsmelding endrer på refusjonsbeløp med infotrygdforlengelse og gap`() {
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null, emptyList())
        )
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            refusjon = Inntektsmelding.Refusjon(INNTEKT / 2, null, emptyList())
        )
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 50.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 50.prosent))
        håndterUtbetalingshistorikk(
            1.vedtaksperiode, ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 18.januar, 31.januar, 50.prosent, INNTEKT / 2),
            inntektshistorikk = listOf(
                Inntektsopplysning(ORGNUMMER, 18.januar, INNTEKT, true)
            )
        )
        håndterYtelser(1.vedtaksperiode)
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `to arbeidsgivere hvor andre arbeidsgiver har brukerutbetaling kaster ut alle perioder på personen`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2, refusjon = Inntektsmelding.Refusjon(INNTEKT, 20.januar))
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            inntektsvurdering = Inntektsvurdering(
                listOf(
                    sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12)),
                    sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12))
                )
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3)),
                    grunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(3))
                ), arbeidsforhold = emptyList()
            ),
            orgnummer = a1
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            TIL_INFOTRYGD,
            orgnummer = a1
        )
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            TIL_INFOTRYGD,
            orgnummer = a2
        )

    }

    @Test
    fun `en overgang fra Infotrygd uten inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 10.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 10.februar, 100.prosent))
        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            inntektshistorikk = listOf(
                Inntektsopplysning(orgnummer = ORGNUMMER, sykepengerFom = 17.januar, inntekt = INNTEKT, refusjonTilArbeidsgiver = true)
            ),
            utbetalinger = arrayOf(
                ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 31.januar, 100.prosent, INNTEKT)
            )
        )


        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertWarning("Fant ikke refusjonsgrad for perioden. Undersøk oppgitt refusjon før du utbetaler.", 1.vedtaksperiode.filter())
    }

    @Test
    fun `en forlengelse av overgang fra Infotrygd uten inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 10.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 10.februar, 100.prosent))

        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            inntektshistorikk = listOf(
                Inntektsopplysning(orgnummer = ORGNUMMER, sykepengerFom = 17.januar, inntekt = INNTEKT, refusjonTilArbeidsgiver = true)
            ),
            utbetalinger = arrayOf(
                ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 31.januar, 100.prosent, INNTEKT)
            )
        )

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        forlengVedtak(11.februar, 28.februar)

        assertWarning("Fant ikke refusjonsgrad for perioden. Undersøk oppgitt refusjon før du utbetaler.", 1.vedtaksperiode.filter())
        assertWarning("Fant ikke refusjonsgrad for perioden. Undersøk oppgitt refusjon før du utbetaler.", 2.vedtaksperiode.filter())
    }

    @Test
    fun `førstegangsbehandling i Spleis etter en overgang fra Infotrygd uten inntektsmelding `() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 10.februar, 100.prosent))
        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            inntektshistorikk = listOf(
                Inntektsopplysning(orgnummer = ORGNUMMER, sykepengerFom = 17.januar, inntekt = INNTEKT, refusjonTilArbeidsgiver = true)
            ),
            utbetalinger = arrayOf(
                ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 17.januar, 31.januar, 100.prosent, INNTEKT)
            )
        )

        håndterSøknad(Sykdom(1.februar, 10.februar, 100.prosent))

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        nyttVedtak(1.mars, 31.mars)

        assertWarning("Fant ikke refusjonsgrad for perioden. Undersøk oppgitt refusjon før du utbetaler.", 1.vedtaksperiode.filter())
        assertNoWarnings(2.vedtaksperiode.filter(ORGNUMMER))
    }

    @Test
    fun `to arbeidsgivere, en av dem mangler refusjon, begge får warning`() = Toggle.FlereArbeidsgivereFraInfotrygd.enable {
        håndterInntektsmelding(arbeidsgiverperioder = listOf(1.januar til 16.januar), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 10.februar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 10.februar, 100.prosent), orgnummer = a2)

        håndterSøknad(Sykdom(1.februar, 10.februar, 100.prosent), orgnummer = a1)

        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektshistorikk = listOf(
                Inntektsopplysning(orgnummer = a1, sykepengerFom = 17.januar, inntekt = INNTEKT, refusjonTilArbeidsgiver = true),
                Inntektsopplysning(orgnummer = a2, sykepengerFom = 17.januar, inntekt = INNTEKT, refusjonTilArbeidsgiver = true)
            ),
            utbetalinger = arrayOf(
                ArbeidsgiverUtbetalingsperiode(a1, 17.januar, 31.januar, 100.prosent, INNTEKT),
                ArbeidsgiverUtbetalingsperiode(a2, 17.januar, 31.januar, 100.prosent, INNTEKT)
            )
        )

        håndterSøknad(Sykdom(1.februar, 10.februar, 100.prosent), orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertWarning("Fant ikke refusjonsgrad for perioden. Undersøk oppgitt refusjon før du utbetaler.", 1.vedtaksperiode.filter(a1))
        assertWarning("Fant ikke refusjonsgrad for perioden. Undersøk oppgitt refusjon før du utbetaler.", 1.vedtaksperiode.filter(a2))
    }

    @Test
    fun `Finner refusjon fra feil inntektsmelding ved Infotrygdforlengelse`() {
        håndterSykmelding(Sykmeldingsperiode(22.januar, 31.januar, 100.prosent))
        // Inntektsmelding blir ikke brukt ettersom det er forlengelse fra Infotrygd.
        // Når vi ser etter refusjon for Infotrygdperioden finner vi alikevel frem til denne inntektsmeldingen og forsøker å finne
        // refusjonsbeløp på 1-5.Januar som er før arbeidsgiverperioden
        håndterInntektsmelding(listOf(Periode(6.januar, 21.januar)))
        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.januar, 21.januar, 100.prosent, 1000.daglig),
            inntektshistorikk = listOf(Inntektsopplysning(ORGNUMMER, 1.januar, INNTEKT, true))
        )
        håndterSøknad(Sykdom(22.januar, 31.januar, 100.prosent))

        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertInfo("Refusjon gjelder ikke for hele utbetalingsperioden", 1.vedtaksperiode.filter())
    }

    @Test
    fun `Første utbetalte dag er før første fraværsdag`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(), førsteFraværsdag = 17.januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))

        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertInfo("Refusjon gjelder ikke for hele utbetalingsperioden", 1.vedtaksperiode.filter())
        assertWarning("Første fraværsdag i inntektsmeldingen er ulik skjæringstidspunktet. Kontrollér at inntektsmeldingen er knyttet til riktig periode.", 1.vedtaksperiode.filter())
    }

    @Test
    fun `Korrigerende inntektsmelding med feil skjæringstidspunkt går til manuell behandling på grunn av warning`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(emptyList(), førsteFraværsdag = 17.januar)

        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        assertInfo("Refusjon gjelder ikke for hele utbetalingsperioden", 1.vedtaksperiode.filter())
        assertWarning("Mottatt flere inntektsmeldinger - den første inntektsmeldingen som ble mottatt er lagt til grunn. Utbetal kun hvis det blir korrekt.", 1.vedtaksperiode.filter())
    }

    @Test
    fun `arbeidsgiver sender unødvendig inntektsmelding ved forlengelse før sykmelding`() {
        // Etter diskusjon med Morten ble vi enige om at vi ikke trenger warning på en forlengelse hvor inntektsmelding kom før sykmelding.
        // Om ny inntektsmelding fører til brukerutbetalinger vil dette bli oppdaget og sendt til Infotrygd senere
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar))
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode)
        håndterYtelser(1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode)
        håndterUtbetalt()

        håndterInntektsmelding(emptyList(), førsteFraværsdag = 1.februar)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent))
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent))
        håndterYtelser(2.vedtaksperiode)

        assertNoWarnings(1.vedtaksperiode.filter())
        assertNoWarnings(2.vedtaksperiode.filter())
        assertInfo("Refusjon gjelder ikke for hele utbetalingsperioden", 2.vedtaksperiode.filter())
    }

    @Test
    fun `to arbeidsgivere, om første har opphør i refusjon kastes begge ut`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1, refusjon = Inntektsmelding.Refusjon(INNTEKT, 30.januar, emptyList()))
        assertSisteForkastetPeriodeTilstand(a1, 1.vedtaksperiode, TIL_INFOTRYGD)
        assertError("Arbeidsgiver opphører refusjon (mistenker brukerutbetaling ved flere arbeidsgivere)", 1.vedtaksperiode.filter(a1))
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)
        assertSisteForkastetPeriodeTilstand(a2, 1.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `to arbeidsgivere, om første har endring i refusjon kastes begge ut`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            orgnummer = a1,
            refusjon = Inntektsmelding.Refusjon(INNTEKT, null, listOf(EndringIRefusjon(INNTEKT/2, 30.januar)))
        )
        assertSisteForkastetPeriodeTilstand(a1, 1.vedtaksperiode, TIL_INFOTRYGD)
        assertError("Arbeidsgiver har endringer i refusjon (mistenker brukerutbetaling ved flere arbeidsgivere)", 1.vedtaksperiode.filter(a1))
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)
        assertSisteForkastetPeriodeTilstand(a2, 1.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `to arbeidsgivere, om første refunderer delvis kastes begge ut`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            orgnummer = a1,
            refusjon = Inntektsmelding.Refusjon(INNTEKT/2, null, emptyList())
        )
        assertSisteForkastetPeriodeTilstand(a1, 1.vedtaksperiode, TIL_INFOTRYGD)
        assertError("Inntektsmelding inneholder beregnet inntekt og refusjon som avviker med hverandre (mistenker brukerutbetaling ved flere arbeidsgivere)", 1.vedtaksperiode.filter(a1))
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)
        assertSisteForkastetPeriodeTilstand(a2, 1.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `to arbeidsgivere, om første ikke refunderer`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            listOf(1.januar til 16.januar),
            orgnummer = a1,
            refusjon = Inntektsmelding.Refusjon(INGEN, null, emptyList())
        )
        assertSisteForkastetPeriodeTilstand(a1, 1.vedtaksperiode, TIL_INFOTRYGD)
        assertError("Arbeidsgiver forskutterer ikke (mistenker brukerutbetaling ved flere arbeidsgivere)", 1.vedtaksperiode.filter(a1))
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)
        assertSisteForkastetPeriodeTilstand(a2, 1.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `to arbeidsgivere, hvor den andre har opphør i refusjon`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2, refusjon = Inntektsmelding.Refusjon(INNTEKT, 15.januar, emptyList()))
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode, orgnummer = a1, inntektsvurdering = Inntektsvurdering(
                listOf(
                    sammenligningsgrunnlag(a1, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12)),
                    sammenligningsgrunnlag(a2, finnSkjæringstidspunkt(a1, 1.vedtaksperiode), INNTEKT.repeat(12))
                )
            )
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        assertForkastetPeriodeTilstander(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            AVVENTER_ARBEIDSGIVERE,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
            TIL_INFOTRYGD,
            orgnummer = a1
        )
        assertForkastetPeriodeTilstander(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            AVVENTER_ARBEIDSGIVERE,
            TIL_INFOTRYGD,
            orgnummer = a2
        )
    }
}
