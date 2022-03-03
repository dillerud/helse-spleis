package no.nav.helse.person

import no.nav.helse.person.Dokumentsporing.Type.*
import java.util.*

internal data class Dokumentsporing private constructor(private val id: UUID, private val type: Type) {

    companion object {
        internal fun sykmelding(id: UUID) = Dokumentsporing(id, Sykmelding)
        internal fun søknad(id: UUID) = Dokumentsporing(id, Søknad)
        internal fun inntektsmelding(id: UUID) = Dokumentsporing(id, Inntektsmelding)
        internal fun overstyrTidslinje(id: UUID) = Dokumentsporing(id, OverstyrTidslinje)
        internal fun overstyrInntekt(id: UUID) = Dokumentsporing(id, OverstyrInntekt)
        internal fun overstyrArbeidsforhold(id: UUID) = Dokumentsporing(id, OverstyrArbeidsforhold)

        internal fun Iterable<Dokumentsporing>.toMap() = associate { it.id to it.type }
        internal fun Iterable<Dokumentsporing>.ider() = map { it.id }.toSet()
        internal fun Map<UUID, Type>.tilSporing() = map { Dokumentsporing(it.key, it.value) }.toSet()

        internal fun Iterable<Dokumentsporing>.harSøknad() = any { it.type == Søknad }
        internal fun Iterable<Dokumentsporing>.harInntektsmelding() = any { it.type == Inntektsmelding }
    }

    internal enum class Type {
        Sykmelding,
        Søknad,
        Inntektsmelding,
        OverstyrTidslinje,
        OverstyrInntekt,
        OverstyrArbeidsforhold,
    }

    internal fun toMap() = mapOf(id.toString() to type.name)
}

