package no.nav.helse.person.etterlevelse

import no.nav.helse.person.UtbetalingsdagVisitor
import no.nav.helse.person.etterlevelse.SubsumsjonObserver.UtbetalingstidslinjeVisitor.Periode.Companion.dager
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Prosent
import no.nav.helse.økonomi.Økonomi
import java.time.LocalDate
import java.time.Year

interface SubsumsjonObserver {

    /**
     * Vurdering av medlemskap
     *
     * Lovdata: [lenke](https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_2-2#KAPITTEL_2-2)
     *
     * @param oppfylt hvorvidt sykmeldte har oppfylt krav til medlemskap i folketrygden
     */
    @Suppress("UNUSED_PARAMETER")
    fun `§2`(oppfylt: Boolean) {
    }

    /**
     * Vurdering av opptjeningstid
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-2)
     *
     * @param oppfylt hvorvidt sykmeldte har oppfylt krav om opptjeningstid
     * @param skjæringstidspunkt dato som antall opptjeningsdager regnes mot
     * @param tilstrekkeligAntallOpptjeningsdager antall opptjeningsdager som kreves for at vilkåret skal være [oppfylt]
     * @param arbeidsforhold hvilke arbeidsforhold det er tatt utgangspunkt i ved beregning av opptjeningstid
     * @param antallOpptjeningsdager antall opptjeningsdager sykmeldte faktisk har på [skjæringstidspunkt]
     */
    fun `§8-2 ledd 1`(
        oppfylt: Boolean,
        skjæringstidspunkt: LocalDate,
        tilstrekkeligAntallOpptjeningsdager: Int,
        arbeidsforhold: List<Map<String, Any?>>,
        antallOpptjeningsdager: Int
    ) {
        // versjon = LocalDate.of(2020, 6, 12)
    }

    /**
     * Vurdering av rett til sykepenger ved fylte 70 år
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-3)
     *
     * @param oppfylt hvorvidt sykmeldte har fylt 70 år. Oppfylt så lenge sykmeldte ikke er 70 år eller eldre
     * @param syttiårsdagen dato sykmeldte fyller 70 år
     * @param utfallFom fra-og-med-dato [oppfylt]-vurderingen gjelder for
     * @param utfallTom til-og-med-dato [oppfylt]-vurderingen gjelder for
     * @param tidslinjeFom fra-og-med-dato vurderingen gjøres for
     * @param tidslinjeTom til-og-med-dato vurderingen gjøres for
     * @param avvisteDager alle dager vurderingen ikke er [oppfylt] for. Tom dersom sykmeldte ikke fyller 70 år mellom [tidslinjeFom] og [tidslinjeTom]
     */
    fun `§8-3 ledd 1 punktum 2`(
        oppfylt: Boolean,
        syttiårsdagen: LocalDate,
        utfallFom: LocalDate,
        utfallTom: LocalDate,
        tidslinjeFom: LocalDate,
        tidslinjeTom: LocalDate,
        avvisteDager: List<LocalDate>
    ) {
        // versjon = LocalDate.of(2011, 12, 16)
    }

    /**
     * Vurdering av krav til minimum inntekt
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-3)
     *
     * @param oppfylt hvorvidt sykmeldte har inntekt lik eller større enn minimum inntekt
     * @param skjæringstidspunkt dato det tas utgangspunkt i ved vurdering av minimum inntekt
     * @param grunnlagForSykepengegrunnlag total inntekt på tvers av alle relevante arbeidsgivere
     * @param minimumInntekt minimum beløp [grunnlagForSykepengegrunnlag] må være lik eller større enn for at vilkåret skal være [oppfylt]
     */
    fun `§8-3 ledd 2 punktum 1`(
        oppfylt: Boolean,
        skjæringstidspunkt: LocalDate,
        grunnlagForSykepengegrunnlag: Inntekt,
        minimumInntekt: Inntekt
    ) {
        // versjon = LocalDate.of(2011, 12, 16)
    }

    /**
     * Vurdering av maksimalt sykepengegrunnlag
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-10)
     *
     * Merk: er alltid oppfylt
     *
     * @param erBegrenset dersom hjemlen slår inn ved at [grunnlagForSykepengegrunnlag] blir begrenset til [maksimaltSykepengegrunnlag]
     * @param maksimaltSykepengegrunnlag maksimalt årlig beløp utbetaling skal beregnes ut fra
     * @param skjæringstidspunkt dato [maksimaltSykepengegrunnlag] settes ut fra
     * @param grunnlagForSykepengegrunnlag total inntekt på tvers av alle relevante arbeidsgivere
     */
    fun `§8-10 ledd 2 punktum 1`(
        erBegrenset: Boolean,
        maksimaltSykepengegrunnlag: Inntekt,
        skjæringstidspunkt: LocalDate,
        grunnlagForSykepengegrunnlag: Inntekt
    ) {
        // versjon = LocalDate.of(2020, 1, 1)
    }

    //TODO: Hvordan skal denne kunne legges inn???
    @Suppress("UNUSED_PARAMETER")
    fun `§8-10 ledd 3`(oppfylt: Boolean) {
    }

    fun `§8-11 første ledd`() {
        // versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO
    }

    /**
     * Vurdering av maksimalt antall sykepengedager
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-12)
     *
     * @param oppfylt **true** dersom vedtaksperioden ikke inneholder [avvisteDager] som følge av at man har nådd [maksdato]
     * @param fom første NAV-dag i et helt sykepengeforløp dersom vilkåret er [oppfylt], ellers første avviste dag
     * @param tom hittil siste NAV-dag i et helt sykepengeforløp dersom vilkåret er [oppfylt], ellers siste avviste dag
     * @param tidslinjegrunnlag tidslinje det tas utgangspunkt i når man beregner [gjenståendeSykedager], [forbrukteSykedager] og [maksdato]
     * @param gjenståendeSykedager antall gjenstående sykepengedager ved siste utbetalte dag. 0 dersom vilkåret ikke er [oppfylt]
     * @param forbrukteSykedager antall forbrukte sykepengedager
     * @param maksdato dato for opphør av rett til sykepenger
     * @param avvisteDager dager vilkåret ikke er [oppfylt] for
     */
    fun `§8-12 ledd 1 punktum 1`(
        oppfylt: Boolean,
        fom: LocalDate,
        tom: LocalDate,
        tidslinjegrunnlag: List<List<Map<String, Any>>>,
        beregnetTidslinje: List<Map<String, Any>>,
        gjenståendeSykedager: Int,
        forbrukteSykedager: Int,
        maksdato: LocalDate,
        avvisteDager: List<LocalDate>
    ) {

        // versjon = LocalDate.of(2021, 5, 21)
    }

    /**
     * Vurdering av ny rett til sykepenger
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-12)
     *
     * @param oppfylt **true** dersom det har vært tilstrekelig opphold
     * @param dato dato vurdering av hjemmel gjøres
     * @param tilstrekkeligOppholdISykedager antall dager med opphold i ytelsen som nødvendig for å oppnå ny rett til sykepenger
     * @param tidslinjegrunnlag alle tidslinjer det tas utgangspunkt i ved bygging av [beregnetTidslinje]
     * @param beregnetTidslinje tidslinje det tas utgangspunkt i ved utbetaling for aktuell vedtaksperiode
     */
    fun `§8-12 ledd 2`(
        oppfylt: Boolean,
        dato: LocalDate,
        tilstrekkeligOppholdISykedager: Int,
        tidslinjegrunnlag: List<List<Map<String, Any>>>,
        beregnetTidslinje: List<Map<String, Any>>
    ) {
        // versjon = LocalDate.of(2021, 5, 21)
    }

    /**
     * Vurdering av graderte sykepenger
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-13)
     *
     * @param oppfylt **true** dersom uføregraden er minst 20%
     * @param avvisteDager dager som ikke møter kriterie for [oppfylt]
     */
    fun `§8-13 ledd 1`(oppfylt: Boolean, avvisteDager: List<LocalDate>) {
        // punktum = (1..2).punktum,
        // versjon = FOLKETRYGDLOVENS_OPPRINNELSESDATO,
    }

    /**
     * Fastsettelse av dekningsgrunnlag
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-16)
     *
     * @param dato dagen [dekningsgrunnlag] beregnes for
     * @param dekningsgrad hvor stor andel av inntekten det ytes sykepenger av
     * @param inntekt inntekt for aktuell arbeidsgiver
     * @param dekningsgrunnlag maks dagsats før reduksjon til 6G og reduksjon for sykmeldingsgrad
     */
    fun `§8-16 ledd 1`(dato: LocalDate, dekningsgrad: Double, inntekt: Double, dekningsgrunnlag: Double) {}

    /**
     * Vurdering av når utbetaling av sykepenger tidligst skal starte
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-17)
     *
     * @param arbeidsgiverperiode alle arbeidsgiverperiode-dager
     * @param førsteNavdag første dag NAV skal utbetale
     */
    fun `§8-17 ledd 1 bokstav a`(arbeidsgiverperiode: List<LocalDate>, førsteNavdag: LocalDate) {}

    @Suppress("UNUSED_PARAMETER")
    fun `§8-17 ledd 2`(oppfylt: Boolean) {
    } //Legges inn på ferie/permisjonsdager i utbetalingstidslinje, med periodene av ferie/permisjon som input

    @Suppress("UNUSED_PARAMETER")
    fun `§8-28 ledd 3 bokstav a`(
        oppfylt: Boolean,
        //inntekter: List<Inntektshistorikk.Skatt>,
        //inntekterSisteTreMåneder: List<Inntektshistorikk.Skatt>,
        grunnlagForSykepengegrunnlag: Inntekt
    ) {
    }

    /**
     * Fastsettelse av sykepengegrunnlaget
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-30)
     *
     * Merk: Alltid oppfylt
     *
     * @param grunnlagForSykepengegrunnlagPerArbeidsgiver beregnet inntekt per arbeidsgiver
     * @param grunnlagForSykepengegrunnlag beregnet inntekt på tvers av arbeidsgivere
     */
    fun `§8-30 ledd 1`(
        grunnlagForSykepengegrunnlagPerArbeidsgiver: Map<String, Inntekt>,
        grunnlagForSykepengegrunnlag: Inntekt
    ) {
        // versjon = LocalDate.of(2019, 1, 1),
        // punktum = 1.punktum
        val beregnetMånedsinntektPerArbeidsgiver = grunnlagForSykepengegrunnlagPerArbeidsgiver
            .mapValues { it.value.reflection { _, månedlig, _, _ -> månedlig } }
    }

    fun `§8-30 ledd 2 punktum 1`(
        oppfylt: Boolean,
        maksimaltTillattAvvikPåÅrsinntekt: Prosent,
        grunnlagForSykepengegrunnlag: Inntekt,
        sammenligningsgrunnlag: Inntekt,
        avvik: Prosent
    ) {
        // versjon = LocalDate.of(2017, 4, 5),
        // punktum = 1.punktum
    }

    fun `§8-33 ledd 1`() {}

    @Suppress("UNUSED_PARAMETER")
    fun `§8-33 ledd 3`(
        grunnlagForFeriepenger: Int,
        opptjeningsår: Year,
        prosentsats: Double,
        alder: Int,
        feriepenger: Double
    ) {
    }

    /**
     * Vurdering av krav til minimum inntekt ved alder mellom 67 og 70 år
     *
     * Lovdata: [lenke](https://lovdata.no/lov/1997-02-28-19/%C2%A78-51)
     *
     * @param oppfylt dersom vedkommende har inntekt større enn eller lik to ganger grunnbeløpet. Det er en forutsetning at vedkommende er mellom 67 og 70 år
     * @param skjæringstidspunkt dato det tas utgangspunkt i ved vurdering av minimum inntekt
     * @param alderPåSkjæringstidspunkt alder på skjæringstidspunktet
     * @param grunnlagForSykepengegrunnlag total inntekt på tvers av alle relevante arbeidsgivere
     * @param minimumInntekt minimum beløp [grunnlagForSykepengegrunnlag] må være lik eller større enn for at vilkåret skal være [oppfylt]
     */
    fun `§8-51 ledd 2`(
        oppfylt: Boolean,
        skjæringstidspunkt: LocalDate,
        alderPåSkjæringstidspunkt: Int,
        grunnlagForSykepengegrunnlag: Inntekt,
        minimumInntekt: Inntekt
    ) {}

    fun `§8-51 ledd 3`(
        oppfylt: Boolean,
        maksSykepengedagerOver67: Int,
        gjenståendeSykedager: Int,
        forbrukteSykedager: Int,
        maksdato: LocalDate
    ) {
        // versjon = LocalDate.of(2011, 12, 16),
        // punktum = 1.punktum
    }

    private class UtbetalingstidslinjeVisitor(utbetalingstidslinje: Utbetalingstidslinje) : UtbetalingsdagVisitor {
        private val navdager = mutableListOf<Periode>()
        private var forrigeDato: LocalDate? = null

        private class Periode(
            val fom: LocalDate,
            var tom: LocalDate,
            val dagtype: String
        ) {
            companion object {
                fun List<Periode>.dager() = map {
                    mapOf(
                        "fom" to it.fom,
                        "tom" to it.tom,
                        "dagtype" to it.dagtype
                    )
                }
            }
        }

        init {
            utbetalingstidslinje.accept(this)
        }

        fun dager() = navdager.dager()

        override fun visit(dag: Utbetalingstidslinje.Utbetalingsdag.NavDag, dato: LocalDate, økonomi: Økonomi) {
            visit(dato, "NAVDAG")
        }

        override fun visit(dag: Utbetalingstidslinje.Utbetalingsdag.NavHelgDag, dato: LocalDate, økonomi: Økonomi) {
            visit(dato, "NAVDAG")
        }

        override fun visit(dag: Utbetalingstidslinje.Utbetalingsdag.Fridag, dato: LocalDate, økonomi: Økonomi) {
            if (forrigeDato != null && forrigeDato?.plusDays(1) == dato) visit(dato, "FRIDAG")
        }

        private fun visit(dato: LocalDate, dagtype: String) {
            forrigeDato = dato
            if (navdager.isEmpty() || dagtype != navdager.last().dagtype || navdager.last().tom.plusDays(1) != dato) {
                navdager.add(Periode(dato, dato, dagtype))
            } else {
                navdager.last().tom = dato
            }
        }
    }

    companion object {
        internal fun List<Utbetalingstidslinje>.toSubsumsjonFormat(): List<List<Map<String, Any>>> = map { it.toSubsumsjonFormat() }

        internal fun Utbetalingstidslinje.toSubsumsjonFormat(): List<Map<String, Any>> = UtbetalingstidslinjeVisitor(this).dager()
    }
}
