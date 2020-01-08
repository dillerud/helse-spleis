package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.*

internal class Utbetalingsgrense(private val alder: Alder, arbeidsgiverRegler: ArbeidsgiverRegler):
    Utbetalingstidslinje.UtbetalingsdagVisitor {

    companion object {
        const val TILSTREKKELIG_OPPHOLD_I_SYKEDAGER = 26*7
        private const val HISTORISK_PERIODE_I_ÅR: Long = 3
    }
    private var sisteBetalteDag: LocalDate? = null
    private var state: State = State.Initiell
    private val teller = UtbetalingTeller(alder, arbeidsgiverRegler)
    private var opphold = 0
    private lateinit var sakensStartdato: LocalDate  // Date of first NAV payment in a new 248 period
    private lateinit var dekrementerfom: LocalDate  // Three year boundary from first sick day after a work day
    private val ubetalteDager = mutableListOf<Utbetalingstidslinje.Utbetalingsdag.AvvistDag>()
    private val betalbarDager = mutableMapOf<LocalDate, NavDag>()

    internal fun maksdato() = sisteBetalteDag?.let { teller.maksdato(it) }

    internal fun ubetalteDager(): List<Utbetalingstidslinje.Utbetalingsdag.AvvistDag> {
        return ubetalteDager
    }

    private fun state(nyState: State) {
        state.leaving(this)
        state = nyState
        state.entering(this)
    }

    override fun visitNavDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavDag) {
        if (dag.dato >= alder.øvreAldersgrense) state(State.Karantene) else betalbarDager[dag.dato] = dag
        state.betalbarDag(this, dag.dato)
    }

    override fun visitNavHelgDag(dag: Utbetalingstidslinje.Utbetalingsdag.NavHelgDag) {
        oppholdsdag(dag.dato)
    }

    override fun visitArbeidsdag(dag: Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag) {
        oppholdsdag(dag.dato)
    }

    override fun visitArbeidsgiverperiodeDag(dag: Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag) {
        oppholdsdag(dag.dato)
    }

    override fun visitFridag(dag: Utbetalingstidslinje.Utbetalingsdag.Fridag) {
        oppholdsdag(dag.dato)
    }

    override fun visitAvvistDag(dag: Utbetalingstidslinje.Utbetalingsdag.AvvistDag) {
        oppholdsdag(dag.dato)
    }

    override fun visitUkjentDag(dag: Utbetalingstidslinje.Utbetalingsdag.UkjentDag) {
        oppholdsdag(dag.dato)
    }

    private fun oppholdsdag(dagen: LocalDate) {
        opphold += 1
        state.oppholdsdag(this, dagen)
    }

    private fun nextState(dagen: LocalDate) : State? {
        if (opphold >= TILSTREKKELIG_OPPHOLD_I_SYKEDAGER) {
            teller.resett(dagen.plusDays(1))
            return State.Initiell
        }
        return if (teller.påGrensen(dagen)) State.Karantene else null
    }

    private fun dekrementer(tom: LocalDate) {
        val dekrementertom = tom.minusYears(HISTORISK_PERIODE_I_ÅR)
        if (dekrementertom >= sakensStartdato) {
            dekrementerfom.datesUntil(dekrementertom).forEach { dato ->
                betalbarDager[dato]?.also { teller.dekrementer(dato) }
            }
        }
        dekrementerfom = dekrementertom
    }

    private sealed class State {
        open fun betalbarDag(avgrenser: Utbetalingsgrense, dagen: LocalDate) {}
        open fun oppholdsdag(avgrenser: Utbetalingsgrense, dagen: LocalDate) {}
        open fun entering(avgrenser: Utbetalingsgrense) {}
        open fun leaving(avgrenser: Utbetalingsgrense) {}

        internal object Initiell: State() {
            override fun entering(avgrenser: Utbetalingsgrense) {
                avgrenser.opphold = 0
            }
            override fun betalbarDag(avgrenser: Utbetalingsgrense, dagen: LocalDate) {
                avgrenser.sakensStartdato = dagen
                avgrenser.dekrementerfom = dagen.minusYears(HISTORISK_PERIODE_I_ÅR)
                avgrenser.teller.inkrementer(dagen)
                avgrenser.sisteBetalteDag = dagen
                avgrenser.state(Syk)
            }
        }

        internal object Syk: State() {
            override fun entering(avgrenser: Utbetalingsgrense) {
                avgrenser.opphold = 0
            }

            override fun betalbarDag(avgrenser: Utbetalingsgrense, dagen: LocalDate) {
                avgrenser.teller.inkrementer(dagen)
                avgrenser.sisteBetalteDag = dagen
                avgrenser.nextState(dagen)?.run { avgrenser.state(this) }
            }

            override fun oppholdsdag(avgrenser: Utbetalingsgrense, dagen: LocalDate) {
                avgrenser.state(Opphold)
            }
        }

        internal object Opphold: State() {

            override fun betalbarDag(avgrenser: Utbetalingsgrense, dagen: LocalDate) {
                avgrenser.teller.inkrementer(dagen)
                avgrenser.sisteBetalteDag = dagen
                avgrenser.dekrementer(dagen)
                avgrenser.state(avgrenser.nextState(dagen) ?: Syk)
            }

            override fun oppholdsdag(avgrenser: Utbetalingsgrense, dagen: LocalDate) {
                avgrenser.nextState(dagen)?.run { avgrenser.state(this) }
            }
        }

        internal object Karantene: State() {
            override fun betalbarDag(avgrenser: Utbetalingsgrense, dagen: LocalDate) {
                avgrenser.opphold += 1
                avgrenser.ubetalteDager.add(AvvistDag(dagen, Begrunnelse.SykepengedagerOppbrukt))
                avgrenser.nextState(dagen)?.run { avgrenser.state(this) }
            }

            override fun oppholdsdag(avgrenser: Utbetalingsgrense, dagen: LocalDate) {
                avgrenser.nextState(dagen)?.run { avgrenser.state(this) }
            }
        }

    }
}
