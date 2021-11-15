package no.nav.helse.person

import no.nav.helse.Grunnbeløp
import no.nav.helse.person.Aktivitetslogg.Aktivitet.Etterlevelse.Vurderingsresultat.Companion.`§8-10 ledd 2`
import no.nav.helse.person.ArbeidsgiverInntektsopplysning.Companion.inntekt
import no.nav.helse.person.ArbeidsgiverInntektsopplysning.Companion.inntektsopplysningPerArbeidsgiver
import no.nav.helse.person.Sykepengegrunnlag.Begrensning.*
import no.nav.helse.økonomi.Inntekt
import java.time.LocalDate

internal class Sykepengegrunnlag(
    internal val sykepengegrunnlag: Inntekt,
    private val arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
    internal val grunnlagForSykepengegrunnlag: Inntekt,
    internal val begrensning: Begrensning
) {
    private companion object {
        private fun sykepengegrunnlag(inntekt: Inntekt, skjæringstidspunkt: LocalDate, aktivitetslogg: IAktivitetslogg): Inntekt {
            val maks = Grunnbeløp.`6G`.beløp(skjæringstidspunkt)
            return if (inntekt > maks) {
                aktivitetslogg.`§8-10 ledd 2`(
                    oppfylt = true,
                    funnetRelevant = true,
                    maks = maks,
                    skjæringstidspunkt = skjæringstidspunkt,
                    grunnlagForSykepengegrunnlag = inntekt
                )
                maks
            } else {
                aktivitetslogg.`§8-10 ledd 2`(
                    oppfylt = true,
                    funnetRelevant = false,
                    maks = maks,
                    skjæringstidspunkt = skjæringstidspunkt,
                    grunnlagForSykepengegrunnlag = inntekt
                )
                inntekt
            }
        }
    }

    constructor(
        arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
        skjæringstidspunkt: LocalDate,
        aktivitetslogg: IAktivitetslogg
    ) : this(
        sykepengegrunnlag(arbeidsgiverInntektsopplysninger.inntekt(), skjæringstidspunkt, aktivitetslogg),
        arbeidsgiverInntektsopplysninger,
        arbeidsgiverInntektsopplysninger.inntekt(),
        if (arbeidsgiverInntektsopplysninger.inntekt() > Grunnbeløp.`6G`.beløp(skjæringstidspunkt)) ER_6G_BEGRENSET else ER_IKKE_6G_BEGRENSET
    )

    constructor(
        arbeidsgiverInntektsopplysninger: List<ArbeidsgiverInntektsopplysning>,
    ) : this(
        arbeidsgiverInntektsopplysninger.inntekt(),
        arbeidsgiverInntektsopplysninger,
        arbeidsgiverInntektsopplysninger.inntekt(),
        VURDERT_I_INFOTRYGD
    )

    internal fun accept(vilkårsgrunnlagHistorikkVisitor: VilkårsgrunnlagHistorikkVisitor) {
        vilkårsgrunnlagHistorikkVisitor.preVisitSykepengegrunnlag(this, sykepengegrunnlag, grunnlagForSykepengegrunnlag, begrensning)
        arbeidsgiverInntektsopplysninger.forEach { it.accept(vilkårsgrunnlagHistorikkVisitor) }
        vilkårsgrunnlagHistorikkVisitor.postVisitSykepengegrunnlag(this, sykepengegrunnlag, grunnlagForSykepengegrunnlag, begrensning)
    }

    internal fun avviksprosent(sammenligningsgrunnlag: Inntekt) = grunnlagForSykepengegrunnlag.avviksprosent(sammenligningsgrunnlag)
    internal fun inntektsopplysningPerArbeidsgiver(): Map<String, Inntektshistorikk.Inntektsopplysning> =
        arbeidsgiverInntektsopplysninger.inntektsopplysningPerArbeidsgiver()

    enum class Begrensning {
        ER_6G_BEGRENSET, ER_IKKE_6G_BEGRENSET, VURDERT_I_INFOTRYGD
    }
}
