package no.nav.helse.hendelser

import no.nav.helse.FeatureToggle
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.sykdomstidslinje.ConcreteSykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.sykdomstidslinje.dag.*
import no.nav.helse.sykdomstidslinje.merge
import no.nav.helse.tournament.søknadDagturnering
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class Søknad constructor(
    meldingsreferanseId: UUID,
    private val fnr: String,
    private val aktørId: String,
    private val orgnummer: String,
    private val perioder: List<Periode>,
    private val harAndreInntektskilder: Boolean,
    private val sendtTilNAV: LocalDateTime?
) : SykdomstidslinjeHendelse(meldingsreferanseId) {

    private val fom: LocalDate
    private val tom: LocalDate
    private var forrigeTom: LocalDate? = null

    init {
        if (perioder.isEmpty()) severe("Søknad må inneholde perioder")
        perioder.filterIsInstance<Periode.Sykdom>()
            .also { fom = it.minBy { it.fom }?.fom ?: severe("Søknad mangler fradato") }
            .also { tom = it.maxBy { it.tom }?.tom ?: severe("Søknad mangler tildato") }
    }

    override fun sykdomstidslinje() = perioder.map(Periode::sykdomstidslinje).merge(søknadDagturnering)
    override fun sykdomstidslinje(tom: LocalDate): ConcreteSykdomstidslinje {
        require(forrigeTom == null || (forrigeTom != null && tom > forrigeTom)) { "Kalte metoden flere ganger med samme eller en tidligere dato" }

        return sykdomstidslinje().subset(forrigeTom, tom)
            .also { trimLeft(tom) }
            ?: severe("Ugydlig subsetting av tidslinjen til søknad")
    }

    internal fun trimLeft(dato: LocalDate) { forrigeTom = dato }

    override fun fødselsnummer() = fnr

    override fun organisasjonsnummer() = orgnummer

    override fun aktørId() = aktørId

    override fun valider(): Aktivitetslogg {
        perioder.forEach { it.valider(this) }
        if ( harAndreInntektskilder ) error("Søknaden inneholder andre inntektskilder")
        if (sendtTilNAV?.toLocalDate()?.isAfter(tom.plusMonths(3)) == true) error("Søknaden er sendt inn 3 måneder etter TOM")
        return aktivitetslogg
    }

    override fun fortsettÅBehandle(arbeidsgiver: Arbeidsgiver) {
        arbeidsgiver.håndter(this)
    }

    override fun melding(klassName: String) = "Søknad"

    sealed class Periode(internal val fom: LocalDate, internal val tom: LocalDate) {

        internal abstract fun sykdomstidslinje(): ConcreteSykdomstidslinje

        internal open fun valider(søknad: Søknad) {}

        internal fun valider(søknad: Søknad, beskjed: String) {
            if (fom < søknad.fom || tom > søknad.tom) søknad.error(beskjed)
        }

        class Ferie(fom: LocalDate, tom: LocalDate) : Periode(fom, tom) {
            override fun sykdomstidslinje() =
                ConcreteSykdomstidslinje.ferie(fom, tom, SøknadDagFactory)

            override fun valider(søknad: Søknad) =
                valider(søknad, "Søknaden inneholder Feriedager utenfor sykdomsvindu")
        }

        class Sykdom(
            fom: LocalDate,
            tom: LocalDate,
            private val grad: Int,
            private val faktiskGrad: Double = grad.toDouble()
        ) : Periode(fom, tom) {
            override fun sykdomstidslinje() =
                ConcreteSykdomstidslinje.sykedager(fom, tom, faktiskGrad, SøknadDagFactory)

            override fun valider(søknad: Søknad) {
                if (grad != 100 && (!FeatureToggle.støtterGradertSykdom)) søknad.error("Søknaden inneholder gradert sykdomsperiode")
                if (faktiskGrad != grad.toDouble() && (!FeatureToggle.støtterGradertSykdom)) søknad.error("Søker oppgir gradert sykdomsperiode")
            }
        }

        class Utdanning(fom: LocalDate, tom: LocalDate) : Periode(fom, tom) {
            override fun sykdomstidslinje() =
                ConcreteSykdomstidslinje.studiedager(fom, tom, SøknadDagFactory)

            override fun valider(søknad: Søknad) =
                søknad.error("Søknaden inneholder en Utdanningsperiode")
        }

        class Permisjon(fom: LocalDate, tom: LocalDate) : Periode(fom, tom) {
            override fun sykdomstidslinje() =
                ConcreteSykdomstidslinje.permisjonsdager(fom, tom, SøknadDagFactory)

            override fun valider(søknad: Søknad) =
                søknad.error("Søknaden inneholder en Permisjonsperiode")
        }

        class Egenmelding(fom: LocalDate, tom: LocalDate) : Periode(fom, tom) {
            override fun sykdomstidslinje() =
                ConcreteSykdomstidslinje.egenmeldingsdager(fom, tom, SøknadDagFactory)
        }

        class Arbeid(fom: LocalDate, tom: LocalDate) : Periode(fom, tom) {
            override fun sykdomstidslinje() =
                ConcreteSykdomstidslinje.ikkeSykedager(fom, tom, SøknadDagFactory)

            override fun valider(søknad: Søknad) =
                valider(søknad, "Søknaden inneholder Arbeidsdager utenfor sykdomsvindu")
        }
    }

    internal object SøknadDagFactory : DagFactory {
        override fun arbeidsdag(dato: LocalDate): Arbeidsdag = Arbeidsdag.Søknad(dato)
        override fun egenmeldingsdag(dato: LocalDate): Egenmeldingsdag = Egenmeldingsdag.Søknad(dato)
        override fun feriedag(dato: LocalDate): Feriedag = Feriedag.Søknad(dato)
        override fun permisjonsdag(dato: LocalDate): Permisjonsdag = Permisjonsdag.Søknad(dato)
        override fun studiedag(dato: LocalDate): Studiedag = Studiedag(dato)
        override fun sykedag(dato: LocalDate, grad: Double): Sykedag.Søknad = Sykedag.Søknad(dato, grad)
        override fun kunArbeidsgiverSykedag(dato: LocalDate, grad: Double): KunArbeidsgiverSykedag = KunArbeidsgiverSykedag(dato, grad)
        override fun sykHelgedag(dato: LocalDate, grad: Double): SykHelgedag.Søknad = SykHelgedag.Søknad(dato, grad)
        override fun utenlandsdag(dato: LocalDate): Utenlandsdag = Utenlandsdag(dato)

    }
}
