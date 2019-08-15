package no.nav.helse.sakskompleks

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.readResource
import no.nav.helse.sakskompleks.domain.Sakskompleks
import no.nav.helse.sykmelding.domain.Sykmelding
import no.nav.helse.sykmelding.domain.SykmeldingMessage
import no.nav.helse.søknad.domain.Sykepengesøknad
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class SakskompleksServiceTest {

    companion object {
        private val objectMapper = jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        private val testSykmelding = SykmeldingMessage(objectMapper.readTree("/sykmelding.json".readResource()))

        private val testSøknad = Sykepengesøknad(objectMapper.readTree("/søknad_arbeidstaker_sendt_nav.json".readResource()))
    }

    @Test
    fun `skal ikke finne sak når søknaden ikke er tilknyttet en sak`() {
        val sakskompleksDao = mockk<SakskompleksDao>()

        every {
            sakskompleksDao.finnSaker(testSøknad.aktørId)
        } returns emptyList()

        val sakskompleksService = SakskompleksService(sakskompleksDao)

        val sak = sakskompleksService.finnSak(testSøknad)

        assertNull(sak)

        verify(exactly = 1) {
            sakskompleksDao.finnSaker(testSøknad.aktørId)
        }
    }

    @Test
    fun `skal finne sak når søknaden er tilknyttet en sak`() {
        val sakForBruker = etSakskompleks(
                sykmeldinger = listOf(testSykmelding.sykmelding),
                søknader = listOf(testSøknad)
        )

        val sakskompleksDao = mockk<SakskompleksDao>()

        every {
            sakskompleksDao.finnSaker(testSøknad.aktørId)
        } returns listOf(sakForBruker)

        val sakskompleksService = SakskompleksService(sakskompleksDao)

        val sak = sakskompleksService.finnSak(testSøknad)

        assertEquals(sakForBruker, sak)

        verify(exactly = 1) {
            sakskompleksDao.finnSaker(testSøknad.aktørId)
        }
    }

    @Test
    fun `skal ikke finne sak når sykmeldingen ikke er tilknyttet en sak`() {
        val sakskompleksDao = mockk<SakskompleksDao>()

        every {
            sakskompleksDao.finnSaker(testSykmelding.sykmelding.aktørId)
        } returns emptyList()

        val sakskompleksService = SakskompleksService(sakskompleksDao)

        val sak = sakskompleksService.finnSak(testSykmelding.sykmelding)

        assertNull(sak)

        verify(exactly = 1) {
            sakskompleksDao.finnSaker(testSykmelding.sykmelding.aktørId)
        }
    }

    @Test
    fun `skal finne sak når sykmeldingen er tilknyttet en sak`() {
        val sakForBruker = etSakskompleks(
                sykmeldinger = listOf(testSykmelding.sykmelding)
        )

        val sakskompleksDao = mockk<SakskompleksDao>()

        every {
            sakskompleksDao.finnSaker(testSykmelding.sykmelding.aktørId)
        } returns listOf(sakForBruker)

        val sakskompleksService = SakskompleksService(sakskompleksDao)

        val sak = sakskompleksService.finnSak(testSykmelding.sykmelding)

        assertEquals(sakForBruker, sak)

        verify(exactly = 1) {
            sakskompleksDao.finnSaker(testSykmelding.sykmelding.aktørId)
        }
    }

    @Test
    fun `skal oppdatere sak når aktøren har en sak`() {
        val sakForBruker = etSakskompleks(
                sykmeldinger = listOf(testSykmelding.sykmelding)
        )
        val oppdatertSak = sakForBruker.copy(
                sykmeldinger = sakForBruker.sykmeldinger + testSykmelding.sykmelding
        )

        val sakskompleksDao = mockk<SakskompleksDao>()

        every {
            sakskompleksDao.finnSaker(testSykmelding.sykmelding.aktørId)
        } returns listOf(sakForBruker)

        every {
            sakskompleksDao.oppdaterSak(oppdatertSak)
        } returns 1

        val sakskompleksService = SakskompleksService(sakskompleksDao)

        val sak = sakskompleksService.finnEllerOpprettSak(testSykmelding.sykmelding)

        assertEquals(oppdatertSak, sak)

        verify(exactly = 1) {
            sakskompleksDao.finnSaker(testSykmelding.sykmelding.aktørId)
            sakskompleksDao.oppdaterSak(oppdatertSak)
        }
        verify(exactly = 0) {
            sakskompleksDao.opprettSak(any())
        }
    }

    @Test
    fun `skal opprette sak når sykmeldingen ikke er tilknyttet en sak`() {
        val sakskompleksDao = mockk<SakskompleksDao>()

        every {
            sakskompleksDao.finnSaker(testSykmelding.sykmelding.aktørId)
        } returns emptyList()

        every {
            sakskompleksDao.opprettSak(match { sak ->
                sak.aktørId == testSykmelding.sykmelding.aktørId
                        && sak.sykmeldinger.size == 1 && sak.sykmeldinger[0] == testSykmelding.sykmelding
                        && sak.søknader.isEmpty()
            })
        } returns 1

        val sakskompleksService = SakskompleksService(sakskompleksDao)

        val sak = sakskompleksService.finnEllerOpprettSak(testSykmelding.sykmelding)

        assertEquals(testSykmelding.sykmelding.aktørId, sak.aktørId)
        assertEquals(listOf(testSykmelding.sykmelding), sak.sykmeldinger)
        assertTrue(sak.søknader.isEmpty())

        verify(exactly = 1) {
            sakskompleksDao.finnSaker(testSykmelding.sykmelding.aktørId)
            sakskompleksDao.opprettSak(any())
        }
    }

    @Test
    fun `skal oppdatere sakskompleks`() {
        val sakskompleksDao = mockk<SakskompleksDao>(relaxed = true)
        val sakskompleksService = SakskompleksService(sakskompleksDao)

        val etSakskompleks = etSakskompleks()

        sakskompleksService.oppdaterSak(etSakskompleks)

        verify(exactly = 1) {
            sakskompleksDao.oppdaterSak(etSakskompleks)
        }
    }

    private fun etSakskompleks(id: UUID = UUID.randomUUID(),
                               aktørId: String = "1234567890123",
                               sykmeldinger: List<Sykmelding> = emptyList(),
                               søknader: List<Sykepengesøknad> = emptyList()) =
            Sakskompleks(
                    id = id,
                    aktørId = aktørId,
                    sykmeldinger = sykmeldinger,
                    søknader = søknader
            )
}
