package no.nav.helse.spleis

import no.nav.helse.hendelser.*

interface HendelseListener {
    fun onPåminnelse(påminnelse: Påminnelse) {}
    fun onYtelser(ytelser: Ytelser) {}
    fun onManuellSaksbehandling(manuellSaksbehandling: ManuellSaksbehandling) {}
    fun onInntektsmelding(inntektsmelding: Inntektsmelding) {}
    fun onNySøknad(søknad: NySøknad) {}
    fun onSendtSøknad(søknad: SendtSøknad) {}
}
