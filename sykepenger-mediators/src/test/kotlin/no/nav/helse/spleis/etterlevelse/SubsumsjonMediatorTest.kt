package no.nav.helse.spleis.etterlevelse

import no.nav.helse.Toggle
import no.nav.helse.januar
import no.nav.helse.spleis.e2e.AbstractEndToEndMediatorTest
import no.nav.inntektsmeldingkontrakt.Periode
import no.nav.syfo.kafka.felles.SoknadsperiodeDTO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SubsumsjonMediatorTest : AbstractEndToEndMediatorTest() {

    @Test
    fun `subsumsjon-hendelser`() {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(0, listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)))
        sendInntektsmelding(0, listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendYtelser(0)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)

        assertTrue(testRapid.inspektør.meldinger("subsumsjon").isEmpty())
    }

    @Test
    fun `subsumsjon-hendelser - med toggle`() = Toggle.SubsumsjonHendelser.enable {
        sendNySøknad(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100))
        sendSøknad(0, listOf(SoknadsperiodeDTO(fom = 3.januar, tom = 26.januar, sykmeldingsgrad = 100)))
        sendInntektsmelding(0, listOf(Periode(fom = 3.januar, tom = 18.januar)), førsteFraværsdag = 3.januar)
        sendYtelser(0)
        sendVilkårsgrunnlag(0)
        sendYtelser(0)

        val subsumsjoner = testRapid.inspektør.meldinger("subsumsjon").map { it["subsumsjon"] }
        assertTrue(subsumsjoner.isNotEmpty())
        val subsumsjon = subsumsjoner.first { it["paragraf"].asText() == "8-17" }
        assertEquals(subsumsjon["bokstav"].asText(), "a")
        assertTrue(subsumsjon["ledd"].isInt)
        assertEquals(subsumsjon["ledd"].asInt(), 1)
    }
}