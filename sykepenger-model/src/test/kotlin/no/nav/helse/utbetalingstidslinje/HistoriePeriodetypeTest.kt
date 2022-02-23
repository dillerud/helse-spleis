package no.nav.helse.utbetalingstidslinje

import no.nav.helse.*
import no.nav.helse.hendelser.*
import no.nav.helse.hendelser.utbetaling.Utbetalingsgodkjenning
import no.nav.helse.inspectors.inspektør
import no.nav.helse.person.*
import no.nav.helse.person.Periodetype.*
import no.nav.helse.person.Sykepengegrunnlag.Begrensning.ER_IKKE_6G_BEGRENSET
import no.nav.helse.person.etterlevelse.MaskinellJurist
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.person.infotrygdhistorikk.Friperiode
import no.nav.helse.person.infotrygdhistorikk.Infotrygdperiode
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse
import no.nav.helse.testhelpers.*
import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosent
import no.nav.helse.økonomi.Prosentdel
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.*

internal class HistoriePeriodetypeTest {
    private companion object {
        private const val aktørId = "aktørId"
        private val UNG_PERSON_FNR_2018 = "12029240045".somFødselsnummer()
        private const val AG1 = "AG1"
    }
    private lateinit var person: Person
    private lateinit var arbeidsgiver: Arbeidsgiver

    @BeforeEach
    fun setup() {
        person = Person(aktørId, UNG_PERSON_FNR_2018, MaskinellJurist())
        arbeidsgiver = Arbeidsgiver(person, AG1, MaskinellJurist())
        person.håndter(Sykmelding(
            meldingsreferanseId = UUID.randomUUID(),
            fnr = UNG_PERSON_FNR_2018.toString(),
            aktørId = aktørId,
            orgnummer = AG1,
            sykeperioder = listOf(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent)),
            sykmeldingSkrevet = 1.februar.atStartOfDay(),
            mottatt = 1.februar.atStartOfDay()
        ))
    }

    @Test
    fun `infotrygd - gap - spleis - gap - infotrygd - spleis - spleis`() {
        historie(utbetaling(1.januar, 31.januar), utbetaling(9.april, 30.april))
        addTidligereUtbetalinger(navdager(1.mars, 30.mars))
        addTidligereUtbetalinger(navdager(1.mai, 31.mai))
        addSykdomshistorikk(sykedager(1.juni, 30.juni))

        assertFalse(arbeidsgiver.forlengerInfotrygd(1.mars til 31.mars))
        assertEquals(FØRSTEGANGSBEHANDLING, arbeidsgiver.periodetype(1.mars til 31.mars))

        assertTrue(arbeidsgiver.forlengerInfotrygd(1.mai til 31.mai))
        assertEquals(OVERGANG_FRA_IT, arbeidsgiver.periodetype(1.mai til 31.mai))

        assertTrue(arbeidsgiver.forlengerInfotrygd(1.juni til 30.juni))
        assertEquals(INFOTRYGDFORLENGELSE, arbeidsgiver.periodetype(1.juni til 30.juni))
    }

    @Test
    fun `infotrygd - spleis - spleis`() {
        historie(utbetaling(1.januar, 31.januar))
        addTidligereUtbetalinger(navdager(1.februar, 28.februar))
        addSykdomshistorikk(sykedager(1.mars, 31.mars))

        assertTrue(arbeidsgiver.forlengerInfotrygd(1.februar til 28.februar))
        assertEquals(OVERGANG_FRA_IT, arbeidsgiver.periodetype(1.februar til 28.februar))
        assertEquals(INFOTRYGDFORLENGELSE, arbeidsgiver.periodetype(1.mars til 31.mars))
    }

    @Test
    fun `infotrygd - spleis - infotrygd - spleis`() {
        historie(utbetaling(1.januar, 31.januar), utbetaling(1.mars, 31.mars))
        addTidligereUtbetalinger(navdager(1.februar, 28.februar))
        addSykdomshistorikk(sykedager(1.april, 30.april))

        assertTrue(arbeidsgiver.forlengerInfotrygd(1.februar til 28.februar))
        assertEquals(OVERGANG_FRA_IT, arbeidsgiver.periodetype(1.februar til 28.februar))

        assertTrue(arbeidsgiver.forlengerInfotrygd(1.april til 30.april))
        assertEquals(INFOTRYGDFORLENGELSE, arbeidsgiver.periodetype(1.april til 30.april))
    }

    @Test
    fun `spleis - infotrygd - spleis`() {
        historie(utbetaling(1.februar, 28.februar))
        addTidligereUtbetalinger(navdager(1.januar, 31.januar))
        addSykdomshistorikk(sykedager(1.mars, 31.mars))
        assertEquals(FØRSTEGANGSBEHANDLING, arbeidsgiver.periodetype(1.januar til 31.januar))
        assertFalse(arbeidsgiver.forlengerInfotrygd(1.mars til 31.mars))
        assertEquals(FORLENGELSE, arbeidsgiver.periodetype(1.mars til 31.mars))
    }

    @Test
    fun `infotrygd - spleis`() {
        historie(utbetaling(1.januar, 31.januar))
        addSykdomshistorikk(sykedager(1.februar, 28.februar))
        assertTrue(arbeidsgiver.forlengerInfotrygd(1.februar til 28.februar))
        assertEquals(OVERGANG_FRA_IT, arbeidsgiver.periodetype(1.februar til 28.februar))
    }

    @Test
    fun `infotrygd - gap - spleis`() {
        historie(utbetaling(1.februar, 27.februar))
        addSykdomshistorikk(sykedager(1.mars, 31.mars))
        assertEquals(FØRSTEGANGSBEHANDLING, arbeidsgiver.periodetype(1.mars til 31.mars))
        assertFalse(arbeidsgiver.forlengerInfotrygd(1.mars til 31.mars))
    }

    @Test
    fun `spleis - gap - infotrygd - spleis`() {
        historie(utbetaling(1.februar, 28.februar))
        addTidligereUtbetalinger(navdager(1.januar, 30.januar))
        addSykdomshistorikk(sykedager(1.mars, 31.mars))
        assertEquals(OVERGANG_FRA_IT, arbeidsgiver.periodetype(1.mars til 31.mars))
    }

    @Test
    fun `ubetalt spleis - ubetalt spleis`() {
        addSykdomshistorikk(sykedager(1.februar, 28.februar))
        addSykdomshistorikk(sykedager(1.mars, 31.mars))
        assertEquals(FØRSTEGANGSBEHANDLING, arbeidsgiver.periodetype(1.januar til 28.februar))
        assertFalse(arbeidsgiver.erForlengelse(1.januar til 28.februar))
        assertFalse(arbeidsgiver.forlengerInfotrygd(1.mars til 31.mars))
        assertEquals(FØRSTEGANGSBEHANDLING, arbeidsgiver.periodetype(1.mars til 31.mars))
        assertTrue(arbeidsgiver.erForlengelse(1.mars til 31.mars))
    }

    @Test
    fun `spleis - ubetalt spleis - ubetalt spleis`() {
        addTidligereUtbetalinger(navdager(1.januar, 31.januar))
        addSykdomshistorikk(sykedager(1.februar, 28.februar))
        addSykdomshistorikk(sykedager(1.mars, 31.mars))
        assertEquals(FØRSTEGANGSBEHANDLING, arbeidsgiver.periodetype(1.januar til 31.januar))
        assertFalse(arbeidsgiver.forlengerInfotrygd(1.februar til 28.februar))
        assertEquals(FORLENGELSE, arbeidsgiver.periodetype(1.februar til 28.februar))
        assertFalse(arbeidsgiver.forlengerInfotrygd(1.mars til 31.mars))
        assertEquals(FORLENGELSE, arbeidsgiver.periodetype(1.mars til 31.mars))
    }

    private fun utbetaling(fom: LocalDate, tom: LocalDate, inntekt: Inntekt = 1000.daglig, grad: Prosentdel = 100.prosent, orgnr: String = AG1) =
        ArbeidsgiverUtbetalingsperiode(orgnr, fom,  tom, grad, inntekt)

    private fun ferie(fom: LocalDate, tom: LocalDate) =
        Friperiode(fom,  tom)

    private fun historie(vararg perioder: Infotrygdperiode) {
        person.håndter(Utbetalingshistorikk(
            meldingsreferanseId = UUID.randomUUID(),
            aktørId = aktørId,
            fødselsnummer = UNG_PERSON_FNR_2018.toString(),
            organisasjonsnummer = AG1,
            vedtaksperiodeId = UUID.randomUUID().toString(),
            arbeidskategorikoder = emptyMap(),
            harStatslønn = false,
            perioder = perioder.toList(),
            inntektshistorikk = emptyList(),
            ugyldigePerioder = emptyList(),
            besvart = LocalDateTime.now()
        ))
    }

    private fun navdager(fom: LocalDate, tom: LocalDate) =
        tidslinjeOf(fom.dagerMellom(tom).NAV, startDato = fom)

    private fun arbeidsdager(fom: LocalDate, tom: LocalDate) =
        tidslinjeOf(fom.dagerMellom(tom).ARB, startDato = fom)

    private fun feriedager(fom: LocalDate, tom: LocalDate) =
        tidslinjeOf(fom.dagerMellom(tom).FRI, startDato = fom)

    private fun LocalDate.dagerMellom(tom: LocalDate) =
        ChronoUnit.DAYS.between(this, tom).toInt() + 1

    private fun sykedager(fom: LocalDate, tom: LocalDate, grad: Prosentdel = 100.prosent, kilde: SykdomstidslinjeHendelse.Hendelseskilde = SykdomstidslinjeHendelse.Hendelseskilde.INGEN) =
        Sykdomstidslinje.sykedager(fom, tom, grad, kilde)

    private fun addTidligereUtbetalinger(utbetalingstidslinje: Utbetalingstidslinje) {
        arbeidsgiver.oppdaterSykdom(TestHendelse(Utbetalingstidslinje.konverter(utbetalingstidslinje)))
        val vilkårsgrunnlagHistorikk = VilkårsgrunnlagHistorikk()
        vilkårsgrunnlagHistorikk.lagre(1.januar, VilkårsgrunnlagHistorikk.Grunnlagsdata(
            skjæringstidspunkt = 1.januar,
            sykepengegrunnlag = sykepengegrunnlag(30000.månedlig),
            sammenligningsgrunnlag = sammenligningsgrunnlag(30000.månedlig),
            avviksprosent = Prosent.prosent(0.0),
            opptjening = Opptjening.opptjening(emptyList(), 1.januar, MaskinellJurist()),
            antallOpptjeningsdagerErMinst = 28,
            harOpptjening = true,
            medlemskapstatus = Medlemskapsvurdering.Medlemskapstatus.Ja,
            harMinimumInntekt = true,
            vurdertOk = true,
            meldingsreferanseId = UUID.randomUUID(),
            vilkårsgrunnlagId = UUID.randomUUID()
        ))
        arbeidsgiver.addInntekt(
            inntektsmelding = Inntektsmelding(
                meldingsreferanseId = UUID.randomUUID(),
                refusjon = Inntektsmelding.Refusjon(30000.månedlig, null),
                orgnummer = arbeidsgiver.organisasjonsnummer(),
                fødselsnummer = AbstractPersonTest.UNG_PERSON_FNR_2018.toString(),
                aktørId = AbstractPersonTest.AKTØRID,
                førsteFraværsdag = 1.januar,
                beregnetInntekt = 30000.månedlig,
                arbeidsgiverperioder = listOf(),
                arbeidsforholdId = null,
                begrunnelseForReduksjonEllerIkkeUtbetalt = null,
                harOpphørAvNaturalytelser = false,
                mottatt = LocalDateTime.now()
            ),
            skjæringstidspunkt = 1.januar,
            subsumsjonObserver = MaskinellJurist()
        )
        MaksimumUtbetaling(listOf(utbetalingstidslinje), Aktivitetslogg(), 1.januar).betal()
        arbeidsgiver.lagreUtbetalingstidslinjeberegning(arbeidsgiver.organisasjonsnummer(), utbetalingstidslinje, vilkårsgrunnlagHistorikk)
        val utbetaling = arbeidsgiver.lagUtbetaling(Aktivitetslogg(), UNG_PERSON_FNR_2018.toString(), LocalDate.MAX, 0, 0, utbetalingstidslinje.periode(), null)
        utbetaling.håndter(
            Utbetalingsgodkjenning(
                meldingsreferanseId = UUID.randomUUID(),
                aktørId = aktørId,
                fødselsnummer = UNG_PERSON_FNR_2018.toString(),
                organisasjonsnummer = arbeidsgiver.organisasjonsnummer(),
                utbetalingId = utbetaling.inspektør.utbetalingId,
                vedtaksperiodeId = UUID.randomUUID().toString(),
                saksbehandler = "Z999999",
                saksbehandlerEpost = "z999999@nav.no",
                utbetalingGodkjent = true,
                godkjenttidspunkt = LocalDateTime.now(),
                automatiskBehandling = true
            )
        )
    }

    private fun addSykdomshistorikk(sykdomstidslinje: Sykdomstidslinje) {
        arbeidsgiver.oppdaterSykdom(TestHendelse(sykdomstidslinje))
    }

    private fun sykepengegrunnlag(inntekt: Inntekt) = Sykepengegrunnlag(
        arbeidsgiverInntektsopplysninger = listOf(),
        sykepengegrunnlag = inntekt,
        grunnlagForSykepengegrunnlag = inntekt,
        begrensning = ER_IKKE_6G_BEGRENSET,
        deaktiverteArbeidsforhold = emptyList()
    )

    private fun sammenligningsgrunnlag(inntekt: Inntekt) = Sammenligningsgrunnlag(
        arbeidsgiverInntektsopplysninger = listOf(
            ArbeidsgiverInntektsopplysning("orgnummer",
                Inntektshistorikk.SkattComposite(UUID.randomUUID(), (0 until 12).map {
                    Inntektshistorikk.Skatt.Sammenligningsgrunnlag(
                        dato = LocalDate.now(),
                        hendelseId = UUID.randomUUID(),
                        beløp = inntekt,
                        måned = YearMonth.of(2017, it + 1),
                        type = Inntektshistorikk.Skatt.Inntekttype.LØNNSINNTEKT,
                        fordel = "fordel",
                        beskrivelse = "beskrivelse"
                    )
                })
            )),
    )
}
