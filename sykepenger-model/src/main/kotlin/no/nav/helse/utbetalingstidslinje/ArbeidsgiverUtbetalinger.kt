package no.nav.helse.utbetalingstidslinje

import java.time.LocalDate
import no.nav.helse.hendelser.Periode
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.person.VilkårsgrunnlagHistorikk
import no.nav.helse.person.etterlevelse.SubsumsjonObserver
import no.nav.helse.person.infotrygdhistorikk.Infotrygdhistorikk

internal class ArbeidsgiverUtbetalinger(
    private val regler: ArbeidsgiverRegler,
    private val arbeidsgivere: Map<Arbeidsgiver, IUtbetalingstidslinjeBuilder>,
    private val infotrygdhistorikk: Infotrygdhistorikk,
    private val alder: Alder,
    private val dødsdato: LocalDate?,
    private val vilkårsgrunnlagHistorikk: VilkårsgrunnlagHistorikk,
    private val subsumsjonObserver: SubsumsjonObserver
) {
    internal lateinit var maksimumSykepenger: Alder.MaksimumSykepenger

    internal fun beregn(
        aktivitetslogg: IAktivitetslogg,
        organisasjonsnummer: String,
        periode: Periode,
        virkningsdato: LocalDate = periode.endInclusive
    ) {
        val tidslinjer = arbeidsgivere
            .mapValues { (arbeidsgiver, builder) -> arbeidsgiver.build(subsumsjonObserver, infotrygdhistorikk, builder, periode) }
            .filterValues { it.utbetalingsdager.isNotEmpty() }
        filtrer(aktivitetslogg, tidslinjer, periode, virkningsdato)
        tidslinjer.forEach { (arbeidsgiver, utbetalingstidslinje) ->
            arbeidsgiver.lagreUtbetalingstidslinjeberegning(organisasjonsnummer, utbetalingstidslinje, vilkårsgrunnlagHistorikk)
        }
    }

    private fun filtrer(
        aktivitetslogg: IAktivitetslogg,
        arbeidsgivere: Map<Arbeidsgiver, Utbetalingstidslinje>,
        periode: Periode,
        virkningsdato: LocalDate,
    ) {
        val tidslinjer = arbeidsgivere.values.toList()
        Sykdomsgradfilter(tidslinjer, periode, aktivitetslogg, subsumsjonObserver).filter()
        AvvisDagerEtterDødsdatofilter(tidslinjer, periode, dødsdato, aktivitetslogg).filter()
        vilkårsgrunnlagHistorikk.avvisInngangsvilkår(tidslinjer, alder)
        maksimumSykepenger = MaksimumSykepengedagerfilter(alder, regler, periode, aktivitetslogg, subsumsjonObserver).filter(
            tidslinjer,
            infotrygdhistorikk.utbetalingstidslinje().kutt(periode.endInclusive)
        )
        arbeidsgivere.forEach { (arbeidsgiver, tidslinje) ->
            Refusjonsgjødsler(
                tidslinje = tidslinje + arbeidsgiver.utbetalingstidslinje(infotrygdhistorikk),
                refusjonshistorikk = arbeidsgiver.refusjonshistorikk,
                infotrygdhistorikk = infotrygdhistorikk,
                organisasjonsnummer = arbeidsgiver.organisasjonsnummer()
            ).gjødsle(aktivitetslogg, periode)
        }
        MaksimumUtbetaling(tidslinjer, aktivitetslogg, virkningsdato).betal()
    }
}
