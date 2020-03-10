CREATE TABLE person
(
    id             BIGSERIAL,
    skjema_versjon INT                      NOT NULL,
    fnr            VARCHAR(32)              NOT NULL,
    aktor_id       VARCHAR(32)              NOT NULL,
    data           JSONB                    NOT NULL,
    opprettet      TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'utc'),
    PRIMARY KEY (id)
);

create index "index_person_fnr" on person using btree (fnr);
create index "index_aktor_id" on person using btree (aktor_id);

CREATE TABLE utbetalingsreferanse
(
    id                VARCHAR(30),
    aktor_id          VARCHAR(32) NOT NULL,
    orgnr             VARCHAR(32) NOT NULL,
    vedtaksperiode_id VARCHAR(36) NOT NULL
);

CREATE TABLE melding
(
    id           BIGSERIAL,
    melding_id   VARCHAR(40)              NOT NULL,
    melding_type VARCHAR(40)              NOT NULL,
    data         JSONB                    NOT NULL,
    lest_dato    TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'utc'),
    PRIMARY KEY (id)
);
