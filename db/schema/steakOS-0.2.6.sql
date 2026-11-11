-- steakOS production schema pack 0.2.6
-- ADR-058 / PDR-036. DDL only (tables, indexes, sequences, constraints).
-- No user rows. No passwords.
-- Generated from a clean boot of steakOS-0.2.6.jar (grails.env=development, dbCreate=update)
-- against an empty database, then pg_dump --schema-only.
-- Apply once to an empty steakos_prod. Do not use this to upgrade an existing database.

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)
-- Dumped by pg_dump version 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--



SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: account; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.account (
    id bigint NOT NULL,
    version bigint NOT NULL,
    code character varying(32) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    account_type character varying(32) NOT NULL,
    account_sub_type character varying(64),
    currency_code character varying(8) NOT NULL,
    parent_code character varying(32),
    system_account boolean,
    active boolean,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: address; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.address (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    address_type character varying(32) NOT NULL,
    street character varying(255) NOT NULL,
    street_no character varying(32),
    street2 character varying(255),
    street3 character varying(255),
    street4 character varying(255),
    street5 character varying(255),
    street6 character varying(255),
    lot character varying(64),
    building character varying(128),
    room character varying(64),
    locality character varying(128),
    postal_code character varying(32),
    city character varying(128) NOT NULL,
    region character varying(128),
    county character varying(128),
    country_id bigint NOT NULL,
    latitude numeric(19,8),
    longitude numeric(19,8),
    active boolean,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: agent_api_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_api_token (
    id bigint NOT NULL,
    version bigint NOT NULL,
    token_prefix character varying(16) NOT NULL,
    token_hash character varying(128) NOT NULL,
    label character varying(255) NOT NULL,
    user_id bigint NOT NULL,
    linked_tenant_key character varying(255) NOT NULL,
    host_label character varying(128),
    revoked boolean NOT NULL,
    date_created timestamp without time zone,
    last_used timestamp without time zone,
    created_by character varying(128)
);


--
-- Name: agent_approval; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_approval (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    agent_id bigint,
    proposal_ref character varying(255),
    proposal_json text,
    target_family character varying(64),
    decision character varying(32) NOT NULL,
    decided_at timestamp without time zone,
    decided_by_npub character varying(128),
    risk_level character varying(16) NOT NULL,
    nostr_event_id character varying(128),
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: agent_human_link; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_human_link (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    agent_id bigint NOT NULL,
    user_id bigint,
    human_npub character varying(128),
    link_kind character varying(32) NOT NULL,
    active boolean NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: agent_memory; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_memory (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    agent_id bigint,
    memory_kind character varying(32) NOT NULL,
    payload_json text,
    score numeric(19,4),
    source_ref character varying(255),
    memory_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: agent_resource; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agent_resource (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    agent_key character varying(64) NOT NULL,
    runtime_kind character varying(32) NOT NULL,
    endpoint_ref character varying(512),
    tools_json text,
    agent_status character varying(32) NOT NULL,
    version_label character varying(64),
    agent_npub character varying(128),
    hive_id bigint,
    task_context text,
    soul text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: bank_account; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bank_account (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    legal_entity_id bigint NOT NULL,
    account_kind character varying(32) NOT NULL,
    payment_scheme character varying(16),
    iban character varying(64),
    bic character varying(32),
    branch_name character varying(255),
    account_number character varying(64),
    routing_number character varying(32),
    currency_code character varying(8) NOT NULL,
    external_ref text,
    active boolean NOT NULL,
    access character varying(4000),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32)
);


--
-- Name: bill_of_materials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bill_of_materials (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    catalogue_item_id bigint NOT NULL,
    bom_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: bom_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bom_item (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255),
    description text,
    bom_id bigint NOT NULL,
    line_no integer NOT NULL,
    component_item_id bigint NOT NULL,
    quantity numeric(19,8) NOT NULL,
    unit character varying(16) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: budget; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.budget (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    period character varying(7) NOT NULL,
    amount_eur numeric(19,8) NOT NULL,
    currency_code character varying(8) NOT NULL,
    account_code character varying(32),
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: business_process; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.business_process (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    process_code character varying(64),
    process_status character varying(32) NOT NULL,
    process_kind character varying(32) NOT NULL,
    version_label character varying(64),
    owner_ref character varying(255),
    depends_on_codes character varying(512),
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: candidate; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.candidate (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name text NOT NULL,
    description text,
    email character varying(255),
    source character varying(128),
    role_label character varying(255),
    stage character varying(32) NOT NULL,
    hr_file_id bigint,
    applied_at timestamp without time zone,
    hired_at timestamp without time zone,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: catalogue_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.catalogue_item (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    code character varying(64),
    unit character varying(16) NOT NULL,
    nature character varying(16) NOT NULL,
    can_be_sold boolean NOT NULL,
    can_be_bought boolean NOT NULL,
    active boolean NOT NULL,
    list_price numeric(19,8),
    currency character varying(8) NOT NULL,
    default_tax_code character varying(32),
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: change_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.change_item (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    change_id bigint NOT NULL,
    item_no integer NOT NULL,
    item_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: change_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.change_request (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    change_status character varying(32) NOT NULL,
    planned_start timestamp without time zone,
    planned_end timestamp without time zone,
    impact_note text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: claims; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.claims (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    claim_number character varying(64),
    warranty_id bigint NOT NULL,
    inquiry_id bigint,
    claim_amount_eur numeric(19,8) NOT NULL,
    claim_amount_sats bigint,
    currency_code character varying(8) NOT NULL,
    investigation_notes text,
    needs_field_visit boolean,
    received_at timestamp without time zone,
    validated_at timestamp without time zone,
    approved_at timestamp without time zone,
    closed_at timestamp without time zone,
    resolution text,
    finance_credit_invoice_id bigint,
    claim_status character varying(32) NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: classification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.classification (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: collection_activity; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.collection_activity (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    invoice_id bigint NOT NULL,
    kind character varying(32) NOT NULL,
    channel character varying(32),
    activity_at timestamp without time zone NOT NULL,
    promised_amount_eur numeric(19,8),
    promised_pay_by timestamp without time zone,
    promise_status character varying(32) NOT NULL,
    access character varying(4000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: comms_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comms_log (
    id bigint NOT NULL,
    version bigint NOT NULL,
    stakeholder_party_id bigint NOT NULL,
    subject character varying(255) NOT NULL,
    summary text,
    channel character varying(32) NOT NULL,
    direction character varying(16) NOT NULL,
    occurred_at timestamp without time zone,
    status character varying(32) NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: company_currency; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.company_currency (
    id bigint NOT NULL,
    version bigint NOT NULL,
    currency_code character varying(3) NOT NULL,
    in_use boolean NOT NULL,
    date_created timestamp without time zone NOT NULL,
    last_updated timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL
);


--
-- Name: company_setting; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.company_setting (
    id bigint NOT NULL,
    version bigint NOT NULL,
    setting_key character varying(255) NOT NULL,
    setting_value text,
    category character varying(64) NOT NULL,
    data_type character varying(16) NOT NULL,
    date_created timestamp without time zone NOT NULL,
    last_updated timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL
);


--
-- Name: company_tax_code; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.company_tax_code (
    id bigint NOT NULL,
    version bigint NOT NULL,
    tax_code character varying(32) NOT NULL,
    in_use boolean NOT NULL,
    date_created timestamp without time zone NOT NULL,
    last_updated timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL
);


--
-- Name: competitor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.competitor (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    type character varying(128),
    sources text,
    status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    tags character varying(2000)
);


--
-- Name: compliance_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.compliance_item (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    item_kind character varying(32) NOT NULL,
    status character varying(32) NOT NULL,
    owner_name character varying(255),
    effective_from timestamp without time zone,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: component; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.component (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    sku character varying(64),
    unit character varying(16) NOT NULL,
    catalogue_item_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: config_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.config_item (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    ci_kind character varying(32) NOT NULL,
    hostname character varying(255),
    ip_address character varying(64),
    ci_status character varying(32) NOT NULL,
    system_id bigint,
    solution_id bigint,
    physical_asset_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: content; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: content_blob; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content_blob (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255),
    description text,
    mime_type character varying(128),
    byte_size bigint NOT NULL,
    checksum_sha256 character varying(64) NOT NULL,
    storage character varying(16) NOT NULL,
    bytes bytea,
    fs_path character varying(1024),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL
);


--
-- Name: content_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content_type (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    code character varying(64) NOT NULL,
    classification character varying(128),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: content_type_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content_type_config (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    content_type_code character varying(64) NOT NULL,
    classification character varying(128),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: contract; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: countries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.countries (
    id bigint NOT NULL,
    version bigint NOT NULL,
    code character varying(2) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    enabled boolean
);


--
-- Name: currencies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.currencies (
    id bigint NOT NULL,
    version bigint NOT NULL,
    code character varying(3) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    decimals integer,
    enabled boolean
);


--
-- Name: customer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    customer_kind character varying(32) NOT NULL,
    email character varying(255),
    phone character varying(64),
    contact_name character varying(255),
    customer_npub character varying(200),
    tax_id character varying(64),
    currency_code character varying(8) NOT NULL,
    communication_locale character varying(16),
    active boolean,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: customer_quote; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_quote (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    quote_number character varying(64),
    customer_id bigint,
    customer_name character varying(255),
    issue_date timestamp without time zone,
    valid_until timestamp without time zone,
    amount_eur numeric(19,8) NOT NULL,
    net_amount_eur numeric(19,8),
    tax_amount_eur numeric(19,8),
    amount_sats bigint,
    currency_code character varying(8) NOT NULL,
    document_locale character varying(16),
    quote_status character varying(32) NOT NULL,
    resulting_order_id bigint,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    nostr_event_id character varying(128),
    nostr_message_ref character varying(256),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    public_nostr boolean,
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: customer_quote_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_quote_dynamic (
    customer_quote_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: data; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    retention_days integer,
    data_class character varying(32) NOT NULL,
    owner_ref character varying(128),
    data_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: development_project; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.development_project (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    project_kind character varying(32) NOT NULL,
    project_status character varying(32) NOT NULL,
    catalogue_item_id bigint,
    start_date timestamp without time zone,
    end_date timestamp without time zone,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: global_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.global_config (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: global_config_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.global_config_dynamic (
    global_config_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: go_to_market; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.go_to_market (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: goal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.goal (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    hr_file_id bigint,
    due_date timestamp without time zone,
    progress integer NOT NULL,
    goal_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: headcount_plan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.headcount_plan (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    period_start timestamp without time zone,
    period_end timestamp without time zone,
    planned_count integer NOT NULL,
    role_label character varying(255),
    plan_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: help_topic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.help_topic (
    id bigint NOT NULL,
    version bigint NOT NULL,
    help_key character varying(128) NOT NULL,
    title character varying(255) NOT NULL,
    route_pattern character varying(255),
    family character varying(64),
    sort_order integer NOT NULL,
    system_default boolean NOT NULL,
    date_created timestamp without time zone NOT NULL,
    last_updated timestamp without time zone NOT NULL
);


--
-- Name: hibernate_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.hibernate_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hive; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hive (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    apqc_area_code character varying(32),
    hive_status character varying(32) NOT NULL,
    hive_master_agent_id bigint,
    max_agents integer,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: hrfile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.hrfile (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name text NOT NULL,
    description text,
    user_id bigint NOT NULL,
    username character varying(255),
    employment_kind character varying(32) NOT NULL,
    employment_status character varying(32) NOT NULL,
    hire_date timestamp without time zone,
    exit_date timestamp without time zone,
    email character varying(255),
    person_ref character varying(128),
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: import_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.import_job (
    id bigint NOT NULL,
    version bigint NOT NULL,
    pack_type character varying(32) NOT NULL,
    status character varying(16) NOT NULL,
    file_name character varying(512),
    content_type character varying(128),
    raw_text text,
    mapping_json text,
    report_json text,
    row_count integer NOT NULL,
    created_count integer NOT NULL,
    updated_count integer NOT NULL,
    skipped_count integer NOT NULL,
    error_count integer NOT NULL,
    error_summary text,
    date_created timestamp without time zone NOT NULL,
    last_updated timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL
);


--
-- Name: inquiry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inquiry (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    inquiry_number character varying(64),
    customer_id bigint,
    customer_name character varying(255),
    channel character varying(16) NOT NULL,
    kind character varying(16) NOT NULL,
    priority character varying(16) NOT NULL,
    service_level_id bigint,
    sales_order_id bigint,
    service_order_id bigint,
    opened_at timestamp without time zone,
    response_due_at timestamp without time zone,
    resolve_due_at timestamp without time zone,
    resolved_at timestamp without time zone,
    closed_at timestamp without time zone,
    assigned_to character varying(255),
    case_status character varying(32) NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    nostr_event_id character varying(128),
    nostr_message_ref character varying(256),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    public_nostr boolean,
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: invoice; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    invoice_number character varying(64),
    customer_id bigint,
    customer_name character varying(255),
    customer_contact character varying(512),
    issue_date timestamp without time zone,
    due_date timestamp without time zone,
    amount_eur numeric(19,8) NOT NULL,
    net_amount_eur numeric(19,8),
    tax_amount_eur numeric(19,8),
    amount_sats bigint,
    exchange_rate numeric(19,12),
    currency_code character varying(8) NOT NULL,
    document_locale character varying(16),
    invoice_status character varying(32) NOT NULL,
    recurring boolean,
    settled_at timestamp without time zone,
    reference_text text,
    issue_journal_entry_id bigint,
    access character varying(4000),
    ai_generated boolean,
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    nostr_event_id character varying(128),
    nostr_message_ref character varying(256),
    npub_owner character varying(200) NOT NULL,
    proposal_id character varying(128),
    protection character varying(32),
    public_nostr boolean,
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: invoice_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_dynamic (
    invoice_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: it_ticket; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.it_ticket (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    ticket_number character varying(64),
    ticket_kind character varying(16) NOT NULL,
    priority character varying(16) NOT NULL,
    ticket_status character varying(32) NOT NULL,
    config_item_id bigint,
    solution_id bigint,
    system_id bigint,
    opened_at timestamp without time zone,
    resolved_at timestamp without time zone,
    closed_at timestamp without time zone,
    assigned_to character varying(255),
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: itchange; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.itchange (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    change_kind character varying(32) NOT NULL,
    risk_level character varying(16) NOT NULL,
    change_window_start timestamp without time zone,
    change_window_end timestamp without time zone,
    change_status character varying(32) NOT NULL,
    solution_id bigint,
    system_id bigint,
    config_item_id bigint,
    cab_note text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: journal_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.journal_entry (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    entry_date timestamp without time zone NOT NULL,
    period character varying(7) NOT NULL,
    functional_currency character varying(8) NOT NULL,
    source_type character varying(64),
    source_id character varying(128),
    event_id character varying(128),
    memo text,
    posted boolean,
    posted_at timestamp without time zone,
    reverses_entry_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    nostr_event_id character varying(128),
    nostr_message_ref character varying(256),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    public_nostr boolean,
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: journal_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.journal_line (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    journal_entry_id bigint NOT NULL,
    account_id bigint NOT NULL,
    line_no integer NOT NULL,
    debit_eur numeric(19,8) NOT NULL,
    credit_eur numeric(19,8) NOT NULL,
    amount_sats bigint,
    memo character varying(1000),
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: knowledge_area; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_area (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    parent_id bigint,
    area_code character varying(64),
    area_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: legal_entity; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.legal_entity (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    code character varying(64),
    kind character varying(16) NOT NULL,
    legal_form character varying(64),
    incorporation_date timestamp without time zone,
    tax_id character varying(64),
    country_code character varying(8),
    functional_currency character varying(8) NOT NULL,
    dual_sats boolean NOT NULL,
    registered_address_text text,
    website text,
    main_email character varying(255),
    access character varying(4000),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000)
);


--
-- Name: lot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lot (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    lot_code character varying(64) NOT NULL,
    catalogue_item_id bigint NOT NULL,
    quantity numeric(19,8) NOT NULL,
    manufactured_on timestamp without time zone,
    lot_status character varying(32) NOT NULL,
    source_production_order_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: market; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.market (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: object_content; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.object_content (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    host_package character varying(64) NOT NULL,
    host_resource character varying(128) NOT NULL,
    host_id bigint NOT NULL,
    channel character varying(16) NOT NULL,
    content_type_code character varying(64),
    title character varying(255),
    body_format character varying(16),
    body_text text,
    url character varying(2000),
    pinned boolean NOT NULL,
    file_name character varying(512),
    mime_type character varying(128),
    byte_size bigint,
    checksum_sha256 character varying(64),
    blob_id bigint,
    access character varying(4000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: object_content_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.object_content_profile (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    host_package character varying(64) NOT NULL,
    host_resource character varying(128) NOT NULL,
    allowed_type_codes character varying(2000) NOT NULL,
    system_default boolean NOT NULL,
    classification character varying(128),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: order_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_line (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    sales_order_id bigint NOT NULL,
    line_no integer NOT NULL,
    quantity numeric(19,8) NOT NULL,
    unit_price_eur numeric(19,8) NOT NULL,
    line_total_eur numeric(19,8) NOT NULL,
    unit_price_sats bigint,
    line_total_sats bigint,
    product_ref_id bigint,
    product_ref_type character varying(64),
    catalogue_item_id bigint,
    tax_code character varying(32),
    tax_rate_percent numeric(19,4),
    tax_amount_eur numeric(19,8),
    reverse_charge boolean,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: party_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.party_access (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name text NOT NULL,
    description text,
    user_id bigint NOT NULL,
    username character varying(255),
    party_kind character varying(32) NOT NULL,
    active boolean,
    notes text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: payable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payable (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    vendor_id bigint,
    vendor_name character varying(255),
    amount_eur numeric(19,8) NOT NULL,
    amount_sats bigint,
    currency_code character varying(8) NOT NULL,
    issue_date timestamp without time zone,
    due_date timestamp without time zone,
    paid_at timestamp without time zone,
    payable_status character varying(32) NOT NULL,
    create_journal_entry_id bigint,
    journal_entry_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    nostr_event_id character varying(128),
    nostr_message_ref character varying(256),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    public_nostr boolean,
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: payable_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payable_dynamic (
    payable_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: payment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    invoice_id bigint NOT NULL,
    rail character varying(32) NOT NULL,
    amount_eur numeric(19,8) NOT NULL,
    amount_sats bigint,
    received_at timestamp without time zone,
    payment_status character varying(32) NOT NULL,
    external_tx_id character varying(256),
    event_id character varying(128),
    journal_entry_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    nostr_event_id character varying(128),
    nostr_message_ref character varying(256),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    public_nostr boolean,
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: payment_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_dynamic (
    payment_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: payment_option; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_option (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    invoice_id bigint NOT NULL,
    rail character varying(32) NOT NULL,
    payment_uri text,
    qr_payload text,
    estimated_settle character varying(128),
    external_invoice_id character varying(256),
    active boolean,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: payroll; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payroll (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    period character varying(7) NOT NULL,
    period_start timestamp without time zone,
    period_end timestamp without time zone,
    total_eur numeric(19,8) NOT NULL,
    total_sats bigint,
    currency_code character varying(8) NOT NULL,
    payroll_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: payroll_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payroll_item (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    period character varying(7),
    person_ref character varying(128),
    amount_eur numeric(19,8) NOT NULL,
    amount_sats bigint,
    currency_code character varying(8) NOT NULL,
    payroll_status character varying(32) NOT NULL,
    journal_entry_id bigint,
    pay_journal_entry_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: physical_asset; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.physical_asset (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    asset_tag character varying(128),
    location character varying(255),
    owner_name character varying(255),
    status character varying(32) NOT NULL,
    it_managed boolean NOT NULL,
    config_item_id bigint,
    financial_depreciation boolean NOT NULL,
    requires_maintenance boolean NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: policy; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.policy (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    code character varying(64),
    policy_kind character varying(32) NOT NULL,
    effective_from timestamp without time zone,
    active boolean,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: portfolio; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.portfolio (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    owner_ref character varying(255),
    portfolio_status character varying(32) NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: procedure; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.procedure (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    process_id bigint,
    version_label character varying(64),
    active boolean NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: process_step; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_step (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    process_id bigint NOT NULL,
    step_no integer NOT NULL,
    predecessor_id bigint,
    step_code character varying(64),
    desk_hint character varying(255),
    outcome_status character varying(64),
    actor_hint character varying(32),
    tip_what text,
    tip_who character varying(128),
    tip_where character varying(255),
    tip_pitfall text,
    stub boolean,
    help_key character varying(128),
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: production_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_order (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    order_number character varying(64),
    catalogue_item_id bigint NOT NULL,
    quantity numeric(19,8) NOT NULL,
    unit character varying(16) NOT NULL,
    scheduled_start timestamp without time zone,
    scheduled_end timestamp without time zone,
    completed_at timestamp without time zone,
    prod_status character varying(32) NOT NULL,
    source_sales_order_id bigint,
    resulting_lot_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: program; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.program (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    portfolio_id bigint,
    program_status character varying(32) NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: project; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    program_id bigint,
    portfolio_id bigint,
    start_date timestamp without time zone,
    end_date timestamp without time zone,
    project_status character varying(32) NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: project_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_item (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    project_id bigint NOT NULL,
    item_no integer NOT NULL,
    item_status character varying(32) NOT NULL,
    task_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: purchase_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    po_number character varying(64),
    vendor_id bigint,
    vendor_name character varying(255),
    order_date timestamp without time zone,
    confirmed_at timestamp without time zone,
    amount_eur numeric(19,8) NOT NULL,
    net_amount_eur numeric(19,8),
    tax_amount_eur numeric(19,8),
    amount_sats bigint,
    currency_code character varying(8) NOT NULL,
    document_locale character varying(16),
    po_status character varying(32) NOT NULL,
    finance_payable_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    nostr_event_id character varying(128),
    nostr_message_ref character varying(256),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    public_nostr boolean,
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: purchase_order_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order_dynamic (
    purchase_order_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: purchase_order_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order_line (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    purchase_order_id bigint NOT NULL,
    line_no integer NOT NULL,
    quantity numeric(19,8) NOT NULL,
    unit_price_eur numeric(19,8) NOT NULL,
    line_total_eur numeric(19,8) NOT NULL,
    unit_price_sats bigint,
    line_total_sats bigint,
    product_ref_id bigint,
    product_ref_type character varying(64),
    catalogue_item_id bigint,
    quantity_received numeric(19,8) NOT NULL,
    tax_code character varying(32),
    tax_rate_percent numeric(19,4),
    tax_amount_eur numeric(19,8),
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: quality; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quality (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    quality_code character varying(64),
    quality_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: quality_check; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quality_check (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    lot_id bigint NOT NULL,
    checked_on timestamp without time zone,
    check_result character varying(16) NOT NULL,
    notes text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: quality_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quality_item (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    quality_id bigint NOT NULL,
    item_kind character varying(16) NOT NULL,
    item_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: quote_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quote_line (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    quote_id bigint NOT NULL,
    line_no integer NOT NULL,
    quantity numeric(19,8) NOT NULL,
    unit_price_eur numeric(19,8) NOT NULL,
    line_total_eur numeric(19,8) NOT NULL,
    unit_price_sats bigint,
    line_total_sats bigint,
    product_ref_id bigint,
    product_ref_type character varying(64),
    catalogue_item_id bigint,
    tax_code character varying(32),
    tax_rate_percent numeric(19,4),
    tax_amount_eur numeric(19,8),
    reverse_charge boolean,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: receipt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.receipt (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    receipt_number character varying(64),
    purchase_order_id bigint NOT NULL,
    received_date timestamp without time zone,
    receipt_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: receipt_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.receipt_line (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    receipt_id bigint NOT NULL,
    line_no integer NOT NULL,
    purchase_order_line_id bigint,
    quantity_received numeric(19,8) NOT NULL,
    catalogue_item_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: resource; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.resource (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    resource_kind character varying(32) NOT NULL,
    capacity_hours numeric(19,8),
    agent_ref_id bigint,
    hr_file_id bigint,
    person_ref character varying(255),
    email character varying(255),
    active boolean,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: risk_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.risk_item (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    status character varying(32) NOT NULL,
    severity character varying(32) NOT NULL,
    likelihood character varying(32) NOT NULL,
    owner_name character varying(255),
    treatment_note text,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: rma; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rma (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    rma_number character varying(64),
    customer_id bigint,
    customer_name character varying(255),
    sales_order_id bigint,
    inquiry_id bigint,
    catalogue_item_id bigint,
    product_name character varying(255),
    quantity numeric(19,8) NOT NULL,
    reason text,
    expected_refund_eur numeric(19,8),
    expected_refund_sats bigint,
    currency_code character varying(8) NOT NULL,
    requested_at timestamp without time zone,
    confirmed_at timestamp without time zone,
    closed_at timestamp without time zone,
    stock_move_id bigint,
    rma_status character varying(32) NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role (
    id bigint NOT NULL,
    version bigint NOT NULL,
    authority character varying(255) NOT NULL
);


--
-- Name: role_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_profile (
    id bigint NOT NULL,
    version bigint NOT NULL,
    profile_id character varying(64) NOT NULL,
    label character varying(128) NOT NULL,
    default_home character varying(128) NOT NULL,
    spring_roles_json text NOT NULL,
    matrix_json text NOT NULL,
    matrix_version integer NOT NULL,
    system_preset boolean NOT NULL
);


--
-- Name: sales_crm_activity; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_crm_activity (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    opportunity_id bigint NOT NULL,
    kind character varying(32) NOT NULL,
    channel character varying(32),
    activity_at timestamp without time zone NOT NULL,
    due_date timestamp without time zone,
    access character varying(4000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: sales_opportunity; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_opportunity (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    stage character varying(32) NOT NULL,
    customer_id bigint,
    contact_name character varying(255),
    contact_email character varying(255),
    expected_amount_eur numeric(19,8),
    currency_code character varying(8) NOT NULL,
    expected_close_date timestamp without time zone,
    owner_name character varying(255),
    source character varying(32),
    resulting_quote_id bigint,
    active boolean NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: sales_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_order (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    order_number character varying(64),
    customer_id bigint,
    customer_name character varying(255),
    source_quote_id bigint,
    order_date timestamp without time zone,
    confirmed_at timestamp without time zone,
    amount_eur numeric(19,8) NOT NULL,
    net_amount_eur numeric(19,8),
    tax_amount_eur numeric(19,8),
    amount_sats bigint,
    currency_code character varying(8) NOT NULL,
    document_locale character varying(16),
    order_status character varying(32) NOT NULL,
    finance_invoice_id bigint,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    nostr_event_id character varying(128),
    nostr_message_ref character varying(256),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    public_nostr boolean,
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: sales_order_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_order_dynamic (
    sales_order_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: sales_plan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_plan (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: sales_price_list; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_price_list (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    code character varying(64),
    list_kind character varying(32) NOT NULL,
    currency_code character varying(8) NOT NULL,
    active boolean NOT NULL,
    valid_from timestamp without time zone,
    valid_to timestamp without time zone,
    customer_id bigint,
    default_discount_percent numeric(5,4),
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: sales_price_list_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_price_list_line (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    price_list_id bigint NOT NULL,
    line_no integer NOT NULL,
    catalogue_item_id bigint,
    item_code character varying(64),
    min_quantity numeric(19,8) NOT NULL,
    unit_price numeric(19,8) NOT NULL,
    discount_percent numeric(5,4),
    currency_code character varying(8),
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: satisfaction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.satisfaction (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: service_level; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_level (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    code character varying(64),
    response_hours integer,
    resolve_hours integer,
    channel character varying(16) NOT NULL,
    active boolean,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: service_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_order (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    order_number character varying(64),
    catalogue_item_id bigint,
    customer_id bigint,
    customer_name character varying(255),
    source_sales_order_id bigint,
    resource_id bigint,
    scheduled_at timestamp without time zone,
    started_at timestamp without time zone,
    completed_at timestamp without time zone,
    amount_eur numeric(19,8) NOT NULL,
    amount_sats bigint,
    currency_code character varying(8) NOT NULL,
    fulfill_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    nostr_event_id character varying(128),
    nostr_message_ref character varying(256),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    public_nostr boolean,
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: service_order_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_order_dynamic (
    service_order_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: service_order_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.service_order_line (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    service_order_id bigint NOT NULL,
    line_no integer NOT NULL,
    quantity numeric(19,8) NOT NULL,
    unit_price_eur numeric(19,8) NOT NULL,
    line_total_eur numeric(19,8) NOT NULL,
    unit_price_sats bigint,
    line_total_sats bigint,
    service_ref_id bigint,
    product_ref_id bigint,
    product_ref_type character varying(64),
    catalogue_item_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: setup_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.setup_state (
    id bigint NOT NULL,
    version bigint NOT NULL,
    setup_complete boolean NOT NULL,
    template_tier character varying(16) NOT NULL,
    draft_step integer NOT NULL,
    draft_json text,
    wizard_version integer NOT NULL,
    completed_at timestamp without time zone,
    completed_by character varying(255),
    notes text,
    date_created timestamp without time zone NOT NULL,
    last_updated timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL
);


--
-- Name: simple_class; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.simple_class (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    classification character varying(128),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: simple_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.simple_status (
    id bigint NOT NULL,
    version bigint NOT NULL,
    code character varying(32) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    sort_order integer,
    enabled boolean
);


--
-- Name: simple_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.simple_type (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    classification character varying(128),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: simple_unit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.simple_unit (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    classification character varying(128),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: site; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.site (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    legal_entity_id bigint NOT NULL,
    site_type character varying(32) NOT NULL,
    address_text text,
    active boolean NOT NULL,
    access character varying(4000),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32)
);


--
-- Name: solution; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solution (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    integration_type character varying(32) NOT NULL,
    lifecycle_status character varying(32) NOT NULL,
    endpoint_ref character varying(2000),
    version_label character varying(64),
    system_id bigint,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: solution_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solution_dynamic (
    solution_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: sourcing_category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sourcing_category (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: sourcing_plan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sourcing_plan (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: stakeholder_party; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stakeholder_party (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    party_kind character varying(32) NOT NULL,
    contact_name character varying(255),
    contact_email character varying(255),
    ethics_note text,
    external_ref character varying(128),
    status character varying(32) NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: standard; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.standard (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    metric_code character varying(64),
    target_value character varying(128),
    unit character varying(64),
    standard_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: stock_move; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_move (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    catalogue_item_id bigint NOT NULL,
    qty_delta numeric(19,8) NOT NULL,
    reason character varying(32) NOT NULL,
    source_type character varying(64),
    source_id character varying(64),
    lot_id bigint,
    moved_at timestamp without time zone,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: strategy_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.strategy_item (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    type character varying(128),
    item_kind character varying(32) NOT NULL,
    status character varying(32) NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    tags character varying(2000)
);


--
-- Name: supported_locale; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.supported_locale (
    id bigint NOT NULL,
    version bigint NOT NULL,
    code character varying(16) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    language_code character varying(8) NOT NULL,
    country_code character varying(2) NOT NULL,
    currency_code character varying(3) NOT NULL,
    enabled boolean
);


--
-- Name: system; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    system_key character varying(64),
    environment character varying(16) NOT NULL,
    architecture_note text,
    active boolean,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: system_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_dynamic (
    system_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tag (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: task; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    assignee_human_npub character varying(128),
    assignee_agent_id bigint,
    start_at timestamp without time zone,
    end_at timestamp without time zone,
    deadline_at timestamp without time zone,
    urgency integer NOT NULL,
    importance integer NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    deputy_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: tax; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tax (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    period character varying(7) NOT NULL,
    jurisdiction character varying(32) NOT NULL,
    amount_eur numeric(19,8) NOT NULL,
    tax_status character varying(32) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: tax_code; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tax_code (
    id bigint NOT NULL,
    version bigint NOT NULL,
    code character varying(32) NOT NULL,
    jurisdiction character varying(16) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    rate_percent numeric(19,4) NOT NULL,
    tax_kind character varying(32) NOT NULL,
    reverse_charge boolean NOT NULL,
    intra_community boolean NOT NULL,
    enabled boolean
);


--
-- Name: three_way_match; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.three_way_match (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    purchase_order_id bigint NOT NULL,
    receipt_id bigint NOT NULL,
    payable_id bigint NOT NULL,
    match_status character varying(32) NOT NULL,
    amount_po_eur numeric(19,8),
    amount_payable_eur numeric(19,8),
    variance_eur numeric(19,8),
    amount_ok boolean,
    qty_ok boolean,
    proposed_at timestamp without time zone NOT NULL,
    confirmed_at timestamp without time zone,
    confirmed_by_npub character varying(128),
    access character varying(4000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: time_track; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.time_track (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    hr_file_id bigint NOT NULL,
    started_at timestamp without time zone,
    ended_at timestamp without time zone,
    hours numeric(19,8) NOT NULL,
    time_kind character varying(16) NOT NULL,
    time_status character varying(16) NOT NULL,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: treasury; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.treasury (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    account_kind character varying(32) NOT NULL,
    currency_code character varying(8) NOT NULL,
    balance_eur numeric(19,8) NOT NULL,
    balance_sats bigint NOT NULL,
    ledger_account_id bigint,
    external_ref character varying(256),
    active boolean,
    last_reconciled_at timestamp without time zone,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: treasury_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.treasury_dynamic (
    treasury_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public."user" (
    id bigint NOT NULL,
    version bigint NOT NULL,
    username character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    enabled boolean NOT NULL,
    account_expired boolean NOT NULL,
    account_locked boolean NOT NULL,
    password_expired boolean NOT NULL,
    nsec text,
    npub text,
    bunker text,
    totp_secret text,
    totp_enabled boolean NOT NULL,
    totp_recovery_codes text,
    display_name character varying(255),
    role_profile_id character varying(64),
    permission_override_json text
);


--
-- Name: user_company_membership; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_company_membership (
    id bigint NOT NULL,
    version bigint NOT NULL,
    user_id bigint NOT NULL,
    legal_entity_id bigint NOT NULL,
    is_default boolean NOT NULL,
    date_created timestamp without time zone NOT NULL,
    last_updated timestamp without time zone NOT NULL
);


--
-- Name: user_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_config (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    ext_id character varying(256),
    ext_source character varying(128),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: user_config_dynamic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_config_dynamic (
    user_config_id bigint,
    dynamic_object character varying(255),
    dynamic_idx character varying(255),
    dynamic_elt character varying(255) NOT NULL
);


--
-- Name: user_preference; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_preference (
    id bigint NOT NULL,
    version bigint NOT NULL,
    username character varying(255) NOT NULL,
    pref_key character varying(255) NOT NULL,
    pref_value text,
    category character varying(64) NOT NULL,
    last_updated_by character varying(255),
    date_created timestamp without time zone NOT NULL,
    last_updated timestamp without time zone NOT NULL
);


--
-- Name: user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_role (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL
);


--
-- Name: vendor; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vendor (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    email character varying(255),
    phone character varying(64),
    contact_name character varying(255),
    vendor_npub character varying(200),
    tax_id character varying(64),
    currency_code character varying(8) NOT NULL,
    communication_locale character varying(16),
    active boolean,
    access character varying(4000),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: warranty; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.warranty (
    id bigint NOT NULL,
    version bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    warranty_number character varying(64),
    customer_id bigint,
    customer_name character varying(255),
    catalogue_item_id bigint,
    product_name character varying(255),
    sales_order_id bigint,
    valid_from timestamp without time zone,
    valid_to timestamp without time zone,
    cover_status character varying(32) NOT NULL,
    access character varying(4000),
    assignee_agent_id bigint,
    assignee_human_npub character varying(128),
    attachment_count integer,
    attachment_meta character varying(8000),
    classification character varying(128),
    created_by_npub character varying(200),
    date_created timestamp without time zone NOT NULL,
    deputy_npub character varying(200),
    last_updated timestamp without time zone NOT NULL,
    last_updated_by_npub character varying(200),
    npub_owner character varying(200) NOT NULL,
    protection character varying(32),
    revision bigint,
    revision_note character varying(2000),
    status character varying(64),
    tags character varying(2000),
    type character varying(64)
);


--
-- Name: account account_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.account
    ADD CONSTRAINT account_pkey PRIMARY KEY (id);


--
-- Name: address address_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.address
    ADD CONSTRAINT address_pkey PRIMARY KEY (id);


--
-- Name: agent_api_token agent_api_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_api_token
    ADD CONSTRAINT agent_api_token_pkey PRIMARY KEY (id);


--
-- Name: agent_approval agent_approval_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_approval
    ADD CONSTRAINT agent_approval_pkey PRIMARY KEY (id);


--
-- Name: agent_human_link agent_human_link_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_human_link
    ADD CONSTRAINT agent_human_link_pkey PRIMARY KEY (id);


--
-- Name: agent_memory agent_memory_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_memory
    ADD CONSTRAINT agent_memory_pkey PRIMARY KEY (id);


--
-- Name: agent_resource agent_resource_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_resource
    ADD CONSTRAINT agent_resource_pkey PRIMARY KEY (id);


--
-- Name: bank_account bank_account_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_account
    ADD CONSTRAINT bank_account_pkey PRIMARY KEY (id);


--
-- Name: bill_of_materials bill_of_materials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_of_materials
    ADD CONSTRAINT bill_of_materials_pkey PRIMARY KEY (id);


--
-- Name: bom_item bom_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bom_item
    ADD CONSTRAINT bom_item_pkey PRIMARY KEY (id);


--
-- Name: budget budget_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.budget
    ADD CONSTRAINT budget_pkey PRIMARY KEY (id);


--
-- Name: business_process business_process_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_process
    ADD CONSTRAINT business_process_pkey PRIMARY KEY (id);


--
-- Name: candidate candidate_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.candidate
    ADD CONSTRAINT candidate_pkey PRIMARY KEY (id);


--
-- Name: catalogue_item catalogue_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.catalogue_item
    ADD CONSTRAINT catalogue_item_pkey PRIMARY KEY (id);


--
-- Name: change_item change_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.change_item
    ADD CONSTRAINT change_item_pkey PRIMARY KEY (id);


--
-- Name: change_request change_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.change_request
    ADD CONSTRAINT change_request_pkey PRIMARY KEY (id);


--
-- Name: claims claims_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.claims
    ADD CONSTRAINT claims_pkey PRIMARY KEY (id);


--
-- Name: classification classification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classification
    ADD CONSTRAINT classification_pkey PRIMARY KEY (id);


--
-- Name: collection_activity collection_activity_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.collection_activity
    ADD CONSTRAINT collection_activity_pkey PRIMARY KEY (id);


--
-- Name: comms_log comms_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comms_log
    ADD CONSTRAINT comms_log_pkey PRIMARY KEY (id);


--
-- Name: company_currency company_currency_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_currency
    ADD CONSTRAINT company_currency_pkey PRIMARY KEY (id);


--
-- Name: company_setting company_setting_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_setting
    ADD CONSTRAINT company_setting_pkey PRIMARY KEY (id);


--
-- Name: company_tax_code company_tax_code_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_tax_code
    ADD CONSTRAINT company_tax_code_pkey PRIMARY KEY (id);


--
-- Name: competitor competitor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.competitor
    ADD CONSTRAINT competitor_pkey PRIMARY KEY (id);


--
-- Name: compliance_item compliance_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compliance_item
    ADD CONSTRAINT compliance_item_pkey PRIMARY KEY (id);


--
-- Name: component component_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.component
    ADD CONSTRAINT component_pkey PRIMARY KEY (id);


--
-- Name: config_item config_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.config_item
    ADD CONSTRAINT config_item_pkey PRIMARY KEY (id);


--
-- Name: content_blob content_blob_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_blob
    ADD CONSTRAINT content_blob_pkey PRIMARY KEY (id);


--
-- Name: content content_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content
    ADD CONSTRAINT content_pkey PRIMARY KEY (id);


--
-- Name: content_type_config content_type_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_type_config
    ADD CONSTRAINT content_type_config_pkey PRIMARY KEY (id);


--
-- Name: content_type content_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_type
    ADD CONSTRAINT content_type_pkey PRIMARY KEY (id);


--
-- Name: contract contract_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract
    ADD CONSTRAINT contract_pkey PRIMARY KEY (id);


--
-- Name: countries countries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries
    ADD CONSTRAINT countries_pkey PRIMARY KEY (id);


--
-- Name: currencies currencies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.currencies
    ADD CONSTRAINT currencies_pkey PRIMARY KEY (id);


--
-- Name: customer customer_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer
    ADD CONSTRAINT customer_pkey PRIMARY KEY (id);


--
-- Name: customer_quote customer_quote_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_quote
    ADD CONSTRAINT customer_quote_pkey PRIMARY KEY (id);


--
-- Name: data data_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data
    ADD CONSTRAINT data_pkey PRIMARY KEY (id);


--
-- Name: development_project development_project_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.development_project
    ADD CONSTRAINT development_project_pkey PRIMARY KEY (id);


--
-- Name: global_config global_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.global_config
    ADD CONSTRAINT global_config_pkey PRIMARY KEY (id);


--
-- Name: go_to_market go_to_market_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.go_to_market
    ADD CONSTRAINT go_to_market_pkey PRIMARY KEY (id);


--
-- Name: goal goal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal
    ADD CONSTRAINT goal_pkey PRIMARY KEY (id);


--
-- Name: headcount_plan headcount_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.headcount_plan
    ADD CONSTRAINT headcount_plan_pkey PRIMARY KEY (id);


--
-- Name: help_topic help_topic_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.help_topic
    ADD CONSTRAINT help_topic_pkey PRIMARY KEY (id);


--
-- Name: hive hive_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hive
    ADD CONSTRAINT hive_pkey PRIMARY KEY (id);


--
-- Name: hrfile hrfile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.hrfile
    ADD CONSTRAINT hrfile_pkey PRIMARY KEY (id);


--
-- Name: import_job import_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_job
    ADD CONSTRAINT import_job_pkey PRIMARY KEY (id);


--
-- Name: inquiry inquiry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inquiry
    ADD CONSTRAINT inquiry_pkey PRIMARY KEY (id);


--
-- Name: invoice invoice_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice
    ADD CONSTRAINT invoice_pkey PRIMARY KEY (id);


--
-- Name: it_ticket it_ticket_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.it_ticket
    ADD CONSTRAINT it_ticket_pkey PRIMARY KEY (id);


--
-- Name: itchange itchange_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.itchange
    ADD CONSTRAINT itchange_pkey PRIMARY KEY (id);


--
-- Name: journal_entry journal_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.journal_entry
    ADD CONSTRAINT journal_entry_pkey PRIMARY KEY (id);


--
-- Name: journal_line journal_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.journal_line
    ADD CONSTRAINT journal_line_pkey PRIMARY KEY (id);


--
-- Name: knowledge_area knowledge_area_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_area
    ADD CONSTRAINT knowledge_area_pkey PRIMARY KEY (id);


--
-- Name: legal_entity legal_entity_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.legal_entity
    ADD CONSTRAINT legal_entity_pkey PRIMARY KEY (id);


--
-- Name: lot lot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lot
    ADD CONSTRAINT lot_pkey PRIMARY KEY (id);


--
-- Name: market market_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market
    ADD CONSTRAINT market_pkey PRIMARY KEY (id);


--
-- Name: object_content object_content_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.object_content
    ADD CONSTRAINT object_content_pkey PRIMARY KEY (id);


--
-- Name: object_content_profile object_content_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.object_content_profile
    ADD CONSTRAINT object_content_profile_pkey PRIMARY KEY (id);


--
-- Name: order_line order_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_line
    ADD CONSTRAINT order_line_pkey PRIMARY KEY (id);


--
-- Name: party_access party_access_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.party_access
    ADD CONSTRAINT party_access_pkey PRIMARY KEY (id);


--
-- Name: payable payable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payable
    ADD CONSTRAINT payable_pkey PRIMARY KEY (id);


--
-- Name: payment_option payment_option_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_option
    ADD CONSTRAINT payment_option_pkey PRIMARY KEY (id);


--
-- Name: payment payment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_pkey PRIMARY KEY (id);


--
-- Name: payroll_item payroll_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payroll_item
    ADD CONSTRAINT payroll_item_pkey PRIMARY KEY (id);


--
-- Name: payroll payroll_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payroll
    ADD CONSTRAINT payroll_pkey PRIMARY KEY (id);


--
-- Name: physical_asset physical_asset_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_asset
    ADD CONSTRAINT physical_asset_pkey PRIMARY KEY (id);


--
-- Name: policy policy_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy
    ADD CONSTRAINT policy_pkey PRIMARY KEY (id);


--
-- Name: portfolio portfolio_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.portfolio
    ADD CONSTRAINT portfolio_pkey PRIMARY KEY (id);


--
-- Name: procedure procedure_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procedure
    ADD CONSTRAINT procedure_pkey PRIMARY KEY (id);


--
-- Name: process_step process_step_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_step
    ADD CONSTRAINT process_step_pkey PRIMARY KEY (id);


--
-- Name: production_order production_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_order
    ADD CONSTRAINT production_order_pkey PRIMARY KEY (id);


--
-- Name: program program_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.program
    ADD CONSTRAINT program_pkey PRIMARY KEY (id);


--
-- Name: project_item project_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_item
    ADD CONSTRAINT project_item_pkey PRIMARY KEY (id);


--
-- Name: project project_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project
    ADD CONSTRAINT project_pkey PRIMARY KEY (id);


--
-- Name: purchase_order_line purchase_order_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_line
    ADD CONSTRAINT purchase_order_line_pkey PRIMARY KEY (id);


--
-- Name: purchase_order purchase_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order
    ADD CONSTRAINT purchase_order_pkey PRIMARY KEY (id);


--
-- Name: quality_check quality_check_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality_check
    ADD CONSTRAINT quality_check_pkey PRIMARY KEY (id);


--
-- Name: quality_item quality_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality_item
    ADD CONSTRAINT quality_item_pkey PRIMARY KEY (id);


--
-- Name: quality quality_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality
    ADD CONSTRAINT quality_pkey PRIMARY KEY (id);


--
-- Name: quote_line quote_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quote_line
    ADD CONSTRAINT quote_line_pkey PRIMARY KEY (id);


--
-- Name: receipt_line receipt_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipt_line
    ADD CONSTRAINT receipt_line_pkey PRIMARY KEY (id);


--
-- Name: receipt receipt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipt
    ADD CONSTRAINT receipt_pkey PRIMARY KEY (id);


--
-- Name: resource resource_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resource
    ADD CONSTRAINT resource_pkey PRIMARY KEY (id);


--
-- Name: risk_item risk_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_item
    ADD CONSTRAINT risk_item_pkey PRIMARY KEY (id);


--
-- Name: rma rma_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rma
    ADD CONSTRAINT rma_pkey PRIMARY KEY (id);


--
-- Name: role role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);


--
-- Name: role_profile role_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_profile
    ADD CONSTRAINT role_profile_pkey PRIMARY KEY (id);


--
-- Name: sales_crm_activity sales_crm_activity_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_crm_activity
    ADD CONSTRAINT sales_crm_activity_pkey PRIMARY KEY (id);


--
-- Name: sales_opportunity sales_opportunity_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_opportunity
    ADD CONSTRAINT sales_opportunity_pkey PRIMARY KEY (id);


--
-- Name: sales_order sales_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order
    ADD CONSTRAINT sales_order_pkey PRIMARY KEY (id);


--
-- Name: sales_plan sales_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_plan
    ADD CONSTRAINT sales_plan_pkey PRIMARY KEY (id);


--
-- Name: sales_price_list_line sales_price_list_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_price_list_line
    ADD CONSTRAINT sales_price_list_line_pkey PRIMARY KEY (id);


--
-- Name: sales_price_list sales_price_list_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_price_list
    ADD CONSTRAINT sales_price_list_pkey PRIMARY KEY (id);


--
-- Name: satisfaction satisfaction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.satisfaction
    ADD CONSTRAINT satisfaction_pkey PRIMARY KEY (id);


--
-- Name: service_level service_level_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_level
    ADD CONSTRAINT service_level_pkey PRIMARY KEY (id);


--
-- Name: service_order_line service_order_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_order_line
    ADD CONSTRAINT service_order_line_pkey PRIMARY KEY (id);


--
-- Name: service_order service_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_order
    ADD CONSTRAINT service_order_pkey PRIMARY KEY (id);


--
-- Name: setup_state setup_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.setup_state
    ADD CONSTRAINT setup_state_pkey PRIMARY KEY (id);


--
-- Name: simple_class simple_class_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simple_class
    ADD CONSTRAINT simple_class_pkey PRIMARY KEY (id);


--
-- Name: simple_status simple_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simple_status
    ADD CONSTRAINT simple_status_pkey PRIMARY KEY (id);


--
-- Name: simple_type simple_type_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simple_type
    ADD CONSTRAINT simple_type_pkey PRIMARY KEY (id);


--
-- Name: simple_unit simple_unit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simple_unit
    ADD CONSTRAINT simple_unit_pkey PRIMARY KEY (id);


--
-- Name: site site_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.site
    ADD CONSTRAINT site_pkey PRIMARY KEY (id);


--
-- Name: solution solution_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solution
    ADD CONSTRAINT solution_pkey PRIMARY KEY (id);


--
-- Name: sourcing_category sourcing_category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sourcing_category
    ADD CONSTRAINT sourcing_category_pkey PRIMARY KEY (id);


--
-- Name: sourcing_plan sourcing_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sourcing_plan
    ADD CONSTRAINT sourcing_plan_pkey PRIMARY KEY (id);


--
-- Name: stakeholder_party stakeholder_party_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stakeholder_party
    ADD CONSTRAINT stakeholder_party_pkey PRIMARY KEY (id);


--
-- Name: standard standard_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.standard
    ADD CONSTRAINT standard_pkey PRIMARY KEY (id);


--
-- Name: stock_move stock_move_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_move
    ADD CONSTRAINT stock_move_pkey PRIMARY KEY (id);


--
-- Name: strategy_item strategy_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.strategy_item
    ADD CONSTRAINT strategy_item_pkey PRIMARY KEY (id);


--
-- Name: supported_locale supported_locale_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supported_locale
    ADD CONSTRAINT supported_locale_pkey PRIMARY KEY (id);


--
-- Name: system system_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system
    ADD CONSTRAINT system_pkey PRIMARY KEY (id);


--
-- Name: tag tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tag
    ADD CONSTRAINT tag_pkey PRIMARY KEY (id);


--
-- Name: task task_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task
    ADD CONSTRAINT task_pkey PRIMARY KEY (id);


--
-- Name: tax_code tax_code_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tax_code
    ADD CONSTRAINT tax_code_pkey PRIMARY KEY (id);


--
-- Name: tax tax_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tax
    ADD CONSTRAINT tax_pkey PRIMARY KEY (id);


--
-- Name: three_way_match three_way_match_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.three_way_match
    ADD CONSTRAINT three_way_match_pkey PRIMARY KEY (id);


--
-- Name: time_track time_track_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.time_track
    ADD CONSTRAINT time_track_pkey PRIMARY KEY (id);


--
-- Name: treasury treasury_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.treasury
    ADD CONSTRAINT treasury_pkey PRIMARY KEY (id);


--
-- Name: sales_price_list uk1ca2c6f83e157f3b0c25306e24e9; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_price_list
    ADD CONSTRAINT uk1ca2c6f83e157f3b0c25306e24e9 UNIQUE (npub_owner, code);


--
-- Name: company_tax_code uk3095ad442b4a6ac5ad432c7d6966; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_tax_code
    ADD CONSTRAINT uk3095ad442b4a6ac5ad432c7d6966 UNIQUE (npub_owner, tax_code);


--
-- Name: company_currency uk875c00a2413ff8d4b4c7247b4b68; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_currency
    ADD CONSTRAINT uk875c00a2413ff8d4b4c7247b4b68 UNIQUE (npub_owner, currency_code);


--
-- Name: countries uk_5dhgnik9p8t72kaktdb8kd8dt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.countries
    ADD CONSTRAINT uk_5dhgnik9p8t72kaktdb8kd8dt UNIQUE (code);


--
-- Name: currencies uk_5r2dfxl1m7vus47ma0y05sflt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.currencies
    ADD CONSTRAINT uk_5r2dfxl1m7vus47ma0y05sflt UNIQUE (code);


--
-- Name: legal_entity uk_6ljwtmw4u7b04bqmvwkg27pdt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.legal_entity
    ADD CONSTRAINT uk_6ljwtmw4u7b04bqmvwkg27pdt UNIQUE (code);


--
-- Name: content_type uk_6t8yswbnddmfo70kkes4litmw; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_type
    ADD CONSTRAINT uk_6t8yswbnddmfo70kkes4litmw UNIQUE (code);


--
-- Name: role_profile uk_9rbu3k5855pj3n7wx8snkt6i; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_profile
    ADD CONSTRAINT uk_9rbu3k5855pj3n7wx8snkt6i UNIQUE (profile_id);


--
-- Name: simple_status uk_gu0b03yb1dyos2wblun9e3u2i; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.simple_status
    ADD CONSTRAINT uk_gu0b03yb1dyos2wblun9e3u2i UNIQUE (code);


--
-- Name: role uk_irsamgnera6angm0prq1kemt2; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT uk_irsamgnera6angm0prq1kemt2 UNIQUE (authority);


--
-- Name: tax_code uk_m2a26s070jqyvdr5bw64pe7qd; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tax_code
    ADD CONSTRAINT uk_m2a26s070jqyvdr5bw64pe7qd UNIQUE (code);


--
-- Name: help_topic uk_o5pb4tsbxmynfjlvf2m47qfid; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.help_topic
    ADD CONSTRAINT uk_o5pb4tsbxmynfjlvf2m47qfid UNIQUE (help_key);


--
-- Name: agent_api_token uk_okwwtfa59drvxvtugo5mwnhpk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_api_token
    ADD CONSTRAINT uk_okwwtfa59drvxvtugo5mwnhpk UNIQUE (token_hash);


--
-- Name: supported_locale uk_ows5fta222no9nd1uajfyrgx3; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supported_locale
    ADD CONSTRAINT uk_ows5fta222no9nd1uajfyrgx3 UNIQUE (code);


--
-- Name: user uk_sb8bbouer5wak8vyiiy4pf2bx; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."user"
    ADD CONSTRAINT uk_sb8bbouer5wak8vyiiy4pf2bx UNIQUE (username);


--
-- Name: catalogue_item ukaf9672e215deb222a0795ee790c2; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.catalogue_item
    ADD CONSTRAINT ukaf9672e215deb222a0795ee790c2 UNIQUE (npub_owner, code);


--
-- Name: user_company_membership user_company_membership_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_company_membership
    ADD CONSTRAINT user_company_membership_pkey PRIMARY KEY (id);


--
-- Name: user_config user_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_config
    ADD CONSTRAINT user_config_pkey PRIMARY KEY (id);


--
-- Name: user user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public."user"
    ADD CONSTRAINT user_pkey PRIMARY KEY (id);


--
-- Name: user_preference user_preference_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_preference
    ADD CONSTRAINT user_preference_pkey PRIMARY KEY (id);


--
-- Name: user_role user_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT user_role_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: vendor vendor_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendor
    ADD CONSTRAINT vendor_pkey PRIMARY KEY (id);


--
-- Name: warranty warranty_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warranty
    ADD CONSTRAINT warranty_pkey PRIMARY KEY (id);


--
-- Name: account_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX account_code_idx ON public.account USING btree (code);


--
-- Name: agent_api_token_hash_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_api_token_hash_idx ON public.agent_api_token USING btree (token_hash);


--
-- Name: agent_api_token_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_api_token_user_idx ON public.agent_api_token USING btree (user_id);


--
-- Name: agent_approval_decision_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_approval_decision_idx ON public.agent_approval USING btree (decision);


--
-- Name: agent_hive_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_hive_status_idx ON public.hive USING btree (hive_status);


--
-- Name: agent_human_link_agent_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_human_link_agent_idx ON public.agent_human_link USING btree (agent_id);


--
-- Name: agent_memory_agent_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_memory_agent_idx ON public.agent_memory USING btree (agent_id);


--
-- Name: agent_memory_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_memory_kind_idx ON public.agent_memory USING btree (memory_kind);


--
-- Name: agent_resource_hive_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_resource_hive_idx ON public.agent_resource USING btree (hive_id);


--
-- Name: agent_resource_key_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_resource_key_idx ON public.agent_resource USING btree (agent_key);


--
-- Name: agent_resource_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX agent_resource_status_idx ON public.agent_resource USING btree (agent_status);


--
-- Name: asset_physical_ci_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX asset_physical_ci_idx ON public.physical_asset USING btree (config_item_id);


--
-- Name: asset_physical_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX asset_physical_status_idx ON public.physical_asset USING btree (status);


--
-- Name: asset_physical_tag_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX asset_physical_tag_idx ON public.physical_asset USING btree (asset_tag);


--
-- Name: budget_period_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX budget_period_idx ON public.budget USING btree (period);


--
-- Name: candidate_stage_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX candidate_stage_idx ON public.candidate USING btree (stage);


--
-- Name: cap_change_item_chg_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_change_item_chg_idx ON public.change_item USING btree (change_id);


--
-- Name: cap_change_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_change_status_idx ON public.change_request USING btree (change_status);


--
-- Name: cap_knowledge_parent_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_knowledge_parent_idx ON public.knowledge_area USING btree (parent_id);


--
-- Name: cap_process_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_process_code_idx ON public.business_process USING btree (process_code);


--
-- Name: cap_process_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_process_kind_idx ON public.business_process USING btree (process_kind);


--
-- Name: cap_process_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_process_status_idx ON public.business_process USING btree (process_status);


--
-- Name: cap_process_step_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_process_step_code_idx ON public.process_step USING btree (step_code);


--
-- Name: cap_process_step_proc_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_process_step_proc_idx ON public.process_step USING btree (process_id);


--
-- Name: cap_program_portfolio_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_program_portfolio_idx ON public.program USING btree (portfolio_id);


--
-- Name: cap_project_item_proj_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_project_item_proj_idx ON public.project_item USING btree (project_id);


--
-- Name: cap_project_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_project_status_idx ON public.project USING btree (project_status);


--
-- Name: cap_quality_item_q_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cap_quality_item_q_idx ON public.quality_item USING btree (quality_id);


--
-- Name: catalogue_item_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX catalogue_item_code_idx ON public.catalogue_item USING btree (code);


--
-- Name: catalogue_item_nature_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX catalogue_item_nature_idx ON public.catalogue_item USING btree (nature);


--
-- Name: claims_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX claims_number_idx ON public.claims USING btree (claim_number);


--
-- Name: claims_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX claims_status_idx ON public.claims USING btree (claim_status);


--
-- Name: collection_activity_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX collection_activity_at_idx ON public.collection_activity USING btree (activity_at);


--
-- Name: collection_activity_invoice_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX collection_activity_invoice_idx ON public.collection_activity USING btree (invoice_id);


--
-- Name: collection_activity_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX collection_activity_kind_idx ON public.collection_activity USING btree (kind);


--
-- Name: company_currency_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX company_currency_code_idx ON public.company_currency USING btree (currency_code);


--
-- Name: company_currency_npub_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX company_currency_npub_idx ON public.company_currency USING btree (npub_owner);


--
-- Name: company_setting_key_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX company_setting_key_idx ON public.company_setting USING btree (setting_key);


--
-- Name: company_setting_npub_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX company_setting_npub_idx ON public.company_setting USING btree (npub_owner);


--
-- Name: company_tax_code_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX company_tax_code_code_idx ON public.company_tax_code USING btree (tax_code);


--
-- Name: company_tax_code_npub_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX company_tax_code_npub_idx ON public.company_tax_code USING btree (npub_owner);


--
-- Name: delivery_resource_hr_file_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX delivery_resource_hr_file_idx ON public.resource USING btree (hr_file_id);


--
-- Name: delivery_resource_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX delivery_resource_kind_idx ON public.resource USING btree (resource_kind);


--
-- Name: dev_project_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dev_project_kind_idx ON public.development_project USING btree (project_kind);


--
-- Name: dev_project_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX dev_project_status_idx ON public.development_project USING btree (project_status);


--
-- Name: digital_change_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX digital_change_kind_idx ON public.itchange USING btree (change_kind);


--
-- Name: digital_change_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX digital_change_status_idx ON public.itchange USING btree (change_status);


--
-- Name: digital_ci_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX digital_ci_kind_idx ON public.config_item USING btree (ci_kind);


--
-- Name: digital_ci_physical_asset_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX digital_ci_physical_asset_idx ON public.config_item USING btree (physical_asset_id);


--
-- Name: digital_ci_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX digital_ci_status_idx ON public.config_item USING btree (ci_status);


--
-- Name: digital_data_class_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX digital_data_class_idx ON public.data USING btree (data_class);


--
-- Name: digital_solution_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX digital_solution_status_idx ON public.solution USING btree (lifecycle_status);


--
-- Name: digital_solution_type_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX digital_solution_type_idx ON public.solution USING btree (integration_type);


--
-- Name: digital_system_key_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX digital_system_key_idx ON public.system USING btree (system_key);


--
-- Name: digital_ticket_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX digital_ticket_number_idx ON public.it_ticket USING btree (ticket_number);


--
-- Name: digital_ticket_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX digital_ticket_status_idx ON public.it_ticket USING btree (ticket_status);


--
-- Name: grrc_compliance_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX grrc_compliance_kind_idx ON public.compliance_item USING btree (item_kind);


--
-- Name: grrc_compliance_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX grrc_compliance_status_idx ON public.compliance_item USING btree (status);


--
-- Name: grrc_risk_severity_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX grrc_risk_severity_idx ON public.risk_item USING btree (severity);


--
-- Name: grrc_risk_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX grrc_risk_status_idx ON public.risk_item USING btree (status);


--
-- Name: help_topic_key_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX help_topic_key_idx ON public.help_topic USING btree (help_key);


--
-- Name: hr_file_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX hr_file_status_idx ON public.hrfile USING btree (employment_status);


--
-- Name: hr_file_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX hr_file_user_idx ON public.hrfile USING btree (user_id);


--
-- Name: hr_payroll_period_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX hr_payroll_period_idx ON public.payroll USING btree (period);


--
-- Name: hr_policy_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX hr_policy_code_idx ON public.policy USING btree (code);


--
-- Name: inquiry_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inquiry_number_idx ON public.inquiry USING btree (inquiry_number);


--
-- Name: inquiry_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inquiry_status_idx ON public.inquiry USING btree (case_status);


--
-- Name: invoice_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_number_idx ON public.invoice USING btree (invoice_number);


--
-- Name: invoice_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_status_idx ON public.invoice USING btree (invoice_status);


--
-- Name: je_event_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX je_event_idx ON public.journal_entry USING btree (event_id);


--
-- Name: je_period_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX je_period_idx ON public.journal_entry USING btree (period);


--
-- Name: legal_entity_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX legal_entity_code_idx ON public.legal_entity USING btree (code);


--
-- Name: legal_entity_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX legal_entity_kind_idx ON public.legal_entity USING btree (kind);


--
-- Name: lot_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lot_code_idx ON public.lot USING btree (lot_code);


--
-- Name: obj_content_channel_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX obj_content_channel_idx ON public.object_content USING btree (channel);


--
-- Name: obj_content_host_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX obj_content_host_idx ON public.object_content USING btree (host_package, host_resource, host_id);


--
-- Name: obj_content_profile_host_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX obj_content_profile_host_idx ON public.object_content_profile USING btree (host_package, host_resource);


--
-- Name: order_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX order_number_idx ON public.sales_order USING btree (order_number);


--
-- Name: order_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX order_status_idx ON public.sales_order USING btree (order_status);


--
-- Name: party_access_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX party_access_kind_idx ON public.party_access USING btree (party_kind);


--
-- Name: party_access_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX party_access_user_idx ON public.party_access USING btree (user_id);


--
-- Name: payment_event_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX payment_event_idx ON public.payment USING btree (event_id);


--
-- Name: payment_ext_tx_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX payment_ext_tx_idx ON public.payment USING btree (external_tx_id);


--
-- Name: po_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX po_number_idx ON public.purchase_order USING btree (po_number);


--
-- Name: po_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX po_status_idx ON public.purchase_order USING btree (po_status);


--
-- Name: prod_order_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX prod_order_number_idx ON public.production_order USING btree (order_number);


--
-- Name: prod_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX prod_status_idx ON public.production_order USING btree (prod_status);


--
-- Name: quote_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX quote_number_idx ON public.customer_quote USING btree (quote_number);


--
-- Name: quote_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX quote_status_idx ON public.customer_quote USING btree (quote_status);


--
-- Name: receipt_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX receipt_number_idx ON public.receipt USING btree (receipt_number);


--
-- Name: rma_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX rma_number_idx ON public.rma USING btree (rma_number);


--
-- Name: rma_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX rma_status_idx ON public.rma USING btree (rma_status);


--
-- Name: sales_crm_activity_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sales_crm_activity_at_idx ON public.sales_crm_activity USING btree (activity_at);


--
-- Name: sales_crm_activity_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sales_crm_activity_kind_idx ON public.sales_crm_activity USING btree (kind);


--
-- Name: sales_crm_activity_opp_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sales_crm_activity_opp_idx ON public.sales_crm_activity USING btree (opportunity_id);


--
-- Name: sales_opportunity_active_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sales_opportunity_active_idx ON public.sales_opportunity USING btree (active);


--
-- Name: sales_opportunity_customer_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sales_opportunity_customer_idx ON public.sales_opportunity USING btree (customer_id);


--
-- Name: sales_opportunity_stage_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sales_opportunity_stage_idx ON public.sales_opportunity USING btree (stage);


--
-- Name: sales_pll_catalogue_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sales_pll_catalogue_idx ON public.sales_price_list_line USING btree (catalogue_item_id);


--
-- Name: sales_price_list_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sales_price_list_code_idx ON public.sales_price_list USING btree (code);


--
-- Name: sales_price_list_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sales_price_list_kind_idx ON public.sales_price_list USING btree (list_kind);


--
-- Name: service_level_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX service_level_code_idx ON public.service_level USING btree (code);


--
-- Name: service_order_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX service_order_number_idx ON public.service_order USING btree (order_number);


--
-- Name: service_order_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX service_order_status_idx ON public.service_order USING btree (fulfill_status);


--
-- Name: stakeholder_comms_channel_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX stakeholder_comms_channel_idx ON public.comms_log USING btree (channel);


--
-- Name: stakeholder_comms_party_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX stakeholder_comms_party_idx ON public.comms_log USING btree (stakeholder_party_id);


--
-- Name: stakeholder_comms_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX stakeholder_comms_status_idx ON public.comms_log USING btree (status);


--
-- Name: stakeholder_party_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX stakeholder_party_kind_idx ON public.stakeholder_party USING btree (party_kind);


--
-- Name: stakeholder_party_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX stakeholder_party_status_idx ON public.stakeholder_party USING btree (status);


--
-- Name: stock_move_catalogue_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX stock_move_catalogue_idx ON public.stock_move USING btree (catalogue_item_id);


--
-- Name: stock_move_reason_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX stock_move_reason_idx ON public.stock_move USING btree (reason);


--
-- Name: strategy_competitor_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX strategy_competitor_status_idx ON public.competitor USING btree (status);


--
-- Name: strategy_item_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX strategy_item_kind_idx ON public.strategy_item USING btree (item_kind);


--
-- Name: strategy_item_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX strategy_item_status_idx ON public.strategy_item USING btree (status);


--
-- Name: task_assignee_agent_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX task_assignee_agent_idx ON public.task USING btree (assignee_agent_id);


--
-- Name: task_assignee_human_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX task_assignee_human_idx ON public.task USING btree (assignee_human_npub);


--
-- Name: task_deadline_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX task_deadline_idx ON public.task USING btree (deadline_at);


--
-- Name: tax_code_code_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tax_code_code_idx ON public.tax_code USING btree (code);


--
-- Name: tax_code_jur_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tax_code_jur_idx ON public.tax_code USING btree (jurisdiction);


--
-- Name: tax_period_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tax_period_idx ON public.tax USING btree (period);


--
-- Name: three_way_match_payable_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX three_way_match_payable_idx ON public.three_way_match USING btree (payable_id);


--
-- Name: three_way_match_po_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX three_way_match_po_idx ON public.three_way_match USING btree (purchase_order_id);


--
-- Name: three_way_match_receipt_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX three_way_match_receipt_idx ON public.three_way_match USING btree (receipt_id);


--
-- Name: three_way_match_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX three_way_match_status_idx ON public.three_way_match USING btree (match_status);


--
-- Name: treasury_kind_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX treasury_kind_idx ON public.treasury USING btree (account_kind);


--
-- Name: ucm_company_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ucm_company_idx ON public.user_company_membership USING btree (legal_entity_id);


--
-- Name: ucm_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ucm_user_idx ON public.user_company_membership USING btree (user_id);


--
-- Name: user_pref_key_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_pref_key_idx ON public.user_preference USING btree (pref_key);


--
-- Name: user_pref_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_pref_user_idx ON public.user_preference USING btree (username);


--
-- Name: warranty_number_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX warranty_number_idx ON public.warranty USING btree (warranty_number);


--
-- Name: warranty_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX warranty_status_idx ON public.warranty USING btree (cover_status);


--
-- Name: service_order_line fk1l0j14m0069tjctq4n8nyoxt6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_order_line
    ADD CONSTRAINT fk1l0j14m0069tjctq4n8nyoxt6 FOREIGN KEY (service_order_id) REFERENCES public.service_order(id);


--
-- Name: claims fk1mg3xuro18i72iwb18vnvagnj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.claims
    ADD CONSTRAINT fk1mg3xuro18i72iwb18vnvagnj FOREIGN KEY (inquiry_id) REFERENCES public.inquiry(id);


--
-- Name: purchase_order fk20jcn7pw6hvx0uo0sh4y1d9xv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order
    ADD CONSTRAINT fk20jcn7pw6hvx0uo0sh4y1d9xv FOREIGN KEY (vendor_id) REFERENCES public.vendor(id);


--
-- Name: purchase_order_line fk210t80fsgdi4s4g7tlg9vdgkd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_line
    ADD CONSTRAINT fk210t80fsgdi4s4g7tlg9vdgkd FOREIGN KEY (purchase_order_id) REFERENCES public.purchase_order(id);


--
-- Name: receipt_line fk2i96o6ogenrkobg86ybk7rvjf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipt_line
    ADD CONSTRAINT fk2i96o6ogenrkobg86ybk7rvjf FOREIGN KEY (purchase_order_line_id) REFERENCES public.purchase_order_line(id);


--
-- Name: user_company_membership fk371ypl3oaex3tisyryj0i52ms; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_company_membership
    ADD CONSTRAINT fk371ypl3oaex3tisyryj0i52ms FOREIGN KEY (legal_entity_id) REFERENCES public.legal_entity(id);


--
-- Name: sales_order fk3e945b7lii5amycbdj61j974g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order
    ADD CONSTRAINT fk3e945b7lii5amycbdj61j974g FOREIGN KEY (source_quote_id) REFERENCES public.customer_quote(id);


--
-- Name: journal_line fk3hnh2c486fbb0ouh0rsp2xdmh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.journal_line
    ADD CONSTRAINT fk3hnh2c486fbb0ouh0rsp2xdmh FOREIGN KEY (account_id) REFERENCES public.account(id);


--
-- Name: payment_option fk3s6r9apbq3eht4jney4kle3no; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_option
    ADD CONSTRAINT fk3s6r9apbq3eht4jney4kle3no FOREIGN KEY (invoice_id) REFERENCES public.invoice(id);


--
-- Name: quality_check fk6lt00w1y54s6u92gd8eolbh8j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quality_check
    ADD CONSTRAINT fk6lt00w1y54s6u92gd8eolbh8j FOREIGN KEY (lot_id) REFERENCES public.lot(id);


--
-- Name: quote_line fk72t0v8lodknx5o5py8lhoveqv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quote_line
    ADD CONSTRAINT fk72t0v8lodknx5o5py8lhoveqv FOREIGN KEY (quote_id) REFERENCES public.customer_quote(id);


--
-- Name: sales_price_list_line fk7ed1aovdmlgy38aesm1j28ior; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_price_list_line
    ADD CONSTRAINT fk7ed1aovdmlgy38aesm1j28ior FOREIGN KEY (price_list_id) REFERENCES public.sales_price_list(id);


--
-- Name: bank_account fk8nsfsctq31ypojvu2mpj0ixub; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_account
    ADD CONSTRAINT fk8nsfsctq31ypojvu2mpj0ixub FOREIGN KEY (legal_entity_id) REFERENCES public.legal_entity(id);


--
-- Name: user_company_membership fk9v4evu1rovh6u3g59qhb9etwo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_company_membership
    ADD CONSTRAINT fk9v4evu1rovh6u3g59qhb9etwo FOREIGN KEY (user_id) REFERENCES public."user"(id);


--
-- Name: user_role fka68196081fvovjhkek5m97n3y; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT fka68196081fvovjhkek5m97n3y FOREIGN KEY (role_id) REFERENCES public.role(id);


--
-- Name: sales_crm_activity fkblh0uvfv7pmojqlf7lxcy81h; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_crm_activity
    ADD CONSTRAINT fkblh0uvfv7pmojqlf7lxcy81h FOREIGN KEY (opportunity_id) REFERENCES public.sales_opportunity(id);


--
-- Name: bom_item fkbq9scpsk01dm1enitqxj4ngmu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bom_item
    ADD CONSTRAINT fkbq9scpsk01dm1enitqxj4ngmu FOREIGN KEY (component_item_id) REFERENCES public.catalogue_item(id);


--
-- Name: claims fkc5mli5ljqr4xui4elityht41o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.claims
    ADD CONSTRAINT fkc5mli5ljqr4xui4elityht41o FOREIGN KEY (warranty_id) REFERENCES public.warranty(id);


--
-- Name: user_role fkfgsgxvihks805qcq8sq26ab7c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT fkfgsgxvihks805qcq8sq26ab7c FOREIGN KEY (user_id) REFERENCES public."user"(id);


--
-- Name: journal_line fkh19xc8cgke67ybful1msasibo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.journal_line
    ADD CONSTRAINT fkh19xc8cgke67ybful1msasibo FOREIGN KEY (journal_entry_id) REFERENCES public.journal_entry(id);


--
-- Name: bill_of_materials fkh4cms0vw9w8owup06rcmw205u; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_of_materials
    ADD CONSTRAINT fkh4cms0vw9w8owup06rcmw205u FOREIGN KEY (catalogue_item_id) REFERENCES public.catalogue_item(id);


--
-- Name: production_order fkho4xr3rdihshu5heoudgupjwv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_order
    ADD CONSTRAINT fkho4xr3rdihshu5heoudgupjwv FOREIGN KEY (catalogue_item_id) REFERENCES public.catalogue_item(id);


--
-- Name: time_track fkijdtjuo1x4pctwal57rxuy1r4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.time_track
    ADD CONSTRAINT fkijdtjuo1x4pctwal57rxuy1r4 FOREIGN KEY (hr_file_id) REFERENCES public.hrfile(id);


--
-- Name: receipt_line fkjbqvbehdtln7c870ojaejlryd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipt_line
    ADD CONSTRAINT fkjbqvbehdtln7c870ojaejlryd FOREIGN KEY (receipt_id) REFERENCES public.receipt(id);


--
-- Name: receipt fkjxmk0bjvgwm3iux64qgl4w7vp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receipt
    ADD CONSTRAINT fkjxmk0bjvgwm3iux64qgl4w7vp FOREIGN KEY (purchase_order_id) REFERENCES public.purchase_order(id);


--
-- Name: order_line fkk10ctu43vm71vk67ax5acfdau; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_line
    ADD CONSTRAINT fkk10ctu43vm71vk67ax5acfdau FOREIGN KEY (sales_order_id) REFERENCES public.sales_order(id);


--
-- Name: agent_api_token fkkkf4q7l31ol6qf7alo7h47a2y; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agent_api_token
    ADD CONSTRAINT fkkkf4q7l31ol6qf7alo7h47a2y FOREIGN KEY (user_id) REFERENCES public."user"(id);


--
-- Name: rma fkl27pip36ijh7nenb26cluolw6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rma
    ADD CONSTRAINT fkl27pip36ijh7nenb26cluolw6 FOREIGN KEY (inquiry_id) REFERENCES public.inquiry(id);


--
-- Name: lot fkllbtqlkny1d82e0tcietxkui0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lot
    ADD CONSTRAINT fkllbtqlkny1d82e0tcietxkui0 FOREIGN KEY (catalogue_item_id) REFERENCES public.catalogue_item(id);


--
-- Name: service_order fkm2vdu0k8klgxiqkxjr77bmr27; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.service_order
    ADD CONSTRAINT fkm2vdu0k8klgxiqkxjr77bmr27 FOREIGN KEY (resource_id) REFERENCES public.resource(id);


--
-- Name: address fkm6advanx8en6f9gltp94d4srk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.address
    ADD CONSTRAINT fkm6advanx8en6f9gltp94d4srk FOREIGN KEY (country_id) REFERENCES public.countries(id);


--
-- Name: customer_quote fkn7q8711wrd266lnjq7p9oinac; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_quote
    ADD CONSTRAINT fkn7q8711wrd266lnjq7p9oinac FOREIGN KEY (customer_id) REFERENCES public.customer(id);


--
-- Name: inquiry fkogq2fmn258w8i879vug8uxma4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inquiry
    ADD CONSTRAINT fkogq2fmn258w8i879vug8uxma4 FOREIGN KEY (service_level_id) REFERENCES public.service_level(id);


--
-- Name: component fkos52c0fw2mu9qs624eai30vkj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.component
    ADD CONSTRAINT fkos52c0fw2mu9qs624eai30vkj FOREIGN KEY (catalogue_item_id) REFERENCES public.catalogue_item(id);


--
-- Name: site fkqbloswwh25et7h9h6w175ttky; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.site
    ADD CONSTRAINT fkqbloswwh25et7h9h6w175ttky FOREIGN KEY (legal_entity_id) REFERENCES public.legal_entity(id);


--
-- Name: sales_order fkqqe3xj99rblvm5n0h0cp48gsa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_order
    ADD CONSTRAINT fkqqe3xj99rblvm5n0h0cp48gsa FOREIGN KEY (customer_id) REFERENCES public.customer(id);


--
-- Name: goal fks3vqq2qnvw0rghjprr3trqkf6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.goal
    ADD CONSTRAINT fks3vqq2qnvw0rghjprr3trqkf6 FOREIGN KEY (hr_file_id) REFERENCES public.hrfile(id);


--
-- Name: payment fksb24p8f52refbb80qwp4gem9n; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT fksb24p8f52refbb80qwp4gem9n FOREIGN KEY (invoice_id) REFERENCES public.invoice(id);


--
-- Name: bom_item fksms7six07vchimsdjvgvia7n7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bom_item
    ADD CONSTRAINT fksms7six07vchimsdjvgvia7n7 FOREIGN KEY (bom_id) REFERENCES public.bill_of_materials(id);


--
-- PostgreSQL database dump complete
--

-- ADR-058 marker. Installer treats public.schema_pack as "already applied".
CREATE TABLE public.schema_pack (
    id integer PRIMARY KEY,
    software_version character varying(32) NOT NULL,
    install_phase character varying(64) NOT NULL,
    applied_at timestamp without time zone DEFAULT now() NOT NULL
);

INSERT INTO public.schema_pack (id, software_version, install_phase)
VALUES (1, '0.2.6', 'schema-applied');
