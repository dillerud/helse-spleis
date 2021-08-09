package no.nav.helse.hendelser

import no.nav.helse.person.ArbeidstakerHendelse
import no.nav.helse.økonomi.Inntekt
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


class OverstyrInntekt(
    meldingsreferanseId: UUID,
    private val fødselsnummer: String,
    private val aktørId: String,
    private val organisasjonsnummer: String,
    internal  val inntekt: Inntekt,
    internal val skjæringstidspunkt: LocalDate,
    internal val ident: String
) : ArbeidstakerHendelse(meldingsreferanseId) {

    override fun aktørId() = aktørId
    override fun fødselsnummer() = fødselsnummer
    override fun organisasjonsnummer() = organisasjonsnummer

}