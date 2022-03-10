package no.nav.helse.person

import org.junit.jupiter.api.Test

internal class HistorikkTest {

    @Test
    fun ``() {

    }

    private class Testhistorikk: Historikk<Testhistorikk.Testinnslag>() {
        private class Testinnslag(private val testverdi: String): Innslag() {
            override fun equals(other: Any?) = other is Testinnslag && testverdi == other.testverdi
            override fun hashCode() = testverdi.hashCode()
        }

        internal fun lagre(testverdi: String) {
            lagre(Testinnslag(testverdi))
        }
    }
}