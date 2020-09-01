package no.nav.helse.hendelser


import no.nav.helse.person.*
import java.util.*

class Ytelser(
    private val meldingsreferanseId: UUID,
    private val aktørId: String,
    private val fødselsnummer: String,
    private val organisasjonsnummer: String,
    internal val vedtaksperiodeId: String,
    private val utbetalingshistorikk: Utbetalingshistorikk,
    private val foreldrepermisjon: Foreldrepermisjon,
    aktivitetslogg: Aktivitetslogg
) : ArbeidstakerHendelse(aktivitetslogg) {
    internal fun utbetalingshistorikk() = utbetalingshistorikk

    internal fun foreldrepenger() = foreldrepermisjon

    internal fun valider(periode: Periode, periodetype: Periodetype) =
        utbetalingshistorikk.valider(periode, periodetype)

    internal fun addInntekt(organisasjonsnummer: String, inntektshistorikk: Inntektshistorikk) {
        utbetalingshistorikk().addInntekter(this.meldingsreferanseId, organisasjonsnummer, inntektshistorikk)
    }

    internal fun addInntekt(organisasjonsnummer: String, inntektshistorikk: InntektshistorikkVol2) {
        utbetalingshistorikk().addInntekter(this.meldingsreferanseId, organisasjonsnummer, inntektshistorikk)
    }

    override fun aktørId(): String {
        return aktørId
    }

    override fun fødselsnummer(): String {
        return fødselsnummer
    }

    override fun organisasjonsnummer(): String {
        return organisasjonsnummer
    }
}
