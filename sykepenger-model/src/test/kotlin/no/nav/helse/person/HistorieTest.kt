package no.nav.helse.person

import no.nav.helse.hendelser.Utbetalingshistorikk
import no.nav.helse.hendelser.Utbetalingshistorikk.Periode
import no.nav.helse.hendelser.Utbetalingshistorikk.Periode.*
import no.nav.helse.person.Inntektshistorikk.Inntektsendring.Kilde.INNTEKTSMELDING
import no.nav.helse.sykdomstidslinje.Sykdomstidslinje
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse.Hendelseskilde
import no.nav.helse.sykdomstidslinje.SykdomstidslinjeHendelse.Hendelseskilde.Companion.INGEN
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler
import no.nav.helse.utbetalingstidslinje.ArbeidsgiverRegler.Companion.NormalArbeidstaker
import no.nav.helse.utbetalingstidslinje.Utbetalingstidslinje
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.reflect.KClass

internal abstract class HistorieTest {

    protected companion object {
        const val FNR = "12345678910"
        const val AKTØRID = "1234567891011"
        const val AG1 = "1234"
        const val AG2 = "2345"
    }

    protected lateinit var historie: Historie

    @BeforeEach
    fun beforeEach() {
        historie = Historie()
    }

    protected fun refusjon(fom: LocalDate, tom: LocalDate, dagsats: Int = 1000, grad: Int = 100, orgnr: String = AG1) =
        RefusjonTilArbeidsgiver(fom, tom, dagsats, grad, orgnr)

    protected fun bruker(fom: LocalDate, tom: LocalDate, dagsats: Int = 1000, grad: Int = 100, orgnr: String = AG1) =
        Utbetaling(fom, tom, dagsats, grad, orgnr)

    protected fun ferie(fom: LocalDate, tom: LocalDate) =
        Ferie(fom, tom)

    protected fun navdager(fom: LocalDate, tom: LocalDate) =
        tidslinjeOf(fom.dagerMellom(tom).NAV, startDato = fom)

    protected fun arbeidsdager(fom: LocalDate, tom: LocalDate) =
        tidslinjeOf(fom.dagerMellom(tom).ARB, startDato = fom)

    protected fun foreldetdager(fom: LocalDate, tom: LocalDate) =
        tidslinjeOf(fom.dagerMellom(tom).FOR, startDato = fom)

    protected fun avvistedager(fom: LocalDate, tom: LocalDate) =
        tidslinjeOf(fom.dagerMellom(tom).AVV, startDato = fom)

    protected fun feriedager(fom: LocalDate, tom: LocalDate) =
        tidslinjeOf(fom.dagerMellom(tom).FRI, startDato = fom)

    protected fun LocalDate.dagerMellom(tom: LocalDate) =
        ChronoUnit.DAYS.between(this, tom).toInt() + 1

    protected fun sykedager(fom: LocalDate, tom: LocalDate, grad: Int = 100, kilde: Hendelseskilde = INGEN) = Sykdomstidslinje.sykedager(fom, tom, grad, kilde)

    protected fun historie(vararg perioder: Periode) {
        historie = Historie(
            Person(AKTØRID, FNR),
            Utbetalingshistorikk(
                UUID.randomUUID(),
                AKTØRID,
                FNR,
                "ET ORGNR",
                UUID.randomUUID().toString(),
                perioder.toList(),
                emptyList()
            )
        )
    }

    protected fun beregn(orgnr: String, periode: no.nav.helse.hendelser.Periode, vararg inntektsdatoer: LocalDate, regler: ArbeidsgiverRegler = NormalArbeidstaker): Utbetalingstidslinje {
        val inntektshistorikk = Inntektshistorikk(inntektsdatoer.map {
            Inntektshistorikk.Inntektsendring(it, UUID.randomUUID(), 25000.månedlig, INNTEKTSMELDING)
        }.toMutableList())
        return historie.beregnUtbetalingstidslinje(orgnr, periode, inntektshistorikk, regler)
    }

    protected fun skjæringstidspunkt(fom: LocalDate) = historie.skjæringstidspunkt(no.nav.helse.hendelser.Periode(fom, fom))

    protected fun assertSkjæringstidspunkter(kuttdato: LocalDate, vararg datoer: LocalDate) {
        assertEquals(datoer.toList(), historie.skjæringstidspunkter(no.nav.helse.hendelser.Periode(kuttdato, kuttdato)))
    }

    protected fun assertAlleDager(utbetalingstidslinje: Utbetalingstidslinje, periode: no.nav.helse.hendelser.Periode, vararg dager: KClass<out Utbetalingstidslinje.Utbetalingsdag>) {
        utbetalingstidslinje.subset(periode).also { tidslinje ->
            assertTrue(tidslinje.all { it::class in dager }) {
                val ulikeDager = tidslinje.filter { it::class !in dager }
                "Forventet at alle dager skal være en av: ${dager.joinToString { it.simpleName ?: "UKJENT" }}.\n" +
                    ulikeDager.joinToString(prefix = "  - ", separator = "\n  - ", postfix = "\n") {
                        "${it.dato} er ${it::class.simpleName}"
                    } + "\nUtbetalingstidslinje:\n" + tidslinje.toString() + "\n"
            }
        }
    }
}
