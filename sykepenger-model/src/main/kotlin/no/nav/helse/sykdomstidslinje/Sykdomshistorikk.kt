package no.nav.helse.sykdomstidslinje

import no.nav.helse.person.SykdomshistorikkVisitor
import no.nav.helse.tournament.historiskDagturnering
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class Sykdomshistorikk private constructor(
    private val elementer: MutableList<Element>
) {
    internal constructor() : this(mutableListOf())

    internal val size get() = elementer.size
    internal fun isEmpty() = elementer.isEmpty()

    internal fun sykdomstidslinje() = Element.sykdomstidslinje(elementer)

    internal fun håndter(hendelse: SykdomstidslinjeHendelse) {
        elementer.add(
            0, Element.opprett(
                this, hendelse,
                if (elementer.isEmpty()) LocalDate.MAX else sykdomstidslinje().sisteDag()
            )
        )
    }

    internal fun accept(visitor: SykdomshistorikkVisitor) {
        visitor.preVisitSykdomshistorikk(this)
        elementer.forEach { it.accept(visitor) }
        visitor.postVisitSykdomshistorikk(this)
    }

    private fun kalkulerBeregnetSykdomstidslinje(
        hendelse: SykdomstidslinjeHendelse,
        hendelseSykdomstidslinje: ConcreteSykdomstidslinje
    ): ConcreteSykdomstidslinje {
        val tidslinje = if (elementer.isEmpty())
            hendelseSykdomstidslinje
        else
            sykdomstidslinje().merge(hendelseSykdomstidslinje, historiskDagturnering)
        return tidslinje.also { it.valider(hendelse) }
    }

    internal class Element private constructor(
        private val hendelseId: UUID,
        private val tidsstempel: LocalDateTime,
        private val hendelseSykdomstidslinje: ConcreteSykdomstidslinje,
        private val beregnetSykdomstidslinje: ConcreteSykdomstidslinje
    ) {
        fun accept(visitor: SykdomshistorikkVisitor) {
            visitor.preVisitSykdomshistorikkElement(this, hendelseId, tidsstempel)
            visitor.preVisitHendelseSykdomstidslinje()
            hendelseSykdomstidslinje.accept(visitor)
            visitor.postVisitHendelseSykdomstidslinje()
            visitor.preVisitBeregnetSykdomstidslinje()
            beregnetSykdomstidslinje.accept(visitor)
            visitor.postVisitBeregnetSykdomstidslinje()
            visitor.postVisitSykdomshistorikkElement(this, hendelseId, tidsstempel)
        }

        companion object {
            fun sykdomstidslinje(elementer: List<Element>) = elementer.first().beregnetSykdomstidslinje

            fun opprett(
                historikk: Sykdomshistorikk,
                hendelse: SykdomstidslinjeHendelse,
                tom: LocalDate
            ): Element {
                if (!historikk.isEmpty()) hendelse.old_padLeft(historikk.sykdomstidslinje().førsteDag())
                val hendelseSykdomstidslinje = hendelse.sykdomstidslinje(tom)
                return Element(
                    hendelseId = hendelse.meldingsreferanseId(),
                    tidsstempel = LocalDateTime.now(),
                    hendelseSykdomstidslinje = hendelseSykdomstidslinje,
                    beregnetSykdomstidslinje = historikk.kalkulerBeregnetSykdomstidslinje(
                        hendelse,
                        hendelseSykdomstidslinje
                    )
                )
            }
        }
    }
}
