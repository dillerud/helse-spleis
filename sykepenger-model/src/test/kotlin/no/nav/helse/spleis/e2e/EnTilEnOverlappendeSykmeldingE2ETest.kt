package no.nav.helse.spleis.e2e

import no.nav.helse.Toggles
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad
import no.nav.helse.hendelser.til
import no.nav.helse.person.TilstandType.*
import no.nav.helse.testhelpers.SykdomstidslinjeInspektør
import no.nav.helse.testhelpers.januar
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class EnTilEnOverlappendeSykmeldingE2ETest : AbstractEndToEndTest() {

    @BeforeAll
    fun setup() {
        Toggles.OverlappendeSykmelding.enable()
    }

    @AfterAll
    fun teardown() {
        Toggles.OverlappendeSykmelding.pop()
    }

    @Test
    fun `sykmelding nr 2 overlapper perfekt - leses inn i samme rekkefølge som de ble skrevet`() {
        val sykmeldingSkrevet = 3.januar.atStartOfDay()
        håndterSykmelding(Sykmeldingsperiode(3.januar, 15.januar, 90.prosent), sykmeldingSkrevet = sykmeldingSkrevet)
        håndterSykmelding(Sykmeldingsperiode(3.januar, 15.januar, 100.prosent), sykmeldingSkrevet = sykmeldingSkrevet.plusHours(1))
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertEquals(3.januar til 15.januar, inspektør.periode(1.vedtaksperiode))
        SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also { sykdomstidslinjeInspektør ->
            assertTrue(sykdomstidslinjeInspektør.grader.all { it.value == 100 })
        }
        assertNoErrors(inspektør)
        assertWarningTekst(inspektør, "Korrigert sykmelding er lagt til grunn - kontroller dagene i sykmeldingsperioden")
    }

    @Test
    fun `sykmelding nr 2 overlapper perfekt - leses inn i omvendt rekkefølge av når de ble skrevet`() {
        val sykmeldingSkrevet = 3.januar.atStartOfDay()
        håndterSykmelding(Sykmeldingsperiode(3.januar, 15.januar, 90.prosent), sykmeldingSkrevet = sykmeldingSkrevet)
        håndterSykmelding(Sykmeldingsperiode(3.januar, 15.januar, 100.prosent), sykmeldingSkrevet = sykmeldingSkrevet.minusHours(1))
        assertEquals(1, inspektør.vedtaksperiodeTeller)
        assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertEquals(3.januar til 15.januar, inspektør.periode(1.vedtaksperiode))
        SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also { sykdomstidslinjeInspektør ->
            assertTrue(sykdomstidslinjeInspektør.grader.all { it.value == 90 })
        }
        assertNoErrors(inspektør)
        assertWarningTekst(inspektør, "Mottatt en sykmelding som er skrevet tidligere enn den som er lagt til grunn, vurder sykmeldingene og gjør eventuelle justeringer")
    }

    @Test
    fun `sykmelding nr 2 har lik grad som sykmelding nr 1`() {
        val sykmeldingSkrevet = 3.januar.atStartOfDay()
        håndterSykmelding(
            Sykmeldingsperiode(3.januar, 15.januar, 90.prosent),
            Sykmeldingsperiode(16.januar, 20.januar, 40.prosent),
            sykmeldingSkrevet = sykmeldingSkrevet
        )
        håndterSykmelding(
            Sykmeldingsperiode(3.januar, 15.januar, 90.prosent),
            Sykmeldingsperiode(16.januar, 20.januar, 40.prosent),
            sykmeldingSkrevet = sykmeldingSkrevet.plusHours(1)
        )
        assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertEquals(3.januar til 20.januar, inspektør.periode(1.vedtaksperiode))
        assertNoWarnings(inspektør)
    }

    @Test
    fun `sykmelding nr 2 har ulik grad som sykmelding nr 1`() {
        val sykmeldingSkrevet = 3.januar.atStartOfDay()
        håndterSykmelding(
            Sykmeldingsperiode(3.januar, 15.januar, 90.prosent),
            Sykmeldingsperiode(16.januar, 20.januar, 40.prosent),
            sykmeldingSkrevet = sykmeldingSkrevet
        )
        håndterSykmelding(
            Sykmeldingsperiode(3.januar, 16.januar, 90.prosent),
            Sykmeldingsperiode(17.januar, 20.januar, 40.prosent),
            sykmeldingSkrevet = sykmeldingSkrevet.plusHours(1)
        )
        assertTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP)
        assertEquals(3.januar til 20.januar, inspektør.periode(1.vedtaksperiode))
        assertWarningTekst(inspektør, "Korrigert sykmelding er lagt til grunn - kontroller dagene i sykmeldingsperioden")
    }

    @Test
    fun `støtter ikke overlapp inni`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 15.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(4.januar, 15.januar, 100.prosent))
        assertErrors(inspektør)
        assertForkastetPeriodeTilstander(1.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_GAP, TIL_INFOTRYGD)
    }

    @Test
    fun `støtter en til en overlapp for ferdig forlengelse`() {
        nyttVedtak(3.januar, 20.januar, 100.prosent)
        håndterSykmelding(Sykmeldingsperiode(21.januar, 25.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(21.januar, 25.januar, 90.prosent))
        assertEquals(2, inspektør.vedtaksperiodeTeller)
        assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_FERDIG_FORLENGELSE)
        SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also { sykdomstidslinjeInspektør ->
            assertTrue(sykdomstidslinjeInspektør.grader.filterKeys { it.isAfter(20.januar) }.all { it.value == 90 })
        }
        assertNoErrors(inspektør)
        assertWarningTekst(inspektør, "Korrigert sykmelding er lagt til grunn - kontroller dagene i sykmeldingsperioden")
    }

    @Test
    fun `støtter en til en overlapp for uferdig forlengelse`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 15.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(16.januar, 20.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(16.januar, 20.januar, 90.prosent))
        assertEquals(2, inspektør.vedtaksperiodeTeller)
        assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE)
        SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also { sykdomstidslinjeInspektør ->
            assertTrue(sykdomstidslinjeInspektør.grader.filterKeys { it.isAfter(15.januar) }.all { it.value == 90 })
        }
        assertNoErrors(inspektør)
        assertWarningTekst(inspektør, "Korrigert sykmelding er lagt til grunn - kontroller dagene i sykmeldingsperioden")
    }

    @Test
    fun `støtter en til en overlapp for uferdig gap`() {
        håndterSykmelding(Sykmeldingsperiode(3.januar, 15.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(19.januar, 20.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(19.januar, 20.januar, 90.prosent))
        assertEquals(2, inspektør.vedtaksperiodeTeller)
        assertTilstander(2.vedtaksperiode, START, MOTTATT_SYKMELDING_UFERDIG_GAP)
        SykdomstidslinjeInspektør(inspektør.sykdomstidslinje).also { sykdomstidslinjeInspektør ->
            assertTrue(sykdomstidslinjeInspektør.grader.filterKeys { it.isAfter(15.januar) }.all { it.value == 90 })
        }
        assertNoErrors(inspektør)
        assertWarningTekst(inspektør, "Korrigert sykmelding er lagt til grunn - kontroller dagene i sykmeldingsperioden")
    }
}