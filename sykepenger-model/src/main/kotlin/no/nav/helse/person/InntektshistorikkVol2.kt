package no.nav.helse.person


import no.nav.helse.økonomi.Inntekt
import no.nav.helse.økonomi.Inntekt.Companion.INGEN
import no.nav.helse.økonomi.Inntekt.Companion.summer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*

internal class InntektshistorikkVol2 {

    private val historikk = mutableListOf<Innslag>()

    private val innslag
        get() = (historikk.firstOrNull()?.clone() ?: Innslag())
            .also { historikk.add(0, it) }


    internal operator fun invoke(block: InnslagBuilder.() -> Unit) {
        InnslagBuilder(innslag).apply(block)
    }

    internal class InnslagBuilder(private val innslag: Innslag) {
        private val tidsstempel = LocalDateTime.now()

        internal fun createSaksbehandler(
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime? = null
        ) = Saksbehandler(dato, hendelseId, beløp, tidsstempel ?: this.tidsstempel)

        internal fun createInntektsmelding(
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime? = null
        ) = Inntektsmelding(dato, hendelseId, beløp, tidsstempel ?: this.tidsstempel)

        internal fun createInfotrygd(
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            tidsstempel: LocalDateTime? = null
        ) = Infotrygd(dato, hendelseId, beløp, tidsstempel ?: this.tidsstempel)

        internal fun createSkattSykepengegrunnlag(
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Skatt.Inntekttype,
            fordel: String,
            beskrivelse: String,
            tilleggsinformasjon: String?,
            tidsstempel: LocalDateTime? = null
        ) = Skatt.Sykepengegrunnlag(
            dato,
            hendelseId,
            beløp,
            måned,
            type,
            fordel,
            beskrivelse,
            tilleggsinformasjon,
            tidsstempel ?: this.tidsstempel
        )

        internal fun createSkattSammenligningsgrunnlag(
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Skatt.Inntekttype,
            fordel: String,
            beskrivelse: String,
            tilleggsinformasjon: String?,
            tidsstempel: LocalDateTime? = null
        ) = Skatt.Sammenligningsgrunnlag(
            dato,
            hendelseId,
            beløp,
            måned,
            type,
            fordel,
            beskrivelse,
            tilleggsinformasjon,
            tidsstempel ?: this.tidsstempel
        )

        internal fun add(opplysning: Inntektsopplysning) {
            innslag.add(opplysning)
        }

        internal fun addAll(opplysninger: List<Skatt>) {
            innslag.add(SkattComposite(opplysninger))
        }
    }

    internal fun accept(visitor: InntekthistorikkVisitor) {
        visitor.preVisitInntekthistorikkVol2(this)
        historikk.forEach { it.accept(visitor) }
        visitor.postVisitInntekthistorikkVol2(this)
    }

    internal fun grunnlagForSykepengegrunnlag(dato: LocalDate) =
        historikk.first().grunnlagForSykepengegrunnlag(dato) ?: INGEN

    internal fun grunnlagForSammenligningsgrunnlag(dato: LocalDate) =
        historikk.first().grunnlagForSammenligningsgrunnlag(dato) ?: INGEN

    internal fun clone() = InntektshistorikkVol2().also {
        it.historikk.addAll(this.historikk.map(Innslag::clone))
    }

    internal class Innslag {

        private val inntekter = mutableListOf<Inntektsopplysning>()

        fun accept(visitor: InntekthistorikkVisitor) {
            visitor.preVisitInnslag(this)
            inntekter.forEach { it.accept(visitor) }
            visitor.postVisitInnslag(this)
        }

        fun clone() = Innslag().also {
            it.inntekter.addAll(this.inntekter)
        }

        fun add(inntektsopplysning: Inntektsopplysning) {
            inntekter.removeIf { it.skalErstattesAv(inntektsopplysning) }
            inntekter.add(inntektsopplysning)
        }

        fun grunnlagForSykepengegrunnlag(dato: LocalDate) =
            inntekter
                .sorted()
                .mapNotNull { it.grunnlagForSykepengegrunnlag(dato) }
                .firstOrNull()

        fun grunnlagForSammenligningsgrunnlag(dato: LocalDate) =
            inntekter
                .sorted()
                .mapNotNull { it.grunnlagForSammenligningsgrunnlag(dato) }
                .firstOrNull()

    }

    internal interface Inntektsopplysning : Comparable<Inntektsopplysning> {
        val prioritet: Int
        fun accept(visitor: InntekthistorikkVisitor)
        fun grunnlagForSykepengegrunnlag(dato: LocalDate): Inntekt? = null
        fun grunnlagForSammenligningsgrunnlag(dato: LocalDate): Inntekt? = null
        fun skalErstattesAv(other: Inntektsopplysning): Boolean
        override fun compareTo(other: Inntektsopplysning) = -this.prioritet.compareTo(other.prioritet)
    }

    internal class Saksbehandler(
        private val dato: LocalDate,
        private val hendelseId: UUID,
        private val beløp: Inntekt,
        private val tidsstempel: LocalDateTime = LocalDateTime.now()
    ) : Inntektsopplysning {
        override val prioritet = 100

        override fun accept(visitor: InntekthistorikkVisitor) {
            visitor.visitSaksbehandler(this)
        }

        override fun grunnlagForSykepengegrunnlag(dato: LocalDate) = if (dato == this.dato) beløp else null

        override fun skalErstattesAv(other: Inntektsopplysning) =
            other is Saksbehandler && this.dato == other.dato
    }


    internal class Inntektsmelding(
        private val dato: LocalDate,
        private val hendelseId: UUID,
        private val beløp: Inntekt,
        private val tidsstempel: LocalDateTime = LocalDateTime.now()
    ) : Inntektsopplysning {
        override val prioritet = 80

        override fun accept(visitor: InntekthistorikkVisitor) {
            visitor.visitInntektsmelding(this)
        }

        override fun grunnlagForSykepengegrunnlag(dato: LocalDate) = if (dato == this.dato) beløp else null

        override fun skalErstattesAv(other: Inntektsopplysning) =
            other is Inntektsmelding && this.dato == other.dato
    }

    internal class Infotrygd(
        private val dato: LocalDate,
        private val hendelseId: UUID,
        private val beløp: Inntekt,
        private val tidsstempel: LocalDateTime = LocalDateTime.now()
    ) : Inntektsopplysning {
        override val prioritet = 60

        override fun accept(visitor: InntekthistorikkVisitor) {
            visitor.visitInfotrygd(this)
        }

        override fun grunnlagForSykepengegrunnlag(dato: LocalDate) = if (dato == this.dato) beløp else null

        override fun skalErstattesAv(other: Inntektsopplysning) =
            other is Infotrygd && this.dato == other.dato
    }

    internal class SkattComposite(
        private val inntektsopplysninger: List<Skatt> = listOf()
    ) : Inntektsopplysning {

        override val prioritet = inntektsopplysninger.map { it.prioritet }.max() ?: 0

        override fun accept(visitor: InntekthistorikkVisitor) {
            visitor.preVisitSkatt()
            inntektsopplysninger.forEach { it.accept(visitor) }
            visitor.postVisitSkatt()
        }

        override fun grunnlagForSykepengegrunnlag(dato: LocalDate) =
            inntektsopplysninger
                .mapNotNull { it.grunnlagForSykepengegrunnlag(dato) }
                .takeIf { it.isNotEmpty() }
                ?.summer()
                ?.div(3)

        override fun grunnlagForSammenligningsgrunnlag(dato: LocalDate) =
            inntektsopplysninger
                .mapNotNull { it.grunnlagForSammenligningsgrunnlag(dato) }
                .takeIf { it.isNotEmpty() }
                ?.summer()
                ?.div(12)

        override fun skalErstattesAv(other: Inntektsopplysning): Boolean =
            this.inntektsopplysninger.any { it.skalErstattesAv(other) }
                || (other is SkattComposite && other.inntektsopplysninger.any { this.skalErstattesAv(it) })
    }

    internal sealed class Skatt(
        protected val dato: LocalDate,
        protected val hendelseId: UUID,
        protected val beløp: Inntekt,
        protected val måned: YearMonth,
        protected val type: Inntekttype,
        protected val fordel: String,
        protected val beskrivelse: String,
        protected val tilleggsinformasjon: String?,
        protected val tidsstempel: LocalDateTime = LocalDateTime.now()
    ) : Inntektsopplysning {
        internal enum class Inntekttype {
            LØNNSINNTEKT,
            NÆRINGSINNTEKT,
            PENSJON_ELLER_TRYGD,
            YTELSE_FRA_OFFENTLIGE
        }

        internal class Sykepengegrunnlag(
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Inntekttype,
            fordel: String,
            beskrivelse: String,
            tilleggsinformasjon: String?,
            tidsstempel: LocalDateTime = LocalDateTime.now()
        ) : Skatt(
            dato,
            hendelseId,
            beløp,
            måned,
            type,
            fordel,
            beskrivelse,
            tilleggsinformasjon,
            tidsstempel
        ) {
            override val prioritet = 40

            override fun accept(visitor: InntekthistorikkVisitor) {
                visitor.visitSkattSykepengegrunnlag(this)
            }

            override fun grunnlagForSykepengegrunnlag(dato: LocalDate) =
                if (this.dato == dato && måned.isWithinRangeOf(dato, 3)) beløp else null

            override fun skalErstattesAv(other: Inntektsopplysning) =
                other is Sykepengegrunnlag && this.dato == other.dato && this.tidsstempel != other.tidsstempel
        }

        internal class Sammenligningsgrunnlag(
            dato: LocalDate,
            hendelseId: UUID,
            beløp: Inntekt,
            måned: YearMonth,
            type: Inntekttype,
            fordel: String,
            beskrivelse: String,
            tilleggsinformasjon: String?,
            tidsstempel: LocalDateTime = LocalDateTime.now()
        ) : Skatt(
            dato,
            hendelseId,
            beløp,
            måned,
            type,
            fordel,
            beskrivelse,
            tilleggsinformasjon,
            tidsstempel
        ) {
            override val prioritet = 20

            override fun accept(visitor: InntekthistorikkVisitor) {
                visitor.visitSkattSammenligningsgrunnlag(this)
            }

            override fun grunnlagForSammenligningsgrunnlag(dato: LocalDate) =
                if (this.dato == dato && måned.isWithinRangeOf(dato, 12)) beløp else null

            override fun skalErstattesAv(other: Inntektsopplysning) =
                other is Sammenligningsgrunnlag && this.dato == other.dato
        }
    }
}

private fun YearMonth.isWithinRangeOf(dato: LocalDate, måneder: Long) =
    this in YearMonth.from(dato).let { it.minusMonths(måneder)..it.minusMonths(1) }
