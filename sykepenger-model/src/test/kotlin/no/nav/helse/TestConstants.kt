package no.nav.helse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.TestConstants.objectMapper
import no.nav.helse.behov.Behov
import no.nav.helse.behov.BehovsTyper
import no.nav.helse.hendelser.inntektsmelding.InntektsmeldingHendelse
import no.nav.helse.hendelser.påminnelse.Påminnelse
import no.nav.helse.hendelser.saksbehandling.ManuellSaksbehandlingHendelse
import no.nav.helse.hendelser.sykepengehistorikk.Sykepengehistorikk
import no.nav.helse.hendelser.sykepengehistorikk.SykepengehistorikkHendelse
import no.nav.helse.hendelser.søknad.NySøknadHendelse
import no.nav.helse.hendelser.søknad.SendtSøknadHendelse
import no.nav.helse.hendelser.søknad.Sykepengesøknad
import no.nav.helse.sak.TilstandType
import no.nav.helse.sykdomstidslinje.ConcreteSykdomstidslinje
import no.nav.inntektsmeldingkontrakt.*
import no.nav.syfo.kafka.sykepengesoknad.dto.*
import org.junit.jupiter.api.fail
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.util.*

internal object TestConstants {
    internal val objectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    val sykeperiodeFOM = 16.september
    val sykeperiodeTOM = 5.oktober
    val egenmeldingFom = 12.september
    val egenmeldingTom = 15.september
    val ferieFom = 1.oktober
    val ferieTom = 4.oktober
    val fakeFNR = "01019510000"

    fun søknadDTO(
        id: String = UUID.randomUUID().toString(),
        status: SoknadsstatusDTO,
        aktørId: String = UUID.randomUUID().toString().substring(0, 13),
        fødselsnummer: String = UUID.randomUUID().toString(),
        arbeidGjenopptatt: LocalDate? = null,
        korrigerer: String? = null,
        egenmeldinger: List<PeriodeDTO> = listOf(
            PeriodeDTO(
                fom = egenmeldingFom,
                tom = egenmeldingTom
            )
        ),
        søknadsperioder: List<SoknadsperiodeDTO> = listOf(
            SoknadsperiodeDTO(
                fom = sykeperiodeFOM,
                tom = 30.september,
                sykmeldingsgrad = 100
            ), SoknadsperiodeDTO(
                fom = 5.oktober,
                tom = sykeperiodeTOM,
                sykmeldingsgrad = 100
            )
        ),
        fravær: List<FravarDTO> = listOf(
            FravarDTO(
                fom = ferieFom,
                tom = ferieTom,
                type = FravarstypeDTO.FERIE
            )
        ),
        arbeidsgiver: ArbeidsgiverDTO? = ArbeidsgiverDTO(
            navn = "enArbeidsgiver",
            orgnummer = "123456789"
        ),
        sendtNav: LocalDateTime = sykeperiodeTOM.plusDays(10).atStartOfDay()
    ) = SykepengesoknadDTO(
        id = id,
        type = SoknadstypeDTO.ARBEIDSTAKERE,
        status = status,
        aktorId = aktørId,
        fnr = fødselsnummer,
        sykmeldingId = UUID.randomUUID().toString(),
        arbeidsgiver = arbeidsgiver,
        arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
        arbeidsgiverForskutterer = ArbeidsgiverForskuttererDTO.JA,
        fom = søknadsperioder.sortedBy { it.fom }.first().fom,
        tom = søknadsperioder.sortedBy { it.tom }.last().tom,
        startSyketilfelle = LocalDate.of(2019, Month.SEPTEMBER, 10),
        arbeidGjenopptatt = arbeidGjenopptatt,
        korrigerer = korrigerer,
        opprettet = LocalDateTime.now(),
        sendtNav = sendtNav,
        sendtArbeidsgiver = LocalDateTime.of(2019, Month.SEPTEMBER, 30, 0, 0, 0),
        egenmeldinger = egenmeldinger,
        soknadsperioder = søknadsperioder,
        fravar = fravær
    )

    fun sendtSøknadHendelse(
        id: String = UUID.randomUUID().toString(),
        aktørId: String = UUID.randomUUID().toString(),
        fødselsnummer: String = UUID.randomUUID().toString(),
        arbeidGjenopptatt: LocalDate? = null,
        korrigerer: String? = null,
        egenmeldinger: List<PeriodeDTO> = listOf(
            PeriodeDTO(
                fom = egenmeldingFom,
                tom = egenmeldingTom
            )
        ),
        søknadsperioder: List<SoknadsperiodeDTO> = listOf(
            SoknadsperiodeDTO(
                fom = sykeperiodeFOM,
                tom = 30.september,
                sykmeldingsgrad = 100
            ), SoknadsperiodeDTO(
                fom = 5.oktober,
                tom = sykeperiodeTOM,
                sykmeldingsgrad = 100
            )
        ),
        fravær: List<FravarDTO> = listOf(
            FravarDTO(
                fom = ferieFom,
                tom = ferieTom,
                type = FravarstypeDTO.FERIE
            )
        ),
        arbeidsgiver: ArbeidsgiverDTO? = ArbeidsgiverDTO(
            navn = "enArbeidsgiver",
            orgnummer = "123456789"
        ),
        sendtNav: LocalDateTime = sykeperiodeTOM.plusDays(10).atStartOfDay()
    ) = SendtSøknadHendelse(
        Sykepengesøknad(
            søknadDTO(
                id = id,
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                arbeidGjenopptatt = arbeidGjenopptatt,
                korrigerer = korrigerer,
                egenmeldinger = egenmeldinger,
                søknadsperioder = søknadsperioder,
                fravær = fravær,
                status = SoknadsstatusDTO.SENDT,
                arbeidsgiver = arbeidsgiver,
                sendtNav = sendtNav
            ).toJsonNode()
        )
    )

    fun nySøknadHendelse(
        id: String = UUID.randomUUID().toString(),
        aktørId: String = UUID.randomUUID().toString(),
        fødselsnummer: String = fakeFNR,
        arbeidGjenopptatt: LocalDate? = null,
        korrigerer: String? = null,
        egenmeldinger: List<PeriodeDTO> = listOf(
            PeriodeDTO(
                fom = egenmeldingFom,
                tom = egenmeldingTom
            )
        ),
        søknadsperioder: List<SoknadsperiodeDTO> = listOf(
            SoknadsperiodeDTO(
                fom = sykeperiodeFOM,
                tom = 30.september,
                sykmeldingsgrad = 100
            ), SoknadsperiodeDTO(
                fom = 5.oktober,
                tom = sykeperiodeTOM,
                sykmeldingsgrad = 100
            )
        ),
        fravær: List<FravarDTO> = listOf(
            FravarDTO(
                fom = ferieFom,
                tom = ferieTom,
                type = FravarstypeDTO.FERIE
            )
        ),
        arbeidsgiver: ArbeidsgiverDTO? = ArbeidsgiverDTO(
            navn = "enArbeidsgiver",
            orgnummer = "123456789"
        ),
        sendtNav: LocalDateTime = sykeperiodeTOM.plusDays(10).atStartOfDay()
    ) = NySøknadHendelse(
        Sykepengesøknad(
            søknadDTO(
                id = id,
                aktørId = aktørId,
                fødselsnummer = fakeFNR,
                arbeidGjenopptatt = arbeidGjenopptatt,
                korrigerer = korrigerer,
                egenmeldinger = egenmeldinger,
                søknadsperioder = søknadsperioder,
                fravær = fravær,
                status = SoknadsstatusDTO.NY,
                arbeidsgiver = arbeidsgiver,
                sendtNav = sendtNav
            ).toJsonNode()
        )
    )

    fun søknadsperiode(fom: LocalDate, tom: LocalDate, sykemeldingsgrad: Int = 100, faktiskGrad: Int? = null) =
        SoknadsperiodeDTO(fom = fom, tom = tom, sykmeldingsgrad = sykemeldingsgrad, faktiskGrad = faktiskGrad)


    fun inntektsmeldingHendelse(
        aktørId: String = "",
        fødselsnummer: String = fakeFNR,
        virksomhetsnummer: String? = "123456789",
        beregnetInntekt: BigDecimal? = 666.toBigDecimal(),
        førsteFraværsdag: LocalDate = 10.september,
        arbeidsgiverperioder: List<Periode> = listOf(
            Periode(10.september, 10.september.plusDays(16))
        ),
        ferieperioder: List<Periode> = emptyList(),
        refusjon: Refusjon = Refusjon(
            beloepPrMnd = 666.toBigDecimal(),
            opphoersdato = null
        ),
        endringerIRefusjoner: List<EndringIRefusjon> = emptyList()
    ) =
        InntektsmeldingHendelse(
            no.nav.helse.hendelser.inntektsmelding.Inntektsmelding(
                inntektsmeldingDTO(
                    aktørId,
                    fødselsnummer,
                    virksomhetsnummer,
                    førsteFraværsdag,
                    arbeidsgiverperioder,
                    ferieperioder,
                    refusjon,
                    endringerIRefusjoner,
                    beregnetInntekt
                ).toJsonNode()
            )
        )

    fun inntektsmeldingDTO(
        aktørId: String = "",
        fødselsnummer: String = fakeFNR,
        virksomhetsnummer: String? = "123456789",
        førsteFraværsdag: LocalDate = 10.september,
        arbeidsgiverperioder: List<Periode> = listOf(
            Periode(10.september, 10.september.plusDays(16))
        ),
        feriePerioder: List<Periode> = emptyList(),
        refusjon: Refusjon = Refusjon(
            beloepPrMnd = 666.toBigDecimal(),
            opphoersdato = null
        ),
        endringerIRefusjoner: List<EndringIRefusjon> = emptyList(),
        beregnetInntekt: BigDecimal? = 666.toBigDecimal()
    ) =
        Inntektsmelding(
            inntektsmeldingId = "",
            arbeidstakerFnr = fødselsnummer,
            arbeidstakerAktorId = aktørId,
            virksomhetsnummer = virksomhetsnummer,
            arbeidsgiverFnr = null,
            arbeidsgiverAktorId = null,
            arbeidsgivertype = Arbeidsgivertype.VIRKSOMHET,
            arbeidsforholdId = null,
            beregnetInntekt = beregnetInntekt,
            refusjon = refusjon,
            endringIRefusjoner = endringerIRefusjoner,
            opphoerAvNaturalytelser = emptyList(),
            gjenopptakelseNaturalytelser = emptyList(),
            arbeidsgiverperioder = arbeidsgiverperioder,
            status = Status.GYLDIG,
            arkivreferanse = "",
            ferieperioder = feriePerioder,
            foersteFravaersdag = førsteFraværsdag,
            mottattDato = LocalDateTime.now()
        )

    fun responsFraSpole(perioder: List<SpolePeriode>) = mapOf<String, Any>(
        "perioder" to perioder.map {
            mapOf<String, Any>(
                "fom" to "${it.fom}",
                "tom" to "${it.tom}",
                "grad" to it.grad
            )
        }
    )

    fun sykepengehistorikk(
        perioder: List<SpolePeriode>,
        organisasjonsnummer: String = "123546564",
        aktørId: String = "1",
        fødselsnummer: String = fakeFNR,
        vedtaksperiodeId: UUID = UUID.randomUUID()
    ): Sykepengehistorikk {
        val behov = Behov.nyttBehov(
            BehovsTyper.Sykepengehistorikk, mapOf(
                "organisasjonsnummer" to organisasjonsnummer,
                "sakskompleksId" to vedtaksperiodeId.toString(),
                "aktørId" to aktørId,
                "fødselsnummer" to fødselsnummer
            )
        ).also {
            it.løsBehov(
                responsFraSpole(
                    perioder = perioder
                )
            )
        }
        return Sykepengehistorikk(objectMapper.readTree(behov.toJson()))
    }

    fun sykepengehistorikkHendelse(
        sisteHistoriskeSykedag: LocalDate? = LocalDate.now(),
        perioder: List<SpolePeriode>? = null,
        organisasjonsnummer: String = "123546564",
        aktørId: String = "1",
        fødselsnummer: String = fakeFNR,
        vedtaksperiodeId: UUID = UUID.randomUUID()
    ) = SykepengehistorikkHendelse(sykepengehistorikk(
        perioder = perioder ?: sisteHistoriskeSykedag?.let {
            listOf(
                SpolePeriode(
                    fom = sisteHistoriskeSykedag.minusMonths(1),
                    tom = sisteHistoriskeSykedag,
                    grad = "100"
                )
            )
        } ?: emptyList(),
        organisasjonsnummer = organisasjonsnummer,
        aktørId = aktørId,
        fødselsnummer = fødselsnummer,
        vedtaksperiodeId = vedtaksperiodeId
    ))

    fun manuellSaksbehandlingLøsning(
        organisasjonsnummer: String = "123546564",
        aktørId: String = "1",
        fødselsnummer: String = fakeFNR,
        vedtaksperiodeId: String = UUID.randomUUID().toString(),
        utbetalingGodkjent: Boolean,
        saksbehandler: String
    ): Behov {
        return Behov.nyttBehov(
            BehovsTyper.Sykepengehistorikk, mapOf(
                "organisasjonsnummer" to organisasjonsnummer,
                "sakskompleksId" to vedtaksperiodeId,
                "aktørId" to aktørId,
                "fødselsnummer" to fødselsnummer,
                "saksbehandlerIdent" to saksbehandler
            )
        ).also {
            it.løsBehov(
                mapOf(
                    "godkjent" to utbetalingGodkjent
                )
            )
        }
    }

    fun manuellSaksbehandlingHendelse(
        organisasjonsnummer: String = "123546564",
        aktørId: String = "1",
        fødselsnummer: String = fakeFNR,
        vedtaksperiodeId: String = UUID.randomUUID().toString(),
        utbetalingGodkjent: Boolean,
        saksbehandler: String
    ): ManuellSaksbehandlingHendelse {
        return ManuellSaksbehandlingHendelse(
            manuellSaksbehandlingLøsning(
                organisasjonsnummer = organisasjonsnummer,
                aktørId = aktørId,
                fødselsnummer = fødselsnummer,
                vedtaksperiodeId = vedtaksperiodeId,
                utbetalingGodkjent = utbetalingGodkjent,
                saksbehandler = saksbehandler
            )
        )
    }

    fun påminnelseHendelse(vedtaksperiodeId: UUID, tilstand: TilstandType) = Påminnelse.fraJson(objectMapper.convertValue(mapOf(
        "aktørId" to "1",
        "fødselsnummer" to fakeFNR,
        "organisasjonsnummer" to "123546564",
        "vedtaksperiodeId" to vedtaksperiodeId,
        "tilstand" to tilstand.toString(),
        "antallGangerPåminnet" to 0,
        "tilstandsendringstidspunkt" to LocalDateTime.now().toString(),
        "påminnelsestidspunkt" to LocalDateTime.now().toString(),
        "nestePåminnelsestidspunkt" to LocalDateTime.now().toString()
    ))) ?: fail { "påminnelse er null" }
}

internal data class SpolePeriode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: String
)

internal class Uke(ukenr: Long) {
    val mandag = LocalDate.of(2018, 1, 1)
        .plusWeeks(ukenr - 1L)
    val tirsdag get() = mandag.plusDays(1)
    val onsdag get() = mandag.plusDays(2)
    val torsdag get() = mandag.plusDays(3)
    val fredag get() = mandag.plusDays(4)
    val lørdag get() = mandag.plusDays(5)
    val søndag get() = mandag.plusDays(6)
}

internal fun SykepengesoknadDTO.toSendtSøknadHendelse() = SendtSøknadHendelse(
    Sykepengesøknad(
        this.copy(
            status = SoknadsstatusDTO.SENDT
        ).toJsonNode()
    )
)

internal operator fun ConcreteSykdomstidslinje.get(index: LocalDate) = flatten().firstOrNull { it.førsteDag() == index }

internal fun SykepengesoknadDTO.toJsonNode(): JsonNode = objectMapper.valueToTree(this)
internal fun Inntektsmelding.toJsonNode(): JsonNode = objectMapper.valueToTree(this)

internal val Int.juni
    get() = LocalDate.of(2019, Month.JUNE, this)

internal val Int.juli
    get() = LocalDate.of(2019, Month.JULY, this)

internal val Int.august
    get() = LocalDate.of(2019, Month.AUGUST, this)

internal val Int.september
    get() = LocalDate.of(2019, Month.SEPTEMBER, this)

internal val Int.oktober
    get() = LocalDate.of(2019, Month.OCTOBER, this)
