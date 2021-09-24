package no.nav.helse.serde.api.v2

import no.nav.helse.serde.api.*
import java.time.LocalDate

internal class Tidslinjebereginger(generasjonIder: List<GenerasjonIder>, sykdomshistorikkAkkumulator: SykdomshistorikkAkkumulator) {
    private val beregninger: List<ITidslinjeberegning> = lagTidslinjeberegninger(generasjonIder, sykdomshistorikkAkkumulator)

    private fun lagTidslinjeberegninger(
        generasjonIder: List<GenerasjonIder>,
        sykdomshistorikkAkkumulator: SykdomshistorikkAkkumulator
    ): List<ITidslinjeberegning> {
        return generasjonIder.map {
            val sykdomstidslinje = sykdomshistorikkAkkumulator.finnTidslinje(it.sykdomshistorikkId) ?: throw IllegalStateException("Finner ikke sykdomshistorikk for historikkId'en! Hvordan kan det skje?")
            ITidslinjeberegning(
                it.beregningId,
                sykdomstidslinje,
                it.inntektshistorikkId,
                it.vilkårsgrunnlagshistorikkId
            )
        }
    }

    internal fun finn(beregningId: BeregningId): ITidslinjeberegning {
        return beregninger.find { it.beregningId == beregningId } ?: throw IllegalStateException("Finner ikke tidslinjeberegning for beregningId'en! Hvordan kan det skje?")
    }

    internal class ITidslinjeberegning(
        internal val beregningId: BeregningId,
        private val sykdomstidslinje: List<SykdomstidslinjedagDTO>,
        internal val inntektshistorikkId: InntektshistorikkId,
        internal val vilkårsgrunnlagshistorikkId: VilkårsgrunnlagshistorikkId
    ) {
        fun sykdomstidslinje(utbetalingstidslinje: List<UtbetalingstidslinjedagDTO>, fom: LocalDate, tom: LocalDate): List<SammenslåttDag> {
            return sykdomstidslinje
                .subset(fom, tom)
                .merge(utbetalingstidslinje)
        }

        private fun List<SykdomstidslinjedagDTO>.subset(fom: LocalDate, tom: LocalDate) = this.filter { it.dagen in fom..tom }
    }
}

internal fun List<SykdomstidslinjedagDTO>.merge(utbetalingstidslinje: List<UtbetalingstidslinjedagDTO>): List<SammenslåttDag> {

    fun utbetalingsinfo(utbetalingsdag: UtbetalingstidslinjedagDTO) = if (utbetalingsdag is NavDagDTO)
        Utbetalingsinfo(utbetalingsdag.inntekt, utbetalingsdag.utbetaling, utbetalingsdag.totalGrad)
    else null

    fun begrunnelser(utbetalingsdag: UtbetalingstidslinjedagDTO) =
        if (utbetalingsdag is AvvistDagDTO) utbetalingsdag.begrunnelser else null

    return map { sykdomsdag ->
        val utbetalingsdag = utbetalingstidslinje.find { it.dato.isEqual(sykdomsdag.dagen) }
        SammenslåttDag(
            sykdomsdag.dagen,
            sykdomsdag.type,
            utbetalingsdag?.type ?: TypeDataDTO.UkjentDag,
            kilde = sykdomsdag.kilde,
            grad = sykdomsdag.grad,
            utbetalingsinfo = utbetalingsdag?.let { utbetalingsinfo(it) },
            begrunnelser = utbetalingsdag?.let { begrunnelser(it) }
        )
    }
}