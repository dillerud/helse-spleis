package no.nav.helse
internal abstract class Historikk <I: Historikk.Innslag, V: HistorikkVisitor<Historikk<I, V>, I>> private constructor(
    private val historikk: MutableList<I>
) {
    protected constructor() : this(mutableListOf())

    protected fun lagre(innslag: I) {
        if (sisteInnslag() == innslag) return
        historikk.add(innslag)
    }

    protected fun sisteInnslag() = historikk.lastOrNull()

    internal fun accept(historikkVisitor: HistorikkVisitor<Historikk<I, V>, I>) {
        historikkVisitor.preVisitHistorikk(this, historikk)
        historikk.forEach { it.accept(historikkVisitor) }
        historikkVisitor.postVisitHistorikk(this, historikk)
    }

    internal abstract class Innslag {
        abstract override fun equals(other: Any?): Boolean
        abstract override fun hashCode(): Int

        abstract fun accept(historikkVisitor: HistorikkVisitor)
    }
}

internal interface HistorikkVisitor<V: HistorikkVisitor<V, H, I>, H: Historikk<I, V>, I: Historikk.Innslag> {
    fun preVisitHistorikk(historikk: H, historikkelementer: List<I>)
    fun postVisitHistorikk(historikk: H, historikkelementer: List<I>)
}

internal class EnHistorikk : Historikk<EttInnslag, EnHistorikk.Visitor>() {
    internal fun accept(enHistorikkVisitor: EnHistorikk.Visitor) {

    }

    internal class Visitor: HistorikkVisitor<Visitor, EnHistorikk, EttInnslag> {
        override fun preVisitHistorikk(historikk: EnHistorikk, historikkelementer: List<EttInnslag>) {}
        override fun postVisitHistorikk(historikk: EnHistorikk, historikkelementer: List<EttInnslag>) {}

    }
}

internal class EttInnslag: Historikk.Innslag() {
    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

    override fun hashCode(): Int {
        TODO("Not yet implemented")
    }

    override fun accept(historikkVisitor: HistorikkVisitor) {
        historikkVisitor.visitEttInnslag("parametre")
    }
}