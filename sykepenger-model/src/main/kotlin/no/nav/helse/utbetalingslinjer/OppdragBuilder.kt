package no.nav.helse.utbetalingslinjer

import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.UtbetalingsdagVisitor
import no.nav.helse.utbetalingstidslinje.UtbetalingStrategy
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.*
import no.nav.helse.utbetalingstidslinje.genererUtbetalingsreferanse
import no.nav.helse.økonomi.Økonomi
import java.time.LocalDate
import java.util.*
import kotlin.math.roundToInt

internal class OppdragBuilder(
    private val tidslinje: Utbetalingstidslinje,
    private val mottaker: String,
    private val fagområde: Fagområde,
    sisteDato: LocalDate = tidslinje.sisteDato(),
    private val dagStrategy: UtbetalingStrategy = NavDag.reflectedArbeidsgiverBeløp
) : UtbetalingsdagVisitor {
    private val arbeisdsgiverLinjer = mutableListOf<Utbetalingslinje>()
    private var tilstand: Tilstand = MellomLinjer()
    private val fagsystemId = genererUtbetalingsreferanse(UUID.randomUUID())
    private var sisteArbeidsgiverdag: LocalDate? = null

    init {
        tidslinje.kutt(sisteDato).reverse().accept(this)
    }

    internal fun result(): Oppdrag {
        arbeisdsgiverLinjer.removeAll { it.beløp == null }
        arbeisdsgiverLinjer.zipWithNext { a, b -> b.linkTo(a) }
        arbeisdsgiverLinjer.firstOrNull()?.refFagsystemId = null
        return Oppdrag(mottaker, fagområde, arbeisdsgiverLinjer, fagsystemId, sisteArbeidsgiverdag)
    }

    internal fun result(fagsystemIder: MutableList<FagsystemId>, observatør: FagsystemIdObserver, aktivitetslogg: IAktivitetslogg): FagsystemId? {
        val oppdrag = result().takeIf(Oppdrag::isNotEmpty) ?: return null
        return fagsystemIder.firstOrNull { it.utvide(oppdrag, tidslinje, aktivitetslogg) }
            ?: (FagsystemId(observatør, fagsystemId, fagområde, mottaker, FagsystemId.Utbetaling.nyUtbetaling(oppdrag, tidslinje)).also {
                fagsystemIder.add(it)
            })
    }

    private val linje get() = arbeisdsgiverLinjer.first()

    override fun visit(dag: UkjentDag, dato: LocalDate, økonomi: Økonomi) {
        tilstand = Avsluttet
    }

    override fun visit(
        dag: NavDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        økonomi.reflection { grad, aktuellDagsinntekt ->
            if (arbeisdsgiverLinjer.isEmpty()) return@reflection tilstand.nyLinje(dag, dato, grad, aktuellDagsinntekt!!)
            if (grad == linje.grad && (linje.beløp == null || linje.beløp == dagStrategy(dag.økonomi)))
                tilstand.betalingsdag(dag, dato, grad, aktuellDagsinntekt!!)
            else
                tilstand.nyLinje(dag, dato, grad, aktuellDagsinntekt!!)
        }
    }

    override fun visit(
        dag: AnnullertDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        økonomi.reflection { grad, aktuellDagsinntekt ->
            if (arbeisdsgiverLinjer.isEmpty()) return@reflection tilstand.nyLinje(dag, dato, grad, aktuellDagsinntekt!!)
            if (grad == linje.grad && (linje.beløp == null || linje.beløp == dagStrategy(dag.økonomi)))
                tilstand.betalingsdag(dag, dato, grad, aktuellDagsinntekt!!)
            else
                tilstand.nyLinje(dag, dato, grad, aktuellDagsinntekt!!)
        }
    }

    override fun visit(
        dag: NavHelgDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        økonomi.reflection { grad, _ ->
            if (arbeisdsgiverLinjer.isEmpty() || grad != linje.grad)
                tilstand.nyLinje(dag, dato, grad)
            else
                tilstand.helgedag(dag, dato, grad)
        }
    }

    override fun visit(
        dag: Arbeidsdag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        tilstand.ikkeBetalingsdag()
    }

    override fun visit(
        dag: ArbeidsgiverperiodeDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        sisteArbeidsgiverdag?.let { sisteArbeidsgiverdag = dag.dato }
        tilstand = Avsluttet
    }

    override fun visit(
        dag: AvvistDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        tilstand.ikkeBetalingsdag()
    }

    override fun visit(
        dag: Fridag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        tilstand.ikkeBetalingsdag()
    }

    override fun visit(
        dag: ForeldetDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        tilstand.ikkeBetalingsdag()
    }

    private fun addLinje(dag: Utbetalingsdag, dato: LocalDate, grad: Double, aktuellDagsinntekt: Double) {
        arbeisdsgiverLinjer.add(
            0,
            Utbetalingslinje(
                dato,
                dato,
                dagStrategy(dag.økonomi),
                aktuellDagsinntekt.roundToInt(),
                grad,
                fagsystemId
            )
        )
    }


    private fun addLinje(dato: LocalDate, grad: Double) {
        arbeisdsgiverLinjer.add(
            0,
            Utbetalingslinje(dato, dato, null, 0, grad, fagsystemId)
        )
    }

    internal interface Tilstand {
        fun betalingsdag(
            dag: Utbetalingsdag,
            dato: LocalDate,
            grad: Double,
            aktuellDagsinntekt: Double
        ) {
        }

        fun helgedag(
            dag: NavHelgDag,
            dato: LocalDate,
            grad: Double
        ) {
        }

        fun nyLinje(
            dag: Utbetalingsdag,
            dato: LocalDate,
            grad: Double,
            aktuellDagsinntekt: Double
        ) {
        }

        fun nyLinje(
            dag: NavHelgDag,
            dato: LocalDate,
            grad: Double
        ) {
        }

        fun ikkeBetalingsdag() {}
    }

    private inner class MellomLinjer : Tilstand {
        override fun betalingsdag(
            dag: Utbetalingsdag,
            dato: LocalDate,
            grad: Double,
            aktuellDagsinntekt: Double
        ) {
            addLinje(dag, dato, grad, aktuellDagsinntekt)
            tilstand = LinjeMedSats()
        }


        override fun nyLinje(
            dag: Utbetalingsdag,
            dato: LocalDate,
            grad: Double,
            aktuellDagsinntekt: Double
        ) {
            addLinje(dag, dato, grad, aktuellDagsinntekt)
            tilstand = LinjeMedSats()
        }


        override fun helgedag(
            dag: NavHelgDag,
            dato: LocalDate,
            grad: Double
        ) {
            addLinje(dato, grad)
            tilstand = LinjeUtenSats()
        }

        override fun nyLinje(
            dag: NavHelgDag,
            dato: LocalDate,
            grad: Double
        ) {
            addLinje(dato, grad)
            tilstand = LinjeUtenSats()
        }
    }

    private inner class LinjeMedSats : Tilstand {
        override fun ikkeBetalingsdag() {
            tilstand = MellomLinjer()
        }

        override fun betalingsdag(
            dag: Utbetalingsdag,
            dato: LocalDate,
            grad: Double,
            aktuellDagsinntekt: Double
        ) {
            linje.fom = dag.dato
        }

        override fun nyLinje(
            dag: Utbetalingsdag,
            dato: LocalDate,
            grad: Double,
            aktuellDagsinntekt: Double
        ) {
            addLinje(dag, dato, grad, aktuellDagsinntekt)
        }

        override fun helgedag(
            dag: NavHelgDag,
            dato: LocalDate,
            grad: Double
        ) {
            linje.fom = dag.dato
        }

        override fun nyLinje(
            dag: NavHelgDag,
            dato: LocalDate,
            grad: Double
        ) {
            addLinje(dato, grad)
            tilstand = LinjeUtenSats()
        }
    }

    private inner class LinjeUtenSats : Tilstand {
        override fun ikkeBetalingsdag() {
            tilstand = MellomLinjer()
        }

        override fun betalingsdag(
            dag: Utbetalingsdag,
            dato: LocalDate,
            grad: Double,
            aktuellDagsinntekt: Double
        ) {
            linje.beløp = dagStrategy(dag.økonomi)
            linje.aktuellDagsinntekt = aktuellDagsinntekt.roundToInt() //Needs to be changed for self employed
            linje.fom = dag.dato
            tilstand = LinjeMedSats()
        }

        override fun nyLinje(
            dag: Utbetalingsdag,
            dato: LocalDate,
            grad: Double,
            aktuellDagsinntekt: Double
        ) {
            addLinje(dag, dato, grad, aktuellDagsinntekt)
            tilstand = LinjeMedSats()
        }

        override fun helgedag(
            dag: NavHelgDag,
            dato: LocalDate,
            grad: Double
        ) {
            linje.fom = dag.dato
        }

        override fun nyLinje(
            dag: NavHelgDag,
            dato: LocalDate,
            grad: Double
        ) {
            addLinje(dato, grad)
        }
    }

    private object Avsluttet : Tilstand {}
}
