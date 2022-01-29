package no.nav.helse.spleis.e2e

import no.nav.helse.*
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.til
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Inntektskilde
import no.nav.helse.person.Periodetype
import no.nav.helse.person.TilstandType.*
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Inntektsopplysning
import no.nav.helse.serde.reflection.castAsList
import no.nav.helse.testhelpers.inntektperioderForSammenligningsgrunnlag
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

internal class FlereArbeidsgivereInfotrygdTest : AbstractEndToEndTest()  {

    @BeforeEach
    fun setUp() {
        Toggle.FlereArbeidsgivereFraInfotrygd.enable()
    }

    @Test
    fun `Kaster ut saker med flere arbeidsgivere som er overgang fra infotrygd - oppdager dette ved mottak av første sykmelding`() =
        Toggle.FlereArbeidsgivereFraInfotrygd.disable {
            val periode = 27.januar til 31.januar
            val inntektshistorikk = listOf(
                Inntektsopplysning(a1.toString(), 20.januar, INNTEKT, true),
                Inntektsopplysning(a2.toString(), 20.januar, INNTEKT, true)
            )
            val utbetalinger = arrayOf(
                ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar, 26.januar, 100.prosent, INNTEKT),
                ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar, 26.januar, 100.prosent, INNTEKT)
            )
            håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
            håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
            assertSisteForkastetPeriodeTilstand(a1, 1.vedtaksperiode, TIL_INFOTRYGD)
        }

    @Test
    fun `Oppdager at det er en flere arbeidsgivere-sak fra infotrygd mens vi venter på IM eller historikk`() =
        Toggle.FlereArbeidsgivereFraInfotrygd.disable {
            val periode = 27.januar til 15.februar
            val inntektshistorikk = listOf(
                Inntektsopplysning(a1.toString(), 20.januar, INNTEKT, true),
                Inntektsopplysning(a2.toString(), 20.januar, INNTEKT, true)
            )
            val utbetalinger = arrayOf(
                ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar, 26.januar, 100.prosent, INNTEKT),
                ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar, 26.januar, 100.prosent, INNTEKT)
            )
            håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
            håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
            håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
            håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
            assertSisteForkastetPeriodeTilstand(a1, 1.vedtaksperiode, TIL_INFOTRYGD)
            assertSisteForkastetPeriodeTilstand(a2, 1.vedtaksperiode, TIL_INFOTRYGD)
        }

    @Test
    fun `Oppdager at det er en flere arbeidsgivere-sak fra infotrygd etter at vi feilaktig tror det er en førstegangsbehandling frem til etter vilkårsprøving`() =
        Toggle.FlereArbeidsgivereFraInfotrygd.disable {
            val periode = 26.januar til 15.februar
            håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
            håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
            håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
            håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
            håndterInntektsmelding(listOf(11.januar til 26.januar), orgnummer = a1)
            håndterInntektsmelding(listOf(11.januar til 26.januar), orgnummer = a2)
            håndterYtelser(
                1.vedtaksperiode,
                orgnummer = a1,
                inntektshistorikk = listOf(
                    Inntektsopplysning(a1.toString(), 20.januar, INNTEKT, true),
                    Inntektsopplysning(a2.toString(), 20.januar, INNTEKT, true)
                ),
                utbetalinger = arrayOf(
                    ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar, 25.januar, 100.prosent, INNTEKT),
                    ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar, 25.januar, 100.prosent, INNTEKT)
                )
            )
            assertSisteForkastetPeriodeTilstand(a1, 1.vedtaksperiode, TIL_INFOTRYGD)
            assertSisteForkastetPeriodeTilstand(a2, 1.vedtaksperiode, TIL_INFOTRYGD)
        }

    @Test
    fun `Tillater forlengelse av flere arbeidsgivere`() {
        val periode = 1.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a1
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a2
        )
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
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Forlengelsen starter her
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TIL_UTBETALING, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)

        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a2)

        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, true, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TIL_UTBETALING, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a2)
    }

    @Test
    fun `Tillater forlengelse av overgang fra infotrygd for flere arbeidsgivere selv om sykmelding og søknad for en arbeidsgiver kommer før sykmelding for den andre arbeidsgiveren`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )
        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)


        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 10.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a1)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a2)
    }

    @Test
    fun `Tillater forlengelse av overgang fra infotrygd for flere arbeidsgivere selv om sykmelding og søknad for en arbeidsgiver kommer før sykmelding for den andre arbeidsgiveren - påvirker ikke uferdige perioder`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )
        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        val forlengelseperiode = 1.februar(2021) til 10.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)
    }

    @Test
    fun `Tillater forlengelse av forlengelse av overgang fra infotrygd for flere arbeidsgivere selv om sykmelding og søknad for en arbeidsgiver kommer før sykmelding for den andre arbeidsgiveren`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )
        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 10.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen av forlengelsen
        val forlengelsesforlengelseperiode = 11.februar(2021) til 20.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a1)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)

        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(3.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)
    }

    @Test
    fun `Plutselig kommer en tredje arbeidsgiver midt i en forlengelse - siste arbeidsgiver og senere forlengelser forkastes pga manglende inntekter for ny arbeidsgiver`() {
        /*
        * Person har to arbeidsgivere og får utbetalt en førstegangsbehandling og forlengelse. Så blir personen
        * syk for en ny arbeidgiver vi ikke kjente til på skjæringstidspunktet. Da mangler vi inntekt for denne arbeidsgiveren
        * og alle senere perioder hos alle arbeidsgivere. De utbetalte periodene har forsatt basert seg på riktig sykepengegrunnlag og kan gå gjennom.
        * I testen under blir forlengelsen av a1 og a2 ikke ferdig før a3 kommer inn, og derfor går ingen gjennom.
        * Her kommer a3 inn etter at a1 og a2 er ferdig.
        */
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )
        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a3)

        //Her starter forlengelsen av forlengelsen
        val forlengelsesforlengelseperiode = 1.mars(2021) til 31.mars(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)

        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a3)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        assertSisteTilstand(3.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a2)

        håndterSøknad(
            Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a3
        )
        håndterInntektsmelding(
            listOf(forlengelseperiode.start til forlengelseperiode.start.plusDays(16)),
            førsteFraværsdag = forlengelseperiode.start,
            orgnummer = a3
        )
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a3)

        håndterYtelser(1.vedtaksperiode, orgnummer = a3)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a3)
    }

    @Test
    fun `Plutselig kommer en tredje arbeidsgiver midt i en ikke-avsluttet forlengelse - alle arbeidsgivere forkastes pga manglende inntekter for ny arbeidsgiver`() {
        /*
        * Vi støtter ikke at vi plutselig får inn en ny arbeidsgiver med sykdom senere enn skjæringstidspunktet.
        * Alle forlengelsene blir derfor kastet ut når det viser seg at vi ikke har inntekt for alle arbeidsgiverne.
        * I testen over ble forlengelsen av a1 og a2 ferdig før a3 kom inn, og derfor går den gjennom.
        * Her kommer a3 inn før a1 og a2 er ferdig og alle blir kastet ut fordi a3 ikke har inntekt på skjæringstidspunktet.
        * */
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a3)
    }

    @Test
    fun `Tredje arbeidsgiver som tidligere var ghost blir syk midt i en forlengelse - Alle vedtaksperioder går igjennom`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a3.toString(), 20.januar(2021), INNTEKT, true)
        )
        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen av forlengelsen
        val forlengelsesforlengelseperiode = 1.mars(2021) til 31.mars(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a3)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)

        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a3)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a3)

        håndterYtelser(3.vedtaksperiode, orgnummer = a2)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a3
        )
        håndterInntektsmelding(
            listOf(forlengelsesforlengelseperiode.start til forlengelsesforlengelseperiode.start.plusDays(16)),
            førsteFraværsdag = forlengelsesforlengelseperiode.start,
            orgnummer = a3
        )

        assertSisteTilstand(3.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a3)

        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(3.vedtaksperiode, orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a2)
        håndterSimulering(3.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(3.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a3)
        håndterSimulering(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a3)

        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a3)
    }

    @Test
    fun `forlenger forkastet periode med flere arbeidsgivere hvor alle arbeidsgivernes perioder blir forlenget`() {
        /*
        * Vi støtter ikke at vi plutselig får inn en ny arbeidsgiver med sykdom senere enn skjæringstidspunktet.
        * Alle forlengelsene blir derfor kastet ut når det viser seg at vi ikke har inntekt for alle arbeidsgiverne.
        * */
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 10.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a3)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a3)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        håndterInntektsmelding(listOf(forlengelseperiode.start til forlengelseperiode.endInclusive), forlengelseperiode.start, orgnummer = a3)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET_UTEN_UTBETALING, orgnummer = a3)

        //Her starter forlengelsen av forlengelsen
        val forlengelsesforlengelseperiode = 11.februar(2021) til 20.februar(2021)
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a3
        )
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a3)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        håndterUtbetalingshistorikk(3.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a3)
    }

    @Test
    fun `Ghost blir sykmeldt i en forlengelse og alle vedtaksperioder forlenges igjen - Alt går fint`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a3.toString(), 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)

        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a3)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a3)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP, orgnummer = a3)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(forlengelseperiode.start til forlengelseperiode.start.plusDays(16)),
            førsteFraværsdag = forlengelseperiode.start,
            orgnummer = a3
        )
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a3)
        håndterYtelser(1.vedtaksperiode, orgnummer = a3)
        håndterSimulering(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a3)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a3)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a3)

        //Her starter forlengelsen av forlengelsen
        val forlengelsesforlengelseperiode = 1.mars(2021) til 20.mars(2021)
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        håndterSykmelding(
            Sykmeldingsperiode(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a3
        )
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a3)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1
        )
        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a3)

        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a2
        )
        håndterYtelser(3.vedtaksperiode, orgnummer = a2)
        håndterSøknad(
            Sykdom(forlengelsesforlengelseperiode.start, forlengelsesforlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a3
        )
        håndterYtelser(2.vedtaksperiode, orgnummer = a3)

        assertSisteTilstand(3.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a3)

        håndterYtelser(3.vedtaksperiode, orgnummer = a1)
        håndterSimulering(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(3.vedtaksperiode, orgnummer = a1)
        håndterYtelser(3.vedtaksperiode, orgnummer = a2)
        håndterSimulering(3.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(3.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(3.vedtaksperiode, orgnummer = a2)
        håndterYtelser(2.vedtaksperiode, orgnummer = a3)
        håndterSimulering(2.vedtaksperiode, orgnummer = a3)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a3)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a3)

        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(3.vedtaksperiode, AVSLUTTET, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a3)
    }

    @Test
    fun `Tillater forlengelse av flere arbeidsgivere selv om sykmelding og søknad for en arbeidsgiver kommer før sykmelding for den andre arbeidsgiveren`() {
        val periode = 1.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a1
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(1.januar(2021) til 16.januar(2021)),
            førsteFraværsdag = 1.januar(2021),
            orgnummer = a2
        )
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
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, inntektshistorikk = emptyList(), orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Forlengelsen starter her
        val forlengelseperiode = 1.februar(2021) til 28.februar(2021)
        val sykmeldingHendelseId = UUID.randomUUID()
        val søknadHendelseId = UUID.randomUUID()
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1, id = sykmeldingHendelseId)
        håndterSøknad(
            Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent),
            orgnummer = a1,
            id = søknadHendelseId
        )
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertHendelseIder(sykmeldingHendelseId, søknadHendelseId, orgnummer = a1)
    }

    @Test
    fun `Periode som forlenger annen arbeidsgiver, men ikke seg selv, kastes ut fordi den mangler inntekt på skjæringstidspunkt`() {
        val periode = 1.februar(2021) til 28.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 1.januar(2021), INNTEKT, true)
        )
        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 1.januar(2021), 31.januar(2021), 100.prosent, INNTEKT)
        )
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        val periode2 = 1.mars(2021) til 31.mars(2021)
        håndterSykmelding(Sykmeldingsperiode(periode2.start, periode2.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(1.april(2021), 30.april(2021), 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.april(2021), 30.april(2021), 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.april(2021) til 30.april(2021)), førsteFraværsdag = 1.april(2021), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a2)

        håndterSøknad(Sykdom(periode2.start, periode2.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(2.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a2)
    }

    @ForventetFeil("Det finnes ikke inntekt for skjæringstidspunktet (4. januar)")
    @Test
    fun `Tillater flere arbeidsgivere selv om ikke alle har samme periodetype`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 25.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP, orgnummer = a2)

        håndterInntektsmelding(listOf(4.januar(2021) til 19.januar(2021)), førsteFraværsdag = 27.januar(2021), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2)

        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING, orgnummer = a2)

        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
    }

    @Test
    fun `Tillater forlengelse av overgang fra infotrygd for flere arbeidsgivere - a1 kommer til AVVENTER_HISTORIKK først`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )
        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)

        //Her starter forlengelsen
        val forlengelseperiode = 1.februar(2021) til 10.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE, orgnummer = a2)

        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE, 2)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE, 2)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE, 2)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE, 2)

        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TIL_UTBETALING, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2)

        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE, 2)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE, 2)

        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, TIL_UTBETALING, orgnummer = a2)

        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a2)
    }

    @Test
    fun `Håndterer søknader i ikke-kronologisk rekkefølge for flere arbeidsgivere`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1, 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2, 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2, 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)

        val forlengelseperiode = 1.februar(2021) til 10.februar(2021)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(forlengelseperiode.start, forlengelseperiode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2)

        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, orgnummer = a2)

        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)

        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a1)
        håndterSimulering(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a1)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(2.vedtaksperiode, orgnummer = a2)
        håndterSimulering(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalingsgodkjenning(2.vedtaksperiode, orgnummer = a2)
        håndterUtbetalt(2.vedtaksperiode, orgnummer = a2)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)

        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(2.vedtaksperiode, AVSLUTTET, orgnummer = a2)

        assertAlleBehovBesvart()
    }

    @Test
    fun `vedtaksperioder i AVVENTER_ARBEIDSGIVERE får også utbetalingstidslinje`() {
        val periode = 1.februar til 28.februar
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, 25.februar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(periode.start, 25.februar, 100.prosent), orgnummer = a2)
        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 1.januar, 31.januar, 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 1.januar, 31.januar, 100.prosent, INNTEKT),
            inntektshistorikk = listOf(
                Inntektsopplysning(a1.toString(), 1.januar, INNTEKT, true),
                Inntektsopplysning(a2.toString(), 1.januar, INNTEKT, true)
            ),
            orgnummer = a1
        )
        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 1.januar, 31.januar, 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 1.januar, 31.januar, 100.prosent, INNTEKT),
            inntektshistorikk = listOf(
                Inntektsopplysning(a1.toString(), 1.januar, INNTEKT, true),
                Inntektsopplysning(a2.toString(), 1.januar, INNTEKT, true)
            ),
            orgnummer = a2
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2)

        //Utbetalingstidslinjen blir like lang som den som går til AvventerGodkjenning
        assertEquals(25, inspektør(a1).utbetalingstidslinjer(1.vedtaksperiode).size)

        assertEquals(25, inspektør(a2).utbetalingstidslinjer(1.vedtaksperiode).size)
    }

    @Test
    fun `Bygger ikke utbetalingstidlinjer for arbeidsgivere med kun forkastede perioder`() {
        val periode = 1.januar til 31.januar
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterPåminnelse(1.vedtaksperiode, AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP, 1.januar(2017).atStartOfDay(), orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(1.februar, 20.februar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.februar, 20.februar, 100.prosent), orgnummer = a2)
        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 1.januar, 31.januar, 100.prosent, INNTEKT),
            inntektshistorikk = listOf(Inntektsopplysning(a2.toString(), 1.januar, INNTEKT, true)),
            orgnummer = a2
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertEquals(0, inspektør(a1).utbetalingstidslinjer(1.vedtaksperiode).size)
    }

    @Test
    fun `Kaster ut perioder hvis ikke alle forlengelser fra IT har fått sykmeldinger`() {
        håndterSykmelding(Sykmeldingsperiode(1.februar, 28.februar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.februar, 28.februar, 100.prosent), orgnummer = a1)

        håndterUtbetalingshistorikk(
            1.vedtaksperiode,
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 1.januar, 31.januar, 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 1.januar, 31.januar, 100.prosent, INNTEKT),
            inntektshistorikk = listOf(
                Inntektsopplysning(a1.toString(), 1.januar, INNTEKT, true),
                Inntektsopplysning(a2.toString(), 1.januar, INNTEKT, true)
            ),
            orgnummer = a1
        )
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK_FERDIG_GAP,
            AVVENTER_HISTORIKK,
            TIL_INFOTRYGD,
            orgnummer = a1
        )
    }

    @Test
    fun `Orgnummer, vedtaksperiodeIder og vedtaksperiodetyper ligger på godkjenningsbehov`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        val aktiveVedtaksperioder = inspektør.sisteBehov(Aktivitetslogg.Aktivitet.Behov.Behovtype.Godkjenning).detaljer()["aktiveVedtaksperioder"].castAsList<Map<Any, Any>>()
        Assertions.assertTrue(
            aktiveVedtaksperioder.containsAll(
                listOf(
                    mapOf(
                        "orgnummer" to a1.toString(),
                        "vedtaksperiodeId" to 1.vedtaksperiode.id(a1).toString(),
                        "periodetype" to Periodetype.OVERGANG_FRA_IT.name
                    ),
                    mapOf(
                        "orgnummer" to a2.toString(),
                        "vedtaksperiodeId" to 1.vedtaksperiode.id(a2).toString(),
                        "periodetype" to Periodetype.OVERGANG_FRA_IT.name
                    )
                )
            )
        )
    }

    @Test
    fun `forkast alle aktive vedtaksperioder for alle arbeidsgivere dersom en periode til godkjenning avises`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )
        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, false, orgnummer = a1)

        assertSisteForkastetPeriodeTilstand(a1, 1.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteForkastetPeriodeTilstand(a2, 1.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `forkast ettergølgende vedtaksperioder for alle arbeidsgivere dersom en periode til godkjenning avises`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        håndterSykmelding(Sykmeldingsperiode(1.februar, 15.februar, 100.prosent), orgnummer = a1)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, false, orgnummer = a2)

        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, a1)
        assertSisteForkastetPeriodeTilstand(a2, 1.vedtaksperiode, TIL_INFOTRYGD)
        assertSisteForkastetPeriodeTilstand(a1, 2.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `forkast periode som overlapper med annen arbeidsgiver i infotrygd`() {
        val periode = 22.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )
        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        assertSisteForkastetPeriodeTilstand(a1, 1.vedtaksperiode, TIL_INFOTRYGD)
    }

    @Test
    fun `Tillater overgang fra infotrygd for flere arbeidsgivere - a1 kommer til AVVENTER_HISTORIKK først`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertEquals("FLERE_ARBEIDSGIVERE", inspektør(a1).sisteBehov(1.vedtaksperiode).detaljer()["inntektskilde"])

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2)

        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a2)

        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertEquals("FLERE_ARBEIDSGIVERE", inspektør(a2).sisteBehov(1.vedtaksperiode).detaljer()["inntektskilde"])

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING, orgnummer = a2)

        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
    }

    @Test
    fun `Tillater overgang fra infotrygd for flere arbeidsgivere - a2 kommer til AVVENTER_HISTORIKK først`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2)

        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING, orgnummer = a2)

        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
    }

    @Test
    fun `Tillater ikke flere arbeidsgivere hvis ikke alle er overgang fra Infotrygd`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 25.januar(2021), 100.prosent, INNTEKT)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a2)
    }

    @Test
    fun `går ikke i loop mellom AVVENTER_ARBEIDSGIVERE og AVVENTER_HISTORIKK`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), INNTEKT, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT)
        )

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterPåminnelse(1.vedtaksperiode, orgnummer = a1, påminnetTilstand = AVVENTER_ARBEIDSGIVERE)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, MOTTATT_SYKMELDING_FERDIG_GAP, orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterSimulering(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertEquals("FLERE_ARBEIDSGIVERE", inspektør(a1).sisteBehov(1.vedtaksperiode).detaljer()["inntektskilde"])

        håndterPåminnelse(1.vedtaksperiode, orgnummer = a2, påminnetTilstand = AVVENTER_ARBEIDSGIVERE)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_ARBEIDSGIVERE, orgnummer = a2)

        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_HISTORIKK, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2)

        håndterSimulering(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVVENTER_GODKJENNING, orgnummer = a2)
        assertInntektskilde(a1, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertInntektskilde(a2, Inntektskilde.FLERE_ARBEIDSGIVERE)
        assertEquals("FLERE_ARBEIDSGIVERE", inspektør(a2).sisteBehov(1.vedtaksperiode).detaljer()["inntektskilde"])

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, TIL_UTBETALING, orgnummer = a2)

        håndterUtbetalt(1.vedtaksperiode, orgnummer = a2)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a1)
        assertSisteTilstand(1.vedtaksperiode, AVSLUTTET, orgnummer = a2)
    }

    @Test
    fun `sykepengegrunnlag settes per arbeidsgiver i vedtakfattet-event`() {
        val periode = 27.januar(2021) til 31.januar(2021)
        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        val inntektshistorikk = listOf(
            Inntektsopplysning(a1.toString(), 20.januar(2021), INNTEKT, true),
            Inntektsopplysning(a2.toString(), 20.januar(2021), 1000.00.månedlig, true)
        )

        val utbetalinger = arrayOf(
            ArbeidsgiverUtbetalingsperiode(a1.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, INNTEKT),
            ArbeidsgiverUtbetalingsperiode(a2.toString(), 20.januar(2021), 26.januar(2021), 100.prosent, 1000.00.månedlig)
        )

        håndterUtbetalingshistorikk(1.vedtaksperiode, *utbetalinger, inntektshistorikk = inntektshistorikk, orgnummer = a1)

        håndterSykmelding(Sykmeldingsperiode(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a1)
        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        håndterSøknad(Sykdom(periode.start, periode.endInclusive, 100.prosent), orgnummer = a2)
        håndterYtelser(1.vedtaksperiode, orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)
        håndterSimulering(1.vedtaksperiode, orgnummer = a1)

        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true, orgnummer = a1)
        håndterUtbetalt(1.vedtaksperiode, orgnummer = a1)

        assertEquals(31000.00 * 12, observatør.vedtakFattetEvent[1.vedtaksperiode.id(a1)]?.grunnlagForSykepengegrunnlagPerArbeidsgiver?.get(a1.toString()))
        assertEquals(12000.00, observatør.vedtakFattetEvent[1.vedtaksperiode.id(a1)]?.grunnlagForSykepengegrunnlagPerArbeidsgiver?.get(a2.toString()))
    }

}
