package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioder
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.etterlevelse.SubsumsjonObserver
import no.nav.helse.person.etterlevelse.SubsumsjonObserver.Companion.subsumsjonsformat
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Companion.avvis
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Companion.avvisteDager
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje.Companion.periode
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Økonomi.Companion.totalSykdomsgrad

internal class Sykdomsgradfilter(
    private val tidslinjer: List<Utbetalingstidslinje>,
    private val periode: Periode,
    private val aktivitetslogg: IAktivitetslogg,
    private val subsumsjonObserver: SubsumsjonObserver
) {

    internal fun filter(): List<Utbetalingstidslinje> {
        val tidslinjerForSubsumsjon = tidslinjer.subsumsjonsformat()
        val dagerUnderGrensen = periode(tidslinjer).filter { dato -> totalSykdomsgrad(tidslinjer.map { it[dato].økonomi }).erUnderGrensen() }
        Prosentdel.subsumsjon(subsumsjonObserver) { grense ->
            `§ 8-13 ledd 2`(periode, tidslinjerForSubsumsjon, grense, dagerUnderGrensen)
        }
        val result = avvis(tidslinjer, dagerUnderGrensen.grupperSammenhengendePerioder(), listOf(Begrunnelse.MinimumSykdomsgrad))
        val avvisteDager = avvisteDager(result, periode, Begrunnelse.MinimumSykdomsgrad)
        val harAvvisteDager = avvisteDager.isNotEmpty()
        subsumsjonObserver.`§ 8-13 ledd 1`(periode, avvisteDager.map { it.dato }, tidslinjerForSubsumsjon)
        if (harAvvisteDager)
            aktivitetslogg.warn("Minst én dag uten utbetaling på grunn av sykdomsgrad under 20 %%. Vurder å sende vedtaksbrev fra Infotrygd")
        else
            aktivitetslogg.info("Ingen avviste dager på grunn av 20 %% samlet sykdomsgrad-regel for denne perioden")
        return result
    }
}
