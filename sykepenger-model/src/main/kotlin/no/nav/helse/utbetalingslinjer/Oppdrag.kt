package no.nav.helse.utbetalingslinjer

import java.time.LocalDate
import java.time.LocalDate.MIN
import java.time.LocalDateTime
import java.util.*
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Simulering
import no.nav.helse.hendelser.utbetaling.UtbetalingHendelse
import no.nav.helse.hendelser.utbetaling.UtbetalingOverført
import no.nav.helse.person.*
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.utbetalingslinjer.Oppdragstatus.AVVIST
import no.nav.helse.utbetalingslinjer.Oppdragstatus.FEIL
import no.nav.helse.utbetalingstidslinje.genererUtbetalingsreferanse

internal const val WARN_FORLENGER_OPPHØRT_OPPDRAG = "Utbetalingen forlenger et tidligere oppdrag som opphørte alle utbetalte dager. Sjekk simuleringen."
internal const val WARN_OPPDRAG_FOM_ENDRET = "Utbetaling fra og med dato er endret. Kontroller simuleringen"

internal class Oppdrag private constructor(
    private val mottaker: String,
    private val fagområde: Fagområde,
    private val linjer: MutableList<Utbetalingslinje>,
    private var fagsystemId: String,
    private var endringskode: Endringskode,
    private val sisteArbeidsgiverdag: LocalDate?,
    private var nettoBeløp: Int = linjer.sumOf { it.totalbeløp() },
    private var overføringstidspunkt: LocalDateTime? = null,
    private var avstemmingsnøkkel: Long? = null,
    private var status: Oppdragstatus? = null,
    private val tidsstempel: LocalDateTime,
    private var erSimulert: Boolean = false,
    private var simuleringsResultat: Simulering.SimuleringResultat? = null
) : Aktivitetskontekst {
    internal companion object {
        internal fun periode(vararg oppdrag: Oppdrag): Periode? {
            return oppdrag
                .filter { it.linjer.isNotEmpty() }
                .takeIf(List<*>::isNotEmpty)
                ?.let { liste -> Periode(liste.minOf { it.førstedato }, liste.maxOf { it.sistedato }) }
        }

        internal fun stønadsdager(vararg oppdrag: Oppdrag): Int {
            return Utbetalingslinje.stønadsdager(oppdrag.toList().flatMap { it.linjer })
        }

        internal fun synkronisert(vararg oppdrag: Oppdrag): Boolean {
            val endrede = oppdrag.filter { it.harUtbetalinger() }
            return endrede.all { it.status == endrede.first().status }
        }

        internal fun ingenFeil(vararg oppdrag: Oppdrag) = oppdrag.none { it.status in listOf(AVVIST, FEIL) }
        internal fun kanIkkeForsøkesPåNy(vararg oppdrag: Oppdrag) = oppdrag.any { it.status == AVVIST }
    }

    internal val førstedato get() = linjer.firstOrNull()?.let { it.datoStatusFom() ?: it.fom } ?: MIN
    internal val sistedato get() = linjer.lastOrNull()?.tom ?: MIN

    internal constructor(
        mottaker: String,
        fagområde: Fagområde,
        linjer: List<Utbetalingslinje> = listOf(),
        fagsystemId: String = genererUtbetalingsreferanse(UUID.randomUUID()),
        sisteArbeidsgiverdag: LocalDate? = null
    ) : this(
        mottaker,
        fagområde,
        linjer.toMutableList(),
        fagsystemId,
        Endringskode.NY,
        sisteArbeidsgiverdag,
        tidsstempel = LocalDateTime.now()
    )

    internal fun accept(visitor: OppdragVisitor) {
        visitor.preVisitOppdrag(
            this,
            fagområde,
            fagsystemId,
            mottaker,
            førstedato,
            sistedato,
            sisteArbeidsgiverdag,
            stønadsdager(),
            totalbeløp(),
            nettoBeløp,
            tidsstempel,
            endringskode,
            avstemmingsnøkkel,
            status,
            overføringstidspunkt,
            erSimulert,
            simuleringsResultat
        )
        linjer.forEach { it.accept(visitor) }
        visitor.postVisitOppdrag(
            this,
            fagområde,
            fagsystemId,
            mottaker,
            førstedato,
            sistedato,
            sisteArbeidsgiverdag,
            stønadsdager(),
            totalbeløp(),
            nettoBeløp,
            tidsstempel,
            endringskode,
            avstemmingsnøkkel,
            status,
            overføringstidspunkt,
            erSimulert,
            simuleringsResultat
        )
    }

    internal fun fagsystemId() = fagsystemId

    private operator fun contains(other: Oppdrag) = this.tilhører(other) || this.overlapperMed(other) || sammenhengendeUtbetalingsperiode(other)

    internal fun tilhører(other: Oppdrag) = this.fagsystemId == other.fagsystemId && this.fagområde == other.fagområde
    private fun overlapperMed(other: Oppdrag) = !erTomt() && maxOf(this.førstedato, other.førstedato) <= minOf(this.sistedato, other.sistedato)

    private fun sammenhengendeUtbetalingsperiode(other: Oppdrag) =
        this.sisteArbeidsgiverdag != null && this.sisteArbeidsgiverdag == other.sisteArbeidsgiverdag

    internal fun overfør(
        aktivitetslogg: IAktivitetslogg,
        maksdato: LocalDate?,
        saksbehandler: String
    ) {
        if (!harUtbetalinger()) return aktivitetslogg.info("Overfører ikke oppdrag uten endring for fagområde=$fagområde med fagsystemId=$fagsystemId")
        check(endringskode != Endringskode.UEND)
        check(status != Oppdragstatus.AKSEPTERT)
        aktivitetslogg.kontekst(this)
        aktivitetslogg.behov(Behovtype.Utbetaling, "Trenger å sende utbetaling til Oppdrag", behovdetaljer(saksbehandler, maksdato))
    }

    internal fun simuler(aktivitetslogg: IAktivitetslogg, maksdato: LocalDate, saksbehandler: String) {
        if (!harUtbetalinger()) return aktivitetslogg.info("Simulerer ikke oppdrag uten endring fagområde=$fagområde med fagsystemId=$fagsystemId")
        check(endringskode != Endringskode.UEND)
        check(status == null)
        aktivitetslogg.kontekst(this)
        aktivitetslogg.behov(Behovtype.Simulering, "Trenger simulering fra Oppdragssystemet", behovdetaljer(saksbehandler, maksdato))
    }

    override fun toSpesifikkKontekst() = SpesifikkKontekst("Oppdrag", mapOf("fagsystemId" to fagsystemId))

    private fun behovdetaljer(saksbehandler: String, maksdato: LocalDate?): MutableMap<String, Any> {
        return mutableMapOf(
            "mottaker" to mottaker,
            "fagområde" to "$fagområde",
            "linjer" to kopierKunLinjerMedEndring().linjer.map(Utbetalingslinje::toHendelseMap),
            "fagsystemId" to fagsystemId,
            "endringskode" to "$endringskode",
            "saksbehandler" to saksbehandler
        ).apply {
            maksdato?.let {
                put("maksdato", maksdato.toString())
            }
        }
    }

    internal fun totalbeløp() = linjerUtenOpphør().sumOf { it.totalbeløp() }
    internal fun stønadsdager() = linjer.sumOf { it.stønadsdager() }

    internal fun nettoBeløp() = nettoBeløp

    private fun nettoBeløp(tidligere: Oppdrag) {
        nettoBeløp = this.totalbeløp() - tidligere.totalbeløp()
    }

    internal fun harUtbetalinger() = linjer.any(Utbetalingslinje::erForskjell)

    internal fun erRelevant(fagsystemId: String, fagområde: Fagområde) =
        this.fagsystemId == fagsystemId && this.fagområde == fagområde

    internal fun valider(simulering: Simulering) =
        simulering.valider(this)

    private fun kopierKunLinjerMedEndring() = kopierMed(linjer.filter(Utbetalingslinje::erForskjell))

    private fun kopierUtenOpphørslinjer() = kopierMed(linjerUtenOpphør())

    internal fun linjerUtenOpphør() = linjer.filter { !it.erOpphør() }

    internal fun annuller(aktivitetslogg: IAktivitetslogg): Oppdrag {
        return tomtOppdrag().minus(this, aktivitetslogg)
    }

    private fun tomtOppdrag(): Oppdrag =
        Oppdrag(
            mottaker = mottaker,
            fagområde = fagområde,
            fagsystemId = fagsystemId,
            sisteArbeidsgiverdag = sisteArbeidsgiverdag
        )

    internal fun minus(eldre: Oppdrag, aktivitetslogg: IAktivitetslogg): Oppdrag {
        // Vi ønsker ikke å forlenge et oppdrag vi ikke overlapper med, eller et tomt oppdrag
        if (harIngenKoblingTilTidligereOppdrag(eldre)) return this
        // overtar fagsystemId fra tidligere Oppdrag uten utbetaling, gitt at det er samme arbeidsgiverperiode
        if (eldre.erTomt()) return this.also { this.fagsystemId = eldre.fagsystemId }
        return when {
            // om man trekker fra et utbetalt oppdrag med et tomt oppdrag medfører det et oppdrag som opphører (les: annullerer) hele fagsystemIDen
            erTomt() -> annulleringsoppdrag(eldre)
            // "fom" kan flytte seg fremover i tid dersom man, eksempelvis, revurderer en utbetalt periode til å starte med ikke-utbetalte dager (f.eks. ferie)
            eldre.ingenUtbetalteDager() -> {
                aktivitetslogg.warn(WARN_FORLENGER_OPPHØRT_OPPDRAG)
                kjørFrem(eldre)
            }
            fomHarFlyttetSegFremover(eldre.kopierUtenOpphørslinjer()) -> {
                aktivitetslogg.warn("Utbetaling opphører tidligere utbetaling. Kontroller simuleringen")
                returførOgKjørFrem(eldre.kopierUtenOpphørslinjer())
            }
            // utbetaling kan endres til å starte tidligere, eksempelvis via revurdering der feriedager egentlig er sykedager
            fomHarFlyttetSegBakover(eldre.kopierUtenOpphørslinjer()) -> {
                aktivitetslogg.warn(WARN_OPPDRAG_FOM_ENDRET)
                kjørFrem(eldre.kopierUtenOpphørslinjer())
            }
            // fom er lik, men endring kan oppstå overalt ellers
            else -> endre(eldre.kopierUtenOpphørslinjer(), aktivitetslogg)
        }.also { it.nettoBeløp(eldre) }
    }

    private fun harIngenKoblingTilTidligereOppdrag(eldre: Oppdrag) = (eldre.erTomt() && !sammenhengendeUtbetalingsperiode(eldre)) || this !in eldre

    private fun ingenUtbetalteDager() = linjerUtenOpphør().isEmpty()

    internal fun erTomt() = linjer.isEmpty()

    // Vi har oppdaget utbetalingsdager tidligere i tidslinjen
    private fun fomHarFlyttetSegBakover(eldre: Oppdrag) = this.førstedato < eldre.førstedato

    // Vi har endret tidligere utbetalte dager til ikke-utbetalte dager i starten av tidslinjen
    private fun fomHarFlyttetSegFremover(eldre: Oppdrag) = this.førstedato > eldre.førstedato

    // man opphører (annullerer) et annet oppdrag ved å lage en opphørslinje som dekker hele perioden som er utbetalt
    private fun annulleringsoppdrag(tidligere: Oppdrag) = this.also { nåværende ->
        nåværende.kobleTil(tidligere)
        linjer.add(tidligere.linjer.last().opphørslinje(tidligere.linjer.first().fom))
    }

    // når man oppretter en NY linje med dato-intervall "(a, b)" vil oppdragsystemet
    // automatisk opphøre alle eventuelle linjer med fom > b.
    //
    // Eksempel:
    // Oppdrag 1: 5. januar til 31. januar (linje 1)
    // Oppdrag 2: 1. januar til 10. januar
    // Fordi linje "1. januar - 10. januar" opprettes som NY, medfører dette at oppdragsystemet opphører 11. januar til 31. januar automatisk
    private fun kjørFrem(tidligere: Oppdrag) = this.also { nåværende ->
        nåværende.kobleTil(tidligere)
        nåværende.linjer.first().kobleTil(tidligere.linjer.last())
        nåværende.linjer.zipWithNext { a, b -> b.kobleTil(a) }
    }
    private lateinit var tilstand: Tilstand
    private lateinit var sisteLinjeITidligereOppdrag: Utbetalingslinje

    private lateinit var linkTo: Utbetalingslinje

    // forsøker så langt det lar seg gjøre å endre _siste_ linje, dersom mulig *)
    // ellers lager den NY linjer fra og med linja før endringen oppstod
    // *) en linje kan endres dersom "tom"-dato eller grad er eneste forskjell
    //    ulik dagsats eller fom-dato medfører enten at linjen får status OPPH, eller at man overskriver
    //    ved å sende NY linjer
    private fun endre(avtroppendeOppdrag: Oppdrag, aktivitetslogg: IAktivitetslogg) =
        this.also { påtroppendeOppdrag ->
            this.linkTo = avtroppendeOppdrag.linjer.last()
            påtroppendeOppdrag.kobleTil(avtroppendeOppdrag)
            påtroppendeOppdrag.kopierLikeLinjer(avtroppendeOppdrag, aktivitetslogg)
            påtroppendeOppdrag.håndterLengreNåværende(avtroppendeOppdrag)
            if (!påtroppendeOppdrag.linjer.last().erForskjell()) påtroppendeOppdrag.endringskode = Endringskode.UEND
        }

    // når man oppretter en NY linje vil Oppdragsystemet IKKE ta stilling til periodene FØR.
    // Man må derfor eksplisitt opphøre evt. perioder tidligere, som i praksis vil medføre at
    // oppdraget kjøres tilbake, så fremover
    private fun returførOgKjørFrem(tidligere: Oppdrag) = this.also { nåværende ->
        val deletion = nåværende.opphørOppdrag(tidligere)
        nåværende.kjørFrem(tidligere)
        nåværende.linjer.add(0, deletion)
    }

    private fun opphørTidligereLinjeOgOpprettNy(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg) {
        linkTo = tidligere
        linjer.add(linjer.indexOf(nåværende), tidligere.opphørslinje(tidligere.fom))
        nåværende.kobleTil(linkTo)
        tilstand = Ny()
        aktivitetslogg.warn("Endrer tidligere oppdrag. Kontroller simuleringen.")
    }

    private fun opphørOppdrag(tidligere: Oppdrag) =
        tidligere.linjer.last().opphørslinje(tidligere.førstedato)

    private fun kopierMed(linjer: List<Utbetalingslinje>) = Oppdrag(
        mottaker,
        fagområde,
        linjer.toMutableList(),
        fagsystemId,
        endringskode,
        sisteArbeidsgiverdag,
        overføringstidspunkt = overføringstidspunkt,
        avstemmingsnøkkel = avstemmingsnøkkel,
        status = status,
        tidsstempel = tidsstempel
    )

    private fun kopierLikeLinjer(tidligere: Oppdrag, aktivitetslogg: IAktivitetslogg) {
        tilstand = if (tidligere.sistedato > this.sistedato) Slett() else Identisk()
        sisteLinjeITidligereOppdrag = tidligere.linjer.last()
        linjer.zip(tidligere.linjer).forEach { (a, b) -> tilstand.håndterForskjell(a, b, aktivitetslogg) }
    }

    private fun håndterLengreNåværende(tidligere: Oppdrag) {
        if (linjer.size <= tidligere.linjer.size) return
        linjer[tidligere.linjer.size].kobleTil(linkTo)
        linjer
            .subList(tidligere.linjer.size, linjer.size)
            .zipWithNext { a, b -> b.kobleTil(a) }
    }

    private fun kobleTil(tidligere: Oppdrag) {
        this.fagsystemId = tidligere.fagsystemId
        linjer.forEach { it.refFagsystemId = tidligere.fagsystemId }
        this.endringskode = Endringskode.ENDR
    }

    private fun håndterUlikhet(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg) {
        when {
            nåværende.kanEndreEksisterendeLinje(tidligere, sisteLinjeITidligereOppdrag) -> nåværende.endreEksisterendeLinje(tidligere)
            nåværende.skalOpphøreOgErstatte(tidligere, sisteLinjeITidligereOppdrag) -> opphørTidligereLinjeOgOpprettNy(nåværende, tidligere, aktivitetslogg)
            else -> opprettNyLinje(nåværende)
        }
    }

    private fun opprettNyLinje(nåværende: Utbetalingslinje) {
        nåværende.kobleTil(linkTo)
        linkTo = nåværende
        tilstand = Ny()
    }

    internal fun toHendelseMap() = mapOf(
        "mottaker" to mottaker,
        "fagområde" to "$fagområde",
        "linjer" to linjer.map(Utbetalingslinje::toHendelseMap),
        "fagsystemId" to fagsystemId,
        "endringskode" to "$endringskode",
        "sisteArbeidsgiverdag" to sisteArbeidsgiverdag,
        "tidsstempel" to tidsstempel,
        "nettoBeløp" to nettoBeløp,
        "stønadsdager" to stønadsdager(),
        "avstemmingsnøkkel" to avstemmingsnøkkel?.let { "$it" },
        "status" to status?.let { "$it" },
        "overføringstidspunkt" to overføringstidspunkt,
        "fom" to førstedato,
        "tom" to sistedato,
        "simuleringsResultat" to simuleringsResultat?.toMap()
    )

    internal fun lagreOverføringsinformasjon(hendelse: UtbetalingOverført) {
        if (!hendelse.erRelevant(fagsystemId)) return
        if (this.avstemmingsnøkkel == null) this.avstemmingsnøkkel = hendelse.avstemmingsnøkkel
        if (this.overføringstidspunkt == null) this.overføringstidspunkt = hendelse.overføringstidspunkt
        this.status = Oppdragstatus.OVERFØRT
    }

    internal fun lagreOverføringsinformasjon(hendelse: UtbetalingHendelse) {
        if (!hendelse.erRelevant(fagsystemId)) return
        if (this.avstemmingsnøkkel == null) this.avstemmingsnøkkel = hendelse.avstemmingsnøkkel
        if (this.overføringstidspunkt == null) this.overføringstidspunkt = hendelse.overføringstidspunkt
        this.status = hendelse.status
    }

    internal fun håndter(simulering: Simulering) {
        if (!simulering.erRelevantFor(fagområde, fagsystemId)) return
        this.erSimulert = true
        this.simuleringsResultat = simulering.simuleringResultat
    }

    internal fun erKlarForGodkjenning() = !harUtbetalinger() || erSimulert

    private interface Tilstand {
        fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg)
    }

    private inner class Identisk : Tilstand {
         override fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg) {
            if (nåværende == tidligere) return nåværende.markerUendret(tidligere)
            håndterUlikhet(nåværende, tidligere, aktivitetslogg)
        }
    }

    private inner class Slett : Tilstand {
        override fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg) {
            if (nåværende == tidligere) {
                if (nåværende == linjer.last()) return nåværende.kobleTil(linkTo)
                return nåværende.markerUendret(tidligere)
            }
            håndterUlikhet(nåværende, tidligere, aktivitetslogg)
        }
    }

    private inner class Ny : Tilstand {
        override fun håndterForskjell(nåværende: Utbetalingslinje, tidligere: Utbetalingslinje, aktivitetslogg: IAktivitetslogg) {
            nåværende.kobleTil(linkTo)
            linkTo = nåværende
        }
    }
}

