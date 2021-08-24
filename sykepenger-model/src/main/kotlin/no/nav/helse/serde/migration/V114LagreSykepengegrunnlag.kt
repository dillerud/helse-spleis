package no.nav.helse.serde.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.Grunnbeløp
import no.nav.helse.person.ArbeidsgiverInntektsopplysning.Companion.inntekt
import java.time.LocalDate

internal class V114LagreSykepengegrunnlag : JsonMigration(version = 114) {

    override val description: String = "Beregne og lagre sykepengegrunnlag for alle vilkårsgrunnlagelementert"

    override fun doMigration(jsonNode: ObjectNode, meldingerSupplier: MeldingerSupplier) {
        jsonNode["vilkårsgrunnlagHistorikk"].flatMap {
            it["vilkårsgrunnlag"]
        }.forEach { genererSykepengegrunnlag((it as ObjectNode), jsonNode) }
    }

    private fun genererSykepengegrunnlag(vilkårsgrunnlag: ObjectNode, person: ObjectNode) {
        val skjæringstidspunkt = vilkårsgrunnlag["skjæringstidspunkt"].asText()
        val sykepengegrunnlag = vilkårsgrunnlag.with("sykepengegrunnlag")

        sykepengegrunnlag.put("sykepengegrunnlag", 0.0)
        sykepengegrunnlag.put("grunnlagForSykepengegrunnlag", 0.0)

        val arbeidsgiverInntektsopplysninger = sykepengegrunnlag.withArray("arbeidsgiverInntektsopplysninger")


        val grunnlagForSykepengegrunnlag = person["arbeidsgivere"].sumOf { arbeidsgiver ->
            leggTilArbeidsgiverInntektsopplysning(arbeidsgiverInntektsopplysninger, arbeidsgiver as ObjectNode, skjæringstidspunkt)
        } * 12
        val sykepengegrunnlag2 = minOf(grunnlagForSykepengegrunnlag, Grunnbeløp.`6G`.beløp(LocalDate.parse(skjæringstidspunkt)).reflection { årlig, _, _, _ -> årlig }) // TODO: trenger vi vikrningsdato fortsatt

        sykepengegrunnlag.put("sykepengegrunnlag", sykepengegrunnlag2)
        sykepengegrunnlag.put("grunnlagForSykepengegrunnlag", grunnlagForSykepengegrunnlag)
    }

    private fun leggTilArbeidsgiverInntektsopplysning(arbeidsgiverInntektsopplysninger: ArrayNode, arbeidsgiver: ObjectNode, skjæringstidspunkt: String): Double {
        val inntektsopplysning = finnInntektsopplysning(arbeidsgiver["inntektshistorikk"], skjæringstidspunkt)

        if (inntektsopplysning != null) {
            val arbeidsgiverInntektsopplysning = arbeidsgiverInntektsopplysninger.addObject()
            arbeidsgiverInntektsopplysning.put("orgnummer", arbeidsgiver["organisasjonsnummer"].asText())
            arbeidsgiverInntektsopplysning.withArray("inntektsopplysning").add(inntektsopplysning) // TODO: hvis vi skal fjerne inntektsopplysningsreferasne SKAL dette ikke være array

            return if (inntektsopplysning.has("skatteopplysninger")) beregnBeløpFraSkatt(inntektsopplysning.withArray("skatteopplysninger")) else inntektsopplysning["beløp"].asDouble()
        }
        return 0.0
    }

    private fun beregnBeløpFraSkatt(skatteopplysninger: ArrayNode) = skatteopplysninger
        .sumOf { it["beløp"].asDouble() }
        .div(3)

    private fun finnInntektsopplysning(inntektshistorikk: JsonNode, skjæringstidspunkt: String): JsonNode? =
        inntektshistorikk.firstOrNull()
            ?.get("inntektsopplysninger")
            ?.filterNot { it.has("skatteopplysninger") && it["skatteopplysninger"].first()["kilde"].asText() == "SKATT_SAMMENLIGNINGSGRUNNLAG" }
            ?.filter {
                skjæringstidspunkt == if (it.has("skatteopplysninger")) it["skatteopplysninger"].first()["dato"].asText() else it["dato"].asText()  //TODO: vi har ikke sisteførstekjenteukjentedrittdag fra IT :sob: hvis vi ikke finner inntekt, ta første tidligste IT-inntekt etter skjæringstidspunkt
            }?.maxByOrNull {
                when (if (it.has("skatteopplysninger")) it["skatteopplysninger"].first()["kilde"].asText() else it["kilde"].asText()) {
                    "SAKSBEHANDLER" -> 100
                    "INFOTRYGD" -> 80
                    "INNTEKTSMELDING" -> 60
                    "SKATT_SYKEPENGEGRUNNLAG" -> 40
                    else -> 0
                }
            }
}


