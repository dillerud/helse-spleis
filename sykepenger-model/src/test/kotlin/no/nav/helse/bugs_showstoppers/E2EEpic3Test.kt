package no.nav.helse.bugs_showstoppers

import no.nav.helse.hendelser.*
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Egenmelding
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.person.ForlengelseFraInfotrygd
import no.nav.helse.person.TilstandType.*
import no.nav.helse.spleis.e2e.AbstractEndToEndTest
import no.nav.helse.spleis.e2e.TestTidslinjeInspektør
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.sykdomstidslinje.Dag.*
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class E2EEpic3Test : AbstractEndToEndTest() {

    @Test
    internal fun `gradert sykmelding først`() {
        // ugyldig sykmelding lager en tom vedtaksperiode uten tidslinje, som overlapper med alt
        håndterSykmelding(Sykmeldingsperiode(3.januar(2020), 3.januar(2020), 50))
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        håndterSykmelding(Sykmeldingsperiode(13.januar(2020), 17.januar(2020), 100))
        håndterSøknad(Sykdom(13.januar(2020), 17.januar(2020), 100))
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP)
    }

    @Test
    internal fun `Ingen sykedager i tidslinjen - første fraværsdag bug`() {
        håndterSykmelding(Sykmeldingsperiode(6.januar(2020), 7.januar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(8.januar(2020), 10.januar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(27.januar(2020), 28.januar(2020), 100))

        håndterInntektsmelding(
            listOf(
                Periode(18.november(2019), 23.november(2019)),
                Periode(14.oktober(2019), 18.oktober(2019)),
                Periode(1.november(2019), 5.november(2019))
            ), 18.november(2019), listOf(
                Periode(5.desember(2019), 6.desember(2019)),
                Periode(30.desember(2019), 30.desember(2019)),
                Periode(2.januar(2020), 3.januar(2020)),
                Periode(22.januar(2020), 22.januar(2020))
            )
        )

        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_SØKNAD_UFERDIG_FORLENGELSE
        )
        assertTilstander(2, START, MOTTATT_SYKMELDING_UFERDIG_GAP)
    }

    @Test
    internal fun `inntektsmelding starter etter sykmeldingsperioden`() {
        håndterSykmelding(Sykmeldingsperiode(15.januar(2020), 12.februar(2020), 100))
        håndterSøknad(Sykdom(15.januar(2020), 12.februar(2020), 100))
        håndterInntektsmelding(listOf(Periode(16.januar(2020), 31.januar(2020))), 16.januar(2020))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(
            0,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(3.april(2019), 30.april(2019), 100, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(18.mars(2018), 2.april(2018), 100, 100, ORGNUMMER),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
                29.november(2017),
                3.desember(2017),
                100,
                100,
                ORGNUMMER
            ),
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
                13.november(2017),
                28.november(2017),
                100,
                100,
                ORGNUMMER
            )
        )
        håndterSimulering(0)
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING
        )
    }

    @Test
    fun `periode uten sykedager`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 4.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(8.januar, 9.januar, 100))
        håndterSykmelding(Sykmeldingsperiode(15.januar, 16.januar, 100))
        håndterInntektsmeldingMedValidering(
            0,
            listOf(
                Periode(3.januar, 4.januar),
                Periode(15.januar, 16.januar)
            ),
            15.januar
        )

        håndterSøknadMedValidering(0, Sykdom(3.januar, 4.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterUtbetalingsgodkjenning(0, true)

        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_GODKJENNING,
            AVSLUTTET
        )

        håndterSøknadMedValidering(1, Sykdom(8.januar, 9.januar, 100))
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterYtelser(1)   // No history
        håndterUtbetalingsgodkjenning(1, true)

        inspektør.also {
            assertNoErrors(it)
            assertMessages(it)
        }
        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_SØKNAD_UFERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_GODKJENNING,
            AVSLUTTET
        )

        assertTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_SØKNAD_UFERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP
        )
    }

    @Test
    internal fun `enkeltstående sykedag i arbeidsgiverperiode-gap`() {
        håndterSykmelding(Sykmeldingsperiode(10.februar(2020), 12.februar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(14.februar(2020), 14.februar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(27.februar(2020), 28.februar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(10.februar(2020), 12.februar(2020)),
                Periode(27.februar(2020), 28.februar(2020))
            ),
            førsteFraværsdag = 27.februar(2020)
        )
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_SØKNAD_UFERDIG_GAP
        )
        assertTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_SØKNAD_UFERDIG_GAP
        )
    }

    @Test
    fun `Inntektsmelding med ferie etter arbeidsgiverperioden`() {
        håndterSykmelding(Sykmeldingsperiode(10.januar(2020), 21.januar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(23.januar(2020), 24.januar(2020), 100))
        håndterSøknad(Sykdom(23.januar(2020), 24.januar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(6.januar(2020), 21.januar(2020))),
            førsteFraværsdag = 23.januar(2020),
            ferieperioder = listOf(Periode(4.februar(2020), 5.februar(2020)))
        )

        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVVENTER_INNTEKTSMELDING_UFERDIG_GAP,
            AVVENTER_UFERDIG_GAP
        )
    }

    @Test
    internal fun `ignorerer egenmeldingsdag i søknaden langt tilbake i tid`() {
        håndterSykmelding(Sykmeldingsperiode(6.januar(2020), 23.januar(2020), 100))
        håndterSøknad(
            Egenmelding(
                24.september(2019),
                24.september(2019)
            ), // ignored because it's too long ago relative to 6.januar
            Sykdom(6.januar(2020), 23.januar(2020), 100)
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(24.september(2019), 24.september(2019)),
                Periode(27.september(2019), 6.oktober(2019)),
                Periode(14.oktober(2019), 18.oktober(2019))
            ),
            førsteFraværsdag = 24.september(2019),
            ferieperioder = listOf(Periode(7.oktober(2019), 11.oktober(2019)))
        )
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP)
    }

    @Test
    fun `person med gammel sykmelding`() {
        // OBS: Disse kastes ikke ut fordi de er for gamle. De kastes ut fordi de kommer out of order
        håndterSykmelding(Sykmeldingsperiode(13.januar(2020), 31.januar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(9.februar(2017), 15.februar(2017), 100), mottatt = 31.januar(2020).atStartOfDay())

        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP)
    }

    @Test
    internal fun `periode som begynner på siste dag i arbeidsgiverperioden`() {
        håndterSykmelding(Sykmeldingsperiode(3.februar(2020), 17.februar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(18.februar(2020), 1.mars(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(3.februar(2020), 18.februar(2020))
            ),
            førsteFraværsdag = 3.januar(2020)
        )
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_SØKNAD_FERDIG_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_SØKNAD_UFERDIG_FORLENGELSE)
    }

    @Test
    internal fun `sykmeldinger som overlapper med gradering`() {
        håndterSykmelding(Sykmeldingsperiode(13.januar(2020), 28.januar(2020), 100)) // sykmelding A
        håndterSykmelding(
            Sykmeldingsperiode(13.januar(2020), 19.januar(2020), 80),
            Sykmeldingsperiode(20.januar(2020), 26.januar(2020), 100)
        ) // sykmelding B (ignored)
        håndterSykmelding(Sykmeldingsperiode(27.januar(2020), 11.februar(2020), 100)) // sykmelding C (ignored)
        håndterSykmelding(Sykmeldingsperiode(10.februar(2020), 29.februar(2020), 100)) // sykmelding D
        håndterSøknad(
            Sykdom(
                27.januar(2020),
                11.februar(2020),
                100
            )
        ) // søknad for sykemelding C (covers A & D actually)
        assertEquals(2, inspektør.vedtaksperiodeTeller)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(13.januar(2020), 28.januar(2020))
            ),
            førsteFraværsdag = 13.januar(2020)
        ) // <-- error here
        håndterSøknad(Sykdom(10.februar(2020), 29.februar(2020), 100)) // søknad for sykmelding D (ignored)
        håndterSykmelding(Sykmeldingsperiode(1.mars(2020), 15.mars(2020), 100)) // sykmelding E
        assertEquals(3, inspektør.vedtaksperiodeTeller)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_GAP, AVVENTER_INNTEKTSMELDING_UFERDIG_GAP)
    }

    @Test
    internal fun `sykmeldinger som overlapper`() {
        håndterSykmelding(Sykmeldingsperiode(15.januar(2020), 30.januar(2020), 100)) // sykmelding A, part 1
        håndterSykmelding(Sykmeldingsperiode(31.januar(2020), 15.februar(2020), 100)) // sykmelding A, part 2
        håndterSykmelding(Sykmeldingsperiode(16.januar(2020), 31.januar(2020), 100)) // sykmelding B
        håndterSykmelding(Sykmeldingsperiode(1.februar(2020), 16.februar(2020), 100)) // sykmelding C
        håndterSøknad(Sykdom(16.januar(2020), 31.januar(2020), 100)) // -> sykmelding B
        håndterSøknad(Sykdom(1.februar(2020), 16.februar(2020), 100)) // sykmelding C
        håndterSøknad(Sykdom(31.januar(2020), 15.februar(2020), 100)) // sykmelding A, part 2
        håndterSykmelding(Sykmeldingsperiode(18.februar(2020), 8.mars(2020), 100)) // sykmelding D
        assertEquals(3, inspektør.vedtaksperiodeTeller)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE)
        assertTilstander(2, START, MOTTATT_SYKMELDING_UFERDIG_GAP)
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(15.januar(2020), 30.januar(2020))
            ),
            førsteFraværsdag = 15.januar(2020)
        ) // <-- error here
        håndterYtelser(0) // No history
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP)
        assertTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE)
        assertTilstander(2, START, MOTTATT_SYKMELDING_UFERDIG_GAP)
    }

    @Test
    fun `overlapp i arbeidsgivertidslinjer`() {
        håndterSykmelding(Sykmeldingsperiode(7.januar(2020), 13.januar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(14.januar(2020), 24.januar(2020), 100))
        håndterSøknad(
            Sykdom(7.januar(2020), 13.januar(2020), 100)
        )
        håndterSøknad(
            Egenmelding(6.januar(2020), 6.januar(2020)),
            Sykdom(14.januar(2020), 24.januar(2020), 100)
        )
        håndterSykmelding(Sykmeldingsperiode(25.januar(2020), 7.februar(2020), 80))
        håndterSykmelding(Sykmeldingsperiode(8.februar(2020), 28.februar(2020), 80))
        håndterSøknad(Sykdom(25.januar(2020), 7.februar(2020), 80))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(6.januar(2020), 21.januar(2020))
            ),
            førsteFraværsdag = 6.januar(2020)
        )
        håndterSykmelding(Sykmeldingsperiode(29.februar(2020), 11.mars(2020), 80))

        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0) // No history

        assertEquals(5, inspektør.vedtaksperiodeTeller)
        assertNotNull(inspektør.maksdato(0))
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK
        )
        assertTilstander(2, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE)
        assertTilstander(3, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE)
        assertTilstander(4, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE)
    }

    @Test
    fun `ferie inni arbeidsgiverperioden`() {
        håndterSykmelding(Sykmeldingsperiode(21.desember(2019), 5.januar(2020), 80))
        håndterSøknad(
            Egenmelding(18.september(2019), 20.september(2019)),
            Sykdom(21.desember(2019), 5.januar(2020), 80)
        )
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(18.september(2019), 20.september(2019)),
                Periode(21.september(2019), 22.september(2019)),
                Periode(23.september(2019), 30.september(2019)),
                Periode(1.oktober(2019), 2.oktober(2019)),
                Periode(8.oktober(2019), 8.oktober(2019)) // grad for 8. oktober is NaN
            ),
            ferieperioder = listOf(
                Periode(3.oktober(2019), 7.oktober(2019)),
                Periode(9.desember(2019), 23.desember(2019)),
                Periode(27.desember(2019), 27.desember(2019)),
                Periode(30.desember(2019), 30.desember(2019))
            ),
            førsteFraværsdag = 24.desember(2019)
        )
        // Sykedag beats IM Feriedag; 21 Desember to 5 Januar is another employer period!
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0) // No history

        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertNotNull(inspektør.maksdato(0))
        assertTrue(inspektør.utbetalingslinjer(0).isEmpty())
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_GODKJENNING
        )
    }

    @Test
    fun `Inntektsmelding, etter søknad, overskriver sykedager før arbeidsgiverperiode med arbeidsdager`() {
        håndterSykmelding(Sykmeldingsperiode(7.januar, 28.januar, 100))
        håndterSøknad(Sykdom(7.januar, 28.januar, 100))
        // Need to extend Arbeidsdag from first Arbeidsgiverperiode to beginning of Vedtaksperiode, considering weekends
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(9.januar, 24.januar)),
            ferieperioder = emptyList(),
            førsteFraværsdag = 9.januar
        )
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(3, inspektør.sykdomshistorikk.size)
        assertEquals(22, inspektør.sykdomshistorikk.sykdomstidslinje().length())
        assertEquals(7.januar, inspektør.sykdomshistorikk.sykdomstidslinje().førsteDag())
        assertEquals(FriskHelgedag::class, inspektør.sykdomshistorikk.sykdomstidslinje()[7.januar]::class)
        assertEquals(Dag.Arbeidsdag::class, inspektør.sykdomshistorikk.sykdomstidslinje()[8.januar]::class)
        assertEquals(9.januar, inspektør.sykdomshistorikk.sykdomstidslinje().førsteFraværsdag())
        assertEquals(28.januar, inspektør.sykdomshistorikk.sykdomstidslinje().sisteDag())
    }

    @Test
    internal fun `Inntektsmelding, før søknad, overskriver sykedager før arbeidsgiverperiode med arbeidsdager`() {
        håndterSykmelding(Sykmeldingsperiode(7.januar, 28.januar, 100))
        // Need to extend Arbeidsdag from first Arbeidsgiverperiode to beginning of Vedtaksperiode, considering weekends
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(9.januar, 24.januar)),
            ferieperioder = emptyList(),
            førsteFraværsdag = 9.januar
        )
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertEquals(2, inspektør.sykdomshistorikk.size)
        assertEquals(22, inspektør.sykdomshistorikk.sykdomstidslinje().length())
        assertEquals(7.januar, inspektør.sykdomshistorikk.sykdomstidslinje().førsteDag())
        assertEquals(FriskHelgedag::class, inspektør.sykdomshistorikk.sykdomstidslinje()[7.januar]::class)
        assertEquals(Dag.Arbeidsdag::class, inspektør.sykdomshistorikk.sykdomstidslinje()[8.januar]::class)
        assertEquals(9.januar, inspektør.sykdomshistorikk.sykdomstidslinje().førsteFraværsdag())
        assertEquals(28.januar, inspektør.sykdomshistorikk.sykdomstidslinje().sisteDag())
    }

    @Test
    internal fun `andre vedtaksperiode utbetalingslinjer dekker to perioder`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterSøknad(Sykdom(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(1.januar(2020), 16.januar(2020))),
            førsteFraværsdag = 1.januar(2020)
        )
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(1.februar(2020), 28.februar(2020), 100))
        håndterSøknad(Sykdom(1.februar(2020), 28.februar(2020), 100))
        håndterYtelser(1)   // No history
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        assertNotNull(inspektør.maksdato(0))
        assertNotNull(inspektør.maksdato(1))

        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )

        inspektør.also {
            assertEquals(1, it.arbeidsgiverOppdrag[0].size)
            assertEquals(17.januar(2020), it.arbeidsgiverOppdrag[0].first().fom)
            assertEquals(31.januar(2020), it.arbeidsgiverOppdrag[0].first().tom)
            assertEquals(1, it.arbeidsgiverOppdrag[1].size)
            assertEquals(17.januar(2020), it.arbeidsgiverOppdrag[1].first().fom)
            assertEquals(28.februar(2020), it.arbeidsgiverOppdrag[1].first().tom)
        }
    }

    @Test
    fun `simulering av periode der tilstøtende ikke ble utbetalt`() {
        håndterSykmelding(Sykmeldingsperiode(28.januar(2020), 10.februar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(11.februar(2020), 21.februar(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(28.januar(2020), 10.februar(2020), 100))
        håndterSøknad(Sykdom(11.februar(2020), 21.februar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(28.januar(2020), 12.februar(2020))),
            førsteFraværsdag = 28.januar(2020)
        )
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(1)   // No history

        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            AVSLUTTET_UTEN_UTBETALING_MED_INNTEKTSMELDING
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `simulering av periode der tilstøtende ble utbetalt`() {
        håndterSykmelding(Sykmeldingsperiode(17.januar(2020), 10.februar(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(11.februar(2020), 21.februar(2020), 100))
        håndterSøknad(Sykdom(17.januar(2020), 10.februar(2020), 100))
        håndterSøknad(Sykdom(11.februar(2020), 21.februar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(17.januar(2020), 2.februar(2020))),
            førsteFraværsdag = 18.januar(2020)
        )
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)
        håndterYtelser(1)   // No history

        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING,
            AVVENTER_GODKJENNING,
            TIL_UTBETALING,
            AVSLUTTET
        )
        assertTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    internal fun `dobbeltbehandling av første periode aborterer behandling av andre periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterSøknad(Sykdom(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(Periode(1.januar(2020), 16.januar(2020))),
            førsteFraværsdag = 1.januar(2020)
        )
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterSykmelding(Sykmeldingsperiode(1.februar(2020), 28.februar(2020), 100))
        håndterSøknad(Sykdom(1.februar(2020), 28.februar(2020), 100))
        håndterYtelser(
            1, Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(
                17.januar(2020),
                31.januar(2020),
                1400,
                100,
                ORGNUMMER
            )
        )   // Duplicate processing

        assertTilstander(
            0,
            START, MOTTATT_SYKMELDING_FERDIG_GAP, AVVENTER_GAP, AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING, AVVENTER_GODKJENNING, TIL_UTBETALING, AVSLUTTET
        )
        assertTilstander(
            1,
            START, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE,
            AVVENTER_HISTORIKK, AVVENTER_SIMULERING
        )
    }

    @Test
    internal fun `helg i gap i arbeidsgiverperioden`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 10.januar, 100))
        håndterInntektsmelding(listOf(Periode(3.januar, 4.januar), Periode(9.januar, 10.januar)), 3.januar)
        håndterSøknad(Sykdom(3.januar, 10.januar, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history

        inspektør.also {
            assertEquals(4, it.dagtelling[Sykedag::class])
            assertEquals(2, it.dagtelling[FriskHelgedag::class])
            assertEquals(2, it.dagtelling[Dag.Arbeidsdag::class])
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_GODKJENNING
        )
    }

    @Test
    fun `Egenmelding i søknad overstyres av inntektsmelding når IM mottas først`() {
        håndterSykmelding(Sykmeldingsperiode(20.februar(2020), 8.mars(2020), 100))
        håndterInntektsmelding(listOf(Periode(20.februar(2020), 5.mars(2020))), 20.februar(2020))
        håndterSøknad(Egenmelding(17.februar(2020), 19.februar(2020)), Sykdom(20.februar(2020), 8.mars(2020), 100))

        inspektør.also {
            assertEquals(20.februar(2020), it.sykdomstidslinje.førsteDag())
        }
    }

    @Test
    fun `Egenmelding i søknad overstyres av inntektsmelding når IM mottas sist`() {
        håndterSykmelding(Sykmeldingsperiode(20.februar(2020), 8.mars(2020), 100))
        håndterSøknad(Egenmelding(17.februar(2020), 19.februar(2020)), Sykdom(20.februar(2020), 8.mars(2020), 100))
        håndterInntektsmelding(listOf(Periode(20.februar(2020), 5.mars(2020))), 20.februar(2020))

        inspektør.also {
            assertNull(it.dagtelling[Arbeidsgiverdag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
            assertEquals(12, it.dagtelling[Sykedag::class])
            assertEquals(20.februar(2020), it.sykdomstidslinje.førsteDag())
        }
    }

    @Test
    fun `Syk, en arbeidsdag, ferie og syk`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(1.januar(2020), 1.januar(2020)),
                Periode(11.januar(2020), 25.januar(2020))
            ),
            ferieperioder = listOf(Periode(3.januar(2020), 10.januar(2020))),
            førsteFraværsdag = 11.januar(2020)
        )
        håndterSøknad(Sykdom(1.januar(2020), 31.januar(2020), 100), sendtTilNav = 1.februar(2020))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)

        inspektør.also {
            assertEquals(16, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
            assertEquals(8, it.dagtelling[Feriedag::class])
            assertEquals(1, it.dagtelling[Dag.Arbeidsdag::class])

            TestTidslinjeInspektør(it.utbetalingstidslinjer(0)).also { tidslinjeInspektør ->
                assertEquals(16, tidslinjeInspektør.dagtelling[ArbeidsgiverperiodeDag::class])
                assertEquals(8, tidslinjeInspektør.dagtelling[Fridag::class])
                assertEquals(1, tidslinjeInspektør.dagtelling[NavHelgDag::class])
                assertEquals(5, tidslinjeInspektør.dagtelling[NavDag::class])
                assertEquals(1, tidslinjeInspektør.dagtelling[Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag::class])
            }
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `Syk, ferie og syk`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(1.januar(2020), 2.januar(2020)),
                Periode(11.januar(2020), 24.januar(2020))
            ),
            ferieperioder = listOf(Periode(3.januar(2020), 10.januar(2020))),
            førsteFraværsdag = 1.januar(2020)
        )
        håndterSøknad(Sykdom(1.januar(2020), 31.januar(2020), 100), sendtTilNav = 1.februar(2020))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)

        inspektør.also {
            assertEquals(17, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
            assertEquals(8, it.dagtelling[Feriedag::class])

            TestTidslinjeInspektør(it.utbetalingstidslinjer(0)).also { tidslinjeInspektør ->
                assertEquals(8, tidslinjeInspektør.dagtelling[ArbeidsgiverperiodeDag::class])
                assertEquals(8, tidslinjeInspektør.dagtelling[Fridag::class])
                assertEquals(4, tidslinjeInspektør.dagtelling[NavHelgDag::class])
                assertEquals(11, tidslinjeInspektør.dagtelling[NavDag::class])
                assertEquals(null, tidslinjeInspektør.dagtelling[Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag::class])
            }
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `Syk, mange arbeidsdager, syk igjen på en lørdag`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar(2020), 31.januar(2020), 100))
        håndterInntektsmelding(
            arbeidsgiverperioder = listOf(
                Periode(1.januar(2020), 1.januar(2020)),
                Periode(11.januar(2020), 25.januar(2020))
            ),
            førsteFraværsdag = 11.januar(2020)
        )
        håndterSøknad(Sykdom(1.januar(2020), 31.januar(2020), 100), sendtTilNav = 1.februar(2020))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)

        inspektør.also {
            assertEquals(16, it.dagtelling[Sykedag::class])
            assertEquals(6, it.dagtelling[SykHelgedag::class])
            assertEquals(7, it.dagtelling[Dag.Arbeidsdag::class])
            assertEquals(2, it.dagtelling[FriskHelgedag::class])

            TestTidslinjeInspektør(it.utbetalingstidslinjer(0)).also { tidslinjeInspektør ->
                assertEquals(16, tidslinjeInspektør.dagtelling[ArbeidsgiverperiodeDag::class])
                assertEquals(2, tidslinjeInspektør.dagtelling[Fridag::class])
                assertEquals(1, tidslinjeInspektør.dagtelling[NavHelgDag::class])
                assertEquals(5, tidslinjeInspektør.dagtelling[NavDag::class])
                assertEquals(7, tidslinjeInspektør.dagtelling[Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag::class])
            }
        }
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Test
    fun `Utbetaling med forlengelse`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020), 100))
        håndterSøknad(Sykdom(1.juni(2020), 30.juni(2020), 100))
        håndterInntektsmelding(listOf(Periode(1.juni(2020), 16.juni(2020))))

        håndterSykmelding(Sykmeldingsperiode(1.juli(2020), 31.juli(2020), 100))
        håndterSøknad(Sykdom(1.juli(2020), 31.juli(2020), 100))

        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterYtelser(1)
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertEquals(it.arbeidsgiverOppdrag[0].fagsystemId(), it.arbeidsgiverOppdrag[1].fagsystemId())
        }
    }

    @Test
    fun `Grad endrer tredje periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni(2020), 30.juni(2020), 100))
        håndterSøknad(Sykdom(1.juni(2020), 30.juni(2020), 100))
        håndterInntektsmelding(listOf(Periode(1.juni(2020), 16.juni(2020))))

        håndterSykmelding(Sykmeldingsperiode(1.juli(2020), 31.juli(2020), 100))
        håndterSøknad(Sykdom(1.juli(2020), 31.juli(2020), 100))

        håndterSykmelding(Sykmeldingsperiode(1.august(2020), 31.august(2020), 50))
        håndterSøknad(Sykdom(1.august(2020), 31.august(2020), 50))

        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)
        håndterUtbetalt(0, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterYtelser(1)
        håndterSimulering(1)
        håndterUtbetalingsgodkjenning(1, true)
        håndterUtbetalt(1, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        håndterYtelser(2)
        håndterSimulering(2)
        håndterUtbetalingsgodkjenning(2, true)
        håndterUtbetalt(2, UtbetalingHendelse.Oppdragstatus.AKSEPTERT)

        inspektør.also {
            assertEquals(it.arbeidsgiverOppdrag[0].fagsystemId(), it.arbeidsgiverOppdrag[1].fagsystemId())
            assertEquals(it.arbeidsgiverOppdrag[1].fagsystemId(), it.arbeidsgiverOppdrag[2].fagsystemId())
        }
    }

    @Test
    fun `vilkårsgrunnlagfeil på kort arbeidsgiversøknad`() {
        håndterSykmelding(Sykmeldingsperiode(2.mars(2020), 2.mars(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(2.mars(2020), 2.mars(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(16.mars(2020), 29.mars(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(30.mars(2020), 15.april(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(16.mars(2020), 29.mars(2020), 100))
        håndterSøknad(Sykdom(30.mars(2020), 15.april(2020), 100))
        håndterInntektsmeldingMedValidering(
            0, listOf(
                Periode(2.mars(2020), 2.mars(2020)),
                Periode(16.mars(2020), 29.mars(2020)),
                Periode(30.mars(2020), 30.mars(2020))
            ), førsteFraværsdag = 16.mars(2020), refusjon = Triple(null, INNTEKT, emptyList())
        )
        håndterVilkårsgrunnlag(0, INNTEKT, egenAnsatt = true) // make sure Vilkårsgrunnlag fails
        assertForkastetPeriodeTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            TIL_INFOTRYGD
        )
        håndterVilkårsgrunnlag(0, INNTEKT, egenAnsatt = true) // make sure Vilkårsgrunnlag fails
        assertForkastetPeriodeTilstander(
            1,
            START,
            MOTTATT_SYKMELDING_UFERDIG_GAP,
            AVSLUTTET_UTEN_UTBETALING,
            AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD,
            TIL_INFOTRYGD
        )
        assertForkastetPeriodeTilstander(
            2,
            START,
            MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE,
            AVVENTER_UFERDIG_FORLENGELSE,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `ikke medlem`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100))
        håndterInntektsmeldingMedValidering(
            0, listOf(
                Periode(1.januar, 16.januar)
            ), førsteFraværsdag = 1.januar, refusjon = Triple(null, INNTEKT, emptyList())
        )
        håndterVilkårsgrunnlag(
            0,
            INNTEKT,
            medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Nei
        ) // make sure Vilkårsgrunnlag fails
        assertForkastetPeriodeTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `periode som begynner på søndag skal ikke gi warning på krav om minimuminntekt`() {
        håndterSykmelding(Sykmeldingsperiode(15.mars(2020), 8.april(2020), 100))
        håndterInntektsmeldingMedValidering(
            0,
            listOf(Periode(16.mars(2020), 31.mars(2020))),
            førsteFraværsdag = 16.mars(2020),
            refusjon = Triple(null, INNTEKT, emptyList())
        )
        håndterSøknad(Sykdom(15.mars(2020), 8.april(2020), 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)
        assertEquals(ForlengelseFraInfotrygd.NEI, inspektør.forlengelseFraInfotrygd(0))
        assertTrue(inspektør.personLogg.hasOnlyInfoAndNeeds())
        assertTilstander(
            0,
            START,
            MOTTATT_SYKMELDING_FERDIG_GAP,
            AVVENTER_SØKNAD_FERDIG_GAP,
            AVVENTER_VILKÅRSPRØVING_GAP,
            AVVENTER_HISTORIKK,
            AVVENTER_SIMULERING
        )
    }

    @Disabled("Tester ingenting, men viser et out of order-scenario")
    @Test
    fun `Out of order sykmeldinger gjør at vedtaksperiodene feilaktig får forskjellig gruppe-id`() {
        /*Sykmelding for 19-22.mars får en annen gruppeId enn de to foregående,
        selv om den i virkeligheten er en invers forlengelse av disse.
        Inntektsmeldingen som senere kommer inn treffer denne og blir markert som qualified.
        Når neste søknad (23 - 29.mars) plukker opp inntektsmeldingen ser den denne som allerede vurdert
        og går til "AvsluttetUtenUtbetalingMedInntektsmelding" i stedet for "AvventerVilkårsprøvingArbeidsgiversøknad".
        Dette gjør at denne "gruppen" ender opp med å ikke ha vilkårsvurdering.
        Når SpeilBuilder senere henter ut dataForVilkårsvurdering basert på den første perioden knyttet til en gruppeId,
        finner den null. Dermed blir sykepengegrunnlaget null. */
        håndterSykmelding(Sykmeldingsperiode(23.mars(2020), 29.mars(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(30.mars(2020), 12.april(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(19.mars(2020), 22.mars(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(23.mars(2020), 29.mars(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(19.mars(2020), 22.mars(2020), 100))
        håndterSøknad(Sykdom(30.mars(2020), 12.april(2020), 100))
        håndterInntektsmelding(
            førsteFraværsdag = 17.mars(2020),
            arbeidsgiverperioder = listOf(Periode(17.mars(2020), 1.april(2020)))
        )
    }

    @Test
    //TODO: WIP Testnavn reflekterer ikke replay
    fun `Out of order sykmeldinger blir kastet ut og tar med alt etterfølgende`() {
        håndterSykmelding(Sykmeldingsperiode(23.mars(2020), 29.mars(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(30.mars(2020), 2.april(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(10.april(2020), 20.april(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(19.mars(2020), 22.mars(2020), 100))

        assertForkastetPeriodeTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertForkastetPeriodeTilstander(1, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE)
        assertForkastetPeriodeTilstander(2, START, MOTTATT_SYKMELDING_UFERDIG_GAP)
        assertTilstander(0, START, MOTTATT_SYKMELDING_FERDIG_GAP)
    }

    @Test
    fun `Forkasting skal ikke påvirke tilstanden til AVSLUTTET_UTEN_UTBETALING`() {
        håndterSykmelding(Sykmeldingsperiode(31.mars(2020), 13.april(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(31.mars(2020), 13.april(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(4.juni(2020), 11.juni(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(4.juni(2020), 11.juni(2020), 100))
        håndterSykmelding(Sykmeldingsperiode(12.juni(2020), 25.juni(2020), 100))
        håndterSøknad(Sykdom(12.juni(2020), 25.juni(2020), 100))
        håndterInntektsmelding(
            førsteFraværsdag = 4.juni(2020),
            arbeidsgiverperioder = listOf(Periode(4.juni(2020), 19.juni(2020)))
        )
        håndterVilkårsgrunnlag(1, INNTEKT)
        håndterSykmelding(Sykmeldingsperiode(26.juni(2020), 17.juli(2020), 100))
        håndterSøknad(
            Sykdom(26.juni(2020), 17.juli(2020), 100)
        )
        assertDoesNotThrow {
            håndterPåminnelse(3, AVVENTER_INNTEKTSMELDING_UFERDIG_FORLENGELSE, LocalDateTime.now().minusMonths(2))
        }
    }


    @Test
    fun `sykdomstidslinje tømmes helt når perioder blir forkastet, dersom det ikke finnes noen perioder igjen`() {
        //(prod-case der to dager ble igjen etter forkastelse, som medførte wonky sykdomstidslinje senere i behandlingen)
        håndterSykmelding(Sykmeldingsperiode(27.april(2020), 30.april(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(27.april(2020), 30.april(2020), 100))

        håndterSykmelding(Sykmeldingsperiode(8.juni(2020), 21.juni(2020), 100))
        håndterSøknadArbeidsgiver(SøknadArbeidsgiver.Søknadsperiode(8.juni(2020), 21.juni(2020), 100))
        håndterInntektsmelding(listOf(Periode(8.juni(2020), 23.juni(2020))), førsteFraværsdag = 8.juni(2020))

        håndterSykmelding(Sykmeldingsperiode(21.juni(2020), 11.juli(2020), 100))
        håndterSøknad(Sykdom(21.juni(2020), 11.juli(2020), 100))

        håndterPåminnelse(1, AVVENTER_VILKÅRSPRØVING_ARBEIDSGIVERSØKNAD, LocalDateTime.now().minusDays(200))

        håndterSykmelding(Sykmeldingsperiode(12.juli(2020), 31.juli(+2020), 100))
        håndterSøknad(Sykdom(12.juli(2020), 31.juli(2020), 100))

        håndterYtelser(
            0,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(24.juni(2020), 11.juli(2020), 1814, 100, ORGNUMMER),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(24.juni(2020), INNTEKT, ORGNUMMER, true)
            )
        )

        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(
            0,
            Utbetalingshistorikk.Periode.RefusjonTilArbeidsgiver(24.juni(2020), 11.juli(2020), 1814, 100, ORGNUMMER),
            inntektshistorikk = listOf(
                Utbetalingshistorikk.Inntektsopplysning(24.juni(2020), INNTEKT, ORGNUMMER, true)
            )
        )
    }

    @Test
    fun `Inntektsmelding utvider ikke perioden med arbeidsdager`() {
        håndterSykmelding(Sykmeldingsperiode(1.juni, 30.juni, 100))
        håndterInntektsmelding(listOf(Periode(1.juni, 16.juni)), førsteFraværsdag = 1.juni)
        håndterSøknad(Sykdom(1.juni, 30.juni, 100))
        håndterVilkårsgrunnlag(0, INNTEKT)
        håndterYtelser(0)   // No history
        håndterSimulering(0)
        håndterUtbetalingsgodkjenning(0, true)

        håndterSykmelding(Sykmeldingsperiode(9.juli, 31.juli, 100))
        håndterSøknad(Sykdom(9.juli, 31.juli, 100))
        håndterInntektsmelding(listOf(Periode(1.juni, 16.juni)), førsteFraværsdag = 9.juli)

        inspektør.also {
            assertEquals(Periode(1.juni, 30.juni), it.vedtaksperioder(0).periode())
            assertEquals(Periode(9.juli, 31.juli), it.vedtaksperioder(1).periode())
        }
    }
}
