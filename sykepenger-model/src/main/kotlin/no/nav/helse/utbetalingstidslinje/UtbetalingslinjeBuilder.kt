package no.nav.helse.utbetalingstidslinje

import no.nav.helse.sykdomstidslinje.Utbetalingslinje

internal class UtbetalingslinjeBuilder(private val tidslinje: Utbetalingstidslinje) :
    Utbetalingstidslinje.UtbetalingsdagVisitor {
    private val utbetalingslinjer = mutableListOf<Utbetalingslinje>()
    private var helseState: HelseState = Ubetalt()

    internal fun result(): List<Utbetalingslinje> {
        tidslinje.accept(this)
        return utbetalingslinjer
    }

    override fun visitArbeidsdag(dag: Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag) {
        helseState.ikkeBetalingsdag()
    }

    override fun visitArbeidsgiverperiodeDag(dag: Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag) {
        helseState.ikkeBetalingsdag()
    }

    override fun visitAvvistDag(dag: Utbetalingstidslinje.Utbetalingsdag.AvvistDag) {
        helseState.ikkeBetalingsdag()
    }

    override fun visitFridag(dag: Utbetalingstidslinje.Utbetalingsdag.Fridag) {
        helseState.ikkeBetalingsdag()
    }

    override fun visitNavDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavDag) {
        helseState.betalingsdag(dag)
    }

    override fun visitNavHelgDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavHelgDag) {
        // Ignorer
    }

    private interface HelseState {
        fun betalingsdag(dag: Utbetalingstidslinje.Utbetalingsdag.NavDag)
        fun ikkeBetalingsdag() {}
    }

    internal inner class Ubetalt : HelseState {
        override fun betalingsdag(dag: Utbetalingstidslinje.Utbetalingsdag.NavDag) {
            utbetalingslinjer.add(dag.utbetalingslinje())
            helseState = Betalt()
        }
    }

    internal inner class Betalt : HelseState {
        override fun ikkeBetalingsdag() {
            helseState = Ubetalt()
        }

        override fun betalingsdag(dag: Utbetalingstidslinje.Utbetalingsdag.NavDag) {
            if (utbetalingslinjer.last().dagsats == dag.utbetaling)
                dag.oppdater(utbetalingslinjer.last())
            else
                utbetalingslinjer.add(dag.utbetalingslinje())
        }
    }
}
