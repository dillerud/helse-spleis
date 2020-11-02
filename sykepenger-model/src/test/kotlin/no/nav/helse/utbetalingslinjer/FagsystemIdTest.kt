package no.nav.helse.utbetalingslinjer

import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.FagsystemIdVisitor
import no.nav.helse.person.InntektshistorikkVol2
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingstidslinje.MaksimumUtbetaling
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class FagsystemIdTest {

    companion object {
        private const val ORGNUMMER = "123456789"
    }

    private val fagsystemIder: MutableList<FagsystemId> = mutableListOf()
    private val oppdrag: MutableMap<Oppdrag, FagsystemId> = mutableMapOf()
    private lateinit var fagsystemId: FagsystemId
    private lateinit var aktivitetslogg: Aktivitetslogg
    private val inspektør get() = FagsystemIdInspektør(fagsystemIder)

    @BeforeEach
    fun beforeEach() {
        fagsystemIder.clear()
        oppdrag.clear()
        aktivitetslogg = Aktivitetslogg()
    }

    @Test
    fun `Ny fagsystemId`() {
        opprett(1.NAV)
        assertEquals(1, fagsystemIder.size)
        assertOppdragstilstander(0, Oppdrag.Utbetalingtilstand.IkkeUtbetalt)
    }

    @Test
    fun simulere() {
        opprett(1.NAV)
        val maksdato = LocalDate.MAX
        val saksbehandler = "Z999999"
        fagsystemId.simuler(aktivitetslogg, maksdato, saksbehandler)
        assertTrue(aktivitetslogg.behov().isNotEmpty())
        assertSimuleringsbehov(maksdato, saksbehandler)
    }

    @Test
    fun `Nytt element når fagsystemId'er er forskjellige`() {
        opprett(1.NAV)
        opprett(1.NAV, startdato = 17.januar)
        assertEquals(2, fagsystemIder.size)
        assertOppdragstilstander(0, Oppdrag.Utbetalingtilstand.IkkeUtbetalt)
        assertOppdragstilstander(1, Oppdrag.Utbetalingtilstand.IkkeUtbetalt)
    }

    @Test
    fun `legger nytt oppdrag til på eksisterende fagsystemId`() {
        opprettOgUtbetal(16.AP, 5.NAV)
        opprett(5.NAV(1300))
        assertEquals(1, fagsystemIder.size)
        assertOppdragstilstander(0, Oppdrag.Utbetalingtilstand.Utbetalt, Oppdrag.Utbetalingtilstand.IkkeUtbetalt)
    }


    @Test
    fun `Ny fagsystemId når eksisterende fagsystemId er annullert`() {
        opprettOgUtbetal(16.AP, 5.NAV)
        annullere()
        opprett(15.NAV(1300), startdato = 22.januar)
        assertEquals(2, fagsystemIder.size)
        assertTrue(fagsystemIder[0].erAnnullert())
        assertFalse(fagsystemIder[1].erAnnullert())
        assertOppdragstilstander(0, Oppdrag.Utbetalingtilstand.Utbetalt, Oppdrag.Utbetalingtilstand.Utbetalt)
        assertOppdragstilstander(1, Oppdrag.Utbetalingtilstand.IkkeUtbetalt)
    }

    @Test
    fun `kan kun ha ett oppdrag som ikke er utbetalt`() {
        opprett(16.AP, 5.NAV)
        assertThrows<IllegalStateException> {
            opprett(5.NAV(1300))
        }
    }

    @Test
    fun `annullere fagsystemId som ikke er utbetalt`() {
        opprett(16.AP, 5.NAV)
        assertThrows<IllegalStateException> {
            fagsystemId.annullere(aktivitetslogg, LocalDate.MAX, "Z999999", "saksbehandler@nav.no", LocalDateTime.now())
        }
        assertTrue(aktivitetslogg.behov().isEmpty())
    }

    @Test
    fun `ubetalt annullering`() {
        opprettOgUtbetal(16.AP, 5.NAV)
        val maksdato = LocalDate.MAX
        val saksbehandler = "Z999999"
        val saksbehandlerEpost = "saksbehandler@nav.no"
        val godkjenttidspunkt = LocalDateTime.now()
        fagsystemId.annullere(aktivitetslogg, maksdato, saksbehandler, saksbehandlerEpost, godkjenttidspunkt)
        assertFalse(fagsystemId.erAnnullert())
        assertTrue(aktivitetslogg.behov().isNotEmpty())
        assertUtbetalingsbehov(maksdato, saksbehandler, saksbehandlerEpost, godkjenttidspunkt, true)
        assertOppdragstilstander(0, Oppdrag.Utbetalingtilstand.Utbetalt, Oppdrag.Utbetalingtilstand.Overført)
    }

    @Test
    fun `utbetale på en annullert fagsystemId`() {
        opprettOgUtbetal(16.AP, 5.NAV)
        annullere()
        assertThrows<IllegalStateException> { fagsystemId.utbetal(aktivitetslogg, LocalDate.MAX, "Z999999", "saksbehandler@nav.no", LocalDateTime.now()) }
    }

    @Test
    fun `annullere fagsystemId med ubetalte oppdrag`() {
        opprettOgUtbetal(16.AP, 5.NAV)
        opprett(16.NAV)
        annullere()
        assertTrue(fagsystemId.erAnnullert())
        assertOppdragstilstander(0, Oppdrag.Utbetalingtilstand.Utbetalt, Oppdrag.Utbetalingtilstand.IkkeUtbetalt, Oppdrag.Utbetalingtilstand.Utbetalt)
    }

    @Test
    fun `mapper riktig når det finnes flere fagsystemId'er`() {
        val oppdrag1 = opprettOgUtbetal(16.AP, 5.NAV)
        val oppdrag2 = opprettOgUtbetal(16.AP, 5.NAV, startdato = 1.mars)
        val oppdrag1Oppdatert = opprett(16.AP, 5.NAV(1300))
        assertEquals(2, fagsystemIder.size)
        assertEquals(oppdrag1.fagsystemId(), oppdrag1Oppdatert.fagsystemId())
        assertNotEquals(oppdrag2.fagsystemId(), oppdrag1Oppdatert.fagsystemId())
    }

    private fun assertUtbetalingsbehov(maksdato: LocalDate, saksbehandler: String, saksbehandlerEpost: String, godkjenttidspunkt: LocalDateTime, erAnnullering: Boolean) {
        assertUtbetalingsbehov { utbetalingbehov ->
            assertEquals("$maksdato", utbetalingbehov["maksdato"])
            assertEquals(saksbehandler, utbetalingbehov["saksbehandler"])
            assertEquals(saksbehandlerEpost, utbetalingbehov["saksbehandlerEpost"])
            assertEquals("$godkjenttidspunkt", utbetalingbehov.getValue("godkjenttidspunkt"))
            assertEquals(erAnnullering, utbetalingbehov["annullering"] as Boolean)
        }
    }

    private fun assertSimuleringsbehov(maksdato: LocalDate, saksbehandler: String) {
        assertSimuleringsbehov { utbetalingbehov ->
            assertEquals("$maksdato", utbetalingbehov["maksdato"])
            assertEquals(saksbehandler, utbetalingbehov["saksbehandler"])
        }
    }

    private fun assertUtbetalingsbehov(block: (Map<String, Any>) -> Unit) {
        aktivitetslogg.sisteBehov(Behovtype.Utbetaling, block)
    }

    private fun assertSimuleringsbehov(block: (Map<String, Any>) -> Unit) {
        aktivitetslogg.sisteBehov(Behovtype.Simulering, block)
    }

    private fun Aktivitetslogg.sisteBehov(type: Behovtype, block: (Map<String, Any>) -> Unit) {
        this.behov()
            .last { it.type == type }
            .detaljer()
            .also { block(it) }
    }

    private fun assertOppdragstilstander(fagsystemIndeks: Int, vararg tilstander: Oppdrag.Utbetalingtilstand) {
        assertEquals(tilstander.toList(), inspektør.oppdragtilstander(fagsystemIndeks))
    }

    private fun annullere() {
        val maksdato = LocalDate.MAX
        val saksbehandler = "Z999999"
        val saksbehandlerEpost = "saksbehandler@nav.no"
        val godkjenttidspunkt = LocalDateTime.now()
        fagsystemId.annullere(aktivitetslogg, maksdato, saksbehandler, saksbehandlerEpost, godkjenttidspunkt)
        assertTrue(aktivitetslogg.behov().isNotEmpty())
        assertUtbetalingsbehov(maksdato, saksbehandler, saksbehandlerEpost, godkjenttidspunkt, true)
        fagsystemId.håndter(utbetalingHendelse(oppdrag.keys.first()))
    }

    private fun opprettOgUtbetal(vararg dager: Utbetalingsdager, startdato: LocalDate = 1.januar, sisteDato: LocalDate? = null) =
        opprett(*dager, startdato = startdato, sisteDato = sisteDato).also {
            val fagsystemId = oppdrag.getValue(it)
            val maksdato = LocalDate.MAX
            val saksbehandler = "Z999999"
            val saksbehandlerEpost = "saksbehandler@nav.no"
            val godkjenttidspunkt = LocalDateTime.now()
            fagsystemId.utbetal(aktivitetslogg, maksdato, saksbehandler, saksbehandlerEpost, godkjenttidspunkt)
            assertTrue(aktivitetslogg.behov().isNotEmpty())
            assertUtbetalingsbehov(maksdato, saksbehandler, saksbehandlerEpost, godkjenttidspunkt, false)
            fagsystemId.håndter(utbetalingHendelse(it))
        }

    private fun opprett(vararg dager: Utbetalingsdager, startdato: LocalDate = 1.januar, sisteDato: LocalDate? = null): Oppdrag {
        val tidslinje = tidslinjeOf(*dager, startDato = startdato)
        MaksimumUtbetaling(
            listOf(tidslinje),
            aktivitetslogg,
            listOf(1.januar),
            1.januar
        ).betal().let {
            return OppdragBuilder(
                tidslinje,
                ORGNUMMER,
                Fagområde.SykepengerRefusjon,
                sisteDato ?: tidslinje.sisteDato()
            ).result().also {
                fagsystemId = FagsystemId.kobleTil(fagsystemIder, it, aktivitetslogg)
                oppdrag[it] = fagsystemId
            }
        }
    }

    private fun utbetalingHendelse(oppdrag: Oppdrag) = UtbetalingHendelse(
        UUID.randomUUID(),
        UUID.randomUUID().toString(),
        "AKTØRID",
        "FØDSELSNUMMER",
        ORGNUMMER,
        oppdrag.fagsystemId(),
        UtbetalingHendelse.Oppdragstatus.AKSEPTERT,
        "",
        LocalDateTime.now(),
        "EN SAKSBEHANDLER",
        "saksbehandler@saksbehandlersen.no",
        false
    )

    private class FagsystemIdInspektør(fagsystemIder: List<FagsystemId>) : FagsystemIdVisitor {
        private val fagsystemIder = mutableListOf<String>()
        private val oppdragsliste = mutableListOf<List<Oppdrag>>()
        private val oppdragstilstander = mutableMapOf<Int, MutableList<Oppdrag.Utbetalingtilstand>>()
        private var fagsystemIdTeller = 0
        private var oppdragteller = 0

        init {
            fagsystemIder.onEach { it.accept(this) }
        }

        internal fun oppdragtilstander(fagsystemIndeks: Int) = oppdragstilstander[fagsystemIndeks] ?: fail {
            "Finner ikke fagsystem med indeks $fagsystemIndeks"
        }

        internal fun oppdragtilstand(fagsystemIndeks: Int, oppdragIndeks: Int) =
            oppdragstilstander[fagsystemIndeks]?.get(oppdragIndeks) ?: fail { "Finner ikke fagsystem med indeks $fagsystemIndeks eller oppdrag med indeks $oppdragIndeks" }

        override fun preVisitFagsystemId(fagsystemId: FagsystemId, id: String, fagområde: Fagområde) {
            fagsystemIder.add(fagsystemIdTeller, id)
        }

        override fun preVisitOppdragsliste(oppdragsliste: List<Oppdrag>) {
            oppdragteller = 0
            this.oppdragsliste.add(fagsystemIdTeller, oppdragsliste)
        }

        override fun preVisitOppdrag(
            oppdrag: Oppdrag,
            totalBeløp: Int,
            nettoBeløp: Int,
            tidsstempel: LocalDateTime,
            utbetalingtilstand: Oppdrag.Utbetalingtilstand
        ) {
            oppdragstilstander.getOrPut(fagsystemIdTeller) { mutableListOf() }
                .add(0, utbetalingtilstand)
        }

        override fun postVisitOppdrag(
            oppdrag: Oppdrag,
            totalBeløp: Int,
            nettoBeløp: Int,
            tidsstempel: LocalDateTime,
            utbetalingtilstand: Oppdrag.Utbetalingtilstand
        ) {
            oppdragteller += 1
        }

        override fun postVisitFagsystemId(fagsystemId: FagsystemId, id: String, fagområde: Fagområde) {
            fagsystemIdTeller += 1
        }
    }
}
