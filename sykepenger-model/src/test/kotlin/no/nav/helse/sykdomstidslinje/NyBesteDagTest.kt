package no.nav.helse.sykdomstidslinje

import no.nav.helse.testhelpers.TestEvent
import no.nav.helse.testhelpers.mandag
import no.nav.helse.tournament.dagturnering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NyBesteDagTest {

    companion object {
        private val ukjentDag get() = NyDag.NyUkjentDag(2.mandag, TestEvent.søknad)
        private val arbeidsdagFraSøknad get() = NyDag.NyArbeidsdag(2.mandag, TestEvent.søknad)
        private val ferieFraInntektsmelding get() = NyDag.NyFeriedag(2.mandag, TestEvent.inntektsmelding)
        private val arbeidsgiverdagFraInntektsmelding get() = NyDag.NyArbeidsgiverdag(2.mandag, 100.0, TestEvent.inntektsmelding)
        private val ferieFraSøknad get() = NyDag.NyFeriedag(2.mandag, TestEvent.søknad)
        private val sykedagFraSøknad get() = NyDag.NySykedag(2.mandag, 100.0, TestEvent.søknad)
        private val utenlandsFraSøknad get() = NyDag.NyUtenlandsdag(2.mandag, TestEvent.søknad)
    }

    @Test
    fun `inntektsmelding sier ferie, søknad sier syk blir feriedag`() {
        assertWinnerBidirectional(sykedagFraSøknad, ferieFraInntektsmelding, ferieFraInntektsmelding)
    }

    @Test
    fun `nulldag taper mot en gitt dag`() {
        assertWinnerBidirectional(ukjentDag, sykedagFraSøknad, sykedagFraSøknad)
    }

    @Test
    fun `ferie vinner over sykdom`() {
        assertWinner(sykedagFraSøknad, ferieFraSøknad, ferieFraSøknad)
        assertWinner(ferieFraSøknad, sykedagFraSøknad, sykedagFraSøknad)
    }

    @Test
    fun `søknad med egenmelding vinner over en gitt dag`() {
        assertWinnerBidirectional(ferieFraSøknad, arbeidsgiverdagFraInntektsmelding, arbeidsgiverdagFraInntektsmelding)
    }

    @Test
    fun `sammenligning med utenlandsdag git altid ubestemtdag`() {
        assertProblemDagBidirectional(ukjentDag, utenlandsFraSøknad)
        assertProblemDagBidirectional(arbeidsdagFraSøknad, utenlandsFraSøknad)
        assertProblemDagBidirectional(sykedagFraSøknad, utenlandsFraSøknad)
        assertProblemDagBidirectional(arbeidsgiverdagFraInntektsmelding, utenlandsFraSøknad)
        assertProblemDagBidirectional(ferieFraSøknad, utenlandsFraSøknad)
        assertProblemDagBidirectional(ferieFraInntektsmelding, utenlandsFraSøknad)
    }

    @Test
    fun `arbeidsdag vinner over sykedag`() {
        assertWinner(sykedagFraSøknad, arbeidsdagFraSøknad, arbeidsdagFraSøknad)
    }

    @Test
    fun `sykedag fra søknad vinner over egenmeldingsdag i inntektsmelding`() {
        assertWinner(sykedagFraSøknad, arbeidsgiverdagFraInntektsmelding, sykedagFraSøknad)
        assertWinner(arbeidsgiverdagFraInntektsmelding, sykedagFraSøknad, sykedagFraSøknad)
    }

    private fun assertWinner(
        dag1: NyDag,
        dag2: NyDag,
        expectedWinner: NyDag
    ) {
        val winner = dagturnering.beste(dag1, dag2)
        assertEquals(expectedWinner, winner)
    }

    private fun assertWinnerBidirectional(
        dag1: NyDag,
        dag2: NyDag,
        expectedWinner: NyDag
    ) {
        assertWinner(dag1, dag2, expectedWinner)
        assertWinner(dag2, dag1, expectedWinner)
    }

    private fun assertProblemDagBidirectional(
        dag1: NyDag,
        dag2: NyDag
    ) {
        assertWinner(dag1, dag2, dag1.problem(dag2))
        assertWinner(dag2, dag1, dag2.problem(dag1))
    }
}
