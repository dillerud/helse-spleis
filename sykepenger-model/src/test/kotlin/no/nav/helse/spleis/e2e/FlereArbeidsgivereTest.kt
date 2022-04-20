package no.nav.helse.spleis.e2e

import java.time.LocalDateTime
import no.nav.helse.april
import no.nav.helse.assertForventetFeil
import no.nav.helse.august
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding.Refusjon
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Medlemskapsvurdering
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.inspectors.inspektør
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.juli
import no.nav.helse.juni
import no.nav.helse.mai
import no.nav.helse.mars
import no.nav.helse.oktober
import no.nav.helse.person.TilstandType.AVSLUTTET
import no.nav.helse.person.TilstandType.AVSLUTTET_UTEN_UTBETALING
import no.nav.helse.person.TilstandType.AVVENTER_GODKJENNING
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.person.TilstandType.TIL_UTBETALING
import no.nav.helse.person.etterlevelse.MaskinellJurist
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.testhelpers.inntektperioderForSammenligningsgrunnlag
import no.nav.helse.testhelpers.inntektperioderForSykepengegrunnlag
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Inntekt.Companion.årlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FlereArbeidsgivereTest : AbstractEndToEndTest() {

    @Test
    fun `Sammenligningsgrunnlag for flere arbeidsgivere`() {
        val periodeA1 = 1.januar til 31.januar
        nyPeriode(1.januar til 31.januar, a1)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(periodeA1.start, periodeA1.start.plusDays(15))),
            førsteFraværsdag = periodeA1.start,
            beregnetInntekt = 30000.månedlig,
            refusjon = Refusjon(30000.månedlig, null, emptyList()),
            orgnummer = a1
        )
        person.håndter(ytelser(1.vedtaksperiode, orgnummer = a1, inntektshistorikk = emptyList()))
        person.håndter(vilkårsgrunnlag(
                vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                orgnummer = a1,
                inntektsvurdering = Inntektsvurdering(
                        inntekter = inntektperioderForSammenligningsgrunnlag {
                            1.januar(2017) til 1.juni(2017) inntekter {
                                a1 inntekt INNTEKT
                                a2 inntekt 5000.månedlig
                            }
                            1.august(2017) til 1.desember(2017) inntekter {
                                a1 inntekt 17000.månedlig
                                a2 inntekt 3500.månedlig
                            }
                        }
                ),
                inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList())
        )
        )

        val periodeA2 = 15.januar til 15.februar
        nyPeriode(periodeA2, a2)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(periodeA2.start, periodeA2.start.plusDays(15))),
            førsteFraværsdag = periodeA2.start,
            beregnetInntekt = 10000.månedlig,
            refusjon = Refusjon(10000.månedlig, null, emptyList()),
            orgnummer = a2

        )
        assertEquals(318500.årlig, person.beregnSammenligningsgrunnlag(1.januar, MaskinellJurist()).sammenligningsgrunnlag)
    }

    @Test
    fun `Sammenligningsgrunnlag for flere arbeidsgivere med flere sykeperioder`() {
        nyPeriode(15.januar til 5.februar, a1)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(15.januar til 28.januar, 2.februar til 3.februar),
            førsteFraværsdag = 2.februar,
            beregnetInntekt = 30000.månedlig,
            refusjon = Refusjon(30000.månedlig, null, emptyList()),
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1, inntektshistorikk = emptyList())
        håndterVilkårsgrunnlag(1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurdering = Inntektsvurdering(
                inntekter = inntektperioderForSammenligningsgrunnlag {
                    1.januar(2017) til 1.juni(2017) inntekter {
                        a1 inntekt INNTEKT
                        a2 inntekt 5000.månedlig
                    }
                    1.august(2017) til 1.desember(2017) inntekter {
                        a1 inntekt 17000.månedlig
                        a2 inntekt 3500.månedlig
                    }
                }
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList())
        )
        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            TIL_INFOTRYGD
        )
        assertEquals(282500.årlig, person.beregnSammenligningsgrunnlag(2.februar, MaskinellJurist()).sammenligningsgrunnlag)
    }

    @Test
    fun `Sammenligningsgrunnlag for flere arbeidsgivere som overlapper hverandres sykeperioder`() {
        håndterSykmelding(Sykmeldingsperiode(15.januar, 5.februar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(15.januar, 5.februar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(15.januar til 28.januar),
            førsteFraværsdag = 15.januar,
            beregnetInntekt = 30000.månedlig,
            refusjon = Refusjon(30000.månedlig, null, emptyList()),
            orgnummer = a1
        )

        håndterSykmelding(Sykmeldingsperiode(15.januar, 15.februar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(15.januar, 15.februar, 100.prosent), orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurdering = Inntektsvurdering(
                inntekter = inntektperioderForSammenligningsgrunnlag {
                    1.januar(2017) til 1.juni(2017) inntekter {
                        a1 inntekt INNTEKT
                        a2 inntekt 5000.månedlig
                    }
                    1.august(2017) til 1.desember(2017) inntekter {
                        a1 inntekt 17000.månedlig
                        a2 inntekt 3500.månedlig
                    }
                }
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList())
        )

        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(15.januar, 15.januar.plusDays(15))),
            beregnetInntekt = 10000.månedlig,
            førsteFraværsdag = 15.januar,
            refusjon = Refusjon(10000.månedlig, null, emptyList()),
            orgnummer = a2
        )

        assertEquals(318500.årlig, person.beregnSammenligningsgrunnlag(15.januar, MaskinellJurist()).sammenligningsgrunnlag)
    }

    @Test
    fun `vedtaksperioder atskilt med betydelig tid`() {
        prosessperiode(1.januar til 31.januar, a1)
        assertNoErrors()
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)

        prosessperiode(1.mars til 31.mars, a2)
        assertNoErrors()
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
    }

    @Test
    fun `Tillater førstegangsbehandling av flere arbeidsgivere der inntekt i inntektsmelding er på samme dato`() {
        val periode = 1.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a1)

        håndterUtbetalingshistorikk(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a1)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a2)

        håndterInntektsmelding(listOf(1.januar(2021) til 16.januar(2021)), førsteFraværsdag = 1.januar(2021), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a2)

        håndterInntektsmelding(listOf(1.januar(2021) til 16.januar(2021)), førsteFraværsdag = 1.januar(2021), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_VILKÅRSPRØVING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)

        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2020) til 1.desember(2020) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt INNTEKT
                }
            }
        ))
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)
        håndterUtbetalt(orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
    }

    @Test
    fun `Tillater førstegangsbehandling av flere arbeidsgivere der inntekt i inntektsmelding ikke er på samme dato - så lenge de er i samme måned`() {
        val periode = 1.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a1)

        håndterUtbetalingshistorikk(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a1)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a2)

        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a1
        )
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a2)

        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 3.januar(2021), 6.januar(2021) til 18.januar(2021)),
            førsteFraværsdag = 6.januar(2021),
            beregnetInntekt = 1000.månedlig,
            orgnummer = a2
        )
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2020) til 1.desember(2020) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt 1000.månedlig
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)
    }

    @Test
    fun `Tillater ikke førstegangsbehandling av flere arbeidsgivere der inntekt i inntektsmelding ikke er på samme dato - hvis datoene er i forskjellig måned`() {
        val periode = 31.desember(2020) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a1)

        håndterUtbetalingshistorikk(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a1)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a2)

        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(31.desember(2020) til 15.januar(2021)),
            førsteFraværsdag = 31.desember(2020),
            orgnummer = a1
        )
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a2)

        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 3.januar(2021), 6.januar(2021) til 18.januar(2021)),
            førsteFraværsdag = 6.januar(2021),
            beregnetInntekt = 1000.månedlig,
            orgnummer = a2
        )
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2020) til 1.desember(2020) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt 1000.månedlig
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a2)
    }

    @Test
    fun `Lager ikke utbetalinger for vedtaksperioder hos andre arbeidsgivere som ligger senere i tid enn den som er først totalt sett`() {
        val periode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1, 1.januar(2021), INNTEKT, true)
        )
        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, 1.januar(2021), 31.januar(2021), 100.prosent, INNTEKT)
        )
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1, inntektshistorikk = inntektshistorikk)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        val periode2 = 1.mars(2021) til 31.mars(2021)
        val a2Periode = 2.april(2021) til 30.april(2021)
        håndterSykmelding(Sykmeldingsperiode(periode2.start, periode2.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(a2Periode.start, a2Periode.endInclusive, 100.prosent), orgnummer = a2)

        håndterSøknad(Sykdom(periode2.start, periode2.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1, inntektshistorikk = inntektshistorikk)

        assertEquals(0, inspektør(a2).ikkeUtbetalteUtbetalingerForVedtaksperiode(1.vedtaksperiode).size)
        assertEquals(0, inspektør(a2).avsluttedeUtbetalingerForVedtaksperiode(1.vedtaksperiode).size)
    }

    @Test
    fun `tillater to arbeidsgivere med korte perioder, og forlengelse av disse`() {
        val periode = 1.januar(2021) til 14.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)

        val forlengelseperiode = 15.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a1
        )

        assertSisteTilstand(2.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a2)

        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a2
        )

        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)

        håndterVilkårsgrunnlag(2.vedtaksperiode, orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2020) til 1.desember(2020) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(2.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true, orgnummer = a1)

        håndterUtbetalt(orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a2)
    }

    @Test
    fun `medlemskap ikke oppfyllt i vilkårsgrunnlag, avviser perioden riktig for begge arbeidsgivere`() {
        val periode = Periode(1.januar, 31.januar)
        nyPeriode(periode, a1)
        nyPeriode(periode, a2)

        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(periode.start, periode.start.plusDays(15))),
            førsteFraværsdag = periode.start,
            orgnummer = a1
        )

        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(periode.start, periode.start.plusDays(15))),
            førsteFraværsdag = periode.start,
            orgnummer = a2
        )

        historikk(a1)
        person.håndter(
            vilkårsgrunnlag(
                    vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
                    medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Nei,
                    orgnummer = a1,
                    inntektsvurdering = Inntektsvurdering(
                        inntekter = inntektperioderForSammenligningsgrunnlag {
                            1.januar(2017) til 1.desember(2017) inntekter {
                                a1 inntekt INNTEKT
                            }
                            1.januar(2017) til 1.desember(2017) inntekter {
                                a2 inntekt INNTEKT
                            }
                        },
                    ),
                    inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(inntekter = emptyList(), arbeidsforhold = emptyList())
            )
        )
        historikk(a1)

        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)

        assertTrue(person.personLogg.hasWarningsOrWorse())
        inspektør(a1).also {
            it.utbetalingstidslinjer(1.vedtaksperiode).inspektør.also { tidslinjeInspektør ->
                assertEquals(16, tidslinjeInspektør.arbeidsgiverperiodeDagTeller)
                assertEquals(4, tidslinjeInspektør.navHelgDagTeller)
                assertEquals(11, tidslinjeInspektør.avvistDagTeller)
            }
        }
        assertTrue(person.personLogg.hasWarningsOrWorse())
        inspektør(a2).also {
            it.utbetalingstidslinjer(1.vedtaksperiode).inspektør.also { tidslinjeInspektør ->
                assertEquals(16, tidslinjeInspektør.arbeidsgiverperiodeDagTeller)
                assertEquals(4, tidslinjeInspektør.navHelgDagTeller)
                assertEquals(11, tidslinjeInspektør.avvistDagTeller)
            }
        }

    }

    @Test
    fun `To arbeidsgivere med sykdom gir ikke warning for flere inntekter de siste tre månedene`() {
        val periode = 1.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar(2021) til 16.januar(2021)), førsteFraværsdag = 1.januar(2021), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar(2021) til 16.januar(2021)), førsteFraværsdag = 1.januar(2021), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2020) til 1.desember(2020) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertNoWarnings()
    }

    @Test
    fun `Tar hensyn til forkastede perioder ved beregning av maks dato`() {
        //fom       tom      Arb.   forbrukt Akkumulert     gjenstående
        //19/3/20	19/4/20	 AI     22	     22	            226
        //27/4/20	3/6/20	 AI     28	     50	            198
        //4/6/20	19/7/20	 AS     32	     82	            166	        166
        //10/8/20	21/9/20	 AI     31	     113	        135	        129 -- 20/7 - 27/7 ligger som ferie i speil men sykemelding i infotrygd
        //22/9/20	30/9/20	 AS     7	     120	        128	        122
        //10/11/20	1/2/21	 BI     60	     180	        68	        101
        //2/2/21	18/4/21	 BS     54	     234	        14	        47
        //3/5/21	20/5/21	 BS     14	     248	        0	        32

        val inntektshistorikkA1 = listOf(
            Inntektsopplysning(a1, 1.januar, INNTEKT, true),
        )
        val inntektshistorikkA2 = listOf(
            Inntektsopplysning(a1, 1.januar, INNTEKT, true),
            Inntektsopplysning(a2, 1.juli, INNTEKT, true),
        )
        val ITPeriodeA1 = 1.januar til 31.januar
        val ITPeriodeA2 = 1.juli til 31.juli

        val spleisPeriodeA1 = 1.februar til 28.februar
        val spleisPeriodeA2 = 1.august til 31.august
        val utbetalingerA1 = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, ITPeriodeA1.start, ITPeriodeA1.endInclusive, 100.prosent, INNTEKT),
        )
        val utbetalingerA2 = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, ITPeriodeA1.start, ITPeriodeA1.endInclusive, 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2, ITPeriodeA2.start, ITPeriodeA2.endInclusive, 100.prosent, INNTEKT),
        )

        håndterSykmelding(Sykmeldingsperiode(spleisPeriodeA1.start, spleisPeriodeA1.endInclusive, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(spleisPeriodeA1.start, spleisPeriodeA1.endInclusive, 100.prosent), orgnummer = a1)

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalingerA1, inntektshistorikk = inntektshistorikkA1, orgnummer = a1, besvart = LocalDateTime.MIN)
        håndterYtelser(1.vedtaksperiode, *utbetalingerA1, inntektshistorikk = inntektshistorikkA1, orgnummer = a1, besvart = LocalDateTime.MIN)

        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        person.invaliderAllePerioder(hendelselogg, feilmelding = "Feil med vilje")

        håndterSykmelding(Sykmeldingsperiode(spleisPeriodeA2.start, spleisPeriodeA2.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(spleisPeriodeA2.start, spleisPeriodeA2.endInclusive, 100.prosent), orgnummer = a2)

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalingerA2, inntektshistorikk = inntektshistorikkA2, orgnummer = a2, besvart = LocalDateTime.MIN)
        håndterYtelser(1.vedtaksperiode, *utbetalingerA2, inntektshistorikk = inntektshistorikkA2, orgnummer = a2, besvart = LocalDateTime.MIN)

        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertEquals(88, inspektør(a2).forbrukteSykedager(0))
    }

    @Test
    fun `to AG - to perioder på hver - siste periode på første AG til godkjenning, siste periode på andre AG avventer første AG`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(1.januar, 16.januar)),
            beregnetInntekt = 20000.månedlig,
            orgnummer = a1
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(1.januar, 16.januar)),
            beregnetInntekt = 20000.månedlig,
            orgnummer = a2
        )

        håndterYtelser(vedtaksperiodeIdInnhenter = 1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(
            vedtaksperiodeIdInnhenter = 1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurdering = Inntektsvurdering(inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    a1 inntekt 20000.månedlig
                    a2 inntekt 20000.månedlig
                }
            })
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a2)

        håndterYtelser(vedtaksperiodeIdInnhenter = 2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(vedtaksperiodeIdInnhenter = 2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)

        inspektør(a1) {
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
            assertTilstander(
                2.vedtaksperiode,
                START,
                AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
                AVVENTER_HISTORIKK,
                AVVENTER_SIMULERING,
                AVVENTER_GODKJENNING,
                TIL_UTBETALING,
                AVSLUTTET
            )
            assertNoErrors()
            assertEquals(1, avsluttedeUtbetalingerForVedtaksperiode(1.vedtaksperiode).size)
            assertEquals(0, ikkeUtbetalteUtbetalingerForVedtaksperiode(1.vedtaksperiode).size)
            assertEquals(1, avsluttedeUtbetalingerForVedtaksperiode(2.vedtaksperiode).size)
            assertEquals(0, ikkeUtbetalteUtbetalingerForVedtaksperiode(2.vedtaksperiode).size)
        }

        inspektør(a2) {
            assertTilstander(
                1.vedtaksperiode,
                START,
                AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
                AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
                AVVENTER_HISTORIKK,
                AVVENTER_SIMULERING,
                AVVENTER_GODKJENNING,
                TIL_UTBETALING,
                AVSLUTTET
            )
            assertTilstander(
                2.vedtaksperiode,
                START,
                AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
                AVVENTER_HISTORIKK,
                AVVENTER_SIMULERING,
                AVVENTER_GODKJENNING
            )
            assertNoErrors()
            assertEquals(1, avsluttedeUtbetalingerForVedtaksperiode(1.vedtaksperiode).size)
            assertEquals(0, ikkeUtbetalteUtbetalingerForVedtaksperiode(1.vedtaksperiode).size)
            assertEquals(0, avsluttedeUtbetalingerForVedtaksperiode(2.vedtaksperiode).size)
            assertEquals(1, ikkeUtbetalteUtbetalingerForVedtaksperiode(2.vedtaksperiode).size)
        }
    }

    @Test
    fun testyMc() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    a1 inntekt INNTEKT
                    a2 inntekt INNTEKT
                }
            }
        ))
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        inspektør(a1).utbetalinger.forEach {
            assertEquals(a1, it.inspektør.arbeidsgiverOppdrag.inspektør.mottaker)
        }
        inspektør(a2).utbetalinger.forEach {
            assertEquals(a2, it.inspektør.arbeidsgiverOppdrag.inspektør.mottaker)
        }
    }

    @Test
    fun `Beregning av utbetaling over flere arbeidsgivere hvor en arbeidsgiver ikke har utbetaling`() {
        // Oppretter en arbeidsgiverperiode tilbake i tid som ikke skal ha utbetaling
        håndterSykmelding(Sykmeldingsperiode(1.januar(2017), 16.januar(2017), 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar(2017), 16.januar(2017), 100.prosent), orgnummer = a2)
        håndterUtbetalingshistorikk(1.vedtaksperiode, orgnummer = a2)

        assertDoesNotThrow { nyttVedtak(1.januar, 31.januar, orgnummer = a1) }
    }

    @Test
    fun `Går ikke direkte til AVVENTER_HISTORIKK dersom inntektsmelding kommer før søknad`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        val inntektsmeldingId = håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmeldingReplay(inntektsmeldingId, 1.vedtaksperiode.id(a1))

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            orgnummer = a1
        )
    }

    @Test
    fun `Siste arbeidsgiver som går til AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER sparker første tilbake til AVVENTER_HISTORIKK når inntektsmelding kommer før søknad`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            orgnummer = a1
        )
        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            orgnummer = a2
        )
    }

    @Test
    fun `Skal ikke ha noen avviste dager ved ulik startdato selv om arbeidsgiverperiodedag og navdag overlapper og begge har sykdomsgrad på 20 prosent eller høyere`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 20.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(17.januar, 16.februar, 20.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 20.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(17.januar, 16.februar, 20.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterInntektsmelding(listOf(17.januar til 1.februar), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            orgnummer = a1,
            inntektsvurdering = Inntektsvurdering(
                inntekter = inntektperioderForSammenligningsgrunnlag {
                    1.januar(2017) til 1.desember(2017) inntekter {
                        a1 inntekt INNTEKT
                        a2 inntekt INNTEKT
                    }
                }
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = inntektperioderForSykepengegrunnlag {
                    1.oktober(2017) til 1.desember(2017) inntekter {
                        a1 inntekt INNTEKT
                        a2 inntekt INNTEKT
                    }
                }
            , arbeidsforhold = emptyList())
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertForventetFeil(
            forklaring = "Ønsket oppførsel: arbeidsgiverperiodedag må ha ekte grad (ikke 0%), da den teller med i beregning av total sykdomsgrad " +
                "som kan slå ut negativt ved flere arbeidsgivere. Skjer eksempelvis dersom man beregner totalgrad av arbeidsgiverperiodedag hos én " +
                "arbeidsgiver og sykedag med 20% sykdom hos en annen arbeidsgiver",
            nå = {
                assertTrue(inspektør(a1).utbetalingstidslinjer(1.vedtaksperiode).inspektør.avvistDagTeller > 0)
            },
            ønsket = {
                assertEquals(0, inspektør(a1).utbetalingstidslinjer(1.vedtaksperiode).inspektør.avvistDagTeller)
            }
        )
    }

    @Test
    fun `Sykmelding og søknad kommer for to perioder før inntektsmelding kommer - skal fortsatt vilkårsprøve kun én gang`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 18.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 18.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 18.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 18.januar, 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(20.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(22.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(20.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(22.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 20.januar, orgnummer = a1)
        // Sender med en annen inntekt enn i forrige IM for å kunne asserte på at det er denne vi bruker
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 32000.månedlig, førsteFraværsdag = 22.januar, orgnummer = a2)

        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), beregnetInntekt = 31000.månedlig,  orgnummer = a2)

        val sammenligningsgrunnlag = Inntektsvurdering(listOf(
            sammenligningsgrunnlag(a1, 20.januar, INNTEKT.repeat(12)),
            sammenligningsgrunnlag(a2, 20.januar, 32000.månedlig.repeat(12))
        ))
        val sykepengegrunnlag = InntektForSykepengegrunnlag(
            inntekter = listOf(
                grunnlag(a1, 20.januar, INNTEKT.repeat(3)),
                grunnlag(a2, 20.januar, INNTEKT.repeat(3))
            ), arbeidsforhold = emptyList()
        )

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, inntektsvurdering = sammenligningsgrunnlag, inntektsvurderingForSykepengegrunnlag = sykepengegrunnlag, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(orgnummer = a2)

        assertTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer=a1)
        assertTilstand(2.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer=a2)
    }

    @Test
    fun `Burde ikke håndtere sykmelding dersom vi har forkastede vedtaksperioder i andre arbeidsforhold`() {
        håndterSykmelding(Sykmeldingsperiode(2.januar, 1.februar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(2.januar, 1.februar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)

        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, TIL_INFOTRYGD, orgnummer = a2)
    }

    @Test
    fun `kastes ikke ut pga manglende inntekt etter inntektsmelding`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 8.januar, 100.prosent), orgnummer = a2)
        håndterSykmelding(Sykmeldingsperiode(4.januar, 8.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(9.januar, 18.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(9.januar, 18.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(3.januar, 8.januar, 100.prosent), orgnummer = a2)
        håndterUtbetalingshistorikk(1.vedtaksperiode, orgnummer = a2)
        håndterSøknad(Sykdom(4.januar, 8.januar, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(19.januar, 22.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(9.januar, 18.januar, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(2.vedtaksperiode, orgnummer = a1)

        håndterSøknad(Sykdom(9.januar, 18.januar, 100.prosent), orgnummer = a2)
        håndterUtbetalingshistorikk(2.vedtaksperiode, orgnummer = a2)

        håndterSøknad(Sykdom(19.januar, 22.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(4.januar til 19.januar), orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a1)

        assertForventetFeil(
            forklaring = "https://trello.com/c/OFgymppw Sjekker sykepengegrunnlag opp mot skjæringstidspunkt, mens inntekt lagres på 'første fraværsdag'",
            nå = { assertError("Forventer minst ett sykepengegrunnlag som er fra inntektsmelding eller Infotrygd", 3.vedtaksperiode.filter(a1)) },
            ønsket = {
                assertNoErrors(3.vedtaksperiode.filter(a1))
                assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a1)
                assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a2)
                assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a1)
                assertSisteTilstand(2.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a2)
                assertSisteTilstand(3.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a1)
            }
        )
    }

    @Test
    fun `går til AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER ved IM dersom vi har vedtaksperioder som ikke overlapper, men har samme skjæringstidspunkt som nåværende`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent), orgnummer = a3)

        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)

        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)

        assertForventetFeil(
            forklaring = "https://trello.com/c/vVcsM2tp " +
                "Hvis vi mottar inntektsmelding for ag1 og ag2 vil koden i dag hoppe videre til AVVENTER_HISTORIKK " +
                "siden alle perioder som overlapper har inntekt. " +
                "Vi burde forsikre oss om at alle vedtaksperioder som har samme skjæringstidspunkt har inntekt i stedet.",
            nå = {
                assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
                assertTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)
            },
            ønsket = {
                assertTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a1)
                assertTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)
            }
        )
        // TODO: Utvid testen med IM og utbetaling for AG3
    }

    @Test
    fun `forlengelse av AVSLUTTET_UTEN_UTBETALING skal ikke gå til AVVENTER_HISTORIKK ved flere arbeidsgivere om IM kommer først`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 16.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.januar, 16.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 16.januar, 100.prosent), orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(17.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(17.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)
        håndterSøknad(Sykdom(17.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(17.januar, 31.januar, 100.prosent), orgnummer = a2)

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            orgnummer = a2
        )
    }

    @Test
    fun `GjenopptaBehandling poker ikke fremtidig periode for en annen arbeidsgiver videre ved tidligere uferdige perioder`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.mai, 31.mai, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.mai, 31.mai, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.mai til 16.mai), orgnummer = a2)

        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        assertTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)

        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(orgnummer = a1)

        assertTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterVilkårsgrunnlag(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)

        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2)
    }

    @Test
    fun `inntektsmelding skal kun treffe sammenhengende vedtaksperioder, ikke alle med samme skjæringstidspunkt`() {
        // Vedtaksperiode for AG 1 skal bare koble sammen to vedtaksperioder for AG 2 så de får samme skjæringstidspunkt
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 20.januar, 100.prosent), orgnummer = a2)
        håndterSykmelding(Sykmeldingsperiode(25.januar, 31.januar, 100.prosent), orgnummer = a2)

        håndterSøknad(Sykdom(1.januar, 20.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(25.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)

        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)
        assertTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a1)
        assertTilstand(1.vedtaksperiode, AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER, orgnummer = a2)
        assertTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK, orgnummer = a2)
    }

    @Test
    fun `Burde ikke kunne opprette vedtaksperiode før utbetalt periode ved flere AG`() {
        nyttVedtak(1.mars, 31.mars, orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)

        assertForventetFeil(
            forklaring = "Perioden blir ikke forkastet dersom en vedtaksperiode er utbetalt for en annen arbeidsgiver",
            nå = {
                assertFalse(inspektør(a1).periodeErForkastet(1.vedtaksperiode))
            },
            ønsket = {
                assertTrue(inspektør(a1).periodeErForkastet(1.vedtaksperiode))
            }
        )
    }
}
