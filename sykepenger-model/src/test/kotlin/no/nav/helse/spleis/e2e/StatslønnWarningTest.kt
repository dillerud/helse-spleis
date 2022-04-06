package no.nav.helse.spleis.e2e

import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.inspectors.personLogg
import no.nav.helse.januar
import no.nav.helse.person.infotrygdhistorikk.ArbeidsgiverUtbetalingsperiode
import no.nav.helse.utbetalingslinjer.Oppdragstatus
import no.nav.helse.økonomi.Inntekt.Companion.daglig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class StatslønnWarningTest : AbstractEndToEndTest() {

    @Test
    fun `Ikke warning ved statslønn når det ikke er overgang`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 18.februar)
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))
        håndterYtelser(vedtaksperiodeIdInnhenter = 1.vedtaksperiode, statslønn = true)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT)
        håndterYtelser(vedtaksperiodeIdInnhenter = 1.vedtaksperiode)
        håndterSimulering(1.vedtaksperiode)
        håndterUtbetalingsgodkjenning(1.vedtaksperiode, true)
        håndterUtbetalt(Oppdragstatus.AKSEPTERT)

        assertTrue(person.personLogg.warn().isEmpty())
    }

    @Test
    fun `Warning ved statslønn`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), sendtTilNAVEllerArbeidsgiver = 18.februar)
        håndterInntektsmelding(listOf(Periode(1.januar, 16.januar)))
        val historikk = arrayOf(ArbeidsgiverUtbetalingsperiode(ORGNUMMER, 1.desember(2017),  31.desember(2017), 100.prosent, 15000.daglig))
        håndterYtelser(1.vedtaksperiode, *historikk, statslønn = true)

        assertTrue(person.personLogg.hasErrorsOrWorse())
    }

}
