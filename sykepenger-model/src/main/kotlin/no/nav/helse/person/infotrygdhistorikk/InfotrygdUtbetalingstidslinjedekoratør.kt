package no.nav.helse.person.infotrygdhistorikk

import no.nav.helse.hendelser.Periode
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverperiodeMediator
import no.nav.helse.utbetalingstidslinje.Begrunnelse
import no.nav.helse.økonomi.Økonomi
import java.time.LocalDate

internal class InfotrygdUtbetalingstidslinjedekoratør(
    private val other: ArbeidsgiverperiodeMediator,
    private val førsteDag: LocalDate,
    private val betalteDager: List<Periode>
) : ArbeidsgiverperiodeMediator by(other) {
    override fun fridag(dato: LocalDate) {
        if (dato < førsteDag) return
        other.fridag(dato)
    }

    override fun arbeidsdag(dato: LocalDate) {
        if (dato < førsteDag) return
        other.arbeidsdag(dato)
    }

    override fun arbeidsgiverperiodedag(dato: LocalDate, økonomi: Økonomi) {
        if (dato < førsteDag) return
        other.arbeidsgiverperiodedag(dato, økonomi)
    }

    override fun utbetalingsdag(dato: LocalDate, økonomi: Økonomi) {
        if (dato < førsteDag || betalteDager.any { dato in it }) return
        other.utbetalingsdag(dato, økonomi)
    }

    override fun foreldetDag(dato: LocalDate, økonomi: Økonomi) {
        if (dato < førsteDag) return
        other.foreldetDag(dato, økonomi)
    }

    override fun avvistDag(dato: LocalDate, begrunnelse: Begrunnelse) {
        if (dato < førsteDag) return
        other.avvistDag(dato, begrunnelse)
    }
}
