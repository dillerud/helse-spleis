package no.nav.helse.utbetalingstidslinje

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.testhelpers.februar
import no.nav.helse.testhelpers.januar
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class ArbeidsgiverperiodetellerTest {

    private lateinit var teller: Arbeidsgiverperiodeteller
    private lateinit var observatør: Observatør
    private lateinit var strategi: Strategi

    @BeforeEach
    fun setup() {
        teller = Arbeidsgiverperiodeteller(NormalArbeidstaker)
        strategi = Strategi()
        observatør = Observatør(teller)
    }

    @Test
    fun `16 sykedager utgjør ny arbeidsgiverperiode`() {
        val sykedager = 1.januar til 16.januar
        sykedager.tell()
        assertEquals(16, strategi.antallDagerInnenforArbeidsgiverperioden)
        assertEquals(0, strategi.antallDagerIForkantAvEllerEtterFullførtArbeidsgiverperiode)
        assertEquals(1, observatør.arbeidsgiverperioder())
        assertEquals(sykedager, observatør.arbeidsgiverperiode(0))
    }

    @Test
    fun `15 oppholdsdager starter ikke ny arbeidsgiverperiodetelling`() {
        val sykedager = listOf(
            1.januar til 16.januar,
            1.februar til 17.februar
        ).tell()
        assertEquals(16, strategi.antallDagerInnenforArbeidsgiverperioden)
        assertEquals(17, strategi.antallDagerIForkantAvEllerEtterFullførtArbeidsgiverperiode)
        assertEquals(1, observatør.arbeidsgiverperioder())
        assertEquals(sykedager[0], observatør.arbeidsgiverperiode(0))
    }

    @Test
    fun `16 oppholdsdager starter ny arbeidsgiverperiodetelling`() {
        val sykedager = listOf(
            1.januar til 16.januar,
            2.februar til 17.februar
        ).tell()
        assertEquals(32, strategi.antallDagerInnenforArbeidsgiverperioden)
        assertEquals(0, strategi.antallDagerIForkantAvEllerEtterFullførtArbeidsgiverperiode)
        assertEquals(2, observatør.arbeidsgiverperioder())
        assertEquals(sykedager[0], observatør.arbeidsgiverperiode(0))
        assertEquals(sykedager[1], observatør.arbeidsgiverperiode(1))
        assertTrue(observatør.tilbakestillingAvArbeidsgiverperiodetelling(1.februar))
    }

    @Test
    fun `kan ha så mye ferie man vil mellom to sykedager`() {
        1.januar.tell()
        (2.januar til 31.januar).feriedager()
        1.februar.tell()
        assertEquals(16, strategi.antallDagerInnenforArbeidsgiverperioden)
        assertEquals(16, strategi.antallDagerIForkantAvEllerEtterFullførtArbeidsgiverperiode)
        assertEquals(1, observatør.arbeidsgiverperioder())
        assertEquals(1.januar til 16.januar, observatør.arbeidsgiverperiode(0))
    }

    @Test
    fun `ferie teller ikke med i arbeidsgiverperiodetelling dersom dagen etter ferie er noe annet enn sykedag`() {
        1.januar().tell()
        (2.januar til 31.januar).feriedager()
        1.februar().oppholdsdag()
        assertEquals(1, strategi.antallDagerInnenforArbeidsgiverperioden)
        assertEquals(30, strategi.antallDagerIForkantAvEllerEtterFullførtArbeidsgiverperiode)
        assertEquals(0, observatør.arbeidsgiverperioder())
        assertTrue(observatør.tilbakestillingAvArbeidsgiverperiodetelling(17.januar))
    }

    @Test
    fun `ferie i forkant teller ikke med i tellingen`() {
        (1.januar til 31.januar).feriedager()
        val sykedager = (1.februar til 16.februar).tell()
        assertEquals(16, strategi.antallDagerInnenforArbeidsgiverperioden)
        assertEquals(31, strategi.antallDagerIForkantAvEllerEtterFullførtArbeidsgiverperiode)
        assertEquals(1, observatør.arbeidsgiverperioder())
        assertEquals(sykedager, observatør.arbeidsgiverperiode(0))
    }

    @Test
    fun `oppholdsdager i forkant teller ikke med i telling`() {
        (1.januar til 31.januar).oppholdsdager()
        assertEquals(0, strategi.antallDagerInnenforArbeidsgiverperioden)
        assertEquals(0, strategi.antallDagerIForkantAvEllerEtterFullførtArbeidsgiverperiode)
        val sykedager = (1.februar til 16.februar).tell()
        assertEquals(1, observatør.arbeidsgiverperioder())
        assertEquals(sykedager, observatør.arbeidsgiverperiode(0))
    }

    @Test
    fun `sammenblandet sykdom, ferie og opphold`() {
        val del1 = (1.januar til 8.januar).tell()
        val del2 = (9.januar til 10.januar).feriedager()
        val del3 = (11.januar til 11.januar).tell()
        (12.januar til 26.januar).oppholdsdager()
        val del4 = (27.januar til 31.januar).tell()
        assertEquals(16, strategi.antallDagerInnenforArbeidsgiverperioden)
        assertEquals(0, strategi.antallDagerIForkantAvEllerEtterFullførtArbeidsgiverperiode)
        assertEquals(1, observatør.arbeidsgiverperioder())
        assertEquals(listOf(del1, del2, del3, del4), observatør.arbeidsgiverperiode(0))
    }

    @Test
    fun `spredt arbeidsgiverperiode`() {
        val sykedager = listOf(
            1.januar,
            3.januar,
            5.januar,
            7.januar,
            9.januar,
            11.januar,
            13.januar,
            15.januar,
            17.januar,
            19.januar,
            21.januar,
            23.januar,
            25.januar,
            27.januar,
            29.januar,
            31.januar
        ).tell()
        assertEquals(16, strategi.antallDagerInnenforArbeidsgiverperioden)
        assertEquals(0, strategi.antallDagerIForkantAvEllerEtterFullførtArbeidsgiverperiode)
        assertEquals(1, observatør.arbeidsgiverperioder())
        assertEquals(sykedager, observatør.arbeidsgiverperiode(0))
    }

    private fun List<Iterable<LocalDate>>.tell() = apply {
        if (size == 1) {
            first().tell()
            return@apply
        }
        val perioder = mutableListOf<Pair<Periode, (Periode) -> Unit>>()
        this.zipWithNext { left, right ->
            left.tell()
            left.mellom(right).oppholdsdager()
            right.tell()
        }
        perioder.forEach { (periode, callback) -> callback(periode) }
    }

    private fun assertEquals(expected: Iterable<LocalDate>, actual: Arbeidsgiverperiode) {
        assertEquals(expected.toList(), actual.toList())
    }

    private fun assertEquals(expected: List<Iterable<LocalDate>>, actual: Arbeidsgiverperiode) {
        assertEquals(expected.flatMap { it.toList() }, actual.toList())
    }

    private fun Iterable<LocalDate>.mellom(other: Iterable<LocalDate>) = last().plusDays(1) til other.first().minusDays(1)

    private fun LocalDate.tell() = teller.inkrementer(this, strategi)
    private fun LocalDate.oppholdsdag() = teller.dekrementer(this)
    private fun Iterable<LocalDate>.tell() = onEach { teller.inkrementer(it, strategi) }
    private fun Iterable<LocalDate>.oppholdsdager() = forEach { teller.dekrementer(it) }
    private fun Iterable<LocalDate>.feriedager() = onEach { teller.inkrementEllerDekrement(it, strategi) }

    private class Strategi : Arbeidsgiverperiodestrategi {
        var antallDagerInnenforArbeidsgiverperioden = 0
            private set
        var antallDagerIForkantAvEllerEtterFullførtArbeidsgiverperiode = 0
            private set

        override fun dagenErInnenforArbeidsgiverperioden() {
            antallDagerInnenforArbeidsgiverperioden += 1
        }

        override fun dagenErEtterArbeidsgiverperioden() {
            antallDagerIForkantAvEllerEtterFullførtArbeidsgiverperiode += 1
        }
    }

    private class Observatør(teller: Arbeidsgiverperiodeteller) : Arbeidsgiverperiodeteller.Observatør {
        private val arbeidsgiverperioder = mutableListOf<Arbeidsgiverperiode>()
        private val oppholdsdagerSomMedførteTilbakestill = mutableListOf<LocalDate>()

        init {
            teller.observatør(this)
        }

        internal fun arbeidsgiverperioder() = arbeidsgiverperioder.size
        internal fun arbeidsgiverperiode(indeks: Int) = arbeidsgiverperioder[indeks]
        internal fun tilbakestillingAvArbeidsgiverperiodetelling(dato: LocalDate) = dato in oppholdsdagerSomMedførteTilbakestill

        override fun ingenArbeidsgiverperiode(dagen: LocalDate) {
            oppholdsdagerSomMedførteTilbakestill.add(dagen)
        }

        override fun arbeidsgiverperiodeFerdig(arbeidsgiverperiode: Arbeidsgiverperiode, dagen: LocalDate) {
            arbeidsgiverperioder.add(arbeidsgiverperiode)
        }
    }
}