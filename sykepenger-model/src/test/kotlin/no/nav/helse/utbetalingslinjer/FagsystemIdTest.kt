package no.nav.helse.utbetalingslinjer

import no.nav.helse.hendelser.AnnullerUtbetaling
import no.nav.helse.hendelser.UtbetalingHendelse
import no.nav.helse.hendelser.Utbetalingsgodkjenning
import no.nav.helse.person.Aktivitetslogg
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.helse.person.FagsystemIdVisitor
import no.nav.helse.person.IAktivitetslogg
import no.nav.helse.testhelpers.*
import no.nav.helse.utbetalingstidslinje.MaksimumUtbetaling
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class FagsystemIdTest {

    companion object {
        private const val FNR = "12345678910"
        private const val AKTØRID = "1234567891011"
        private const val ORGNUMMER = "123456789"
        private const val SAKSBEHANDLER = "EN SAKSBEHANDLER"
        private const val SAKSBEHANDLEREPOST = "saksbehandler@saksbehandlersen.no"
        private val MAKSDATO = LocalDate.now()
        private val GODKJENTTIDSPUNKT = LocalDateTime.now()
    }

    private val fagsystemIder: MutableList<FagsystemId> = mutableListOf()
    private val oppdrag: MutableMap<Oppdrag, FagsystemId> = mutableMapOf()
    private lateinit var fagsystemId: FagsystemId
    private lateinit var aktivitetslogg: IAktivitetslogg
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
            håndterAnnullering(LocalDate.MAX, "Z999999", "saksbehandler@nav.no", LocalDateTime.now())
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
        håndterAnnullering(maksdato, saksbehandler, saksbehandlerEpost, godkjenttidspunkt)
        assertFalse(fagsystemId.erAnnullert())
        assertTrue(aktivitetslogg.behov().isNotEmpty())
        assertUtbetalingsbehov(maksdato, saksbehandler, saksbehandlerEpost, godkjenttidspunkt, true)
        assertOppdragstilstander(0, Oppdrag.Utbetalingtilstand.Utbetalt, Oppdrag.Utbetalingtilstand.Overført)
    }

    @Test
    fun `utbetale på en annullert fagsystemId`() {
        opprettOgUtbetal(16.AP, 5.NAV)
        annullere()
        assertThrows<IllegalStateException> { håndterGodkjenning(true, LocalDate.MAX, "Z999999", "saksbehandler@nav.no", LocalDateTime.now()) }
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
    fun `avslag på utbetalingsgodkjenning sletter ubetalte oppdrag`() {
        opprettOgUtbetal(16.AP, 5.NAV, godkjent = false)
        assertTrue(fagsystemId.erTom())
    }

    @Test
    fun `godkjent utbetalingsgodkjenning`() {
        opprettOgUtbetal(16.AP, 5.NAV, godkjent = true)
        assertFalse(fagsystemId.erTom())
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

    private fun IAktivitetslogg.sisteBehov(type: Behovtype, block: (Map<String, Any>) -> Unit) {
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
        val saksbehandler = SAKSBEHANDLER
        val saksbehandlerEpost = SAKSBEHANDLEREPOST
        val godkjenttidspunkt = LocalDateTime.now()
        håndterAnnullering(maksdato, saksbehandler, saksbehandlerEpost, godkjenttidspunkt)
        assertTrue(aktivitetslogg.behov().isNotEmpty())
        assertUtbetalingsbehov(maksdato, saksbehandler, saksbehandlerEpost, godkjenttidspunkt, true)
        fagsystemId.håndter(utbetalingHendelse(oppdrag.keys.first()))
    }

    private fun opprettOgUtbetal(vararg dager: Utbetalingsdager, startdato: LocalDate = 1.januar, sisteDato: LocalDate? = null, godkjent: Boolean = true) =
        opprett(*dager, startdato = startdato, sisteDato = sisteDato).also {
            val fagsystemId = oppdrag.getValue(it)
            val maksdato = MAKSDATO
            val saksbehandler = SAKSBEHANDLER
            val saksbehandlerEpost = SAKSBEHANDLEREPOST
            val godkjenttidspunkt = GODKJENTTIDSPUNKT
            håndterGodkjenning(godkjent, maksdato, saksbehandler, saksbehandlerEpost, godkjenttidspunkt)
            if (!godkjent) return@also
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

    private fun håndterGodkjenning(
        godkjent: Boolean,
        maksdato: LocalDate,
        saksbehandler: String,
        saksbehandlerEpost: String,
        godkjenttidspunkt: LocalDateTime
    ) {
        fagsystemId.håndter(utbetalingsgodkjenning(godkjent, saksbehandler, saksbehandlerEpost, godkjenttidspunkt), maksdato)
    }

    private fun håndterAnnullering(
        maksdato: LocalDate,
        saksbehandler: String,
        saksbehandlerEpost: String,
        godkjenttidspunkt: LocalDateTime
    ) {
        fagsystemId.håndter(annullering(saksbehandler, saksbehandlerEpost, godkjenttidspunkt), maksdato)
    }

    private fun utbetalingsgodkjenning(
        godkjent: Boolean,
        saksbehandler: String,
        saksbehandlerEpost: String,
        godkjenttidspunkt: LocalDateTime
    ) = Utbetalingsgodkjenning(
        UUID.randomUUID(),
        AKTØRID,
        FNR,
        ORGNUMMER,
        UUID.randomUUID().toString(),
        saksbehandler,
        saksbehandlerEpost,
        godkjent,
        godkjenttidspunkt,
        false
    ).also { aktivitetslogg = it }

    private fun annullering(
        saksbehandler: String,
        saksbehandlerEpost: String,
        godkjenttidspunkt: LocalDateTime
    ) = AnnullerUtbetaling(
        UUID.randomUUID(),
        AKTØRID,
        FNR,
        ORGNUMMER,
        fagsystemId.fagsystemId(),
        saksbehandler,
        saksbehandlerEpost,
        godkjenttidspunkt
    ).also { aktivitetslogg = it }

    private fun utbetalingHendelse(oppdrag: Oppdrag) = UtbetalingHendelse(
        UUID.randomUUID(),
        UUID.randomUUID().toString(),
        AKTØRID,
        FNR,
        ORGNUMMER,
        oppdrag.fagsystemId(),
        UtbetalingHendelse.Oppdragstatus.AKSEPTERT,
        "",
        GODKJENTTIDSPUNKT,
        SAKSBEHANDLER,
        SAKSBEHANDLEREPOST,
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

        fun oppdragtilstander(fagsystemIndeks: Int) = oppdragstilstander[fagsystemIndeks] ?: fail {
            "Finner ikke fagsystem med indeks $fagsystemIndeks"
        }

        fun oppdragtilstand(fagsystemIndeks: Int, oppdragIndeks: Int) =
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
