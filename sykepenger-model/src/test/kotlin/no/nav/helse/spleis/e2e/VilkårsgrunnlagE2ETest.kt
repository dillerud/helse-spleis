package no.nav.helse.spleis.e2e

import java.time.YearMonth
import no.nav.helse.assertForventetFeil
import no.nav.helse.desember
import no.nav.helse.hendelser.InntektForSykepengegrunnlag
import no.nav.helse.hendelser.Inntektsmelding
import no.nav.helse.hendelser.Inntektsvurdering
import no.nav.helse.hendelser.Sykmeldingsperiode
import no.nav.helse.hendelser.Søknad.Søknadsperiode.Sykdom
import no.nav.helse.hendelser.Vilkårsgrunnlag
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.person.TilstandType.AVVENTER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK
import no.nav.helse.person.TilstandType.AVVENTER_SIMULERING
import no.nav.helse.person.TilstandType.AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER
import no.nav.helse.person.TilstandType.AVVENTER_VILKÅRSPRØVING
import no.nav.helse.person.TilstandType.START
import no.nav.helse.person.TilstandType.TIL_INFOTRYGD
import no.nav.helse.testhelpers.inntektperioderForSammenligningsgrunnlag
import no.nav.helse.økonomi.Inntekt.Companion.månedlig
import no.nav.helse.økonomi.Prosentdel.Companion.prosent
import org.junit.jupiter.api.Test

internal class VilkårsgrunnlagE2ETest : AbstractEndToEndTest() {
    @Test
    fun `mer enn 25% avvik lager kun én errormelding i aktivitetsloggen`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 31.januar))

        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    ORGNUMMER inntekt INNTEKT/2
                }
            }
        ))

        assertError("Har mer enn 25 % avvik", 1.vedtaksperiode.filter())
    }

    @Test
    fun `ingen sammenligningsgrunlag fører til error om 25% avvik`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 31.januar))

        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, inntektsvurdering = Inntektsvurdering(emptyList()))

        assertError("Har mer enn 25 % avvik", 1.vedtaksperiode.filter())
    }

    @Test
    fun `Forkaster etterfølgende perioder dersom vilkårsprøving feilet pga avvik i inntekt på første periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 17.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 17.januar, 100.prosent))
        håndterSykmelding(Sykmeldingsperiode(18.januar, 20.januar, 100.prosent))
        håndterSøknad(Sykdom(18.januar, 20.januar, 100.prosent))

        val arbeidsgiverperioder = listOf(
            1.januar til 16.januar
        )
        håndterInntektsmelding(arbeidsgiverperioder, førsteFraværsdag = 1.januar)
        håndterYtelser(1.vedtaksperiode)
        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    ORGNUMMER inntekt INNTEKT * 2
                }
            }
        ))

        assertForkastetPeriodeTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            TIL_INFOTRYGD
        )

        assertForkastetPeriodeTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            TIL_INFOTRYGD
        )
    }

    @Test
    fun `Forkaster ikke etterfølgende perioder dersom vilkårsprøving feiler pga minimum inntekt på første periode`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 17.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 17.januar, 100.prosent))

        val arbeidsgiverperioder = listOf(
            1.januar til 16.januar
        )

        håndterInntektsmelding(
            arbeidsgiverperioder = arbeidsgiverperioder,
            førsteFraværsdag = 1.januar,
            beregnetInntekt = 1000.månedlig,
            refusjon = Inntektsmelding.Refusjon(1000.månedlig, null, emptyList())
        )

        håndterYtelser(1.vedtaksperiode)

        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    ORGNUMMER inntekt 1000
                }
            }
        ))

        håndterSykmelding(Sykmeldingsperiode(18.januar, 20.januar, 100.prosent))
        håndterSøknad(Sykdom(18.januar, 20.januar, 100.prosent))

        assertTilstander(
            1.vedtaksperiode,
            START,
            AVVENTER_INNTEKTSMELDING_ELLER_HISTORIKK,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER,
            AVVENTER_HISTORIKK,
            AVVENTER_VILKÅRSPRØVING,
            AVVENTER_HISTORIKK,
        )

        assertTilstander(
            2.vedtaksperiode,
            START,
            AVVENTER_TIDLIGERE_ELLER_OVERLAPPENDE_PERIODER
        )
    }

    @Test
    fun `25 % avvik i inntekt lager error`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 17.januar, 100.prosent))
        håndterSøknad(Sykdom(1.januar, 17.januar, 100.prosent))
        håndterInntektsmelding(listOf(1.januar til 16.januar), førsteFraværsdag = 1.januar)

        håndterYtelser(1.vedtaksperiode)

        håndterVilkårsgrunnlag(1.vedtaksperiode, INNTEKT, inntektsvurdering = Inntektsvurdering(
            inntekter = inntektperioderForSammenligningsgrunnlag {
                1.januar(2017) til 1.desember(2017) inntekter {
                    ORGNUMMER inntekt INNTEKT * 2 // 25 % avvik vs inntekt i inntektsmeldingen
                }
            }
        ))

        assertError("Har mer enn 25 % avvik")
    }

    @Test
    fun `skal ikke gjenbruke et vilkårsgrunnlag som feiler pga 25 prosent avvik`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            INNTEKT,
            inntektsvurdering = Inntektsvurdering(
                inntekter = listOf(
                    sammenligningsgrunnlag(a1, 1.januar, (INNTEKT / 2).repeat(12)),
                    sammenligningsgrunnlag(a2, 1.januar, (INNTEKT / 2).repeat(12))
                ),
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, 1.januar, INNTEKT.repeat(3)),
                    grunnlag(a2, 1.januar, INNTEKT.repeat(3))
                ),
                arbeidsforhold = emptyList()
            ),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, 1.desember(2017)),
                Vilkårsgrunnlag.Arbeidsforhold(a2, 1.desember(2017))
            ),
            orgnummer = a1
        )

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)
        assertForventetFeil(
            forklaring = "https://trello.com/c/edYRnoPm",
            nå = { assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2) },
            ønsket = { assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a2) }
        )
    }

    @Test
    fun `skal ikke gjenbruke et vilkårsgrunnlag som feiler pga frilanser arbeidsforhold`() {
        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a1)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a1)

        håndterYtelser(1.vedtaksperiode, orgnummer = a1)

        håndterVilkårsgrunnlag(
            1.vedtaksperiode,
            INNTEKT,
            inntektsvurdering = Inntektsvurdering(
                inntekter = listOf(
                    sammenligningsgrunnlag(a1, 1.januar, INNTEKT.repeat(12)),
                    sammenligningsgrunnlag(a2, 1.januar, INNTEKT.repeat(12))
                ),
            ),
            inntektsvurderingForSykepengegrunnlag = InntektForSykepengegrunnlag(
                inntekter = listOf(
                    grunnlag(a1, 1.januar, INNTEKT.repeat(3)),
                    grunnlag(a2, 1.januar, INNTEKT.repeat(3)),
                    grunnlag(a3, 1.januar, INNTEKT.repeat(1))
                ),
                arbeidsforhold = listOf(
                    InntektForSykepengegrunnlag.Arbeidsforhold(
                        orgnummer = a3,
                        månedligeArbeidsforhold = listOf(
                            InntektForSykepengegrunnlag.MånedligArbeidsforhold(
                                yearMonth = YearMonth.of(2017, 12),
                                erFrilanser = true
                            )
                        ),
                    )
                )
            ),
            arbeidsforhold = listOf(
                Vilkårsgrunnlag.Arbeidsforhold(a1, 1.desember(2017)),
                Vilkårsgrunnlag.Arbeidsforhold(a2, 1.desember(2017))
            ),
            orgnummer = a1,
        )

        håndterSykmelding(Sykmeldingsperiode(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterSøknad(Sykdom(1.januar, 31.januar, 100.prosent), orgnummer = a2)
        håndterInntektsmelding(listOf(1.januar til 16.januar), orgnummer = a2)

        håndterYtelser(1.vedtaksperiode, orgnummer = a2)

        assertForventetFeil(
            forklaring = "https://trello.com/c/edYRnoPm",
            nå = { assertSisteTilstand(1.vedtaksperiode, AVVENTER_SIMULERING, orgnummer = a2) },
            ønsket = { assertSisteTilstand(1.vedtaksperiode, TIL_INFOTRYGD, orgnummer = a2) }
        )
    }
}
