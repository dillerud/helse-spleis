package no.nav.helse.spleis.e2e


import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.helse.desember
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Arbeidsforholdhistorikk
import no.nav.helse.person.Arbeidsgiver
import no.nav.helse.person.IdInnhenter
import no.nav.helse.person.OppdragVisitor
import no.nav.helse.person.PersonVisitor
import no.nav.helse.person.TilstandType
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.serde.api.serializePersonForSpeil
import no.nav.helse.serde.api.v2.HendelseDTO
import no.nav.helse.serde.api.v2.InntektsmeldingDTO
import no.nav.helse.serde.api.v2.SykmeldingDTO
import no.nav.helse.serde.api.v2.SøknadNavDTO
import no.nav.helse.utbetalingslinjer.Endringskode
import no.nav.helse.utbetalingslinjer.Klassekode
import no.nav.helse.utbetalingslinjer.Oppdrag
import no.nav.helse.utbetalingslinjer.Satstype
import no.nav.helse.utbetalingslinjer.Utbetalingslinje
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent

internal class EtterspurtBehov(
    private val type: Aktivitetslogg.Aktivitet.Behov.Behovtype,
    private val tilstand: TilstandType,
    private val orgnummer: String,
    private val vedtaksperiodeId: UUID
) {
    companion object {
        internal fun fjern(liste: MutableList<EtterspurtBehov>, orgnummer: String, type: Aktivitetslogg.Aktivitet.Behov.Behovtype) {
            liste.removeIf { it.orgnummer == orgnummer && it.type == type }
        }

        internal fun finnEtterspurteBehov(behovsliste: List<Aktivitetslogg.Aktivitet.Behov>) =
            behovsliste
                .filter { "tilstand" in it.kontekst() }
                .filter { "organisasjonsnummer" in it.kontekst() }
                .filter { "vedtaksperiodeId" in it.kontekst() }
                .map {
                    EtterspurtBehov(
                        type = it.type,
                        tilstand = enumValueOf(it.kontekst()["tilstand"] as String),
                        orgnummer = (it.kontekst()["organisasjonsnummer"] as String),
                        vedtaksperiodeId = UUID.fromString(it.kontekst()["vedtaksperiodeId"] as String)
                    )
                }

        internal fun finnEtterspurtBehov(
            ikkeBesvarteBehov: MutableList<EtterspurtBehov>,
            type: Aktivitetslogg.Aktivitet.Behov.Behovtype,
            vedtaksperiodeIdInnhenter: IdInnhenter,
            orgnummer: String
        ) =
            ikkeBesvarteBehov.firstOrNull { it.type == type && it.orgnummer == orgnummer && it.vedtaksperiodeId == vedtaksperiodeIdInnhenter.id(orgnummer) }

        internal fun finnEtterspurtBehov(
            ikkeBesvarteBehov: MutableList<EtterspurtBehov>,
            type: Aktivitetslogg.Aktivitet.Behov.Behovtype,
            vedtaksperiodeIdInnhenter: IdInnhenter,
            orgnummer: String,
            tilstand: TilstandType
        ) =
            ikkeBesvarteBehov.firstOrNull {
                it.type == type && it.orgnummer == orgnummer && it.vedtaksperiodeId == vedtaksperiodeIdInnhenter.id(orgnummer) && it.tilstand == tilstand
            }
    }

    override fun toString() = "$type ($tilstand)"
}

internal fun AbstractEndToEndTest.tellArbeidsforholdhistorikkinnslag(orgnummer: String? = null): MutableList<UUID> {
    val arbeidsforholdIder = mutableListOf<UUID>()
    var erIRiktigArbeidsgiver = true
    person.accept(object : PersonVisitor {

        override fun preVisitArbeidsgiver(arbeidsgiver: Arbeidsgiver, id: UUID, organisasjonsnummer: String) {
            erIRiktigArbeidsgiver = orgnummer == null || orgnummer == organisasjonsnummer
        }

        override fun preVisitArbeidsforholdinnslag(arbeidsforholdinnslag: Arbeidsforholdhistorikk.Innslag, id: UUID, skjæringstidspunkt: LocalDate) {
            if (erIRiktigArbeidsgiver) {
                arbeidsforholdIder.add(id)
            }
        }
    })

    return arbeidsforholdIder
}

internal fun AbstractEndToEndTest.tellArbeidsforholdINyesteHistorikkInnslag(orgnummer: String): Int {
    var antall = 0
    var erIRiktigArbeidsgiver = true
    var erIFørsteHistorikkinnslag = true

    person.accept(object : PersonVisitor {

        override fun preVisitArbeidsgiver(arbeidsgiver: Arbeidsgiver, id: UUID, organisasjonsnummer: String) {
            erIRiktigArbeidsgiver = orgnummer == organisasjonsnummer
        }

        override fun visitArbeidsforhold(ansattFom: LocalDate, ansattTom: LocalDate?, deaktivert: Boolean) {
            if (erIRiktigArbeidsgiver && erIFørsteHistorikkinnslag) antall += 1
        }

        override fun postVisitArbeidsforholdinnslag(arbeidsforholdinnslag: Arbeidsforholdhistorikk.Innslag, id: UUID, skjæringstidspunkt: LocalDate) {
            if (erIRiktigArbeidsgiver) erIFørsteHistorikkinnslag = false
        }
    })

    return antall
}

internal fun AbstractEndToEndTest.historikk(orgnummer: String, sykedagstelling: Int = 0) {
    person.håndter(
        ytelser(
            1.vedtaksperiode,
            utbetalinger = utbetalinger(sykedagstelling, orgnummer),
            orgnummer = orgnummer
        )
    )
}

private fun utbetalinger(dagTeller: Int, orgnummer: String): List<ArbeidsgiverUtbetalingsperiode> {
    if (dagTeller == 0) return emptyList()
    val førsteDato = 2.desember(2017).minusDays(
        (
            (dagTeller / 5 * 7) + dagTeller % 5
            ).toLong()
    )
    return listOf(ArbeidsgiverUtbetalingsperiode(orgnummer, førsteDato, 1.desember(2017), 100.prosent, 100.daglig))
}

internal fun AbstractEndToEndTest.finnSkjæringstidspunkt(orgnummer: String, vedtaksperiodeIdInnhenter: IdInnhenter) =
    inspektør(orgnummer).skjæringstidspunkt(vedtaksperiodeIdInnhenter)

internal fun AbstractEndToEndTest.speilApi(hendelser: List<HendelseDTO> = søknadDTOer + sykmeldingDTOer + inntektsmeldingDTOer) = serializePersonForSpeil(person, hendelser)

internal val AbstractEndToEndTest.søknadDTOer get() = søknader.map { (id, triple) ->
    val søknadsperiode = Søknadsperiode.søknadsperiode(triple.third.toList())!!
    SøknadNavDTO(
        id = id.toString(),
        fom = søknadsperiode.first(),
        tom = søknadsperiode.last(),
        rapportertdato = triple.first.atStartOfDay(),
        sendtNav = triple.first.atStartOfDay()
    )
}

private val AbstractEndToEndTest.sykmeldingDTOer get() = sykmeldinger.map { (id, perioder) ->
    val sykmeldingsperiode = Sykmeldingsperiode.periode(perioder.toList())!!
    SykmeldingDTO(
        id = id.toString(),
        fom = sykmeldingsperiode.first(),
        tom = sykmeldingsperiode.last(),
        rapportertdato = sykmeldingsperiode.last().atStartOfDay()
    )
}

private val AbstractEndToEndTest.inntektsmeldingDTOer get() = inntektsmeldinger.map { (id, inntektsmeldingGetter) ->
    val im = inntektsmeldingGetter()
    InntektsmeldingDTO(
        id = id.toString(),
        mottattDato = LocalDateTime.now(),
        beregnetInntekt = inntekter.getValue(id).reflection { årlig, _, _, _ -> årlig }
    )
}

internal fun Oppdrag.first() = linjer().first()
internal fun Oppdrag.last() = linjer().last()
internal fun Oppdrag.single() = linjer().single()
internal operator fun Oppdrag.get(index: Int) = linjer().get(index)
internal val Oppdrag.size get() = linjer().size
internal fun Oppdrag.isEmpty() = linjer().isEmpty()
internal fun Oppdrag.isNotEmpty() = linjer().isNotEmpty()
internal fun Oppdrag.forEach(callback: (Utbetalingslinje) -> Unit) = linjer().forEach(callback)
internal fun Oppdrag.zip(other: Oppdrag) = linjer().zip(other.linjer())
internal fun Oppdrag.sumOf(callback: (Utbetalingslinje) -> Int) = linjer().sumOf(callback)
internal fun <R> Oppdrag.zipWithNext(callback: (a: Utbetalingslinje, b: Utbetalingslinje) -> R) = linjer().zipWithNext(callback)

internal fun Oppdrag.linjer(): List<Utbetalingslinje> {
    val linjer = mutableListOf<Utbetalingslinje>()
    accept(object : OppdragVisitor {
        override fun visitUtbetalingslinje(
            linje: Utbetalingslinje,
            fom: LocalDate,
            tom: LocalDate,
            stønadsdager: Int,
            totalbeløp: Int,
            satstype: Satstype,
            beløp: Int?,
            aktuellDagsinntekt: Int?,
            grad: Int?,
            delytelseId: Int,
            refDelytelseId: Int?,
            refFagsystemId: String?,
            endringskode: Endringskode,
            datoStatusFom: LocalDate?,
            statuskode: String?,
            klassekode: Klassekode
        ) {
            linjer.add(linje)
        }
    })
    return linjer
}
