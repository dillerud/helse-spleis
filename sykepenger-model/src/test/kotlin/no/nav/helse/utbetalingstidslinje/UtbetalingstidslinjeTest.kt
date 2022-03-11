package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import java.util.TreeMap
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.sykdomstidslinje.Dag
import no.nav.helse.testhelpers.AP
import no.nav.helse.testhelpers.ARB
import no.nav.helse.testhelpers.AVV
import no.nav.helse.testhelpers.FOR
import no.nav.helse.testhelpers.FRI
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.UTELATE
import no.nav.helse.testhelpers.tidslinjeOf
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.AvvistDag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.Fridag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavHelgDag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.UkjentDag
import no.nav.helse.økonomi.Økonomi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UtbetalingstidslinjeTest {

    @Test
    fun `avviser skjæringstidspunkt med flere begrunnelser`() {
        tidslinjeOf(5.NAV).also {
            Utbetalingstidslinje.avvis(listOf(it), setOf(1.januar), listOf(Begrunnelse.MinimumSykdomsgrad))
            Utbetalingstidslinje.avvis(listOf(it), setOf(1.januar), listOf(Begrunnelse.EtterDødsdato))
            Utbetalingstidslinje.avvis(listOf(it), setOf(1.januar), listOf(Begrunnelse.ManglerMedlemskap))
            val dag = it[1.januar] as AvvistDag
            assertEquals(3, dag.begrunnelser.size)
        }
    }

    @Test
    fun `avviser perioder med flere begrunnelser`() {
        val periode = 1.januar til 5.januar
        tidslinjeOf(5.NAV).also {
            Utbetalingstidslinje.avvis(listOf(it), listOf(periode), listOf(Begrunnelse.MinimumSykdomsgrad))
            Utbetalingstidslinje.avvis(listOf(it), listOf(periode), listOf(Begrunnelse.EtterDødsdato))
            Utbetalingstidslinje.avvis(listOf(it), listOf(periode), listOf(Begrunnelse.ManglerMedlemskap))
            periode.forEach { dato ->
                val dag = it[dato] as AvvistDag
                assertEquals(3, dag.begrunnelser.size)
            }
        }
    }

    @Test
    fun `samlet periode`() {
        assertEquals(1.januar til 1.januar, Utbetalingstidslinje.periode(listOf(tidslinjeOf(1.NAV))))
        assertEquals(1.desember(2017) til 7.mars, Utbetalingstidslinje.periode(listOf(
            tidslinjeOf(7.NAV),
            tidslinjeOf(7.NAV, startDato = 1.mars),
            tidslinjeOf(7.NAV, startDato = 1.desember(2017)),
        )))
    }

    @Test
    fun `betalte dager`() {
        tidslinjeOf(4.AP, 1.FRI, 6.NAV, 1.AVV, 2.FRI, 5.ARB).also {
            assertTrue(it.harBetalt(1.januar))
            assertFalse(it.harBetalt(5.januar))
            assertTrue(it.harBetalt(6.januar))
            assertTrue(it.harBetalt(7.januar))
            assertTrue(it.harBetalt(12.januar))
            assertFalse(it.harBetalt(15.januar))
            assertTrue(it.harBetalt(1.januar til 7.januar))
            assertFalse(it.harBetalt(13.januar til 20.januar))
        }
    }


    @Test
    fun `fri i helg mappes til ukjentdag`() {
        val tidslinje = tidslinjeOf(4.NAV, 3.FRI, 7.NAV)
        Utbetalingstidslinje.konverter(tidslinje).also {
            assertTrue(it[1.januar] is Dag.Sykedag)
            assertTrue(it[5.januar] is Dag.Feriedag)
            assertTrue(it[6.januar] is Dag.UkjentDag)
            assertTrue(it[8.januar] is Dag.Sykedag)
            assertTrue(it[13.januar] is Dag.SykHelgedag)
        }
    }

    @Test
    fun `mapper utbetalingstidslinje til sykdomstidslinje`() {
        val tidslinje = tidslinjeOf(16.AP, 15.NAV, 4.ARB)
        Utbetalingstidslinje.konverter(tidslinje).also {
            assertTrue(it[1.januar] is Dag.Sykedag)
            assertTrue(it[6.januar] is Dag.SykHelgedag)
            assertTrue(it[31.januar] is Dag.Sykedag)
            assertTrue(it[1.februar] is Dag.Arbeidsdag)
            assertTrue(it[2.februar] is Dag.Arbeidsdag)
            assertTrue(it[3.februar] is Dag.Arbeidsdag)
            assertTrue(it[4.februar] is Dag.Arbeidsdag)
        }
    }

    @Test
    fun `konverterer feriedager, avviste dager og foreldet dager`() {
        val tidslinje = tidslinjeOf(7.NAV, 5.FOR, 2.FRI, 5.AVV, 7.FRI)
        Utbetalingstidslinje.konverter(tidslinje).also {
            assertTrue(it[1.januar] is Dag.Sykedag)
            assertTrue(it[6.januar] is Dag.SykHelgedag)
            assertTrue(it[8.januar] is Dag.ForeldetSykedag)
            assertTrue(it[15.januar] is Dag.Sykedag)
            assertTrue(it[22.januar] is Dag.Feriedag)
        }
    }

    @Test
    fun `sammenhengende perioder brytes opp av arbeidsdager`() {
        val tidslinje = tidslinjeOf(5.NAV, 1.ARB, 5.NAV)
        val result = tidslinje.sammenhengendeUtbetalingsperioder()
        assertEquals(2, result.size)
        assertEquals(1.januar til 5.januar, result.first().periode())
        assertEquals(7.januar til 11.januar, result.last().periode())
    }

    @Test
    fun `sammenhengende perioder brytes opp av ukjent dag`() {
        val tidslinje = medInfotrygdtidslinje(tidslinjeOf(11.NAV), tidslinjeOf(1.NAV, startDato = 6.januar))
        val result = tidslinje.sammenhengendeUtbetalingsperioder()
        assertEquals(2, result.size)
        assertEquals(1.januar til 5.januar, result.first().periode())
        assertEquals(7.januar til 11.januar, result.last().periode())
    }

    @Test
    fun `plusser sammen 1`() {
        val tidslinje1 = tidslinjeOf(3.NAV, 3.UTELATE, 3.NAV)
        val tidslinje2 = tidslinjeOf(3.UTELATE, 3.NAV, 3.UTELATE)
        val result = tidslinje1 + tidslinje2
        result.zipWithNext { a, b ->
            assertTrue(a.dato < b.dato)
        }
    }

    @Test
    fun `plusser sammen 2`() {
        val tidslinje1 = tidslinjeOf(3.UTELATE, 3.NAV, 3.UTELATE)
        val tidslinje2 = tidslinjeOf(3.NAV, 3.UTELATE, 3.NAV)
        val result = tidslinje1 + tidslinje2
        result.zipWithNext { a, b ->
            assertTrue(a.dato < b.dato)
        }
    }

    @Test
    fun `iterator`() {
        val tidslinje = tidslinjeOf(3.NAV, 3.UTELATE, 3.NAV)
        val result = mutableListOf<LocalDate>()
        tidslinje.iterator().forEach { result.add(it.dato) }
        assertEquals(9, result.size)
        assertEquals(1.januar, result.first())
        assertEquals(9.januar, result.last())
    }

    @Test
    fun `reversed iterator`() {
        val tidslinje = tidslinjeOf(3.NAV, 3.UTELATE, 3.NAV)
        val result = mutableListOf<LocalDate>()
        tidslinje.reverse().iterator().forEach { result.add(it.dato) }
        assertEquals(9, result.size)
        assertEquals(9.januar, result.first())
        assertEquals(1.januar, result.last())
    }

    @Test
    fun sorted() {
        val comparator = Comparator(LocalDate::compareTo).reversed().reversed()
        val map = TreeMap<LocalDate, Utbetalingstidslinje.Utbetalingsdag>(comparator)

        map[2.januar] = NavDag(2.januar, Økonomi.ikkeBetalt())
        map[1.januar] = Fridag(1.januar, Økonomi.ikkeBetalt())

        map.forEach { (dato, dag) ->
            println("$dato")
        }
    }

    @Test
    fun `fjerner ledende fridager`() {
        val tidslinje = tidslinjeOf(6.FRI, 5.NAV)
        val result = tidslinje.sammenhengendeUtbetalingsperioder()
        assertEquals(1, result.size)
        assertEquals(7.januar til 11.januar, result.first().periode())
    }

    @Test
    fun `helg blir ikke sett på som en periode`() {
        val tidslinje = tidslinjeOf(5.ARB, 2.NAV, 5.ARB)
        val result = tidslinje.sammenhengendeUtbetalingsperioder()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `foreldet perioder tas med`() {
        val tidslinje = tidslinjeOf(5.NAV, 5.FOR)
        val result = tidslinje.sammenhengendeUtbetalingsperioder()
        assertEquals(1, result.size)
        assertEquals(1.januar til 10.januar, result.first().periode())
    }

    private fun medInfotrygdtidslinje(tidslinje: Utbetalingstidslinje, other: Utbetalingstidslinje) =
        tidslinje.plus(other) { actual, challenger ->
            when (challenger) {
                is NavDag, is NavHelgDag -> UkjentDag(actual.dato, actual.økonomi)
                else -> actual
            }
        }
}
