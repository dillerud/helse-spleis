package no.nav.helse.person.builders

import no.nav.helse.januar
import no.nav.helse.person.PersonObserver.Utbetalingsdag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.Arbeidsdag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.ArbeidsgiverperiodeDag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.AvvistDag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.Feriedag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.ForeldetDag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.Fridag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.NavDag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.NavHelgDag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.Permisjonsdag
import no.nav.helse.person.PersonObserver.Utbetalingsdag.Dagtype.UkjentDag
import no.nav.helse.serde.api.BegrunnelseDTO
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.testhelpers.AP
import no.nav.helse.testhelpers.ARB
import no.nav.helse.testhelpers.AVV
import no.nav.helse.testhelpers.F
import no.nav.helse.testhelpers.FOR
import no.nav.helse.testhelpers.FRI
import no.nav.helse.testhelpers.HELG
import no.nav.helse.testhelpers.NAV
import no.nav.helse.testhelpers.P
import no.nav.helse.testhelpers.UK
import no.nav.helse.testhelpers.UKJ
import no.nav.helse.testhelpers.resetSeed
import no.nav.helse.testhelpers.tidslinjeOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class UtbetalingsdagerBuilderTest {

    @BeforeEach
    fun reset() {
        resetSeed()
    }

    @Test
    fun `bygger utbetalingsdager`() {
        val builder = UtbetalingsdagerBuilder(Sykdomstidslinje())
        val utbetalingstidslinje = tidslinjeOf(1.AP, 1.NAV, 1.HELG, 1.ARB, 1.FRI, 1.FOR, 1.AVV)
        utbetalingstidslinje.accept(builder)
        assertEquals(
            listOf(
                Utbetalingsdag(1.januar, ArbeidsgiverperiodeDag),
                Utbetalingsdag(2.januar, NavDag),
                Utbetalingsdag(3.januar, NavHelgDag),
                Utbetalingsdag(4.januar, Arbeidsdag),
                Utbetalingsdag(5.januar, Fridag),
                Utbetalingsdag(6.januar, ForeldetDag),
                Utbetalingsdag(7.januar, AvvistDag, listOf(BegrunnelseDTO.SykepengedagerOppbrukt))
            ), builder.result()
        )
    }

    @Test
    fun `tidslinje med fridager`() {
        val builder = UtbetalingsdagerBuilder(1.P + 4.F + 1.UK)
        val utbetalingstidslinje = tidslinjeOf(6.FRI)
        utbetalingstidslinje.accept(builder)
        assertEquals(
            listOf(
                Utbetalingsdag(1.januar, Permisjonsdag),
                Utbetalingsdag(2.januar, Feriedag),
                Utbetalingsdag(3.januar, Feriedag),
                Utbetalingsdag(4.januar, Feriedag),
                Utbetalingsdag(5.januar, Feriedag),
                Utbetalingsdag(6.januar, Fridag)
            ), builder.result()
        )
    }

    @Test
    fun `tidslinje med ukjentdag`() {
        val builder = UtbetalingsdagerBuilder(Sykdomstidslinje())
        val utbetalingstidslinje = tidslinjeOf(1.NAV, 1.UKJ, 1.NAV)
        utbetalingstidslinje.accept(builder)
        assertEquals(listOf(
            Utbetalingsdag(1.januar, NavDag),
            Utbetalingsdag(2.januar, UkjentDag),
            Utbetalingsdag(3.januar, NavDag)
        ), builder.result())
    }
}
