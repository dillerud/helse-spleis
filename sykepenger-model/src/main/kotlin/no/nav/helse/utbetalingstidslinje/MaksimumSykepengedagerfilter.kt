package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Etterlevelse.Vurderingsresultat.Companion.`§8-12 ledd 1 punktum 1`
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Etterlevelse.Vurderingsresultat.Companion.`§8-12 ledd 2`
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.UtbetalingsdagVisitor
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.NavDag
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Utbetalingsdag.UkjentDag
import no.nav.helse.økonomi.Økonomi
import java.time.LocalDate

internal data class Sykepengerettighet(
    val maksdato: LocalDate,
    val gjenståendeSykedager: Int,
    val forbrukteSykedager: Int
)

internal class MaksimumSykepengedagerfilter(
    private val alder: Alder,
    private val arbeidsgiverRegler: ArbeidsgiverRegler,
    private val periode: Periode,
    private val aktivitetslogg: IAktivitetslogg
) : UtbetalingsdagVisitor {

    private companion object {
        const val TILSTREKKELIG_OPPHOLD_I_SYKEDAGER = 26 * 7
        private const val HISTORISK_PERIODE_I_ÅR: Long = 3
    }

    private lateinit var sisteUkedag: LocalDate
    private lateinit var sisteBetalteDag: LocalDate
    private lateinit var state: State
    private lateinit var teller: UtbetalingTeller
    private var opphold = 0
    private lateinit var sakensStartdato: LocalDate  // Date of first NAV payment in a new 248 period
    private lateinit var dekrementerfom: LocalDate  // Three year boundary from first sick day after a work day
    private val begrunnelserForAvvisteDager = mutableMapOf<Begrunnelse, MutableSet<LocalDate>>()
    private val avvisteDager get() = begrunnelserForAvvisteDager.values.flatten().toSet()
    private val betalbarDager = mutableMapOf<LocalDate, NavDag>()
    private lateinit var beregnetTidslinje: Utbetalingstidslinje
    private lateinit var tidslinjegrunnlag: List<Utbetalingstidslinje>

    internal fun avvisDag(dag: LocalDate, begrunnelse: Begrunnelse) {
        begrunnelserForAvvisteDager.getOrPut(begrunnelse) {
            mutableSetOf()
        }.add(dag)
    }

    private fun maksdato() =
        if (gjenståendeSykedager() == 0) teller.maksdato(sisteBetalteDag) else teller.maksdato(sisteUkedag)

    private fun gjenståendeSykedager() = teller.gjenståendeSykepengedager(sisteBetalteDag)
    private fun forbrukteSykedager() = teller.forbrukteDager()

    internal fun filter(tidslinjer: List<Utbetalingstidslinje>, personTidslinje: Utbetalingstidslinje): Sykepengerettighet {
        beregnetTidslinje = tidslinjer
            .reduce(Utbetalingstidslinje::plus)
            .plus(personTidslinje)
        tidslinjegrunnlag = tidslinjer + listOf(personTidslinje)
        teller = UtbetalingTeller(alder, arbeidsgiverRegler, aktivitetslogg)
        state = State.Initiell
        beregnetTidslinje.accept(this)
        if (::sakensStartdato.isInitialized) {
            val avvisteDager = avvisteDager.filter { sakensStartdato <= it }
            aktivitetslogg.`§8-12 ledd 1 punktum 1`(
                avvisteDager !in periode,
                this.avvisteDager.firstOrNull() ?: sakensStartdato,
                this.avvisteDager.lastOrNull() ?: sisteBetalteDag,
                tidslinjegrunnlag,
                beregnetTidslinje,
                gjenståendeSykedager(),
                forbrukteSykedager(),
                maksdato(),
                avvisteDager
            )
        }

        begrunnelserForAvvisteDager.forEach { (begrunnelse, avvisteDager) ->
            Utbetalingstidslinje.avvis(
                tidslinjer = tidslinjer,
                avvistePerioder = avvisteDager.grupperSammenhengendePerioder(),
                begrunnelser = listOf(begrunnelse)
            )
        }

        if (avvisteDager in periode)
            aktivitetslogg.warn("Maks antall sykepengedager er nådd i perioden. Vurder å sende vedtaksbrev fra Infotrygd")
        else
            aktivitetslogg.info("Maksimalt antall sykedager overskrides ikke i perioden")

        return Sykepengerettighet(maksdato(), gjenståendeSykedager(), forbrukteSykedager())
    }

    private fun state(nyState: State) {
        if (this.state == nyState) return
        state.leaving(this)
        state = nyState
        state.entering(this)
    }

    override fun preVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje) {
        sisteUkedag = tidslinje.sisteUkedag()
        sisteBetalteDag = tidslinje.periode().endInclusive
    }

    override fun postVisitUtbetalingstidslinje(tidslinje: Utbetalingstidslinje) {
        alder.etterlevelse70år(aktivitetslogg, beregnetTidslinje.periode(), avvisteDager)
    }

    override fun visit(
        dag: NavDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        if (alder.mistetSykepengerett(dato)) state(State.ForGammel) else betalbarDager[dato] = dag
        state.betalbarDag(this, dato)
    }

    override fun visit(
        dag: Utbetalingstidslinje.Utbetalingsdag.NavHelgDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        if (alder.mistetSykepengerett(dato)) state(State.ForGammel)
        state.sykdomshelg(this, dato)
    }

    override fun visit(
        dag: Utbetalingstidslinje.Utbetalingsdag.Arbeidsdag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        oppholdsdag(dato)
    }

    override fun visit(
        dag: Utbetalingstidslinje.Utbetalingsdag.ArbeidsgiverperiodeDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        oppholdsdag(dato)
    }

    override fun visit(
        dag: Utbetalingstidslinje.Utbetalingsdag.Fridag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        state.fridag(this, dato)
    }

    override fun visit(
        dag: Utbetalingstidslinje.Utbetalingsdag.AvvistDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        oppholdsdag(dato)
    }

    override fun visit(
        dag: UkjentDag,
        dato: LocalDate,
        økonomi: Økonomi
    ) {
        oppholdsdag(dato)
    }

    private fun oppholdsdag(dagen: LocalDate) {
        opphold += 1
        state.oppholdsdag(this, dagen)
    }

    private fun nextState(dagen: LocalDate): State? {
        if (opphold >= TILSTREKKELIG_OPPHOLD_I_SYKEDAGER) {
            val gjenståendeSykedager = gjenståendeSykedager()
            aktivitetslogg.`§8-12 ledd 1 punktum 1`(
                avvisteDager.filter { sakensStartdato <= it } !in periode,
                avvisteDager.firstOrNull() ?: sakensStartdato,
                avvisteDager.lastOrNull() ?: sisteBetalteDag,
                tidslinjegrunnlag,
                beregnetTidslinje,
                gjenståendeSykedager,
                forbrukteSykedager(),
                maksdato(),
                avvisteDager.filter { sakensStartdato <= it }
            )
            aktivitetslogg.`§8-12 ledd 2`(dagen, TILSTREKKELIG_OPPHOLD_I_SYKEDAGER, tidslinjegrunnlag, beregnetTidslinje)
            teller.resett(dagen.plusDays(1))
            return State.Initiell
        }
        if (state == State.Karantene) return null
        if (teller.erFørMaksdato(dagen)) return null

        teller.hvisGrensenErNådd(
            hvis248Dager = {
                aktivitetslogg.`§8-12 ledd 1 punktum 1`(
                    true,
                    sakensStartdato,
                    sisteBetalteDag,
                    tidslinjegrunnlag,
                    beregnetTidslinje,
                    gjenståendeSykedager(),
                    forbrukteSykedager(),
                    maksdato(),
                    avvisteDager.filter { sakensStartdato <= it }
                )
            }
        )
        return State.Karantene
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

    private interface State {
        fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {}
        fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) = avgrenser.oppholdsdag(dagen)
        fun entering(avgrenser: MaksimumSykepengedagerfilter) {}
        fun leaving(avgrenser: MaksimumSykepengedagerfilter) {}

        object Initiell : State {
            override fun entering(avgrenser: MaksimumSykepengedagerfilter) {
                avgrenser.opphold = 0
            }

            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.sakensStartdato = dagen
                avgrenser.dekrementerfom = dagen.minusYears(HISTORISK_PERIODE_I_ÅR)
                avgrenser.teller.inkrementer(dagen)
                avgrenser.sisteBetalteDag = dagen
                avgrenser.state(Syk)
            }
        }

        object Syk : State {
            override fun entering(avgrenser: MaksimumSykepengedagerfilter) {
                avgrenser.opphold = 0
            }

            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.teller.inkrementer(dagen)
                avgrenser.sisteBetalteDag = dagen
                avgrenser.nextState(dagen)?.run { avgrenser.state(this) }
            }

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.state(avgrenser.nextState(dagen) ?: Opphold)
            }

            override fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.opphold += 1
            }
        }

        object Opphold : State {

            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.teller.inkrementer(dagen)
                avgrenser.sisteBetalteDag = dagen
                avgrenser.dekrementer(dagen)
                avgrenser.state(avgrenser.nextState(dagen) ?: Syk)
            }

            override fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.opphold += 1
                oppholdsdag(avgrenser, dagen)
            }

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.nextState(dagen)?.run { avgrenser.state(this) }
            }
        }

        object Karantene : State {
            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.opphold += 1
                avgrenser.avvisDag(dagen, when (avgrenser.opphold > TILSTREKKELIG_OPPHOLD_I_SYKEDAGER) {
                    true -> Begrunnelse.NyVilkårsprøvingNødvendig
                    false -> avgrenser.teller.begrunnelse(dagen)
                })
                avgrenser.teller.hvisGrensenErNådd(
                    hvis67År = { maksdato ->
                        avgrenser.teller.`§8-51 ledd 3`(maksdato)
                    }
                )
            }

            override fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.opphold += 1
                /* helg skal ikke medføre ny rettighet */
            }

            override fun oppholdsdag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.nextState(dagen)?.run { avgrenser.state(this) }
            }

            override fun fridag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.opphold += 1
            }
        }

        object ForGammel : State {
            override fun betalbarDag(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                over70(avgrenser, dagen)
            }

            override fun sykdomshelg(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                over70(avgrenser, dagen)
            }

            override fun leaving(avgrenser: MaksimumSykepengedagerfilter) = throw IllegalStateException("Kan ikke gå ut fra state ForGammel")

            private fun over70(avgrenser: MaksimumSykepengedagerfilter, dagen: LocalDate) {
                avgrenser.avvisDag(dagen, Begrunnelse.Over70)
            }
        }
    }
}
