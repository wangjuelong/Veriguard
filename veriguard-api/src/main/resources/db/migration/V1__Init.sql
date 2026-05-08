-- Veriguard schema baseline (二开裁剪后第一次 baseline)
-- 由 pg_dump -s 在 Phase 11 导出，整合所有沙箱 M1 之前的历史 migrations。
-- 后续 schema 变更从 V2 开始。

--
--




--
-- Name: hstore; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS hstore WITH SCHEMA public;


--
-- Name: EXTENSION hstore; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION hstore IS 'data type for storing sets of (key, value) pairs';


--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: author_enum; Type: TYPE; Schema: public; Owner: veriguard
--

CREATE TYPE public.author_enum AS ENUM (
    'HUMAN',
    'AI',
    'AI_OUTDATED'
);


ALTER TYPE public.author_enum OWNER TO veriguard;

--
-- Name: connector_configuration_format; Type: TYPE; Schema: public; Owner: veriguard
--

CREATE TYPE public.connector_configuration_format AS ENUM (
    'DEFAULT',
    'DATE',
    'DATETIME',
    'DURATION',
    'EMAIL',
    'PASSWORD',
    'URI'
);


ALTER TYPE public.connector_configuration_format OWNER TO veriguard;

--
-- Name: connector_configuration_type; Type: TYPE; Schema: public; Owner: veriguard
--

CREATE TYPE public.connector_configuration_type AS ENUM (
    'ARRAY',
    'BOOLEAN',
    'INTEGER',
    'OBJECT',
    'STRING'
);


ALTER TYPE public.connector_configuration_type OWNER TO veriguard;

--
-- Name: connector_instance_current_status_type; Type: TYPE; Schema: public; Owner: veriguard
--

CREATE TYPE public.connector_instance_current_status_type AS ENUM (
    'started',
    'stopped'
);


ALTER TYPE public.connector_instance_current_status_type OWNER TO veriguard;

--
-- Name: connector_instance_requested_status_type; Type: TYPE; Schema: public; Owner: veriguard
--

CREATE TYPE public.connector_instance_requested_status_type AS ENUM (
    'starting',
    'stopping'
);


ALTER TYPE public.connector_instance_requested_status_type OWNER TO veriguard;

--
-- Name: connector_instance_source; Type: TYPE; Schema: public; Owner: veriguard
--

CREATE TYPE public.connector_instance_source AS ENUM (
    'PROPERTIES_MIGRATION',
    'CATALOG_DEPLOYMENT',
    'OTHER'
);


ALTER TYPE public.connector_instance_source OWNER TO veriguard;

--
-- Name: connector_type; Type: TYPE; Schema: public; Owner: veriguard
--

CREATE TYPE public.connector_type AS ENUM (
    'COLLECTOR',
    'INJECTOR',
    'EXECUTOR'
);


ALTER TYPE public.connector_type OWNER TO veriguard;

--
-- Name: array_position_wrapper(anyelement, text); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.array_position_wrapper(a anyelement, b text) RETURNS integer
    LANGUAGE sql
    AS $$
  SELECT array_position(a, b)
      $$;


ALTER FUNCTION public.array_position_wrapper(a anyelement, b text) OWNER TO veriguard;

--
-- Name: array_to_string_wrapper(anyarray, text); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.array_to_string_wrapper(a anyarray, b text) RETURNS text
    LANGUAGE sql IMMUTABLE
    AS $$
        SELECT array_to_string(a, b)
    $$;


ALTER FUNCTION public.array_to_string_wrapper(a anyarray, b text) OWNER TO veriguard;

--
-- Name: array_to_string_wrapper(character varying, text); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.array_to_string_wrapper(a character varying, b text) RETURNS text
    LANGUAGE sql IMMUTABLE
    AS $$
        SELECT a
    $$;


ALTER FUNCTION public.array_to_string_wrapper(a character varying, b text) OWNER TO veriguard;

--
-- Name: array_union(anyarray, anyarray); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.array_union(a anyarray, b anyarray) RETURNS anyarray
    LANGUAGE sql
    AS $$SELECT array_agg(DISTINCT x)FROM (         SELECT unnest(a) x         UNION ALL SELECT unnest(b)     ) AS u    $$;


ALTER FUNCTION public.array_union(a anyarray, b anyarray) OWNER TO veriguard;

--
-- Name: delete_notification_rules_for_scenario(); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.delete_notification_rules_for_scenario() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
      BEGIN
          DELETE FROM notification_rules
          WHERE notification_resource_type = 'SCENARIO'
              AND notification_resource_id = OLD.scenario_id;
          RETURN OLD;
      END;
      $$;


ALTER FUNCTION public.delete_notification_rules_for_scenario() OWNER TO veriguard;

--
-- Name: update_asset_updated_at_after_delete_finding(); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.update_asset_updated_at_after_delete_finding() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
        UPDATE assets
        SET asset_updated_at = now()
        WHERE asset_id = OLD.asset_id;
        RETURN OLD;
    END;
    $$;


ALTER FUNCTION public.update_asset_updated_at_after_delete_finding() OWNER TO veriguard;

--
-- Name: update_asset_updated_at_after_delete_inject(); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.update_asset_updated_at_after_delete_inject() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
        UPDATE assets
        SET asset_updated_at = now()
        WHERE asset_id = OLD.asset_id;
        RETURN OLD;
    END;
    $$;


ALTER FUNCTION public.update_asset_updated_at_after_delete_inject() OWNER TO veriguard;

--
-- Name: update_exercise_updated_at_after_delete_team(); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.update_exercise_updated_at_after_delete_team() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
        UPDATE exercises
        SET exercise_updated_at = now()
        WHERE exercise_id = OLD.exercise_id;
        RETURN OLD;
    END;
    $$;


ALTER FUNCTION public.update_exercise_updated_at_after_delete_team() OWNER TO veriguard;

--
-- Name: update_inject_updated_at_after_delete_inject_child(); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.update_inject_updated_at_after_delete_inject_child() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE injects
    SET inject_updated_at = now()
    WHERE inject_id = OLD.inject_parent_id;
    RETURN OLD;
END;
$$;


ALTER FUNCTION public.update_inject_updated_at_after_delete_inject_child() OWNER TO veriguard;

--
-- Name: update_inject_updated_at_after_delete_team(); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.update_inject_updated_at_after_delete_team() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
        UPDATE injects
        SET inject_updated_at = now()
        WHERE inject_id = OLD.inject_id;
        RETURN OLD;
    END;
    $$;


ALTER FUNCTION public.update_inject_updated_at_after_delete_team() OWNER TO veriguard;

--
-- Name: update_injector_contract_updated_at(); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.update_injector_contract_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE injectors_contracts
    SET injector_contract_updated_at = now()
    WHERE injector_contract_id = OLD.injector_contract_id;  -- Use NEW. if it is AFTER INSERT

    RETURN OLD;
END;
$$;


ALTER FUNCTION public.update_injector_contract_updated_at() OWNER TO veriguard;

--
-- Name: update_launch_order_trigger(); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.update_launch_order_trigger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE exercises
    SET exercise_launch_order = CASE
        WHEN NEW.exercise_start_date IS NULL
            THEN NULL
        ELSE nextval('exercise_launch_order_seq')
        END
    WHERE exercise_id = NEW.exercise_id;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.update_launch_order_trigger() OWNER TO veriguard;

--
-- Name: update_scenario_updated_at_after_delete_team(); Type: FUNCTION; Schema: public; Owner: veriguard
--

CREATE FUNCTION public.update_scenario_updated_at_after_delete_team() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
    BEGIN
        UPDATE scenarios
        SET scenario_updated_at = now()
        WHERE scenario_id = OLD.scenario_id;
        RETURN OLD;
    END;
    $$;


ALTER FUNCTION public.update_scenario_updated_at_after_delete_team() OWNER TO veriguard;

--
-- Name: array_union_agg(anyarray); Type: AGGREGATE; Schema: public; Owner: veriguard
--

CREATE AGGREGATE public.array_union_agg(anyarray) (
    SFUNC = public.array_union,
    STYPE = anyarray,
    INITCOND = '{}'
);


ALTER AGGREGATE public.array_union_agg(anyarray) OWNER TO veriguard;



--
-- Name: agents; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.agents (
    agent_id character varying(255) NOT NULL,
    agent_asset character varying(255) NOT NULL,
    agent_privilege character varying(255) NOT NULL,
    agent_deployment_mode character varying(255) NOT NULL,
    agent_executed_by_user character varying(255) NOT NULL,
    agent_executor character varying(255),
    agent_version character varying(255),
    agent_parent character varying(255),
    agent_inject character varying(255),
    agent_process_name character varying(255),
    agent_external_reference character varying(255),
    agent_last_seen timestamp without time zone,
    agent_created_at timestamp with time zone DEFAULT now(),
    agent_updated_at timestamp with time zone DEFAULT now(),
    agent_cleared_at timestamp without time zone DEFAULT now()
);


ALTER TABLE public.agents OWNER TO veriguard;

--
-- Name: articles; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.articles (
    article_id character varying(255) NOT NULL,
    article_created_at timestamp with time zone DEFAULT now() NOT NULL,
    article_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    article_name text,
    article_content text,
    article_channel character varying(255) NOT NULL,
    article_exercise character varying(255),
    article_author text,
    article_shares integer,
    article_likes integer,
    article_comments integer,
    article_scenario character varying(255)
);


ALTER TABLE public.articles OWNER TO veriguard;

--
-- Name: articles_documents; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.articles_documents (
    article_id character varying(255) NOT NULL,
    document_id character varying(255) NOT NULL
);


ALTER TABLE public.articles_documents OWNER TO veriguard;

--
-- Name: asset_agent_jobs; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.asset_agent_jobs (
    asset_agent_id character varying(255) NOT NULL,
    asset_agent_created_at timestamp with time zone DEFAULT now() NOT NULL,
    asset_agent_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    asset_agent_inject character varying(255),
    asset_agent_command text,
    asset_agent_agent character varying(255)
);


ALTER TABLE public.asset_agent_jobs OWNER TO veriguard;

--
-- Name: asset_groups; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.asset_groups (
    asset_group_id character varying(255) NOT NULL,
    asset_group_name character varying(255) NOT NULL,
    asset_group_description text,
    asset_group_created_at timestamp with time zone DEFAULT now() NOT NULL,
    asset_group_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    asset_group_dynamic_filter json DEFAULT '{"mode":"and","filters":[]}'::json NOT NULL,
    asset_group_external_reference character varying(255)
);


ALTER TABLE public.asset_groups OWNER TO veriguard;

--
-- Name: asset_groups_assets; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.asset_groups_assets (
    asset_group_id character varying(255) NOT NULL,
    asset_id character varying(255) NOT NULL
);


ALTER TABLE public.asset_groups_assets OWNER TO veriguard;

--
-- Name: asset_groups_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.asset_groups_tags (
    asset_group_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.asset_groups_tags OWNER TO veriguard;

--
-- Name: assets; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.assets (
    asset_id character varying(255) NOT NULL,
    asset_type character varying(255) NOT NULL,
    asset_name character varying(255) NOT NULL,
    asset_description text,
    asset_created_at timestamp with time zone DEFAULT now() NOT NULL,
    asset_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    endpoint_ips text[],
    endpoint_hostname character varying(255),
    endpoint_platform character varying(255),
    endpoint_mac_addresses text[],
    asset_external_reference character varying(255),
    endpoint_arch character varying(255) DEFAULT 'x86_64'::character varying NOT NULL,
    security_platform_type character varying(255),
    security_platform_logo_light character varying(255),
    security_platform_logo_dark character varying(255),
    endpoint_seen_ip character varying(255),
    endpoint_is_eol boolean DEFAULT false
);


ALTER TABLE public.assets OWNER TO veriguard;

--
-- Name: assets_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.assets_tags (
    asset_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.assets_tags OWNER TO veriguard;

--
-- Name: attack_patterns; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.attack_patterns (
    attack_pattern_id character varying(255) NOT NULL,
    attack_pattern_created_at timestamp with time zone DEFAULT now() NOT NULL,
    attack_pattern_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    attack_pattern_name character varying(255) NOT NULL,
    attack_pattern_description text,
    attack_pattern_external_id character varying(255) NOT NULL,
    attack_pattern_platforms text[],
    attack_pattern_permissions_required text[],
    attack_pattern_parent character varying(255),
    attack_pattern_stix_id character varying(255)
);


ALTER TABLE public.attack_patterns OWNER TO veriguard;

--
-- Name: attack_patterns_kill_chain_phases; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.attack_patterns_kill_chain_phases (
    attack_pattern_id character varying(255) NOT NULL,
    phase_id character varying(255) NOT NULL
);


ALTER TABLE public.attack_patterns_kill_chain_phases OWNER TO veriguard;

--
-- Name: catalog_connectors; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.catalog_connectors (
    catalog_connector_id character varying(255) NOT NULL,
    catalog_connector_title character varying(255) NOT NULL,
    catalog_connector_slug character varying(255) NOT NULL,
    catalog_connector_description text,
    catalog_connector_short_description text,
    catalog_connector_logo_url character varying(255),
    catalog_connector_use_cases text[],
    catalog_connector_verified boolean DEFAULT false,
    catalog_connector_last_verified_date timestamp without time zone,
    catalog_connector_playbook_supported boolean DEFAULT false,
    catalog_connector_max_confidence_level integer,
    catalog_connector_support_version character varying(50),
    catalog_connector_subscription_link character varying(255),
    catalog_connector_source_code character varying(255),
    catalog_connector_manager_supported boolean DEFAULT false,
    catalog_connector_container_version character varying(50),
    catalog_connector_container_image character varying(255),
    catalog_connector_type public.connector_type,
    catalog_connector_class_name character varying(255),
    catalog_connector_deleted_at timestamp with time zone,
    catalog_connector_created_at timestamp with time zone DEFAULT now(),
    catalog_connector_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.catalog_connectors OWNER TO veriguard;

--
-- Name: catalog_connectors_configuration; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.catalog_connectors_configuration (
    connector_configuration_id character varying(255) NOT NULL,
    connector_configuration_catalog_id character varying(255) NOT NULL,
    connector_configuration_key character varying(255) NOT NULL,
    connector_configuration_description text,
    connector_configuration_writeonly boolean DEFAULT false NOT NULL,
    connector_configuration_required boolean DEFAULT false NOT NULL,
    connector_configuration_created_at timestamp with time zone DEFAULT now(),
    connector_configuration_updated_at timestamp with time zone DEFAULT now(),
    connector_configuration_default jsonb,
    connector_configuration_enum text[],
    connector_configuration_type public.connector_configuration_type,
    connector_configuration_format public.connector_configuration_format
);


ALTER TABLE public.catalog_connectors_configuration OWNER TO veriguard;

--
-- Name: challenge_attempts; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.challenge_attempts (
    challenge_id character varying(255) NOT NULL,
    inject_status_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    challenge_attempt integer DEFAULT 0 NOT NULL,
    attempt_created_at timestamp with time zone DEFAULT now() NOT NULL,
    attempt_updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.challenge_attempts OWNER TO veriguard;

--
-- Name: challenges; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.challenges (
    challenge_id character varying(255) NOT NULL,
    challenge_created_at timestamp with time zone DEFAULT now() NOT NULL,
    challenge_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    challenge_name text,
    challenge_flag text,
    challenge_category character varying(255),
    challenge_content text,
    challenge_score double precision,
    challenge_max_attempts integer
);


ALTER TABLE public.challenges OWNER TO veriguard;

--
-- Name: challenges_documents; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.challenges_documents (
    challenge_id character varying(255) NOT NULL,
    document_id character varying(255) NOT NULL
);


ALTER TABLE public.challenges_documents OWNER TO veriguard;

--
-- Name: challenges_flags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.challenges_flags (
    flag_id character varying(255) NOT NULL,
    flag_created_at timestamp with time zone DEFAULT now() NOT NULL,
    flag_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    flag_type character varying(255) NOT NULL,
    flag_value text NOT NULL,
    flag_challenge character varying(255) NOT NULL
);


ALTER TABLE public.challenges_flags OWNER TO veriguard;

--
-- Name: challenges_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.challenges_tags (
    challenge_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.challenges_tags OWNER TO veriguard;

--
-- Name: channels; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.channels (
    channel_id character varying(255) NOT NULL,
    channel_created_at timestamp with time zone DEFAULT now() NOT NULL,
    channel_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    channel_name text,
    channel_type character varying(255),
    channel_description text,
    channel_logo_dark character varying(255),
    channel_logo_light character varying(255),
    channel_primary_color_dark character varying(255),
    channel_primary_color_light character varying(255),
    channel_secondary_color_dark character varying(255),
    channel_secondary_color_light character varying(255),
    channel_mode character varying(255)
);


ALTER TABLE public.channels OWNER TO veriguard;

--
-- Name: collectors; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.collectors (
    collector_id character varying(255) NOT NULL,
    collector_created_at timestamp with time zone DEFAULT now() NOT NULL,
    collector_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    collector_name character varying(255) NOT NULL,
    collector_type character varying(255) NOT NULL,
    collector_period integer NOT NULL,
    collector_last_execution timestamp without time zone,
    collector_external boolean DEFAULT false NOT NULL,
    collector_security_platform character varying(255),
    collector_state jsonb DEFAULT '{}'::jsonb
);


ALTER TABLE public.collectors OWNER TO veriguard;

--
-- Name: comchecks; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.comchecks (
    comcheck_id character varying(255) NOT NULL,
    comcheck_exercise character varying(255) DEFAULT NULL::character varying,
    comcheck_start_date timestamp(0) with time zone NOT NULL,
    comcheck_end_date timestamp(0) with time zone NOT NULL,
    comcheck_state character varying(256),
    comcheck_subject character varying(256),
    comcheck_message text,
    comcheck_name character varying(256)
);


ALTER TABLE public.comchecks OWNER TO veriguard;

--
-- Name: comchecks_statuses; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.comchecks_statuses (
    status_id character varying(255) NOT NULL,
    status_user character varying(255) DEFAULT NULL::character varying,
    status_comcheck character varying(255) DEFAULT NULL::character varying,
    status_sent_date timestamp without time zone,
    status_receive_date timestamp without time zone,
    status_sent_retry integer
);


ALTER TABLE public.comchecks_statuses OWNER TO veriguard;

--
-- Name: communications; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.communications (
    communication_id character varying(255) NOT NULL,
    communication_received_at timestamp without time zone NOT NULL,
    communication_sent_at timestamp without time zone NOT NULL,
    communication_subject text,
    communication_content text,
    communication_message_id text NOT NULL,
    communication_inject character varying(255),
    communication_ack boolean DEFAULT false,
    communication_animation boolean DEFAULT false,
    communication_content_html text,
    communication_from text NOT NULL,
    communication_to text NOT NULL,
    communication_attachments text[]
);


ALTER TABLE public.communications OWNER TO veriguard;

--
-- Name: communications_users; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.communications_users (
    communication_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


ALTER TABLE public.communications_users OWNER TO veriguard;

--
-- Name: connector_instance_configurations; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.connector_instance_configurations (
    connector_instance_configuration_id character varying(255) NOT NULL,
    connector_instance_configuration_key character varying(255) NOT NULL,
    connector_instance_configuration_value jsonb,
    connector_instance_id character varying(255) NOT NULL,
    connector_instance_created_at timestamp with time zone DEFAULT now(),
    connector_instance_updated_at timestamp with time zone DEFAULT now(),
    connector_instance_configuration_is_encrypted boolean DEFAULT false
);


ALTER TABLE public.connector_instance_configurations OWNER TO veriguard;

--
-- Name: connector_instance_logs; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.connector_instance_logs (
    connector_instance_log_id character varying(255) NOT NULL,
    connector_instance_log text NOT NULL,
    connector_instance_log_created_at timestamp with time zone DEFAULT now(),
    connector_instance_id character varying(255) NOT NULL
);


ALTER TABLE public.connector_instance_logs OWNER TO veriguard;

--
-- Name: connector_instances; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.connector_instances (
    connector_instance_id character varying(255) NOT NULL,
    connector_instance_catalog_id character varying(255) NOT NULL,
    connector_instance_restart_count integer,
    connector_instance_started_at timestamp without time zone,
    connector_instance_is_in_reboot_loop boolean DEFAULT false,
    connector_instance_created_at timestamp with time zone DEFAULT now(),
    connector_instance_updated_at timestamp with time zone DEFAULT now(),
    connector_instance_current_status public.connector_instance_current_status_type NOT NULL,
    connector_instance_requested_status public.connector_instance_requested_status_type NOT NULL,
    connector_instance_source public.connector_instance_source DEFAULT 'OTHER'::public.connector_instance_source NOT NULL
);


ALTER TABLE public.connector_instances OWNER TO veriguard;

--
-- Name: contract_output_elements; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.contract_output_elements (
    contract_output_element_id character varying(255) NOT NULL,
    contract_output_element_is_finding boolean DEFAULT true,
    contract_output_element_rule text NOT NULL,
    contract_output_element_name character varying(50) NOT NULL,
    contract_output_element_key character varying(255) NOT NULL,
    contract_output_element_type character varying(50) NOT NULL,
    contract_output_element_output_parser_id character varying(255) NOT NULL,
    contract_output_element_created_at timestamp(0) with time zone DEFAULT now(),
    contract_output_element_updated_at timestamp(0) with time zone DEFAULT now()
);


ALTER TABLE public.contract_output_elements OWNER TO veriguard;

--
-- Name: contract_output_elements_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.contract_output_elements_tags (
    contract_output_element_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.contract_output_elements_tags OWNER TO veriguard;

--
-- Name: custom_dashboards; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.custom_dashboards (
    custom_dashboard_id character varying(255) NOT NULL,
    custom_dashboard_name character varying(255) NOT NULL,
    custom_dashboard_description character varying(255),
    custom_dashboard_created_at timestamp with time zone DEFAULT now(),
    custom_dashboard_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.custom_dashboards OWNER TO veriguard;

--
-- Name: custom_dashboards_parameters; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.custom_dashboards_parameters (
    custom_dashboards_parameter_id character varying(255) NOT NULL,
    custom_dashboard_id character varying(255) NOT NULL,
    custom_dashboards_parameter_name text NOT NULL,
    custom_dashboards_parameter_type text NOT NULL
);


ALTER TABLE public.custom_dashboards_parameters OWNER TO veriguard;

--
-- Name: cwes; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.cwes (
    cwe_id character varying(255) NOT NULL,
    cwe_external_id character varying(255) NOT NULL,
    cwe_source character varying(255),
    cwe_created_at timestamp with time zone DEFAULT now(),
    cwe_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.cwes OWNER TO veriguard;

--
-- Name: datapacks; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.datapacks (
    datapack_id character varying(255) NOT NULL
);


ALTER TABLE public.datapacks OWNER TO veriguard;

--
-- Name: detection_remediations; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.detection_remediations (
    detection_remediation_id character varying(255) NOT NULL,
    detection_remediation_payload_id character varying(255) NOT NULL,
    detection_remediation_values text,
    detection_remediation_created_at timestamp with time zone DEFAULT now(),
    detection_remediation_updated_at timestamp with time zone DEFAULT now(),
    detection_remediation_collector_type character varying(255),
    author_rule public.author_enum DEFAULT 'HUMAN'::public.author_enum NOT NULL
);


ALTER TABLE public.detection_remediations OWNER TO veriguard;

--
-- Name: documents; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.documents (
    document_id character varying(255) NOT NULL,
    document_name character varying(255) NOT NULL,
    document_description character varying(255) DEFAULT NULL::character varying,
    document_type character varying(255) NOT NULL,
    document_target text
);


ALTER TABLE public.documents OWNER TO veriguard;

--
-- Name: documents_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.documents_tags (
    document_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.documents_tags OWNER TO veriguard;

--
-- Name: domains; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.domains (
    domain_id character varying(255) NOT NULL,
    domain_name character varying(255) NOT NULL,
    domain_color character varying(255) DEFAULT '#FFFFFF'::character varying NOT NULL,
    domain_created_at timestamp with time zone DEFAULT now(),
    domain_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.domains OWNER TO veriguard;

--
-- Name: evaluations; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.evaluations (
    evaluation_id character varying(255) NOT NULL,
    evaluation_score bigint,
    evaluation_objective character varying(255) NOT NULL,
    evaluation_user character varying(255) NOT NULL,
    evaluation_created_at timestamp with time zone DEFAULT now() NOT NULL,
    evaluation_updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.evaluations OWNER TO veriguard;

--
-- Name: execution_traces; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.execution_traces (
    execution_trace_id character varying(255) NOT NULL,
    execution_inject_status_id character varying(255),
    execution_inject_test_status_id character varying(255),
    execution_agent_id character varying(255),
    execution_message text NOT NULL,
    execution_action character varying(255),
    execution_status character varying(255) NOT NULL,
    execution_time timestamp without time zone,
    execution_created_at timestamp with time zone DEFAULT now(),
    execution_updated_at timestamp with time zone DEFAULT now(),
    execution_context_identifiers text[],
    execution_structured_output text,
    CONSTRAINT check_inject_status_or_test_status CHECK (((execution_inject_status_id IS NOT NULL) OR (execution_inject_test_status_id IS NOT NULL)))
);


ALTER TABLE public.execution_traces OWNER TO veriguard;

--
-- Name: executors; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.executors (
    executor_id character varying(255) NOT NULL,
    executor_created_at timestamp with time zone DEFAULT now() NOT NULL,
    executor_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    executor_name character varying(255) NOT NULL,
    executor_type character varying(255) NOT NULL,
    executor_platforms text[],
    executor_doc text,
    executor_background_color character varying(100)
);


ALTER TABLE public.executors OWNER TO veriguard;

--
-- Name: exercise_launch_order_seq; Type: SEQUENCE; Schema: public; Owner: veriguard
--

CREATE SEQUENCE public.exercise_launch_order_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.exercise_launch_order_seq OWNER TO veriguard;

--
-- Name: exercise_mails_reply_to; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.exercise_mails_reply_to (
    exercise_id character varying(255) NOT NULL,
    exercise_reply_to character varying(255)
);


ALTER TABLE public.exercise_mails_reply_to OWNER TO veriguard;

--
-- Name: exercises; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.exercises (
    exercise_id character varying(255) NOT NULL,
    exercise_name character varying(255) NOT NULL,
    exercise_subtitle text,
    exercise_description text,
    exercise_start_date timestamp(0) with time zone,
    exercise_end_date timestamp(0) with time zone,
    exercise_mail_from text NOT NULL,
    exercise_message_header character varying(255) DEFAULT NULL::character varying,
    exercise_message_footer character varying(255) DEFAULT NULL::character varying,
    exercise_created_at timestamp with time zone DEFAULT now() NOT NULL,
    exercise_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    exercise_status character varying(255) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    exercise_logo_dark character varying(255),
    exercise_logo_light character varying(255),
    exercise_lessons_anonymized boolean DEFAULT false,
    exercise_category character varying(255),
    exercise_severity character varying(255),
    exercise_main_focus character varying(255),
    exercise_pause_date timestamp with time zone,
    exercise_launch_order bigint,
    exercise_custom_dashboard character varying(255),
    exercise_security_coverage character varying(255)
);


ALTER TABLE public.exercises OWNER TO veriguard;

--
-- Name: exercises_documents; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.exercises_documents (
    exercise_id character varying(255) NOT NULL,
    document_id character varying(255) NOT NULL
);


ALTER TABLE public.exercises_documents OWNER TO veriguard;

--
-- Name: exercises_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.exercises_tags (
    exercise_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.exercises_tags OWNER TO veriguard;

--
-- Name: exercises_teams; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.exercises_teams (
    exercise_id character varying(255) NOT NULL,
    team_id character varying(255) NOT NULL
);


ALTER TABLE public.exercises_teams OWNER TO veriguard;

--
-- Name: exercises_teams_users; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.exercises_teams_users (
    exercise_id character varying(255) NOT NULL,
    team_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


ALTER TABLE public.exercises_teams_users OWNER TO veriguard;

--
-- Name: findings; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.findings (
    finding_id character varying(255) NOT NULL,
    finding_field character varying(255) NOT NULL,
    finding_type character varying(255) NOT NULL,
    finding_value text NOT NULL,
    finding_labels text[],
    finding_inject_id character varying(255) NOT NULL,
    finding_created_at timestamp with time zone DEFAULT now(),
    finding_updated_at timestamp with time zone DEFAULT now(),
    finding_name character varying(255)
);


ALTER TABLE public.findings OWNER TO veriguard;

--
-- Name: findings_assets; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.findings_assets (
    finding_id character varying(255) NOT NULL,
    asset_id character varying(255) NOT NULL
);


ALTER TABLE public.findings_assets OWNER TO veriguard;

--
-- Name: findings_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.findings_tags (
    finding_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.findings_tags OWNER TO veriguard;

--
-- Name: findings_teams; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.findings_teams (
    finding_id character varying(255) NOT NULL,
    team_id character varying(255) NOT NULL
);


ALTER TABLE public.findings_teams OWNER TO veriguard;

--
-- Name: findings_users; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.findings_users (
    finding_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


ALTER TABLE public.findings_users OWNER TO veriguard;

--
-- Name: grants; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.grants (
    grant_id character varying(255) NOT NULL,
    grant_group character varying(255) DEFAULT NULL::character varying,
    grant_name character varying(255) NOT NULL,
    grant_resource character varying(255) DEFAULT NULL::character varying,
    grant_resource_type character varying(50) NOT NULL
);


ALTER TABLE public.grants OWNER TO veriguard;

--
-- Name: groups; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.groups (
    group_id character varying(255) NOT NULL,
    group_name character varying(255) NOT NULL,
    group_description text,
    group_default_user_assign boolean DEFAULT false
);


ALTER TABLE public.groups OWNER TO veriguard;

--
-- Name: groups_roles; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.groups_roles (
    role_id character varying(255) NOT NULL,
    group_id character varying(255) NOT NULL
);


ALTER TABLE public.groups_roles OWNER TO veriguard;

--
-- Name: import_mappers; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.import_mappers (
    mapper_id uuid NOT NULL,
    mapper_name character varying(255) NOT NULL,
    mapper_inject_type_column character varying(255) NOT NULL,
    mapper_created_at timestamp with time zone DEFAULT now(),
    mapper_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.import_mappers OWNER TO veriguard;

--
-- Name: indexing_status; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.indexing_status (
    indexing_status_type text NOT NULL,
    indexing_status_indexing_date timestamp with time zone NOT NULL
);


ALTER TABLE public.indexing_status OWNER TO veriguard;

--
-- Name: inject_importers; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.inject_importers (
    importer_id uuid NOT NULL,
    importer_mapper_id uuid,
    importer_import_type_value character varying(255) NOT NULL,
    importer_injector_contract_id character varying(255),
    importer_created_at timestamp with time zone DEFAULT now(),
    importer_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.inject_importers OWNER TO veriguard;

--
-- Name: injectors; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injectors (
    injector_id character varying(255) NOT NULL,
    injector_created_at timestamp with time zone DEFAULT now() NOT NULL,
    injector_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    injector_name character varying(255) NOT NULL,
    injector_type character varying(255) NOT NULL,
    injector_external boolean DEFAULT false NOT NULL,
    injector_custom_contracts boolean DEFAULT false,
    injector_category character varying(255),
    injector_executor_commands public.hstore,
    injector_executor_clear_commands public.hstore,
    injector_payloads boolean DEFAULT false,
    injector_dependencies text[] DEFAULT '{}'::text[]
);


ALTER TABLE public.injectors OWNER TO veriguard;

--
-- Name: injectors_contracts; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injectors_contracts (
    injector_contract_id character varying(255) NOT NULL,
    injector_contract_created_at timestamp with time zone DEFAULT now() NOT NULL,
    injector_contract_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    injector_contract_labels public.hstore,
    injector_contract_manual boolean,
    injector_contract_content text NOT NULL,
    injector_id character varying(255) NOT NULL,
    injector_contract_atomic_testing boolean DEFAULT true NOT NULL,
    injector_contract_custom boolean DEFAULT false,
    injector_contract_platforms text[],
    injector_contract_needs_executor boolean DEFAULT false,
    injector_contract_payload character varying(255),
    injector_contract_import_available boolean DEFAULT false NOT NULL,
    injector_contract_external_id character varying
);


ALTER TABLE public.injectors_contracts OWNER TO veriguard;

--
-- Name: injectors_contracts_attack_patterns; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injectors_contracts_attack_patterns (
    attack_pattern_id character varying(255) NOT NULL,
    injector_contract_id character varying(255) NOT NULL
);


ALTER TABLE public.injectors_contracts_attack_patterns OWNER TO veriguard;

--
-- Name: injectors_contracts_domains; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injectors_contracts_domains (
    injector_contract_id character varying(255) NOT NULL,
    domain_id character varying(255) NOT NULL
);


ALTER TABLE public.injectors_contracts_domains OWNER TO veriguard;

--
-- Name: injectors_contracts_vulnerabilities; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injectors_contracts_vulnerabilities (
    injector_contract_id character varying(255) NOT NULL,
    vulnerability_id character varying(255) NOT NULL
);


ALTER TABLE public.injectors_contracts_vulnerabilities OWNER TO veriguard;

--
-- Name: injects; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injects (
    inject_id character varying(255) NOT NULL,
    inject_user character varying(255) DEFAULT NULL::character varying,
    inject_title character varying(255) NOT NULL,
    inject_description text,
    inject_content text,
    inject_all_teams boolean NOT NULL,
    inject_enabled boolean NOT NULL,
    inject_depends_duration bigint NOT NULL,
    inject_depends_from_another character varying(255),
    inject_exercise character varying(255),
    inject_created_at timestamp with time zone DEFAULT now() NOT NULL,
    inject_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    inject_country character varying(255),
    inject_city character varying(255),
    inject_injector_contract character varying(256),
    inject_assets character varying(256),
    injects_asset_groups character varying(256),
    inject_scenario character varying(255),
    inject_trigger_now_date timestamp without time zone,
    inject_collect_status character varying(255)
);


ALTER TABLE public.injects OWNER TO veriguard;

--
-- Name: injects_asset_groups; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injects_asset_groups (
    inject_id character varying(255) NOT NULL,
    asset_group_id character varying(255) NOT NULL
);


ALTER TABLE public.injects_asset_groups OWNER TO veriguard;

--
-- Name: injects_assets; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injects_assets (
    inject_id character varying(255) NOT NULL,
    asset_id character varying(255) NOT NULL
);


ALTER TABLE public.injects_assets OWNER TO veriguard;

--
-- Name: injects_dependencies; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injects_dependencies (
    inject_parent_id character varying(255) NOT NULL,
    inject_children_id character varying(255) NOT NULL,
    dependency_condition jsonb,
    dependency_created_at timestamp with time zone DEFAULT now(),
    dependency_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.injects_dependencies OWNER TO veriguard;

--
-- Name: injects_documents; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injects_documents (
    inject_id character varying(255) NOT NULL,
    document_id character varying(255) NOT NULL,
    document_attached boolean DEFAULT false
);


ALTER TABLE public.injects_documents OWNER TO veriguard;

--
-- Name: injects_expectations; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injects_expectations (
    inject_expectation_id character varying(255) NOT NULL,
    inject_expectation_created_at timestamp with time zone DEFAULT now() NOT NULL,
    inject_expectation_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    user_id character varying(255),
    exercise_id character varying(256),
    inject_id character varying(256) NOT NULL,
    team_id character varying(256),
    inject_expectation_type character varying(255) NOT NULL,
    inject_expectation_score double precision,
    article_id character varying(255),
    challenge_id character varying(255),
    inject_expectation_expected_score double precision DEFAULT 100 NOT NULL,
    inject_expectation_name character varying(255),
    inject_expectation_description text,
    inject_expectation_group boolean DEFAULT false,
    asset_id character varying(256),
    asset_group_id character varying(256),
    inject_expectation_results json,
    inject_expectation_signatures jsonb,
    inject_expiration_time bigint NOT NULL,
    agent_id character varying(256)
);


ALTER TABLE public.injects_expectations OWNER TO veriguard;

--
-- Name: injects_expectations_traces; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injects_expectations_traces (
    inject_expectation_trace_id character varying(255) NOT NULL,
    inject_expectation_trace_expectation character varying(255) NOT NULL,
    inject_expectation_trace_source_id character varying(255) NOT NULL,
    inject_expectation_trace_alert_name text,
    inject_expectation_trace_alert_link text,
    inject_expectation_trace_date timestamp(0) with time zone,
    inject_expectation_trace_created_at timestamp with time zone DEFAULT now() NOT NULL,
    inject_expectation_trace_updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.injects_expectations_traces OWNER TO veriguard;

--
-- Name: injects_statuses; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injects_statuses (
    status_id character varying(255) NOT NULL,
    status_inject character varying(255) DEFAULT NULL::character varying,
    status_name character varying(255) DEFAULT NULL::character varying,
    tracking_sent_date timestamp(0) with time zone DEFAULT NULL::timestamp with time zone,
    tracking_end_date timestamp without time zone,
    status_payload_output json
);


ALTER TABLE public.injects_statuses OWNER TO veriguard;

--
-- Name: injects_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injects_tags (
    inject_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.injects_tags OWNER TO veriguard;

--
-- Name: injects_teams; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injects_teams (
    inject_id character varying(255) NOT NULL,
    team_id character varying(255) NOT NULL
);


ALTER TABLE public.injects_teams OWNER TO veriguard;

--
-- Name: injects_tests_statuses; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.injects_tests_statuses (
    status_id character varying(255) NOT NULL,
    status_name character varying(255) NOT NULL,
    tracking_sent_date timestamp without time zone,
    tracking_end_date timestamp without time zone,
    status_inject character varying(255) NOT NULL,
    status_created_at timestamp with time zone DEFAULT now() NOT NULL,
    status_updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.injects_tests_statuses OWNER TO veriguard;

--
-- Name: kill_chain_phases; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.kill_chain_phases (
    phase_id character varying(255) NOT NULL,
    phase_created_at timestamp with time zone DEFAULT now() NOT NULL,
    phase_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    phase_name character varying(255) NOT NULL,
    phase_kill_chain_name character varying(255) NOT NULL,
    phase_order bigint NOT NULL,
    phase_description text,
    phase_shortname character varying(255) NOT NULL,
    phase_external_id character varying(255) NOT NULL,
    phase_stix_id character varying(255)
);


ALTER TABLE public.kill_chain_phases OWNER TO veriguard;

--
-- Name: lessons_answers; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.lessons_answers (
    lessons_answer_id character varying(255) NOT NULL,
    lessons_answer_created_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_answer_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_answer_positive text,
    lessons_answer_negative text,
    lessons_answer_score integer NOT NULL,
    lessons_answer_question character varying(255) NOT NULL,
    lessons_answer_user character varying(255)
);


ALTER TABLE public.lessons_answers OWNER TO veriguard;

--
-- Name: lessons_categories; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.lessons_categories (
    lessons_category_id character varying(255) NOT NULL,
    lessons_category_created_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_category_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_category_name character varying(255) NOT NULL,
    lessons_category_description text,
    lessons_category_order integer NOT NULL,
    lessons_category_exercise character varying(255),
    lessons_category_scenario character varying(255)
);


ALTER TABLE public.lessons_categories OWNER TO veriguard;

--
-- Name: lessons_categories_teams; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.lessons_categories_teams (
    team_id character varying(255) NOT NULL,
    lessons_category_id character varying(255) NOT NULL
);


ALTER TABLE public.lessons_categories_teams OWNER TO veriguard;

--
-- Name: lessons_questions; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.lessons_questions (
    lessons_question_id character varying(255) NOT NULL,
    lessons_question_created_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_question_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_question_content text NOT NULL,
    lessons_question_explanation text,
    lessons_question_order integer NOT NULL,
    lessons_question_category character varying(255) NOT NULL
);


ALTER TABLE public.lessons_questions OWNER TO veriguard;

--
-- Name: lessons_template_categories; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.lessons_template_categories (
    lessons_template_category_id character varying(255) NOT NULL,
    lessons_template_category_created_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_template_category_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_template_category_name character varying(255) NOT NULL,
    lessons_template_category_description text,
    lessons_template_category_order integer NOT NULL,
    lessons_template_category_template character varying(255) NOT NULL
);


ALTER TABLE public.lessons_template_categories OWNER TO veriguard;

--
-- Name: lessons_template_questions; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.lessons_template_questions (
    lessons_template_question_id character varying(255) NOT NULL,
    lessons_template_question_created_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_template_question_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_template_question_content text NOT NULL,
    lessons_template_question_explanation text,
    lessons_template_question_order integer NOT NULL,
    lessons_template_question_category character varying(255) NOT NULL
);


ALTER TABLE public.lessons_template_questions OWNER TO veriguard;

--
-- Name: lessons_templates; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.lessons_templates (
    lessons_template_id character varying(255) NOT NULL,
    lessons_template_created_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_template_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    lessons_template_name character varying(255) NOT NULL,
    lessons_template_description text
);


ALTER TABLE public.lessons_templates OWNER TO veriguard;

--
-- Name: logs; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.logs (
    log_id character varying(255) NOT NULL,
    log_exercise character varying(255) DEFAULT NULL::character varying,
    log_user character varying(255) DEFAULT NULL::character varying,
    log_title character varying(255) NOT NULL,
    log_content text NOT NULL,
    log_created_at timestamp with time zone DEFAULT now() NOT NULL,
    log_updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.logs OWNER TO veriguard;

--
-- Name: logs_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.logs_tags (
    log_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.logs_tags OWNER TO veriguard;

--
-- Name: mitigations; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.mitigations (
    mitigation_id character varying(255) NOT NULL,
    mitigation_created_at timestamp with time zone DEFAULT now() NOT NULL,
    mitigation_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    mitigation_name character varying(255) NOT NULL,
    mitigation_description text,
    mitigation_external_id character varying(255) NOT NULL,
    mitigation_stix_id character varying(255) NOT NULL,
    mitigation_log_sources text[],
    mitigation_threat_hunting_techniques text
);


ALTER TABLE public.mitigations OWNER TO veriguard;

--
-- Name: mitigations_attack_patterns; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.mitigations_attack_patterns (
    mitigation_id character varying(255) NOT NULL,
    attack_pattern_id character varying(255) NOT NULL
);


ALTER TABLE public.mitigations_attack_patterns OWNER TO veriguard;

--
-- Name: notification_rules; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.notification_rules (
    notification_rule_id character varying(255) NOT NULL,
    notification_resource_type character varying(255) NOT NULL,
    notification_resource_id character varying(255) NOT NULL,
    notification_rule_trigger character varying(255) NOT NULL,
    notification_rule_type character varying(255) NOT NULL,
    notification_rule_subject character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


ALTER TABLE public.notification_rules OWNER TO veriguard;

--
-- Name: objectives; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.objectives (
    objective_id character varying(255) NOT NULL,
    objective_exercise character varying(255) DEFAULT NULL::character varying,
    objective_title character varying(255) DEFAULT NULL::character varying,
    objective_description text,
    objective_priority smallint,
    objective_created_at timestamp with time zone DEFAULT now() NOT NULL,
    objective_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    objective_scenario character varying(255)
);


ALTER TABLE public.objectives OWNER TO veriguard;

--
-- Name: organizations; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.organizations (
    organization_id character varying(255) NOT NULL,
    organization_name character varying(255) NOT NULL,
    organization_description text,
    organization_created_at timestamp with time zone DEFAULT now() NOT NULL,
    organization_updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.organizations OWNER TO veriguard;

--
-- Name: organizations_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.organizations_tags (
    organization_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.organizations_tags OWNER TO veriguard;

--
-- Name: output_parsers; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.output_parsers (
    output_parser_id character varying(255) NOT NULL,
    output_parser_mode character varying(50) NOT NULL,
    output_parser_type character varying(50) NOT NULL,
    output_parser_payload_id character varying(255) NOT NULL,
    output_parser_created_at timestamp with time zone DEFAULT now(),
    output_parser_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.output_parsers OWNER TO veriguard;

--
-- Name: parameters; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.parameters (
    parameter_id character varying(255) NOT NULL,
    parameter_key character varying(255) NOT NULL,
    parameter_value text NOT NULL
);


ALTER TABLE public.parameters OWNER TO veriguard;

--
-- Name: pauses; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.pauses (
    pause_id character varying(255) NOT NULL,
    pause_exercise character varying(255) NOT NULL,
    pause_date timestamp(0) with time zone NOT NULL,
    pause_duration bigint
);


ALTER TABLE public.pauses OWNER TO veriguard;

--
-- Name: payloads; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.payloads (
    payload_id character varying(255) NOT NULL,
    payload_type character varying(255) NOT NULL,
    payload_name character varying(255) NOT NULL,
    payload_description text,
    payload_created_at timestamp with time zone DEFAULT now() NOT NULL,
    payload_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    payload_platforms text[],
    command_executor character varying(255),
    command_content text,
    executable_file character varying(255),
    file_drop_file character varying(255),
    dns_resolution_hostname text,
    network_traffic_ip_src text,
    payload_cleanup_executor character varying(255),
    payload_cleanup_command text,
    payload_arguments json,
    payload_prerequisites json,
    network_traffic_ip_dst text,
    network_traffic_port_src integer,
    network_traffic_port_dst integer,
    network_traffic_protocol character varying(255),
    payload_external_id character varying(255),
    payload_collector character varying(255),
    payload_source character varying(255) DEFAULT 'MANUAL'::character varying,
    payload_status character varying(255) DEFAULT 'UNVERIFIED'::character varying,
    payload_elevation_required boolean DEFAULT false,
    payload_execution_arch character varying(255) DEFAULT 'ALL_ARCHITECTURES'::character varying NOT NULL,
    payload_expectations text[],
    CONSTRAINT chk_payload_cleanup_cmd_consistency CHECK ((((payload_cleanup_executor IS NULL) AND (payload_cleanup_command IS NULL)) OR ((((payload_cleanup_executor)::text <> ''::text) IS TRUE) AND ((payload_cleanup_command <> ''::text) IS TRUE)))),
    CONSTRAINT chk_payload_cmd_consistency CHECK ((((command_executor IS NULL) AND (command_content IS NULL)) OR ((((command_executor)::text <> ''::text) IS TRUE) AND ((command_content <> ''::text) IS TRUE))))
);


ALTER TABLE public.payloads OWNER TO veriguard;

--
-- Name: payloads_attack_patterns; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.payloads_attack_patterns (
    attack_pattern_id character varying(255) NOT NULL,
    payload_id character varying(255) NOT NULL
);


ALTER TABLE public.payloads_attack_patterns OWNER TO veriguard;

--
-- Name: payloads_domains; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.payloads_domains (
    payload_id character varying(255) NOT NULL,
    domain_id character varying(255) NOT NULL
);


ALTER TABLE public.payloads_domains OWNER TO veriguard;

--
-- Name: payloads_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.payloads_tags (
    payload_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.payloads_tags OWNER TO veriguard;

--
-- Name: regex_groups; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.regex_groups (
    regex_group_id character varying(255) NOT NULL,
    regex_group_field character varying(50) NOT NULL,
    regex_group_index_values character varying(50) NOT NULL,
    regex_group_contract_output_element_id character varying(255) NOT NULL,
    regex_group_created_at timestamp with time zone DEFAULT now(),
    regex_group_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.regex_groups OWNER TO veriguard;

--
-- Name: report_informations; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.report_informations (
    report_informations_id uuid NOT NULL,
    report_id uuid NOT NULL,
    report_informations_type character varying(255) NOT NULL,
    report_informations_display boolean DEFAULT false
);


ALTER TABLE public.report_informations OWNER TO veriguard;

--
-- Name: report_inject_comment; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.report_inject_comment (
    report_id uuid NOT NULL,
    inject_id character varying(255) NOT NULL,
    comment text
);


ALTER TABLE public.report_inject_comment OWNER TO veriguard;

--
-- Name: reports; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.reports (
    report_id uuid NOT NULL,
    report_name character varying(255) NOT NULL,
    report_global_observation text,
    report_created_at timestamp with time zone DEFAULT now(),
    report_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.reports OWNER TO veriguard;

--
-- Name: reports_exercises; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.reports_exercises (
    report_id uuid NOT NULL,
    exercise_id character varying(255) NOT NULL
);


ALTER TABLE public.reports_exercises OWNER TO veriguard;

--
-- Name: roles; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.roles (
    role_id character varying(255) NOT NULL,
    role_name character varying(255) NOT NULL,
    role_created_at timestamp with time zone DEFAULT now() NOT NULL,
    role_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    role_description text
);


ALTER TABLE public.roles OWNER TO veriguard;

--
-- Name: roles_capabilities; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.roles_capabilities (
    role_id character varying(255) NOT NULL,
    capability character varying(255) NOT NULL
);


ALTER TABLE public.roles_capabilities OWNER TO veriguard;

--
-- Name: rule_attributes; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.rule_attributes (
    attribute_id uuid NOT NULL,
    attribute_inject_importer_id uuid,
    attribute_name character varying(255) NOT NULL,
    attribute_columns character varying(255),
    attribute_default_value character varying(255),
    attribute_additional_config public.hstore,
    attribute_created_at timestamp with time zone DEFAULT now(),
    attribute_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.rule_attributes OWNER TO veriguard;

--
-- Name: scenario_mails_reply_to; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.scenario_mails_reply_to (
    scenario_id character varying(255) NOT NULL,
    scenario_reply_to character varying(255)
);


ALTER TABLE public.scenario_mails_reply_to OWNER TO veriguard;

--
-- Name: scenarios; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.scenarios (
    scenario_id character varying(255) NOT NULL,
    scenario_name character varying(255) NOT NULL,
    scenario_description text,
    scenario_subtitle text,
    scenario_message_header character varying(255),
    scenario_message_footer character varying(255),
    scenario_mail_from text NOT NULL,
    scenario_created_at timestamp with time zone DEFAULT now() NOT NULL,
    scenario_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    scenario_recurrence character varying(256),
    scenario_recurrence_start timestamp without time zone,
    scenario_recurrence_end timestamp without time zone,
    scenario_category character varying(255),
    scenario_severity character varying(255),
    scenario_main_focus character varying(255),
    scenario_external_reference character varying(255),
    scenario_external_url character varying(255),
    scenario_lessons_anonymized boolean DEFAULT false,
    scenario_custom_dashboard character varying(255),
    scenario_dependencies text[] DEFAULT '{}'::text[],
    scenario_type_affinity character varying(255)
);


ALTER TABLE public.scenarios OWNER TO veriguard;

--
-- Name: scenarios_documents; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.scenarios_documents (
    scenario_id character varying(255) NOT NULL,
    document_id character varying(255) NOT NULL
);


ALTER TABLE public.scenarios_documents OWNER TO veriguard;

--
-- Name: scenarios_exercises; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.scenarios_exercises (
    scenario_id character varying(255) NOT NULL,
    exercise_id character varying(255) NOT NULL
);


ALTER TABLE public.scenarios_exercises OWNER TO veriguard;

--
-- Name: scenarios_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.scenarios_tags (
    scenario_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.scenarios_tags OWNER TO veriguard;

--
-- Name: scenarios_teams; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.scenarios_teams (
    scenario_id character varying(255) NOT NULL,
    team_id character varying(255) NOT NULL
);


ALTER TABLE public.scenarios_teams OWNER TO veriguard;

--
-- Name: scenarios_teams_users; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.scenarios_teams_users (
    scenario_id character varying(255) NOT NULL,
    team_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


ALTER TABLE public.scenarios_teams_users OWNER TO veriguard;

--
-- Name: security_coverage_send_job; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.security_coverage_send_job (
    security_coverage_send_job_id character varying(255) NOT NULL,
    security_coverage_send_job_status character varying(255) DEFAULT 'PENDING'::character varying NOT NULL,
    security_coverage_send_job_updated_at timestamp with time zone DEFAULT now(),
    security_coverage_send_job_simulation character varying(255) NOT NULL
);


ALTER TABLE public.security_coverage_send_job OWNER TO veriguard;

--
-- Name: security_coverages; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.security_coverages (
    security_coverage_id character varying(255) NOT NULL,
    security_coverage_external_id character varying(255) NOT NULL,
    security_coverage_scenario character varying(255),
    security_coverage_name character varying(255) NOT NULL,
    security_coverage_description text,
    security_coverage_scheduling character varying(50) NOT NULL,
    security_coverage_period_start timestamp with time zone,
    security_coverage_period_end timestamp with time zone,
    security_coverage_labels text[],
    security_coverage_attack_pattern_refs jsonb,
    security_coverage_vulnerabilities_refs jsonb,
    security_coverage_content jsonb NOT NULL,
    security_coverage_created_at timestamp with time zone DEFAULT now(),
    security_coverage_updated_at timestamp with time zone DEFAULT now(),
    security_coverage_external_url text,
    security_coverage_bundle_hash_md5 character varying(32) NOT NULL,
    security_coverage_stix_modified timestamp with time zone,
    security_coverage_platforms_affinity text[],
    security_coverage_type_affinity character varying(255),
    security_coverage_indicators_refs jsonb
);


ALTER TABLE public.security_coverages OWNER TO veriguard;

--
-- Name: tag_rule_asset_groups; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.tag_rule_asset_groups (
    tag_rule_id character varying(255) NOT NULL,
    asset_group_id character varying(255) NOT NULL
);


ALTER TABLE public.tag_rule_asset_groups OWNER TO veriguard;

--
-- Name: tag_rules; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.tag_rules (
    tag_rule_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL,
    tag_rule_protected boolean DEFAULT false NOT NULL
);


ALTER TABLE public.tag_rules OWNER TO veriguard;

--
-- Name: tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.tags (
    tag_id character varying(255) NOT NULL,
    tag_name character varying(255) NOT NULL,
    tag_color character varying(255) DEFAULT '#01478DFF'::character varying,
    tag_created_at timestamp with time zone DEFAULT now() NOT NULL,
    tag_updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.tags OWNER TO veriguard;

--
-- Name: teams; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.teams (
    team_id character varying(255) NOT NULL,
    team_name character varying(255) NOT NULL,
    team_description text,
    team_created_at timestamp with time zone DEFAULT now() NOT NULL,
    team_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    team_organization character varying(255),
    team_contextual boolean DEFAULT false
);


ALTER TABLE public.teams OWNER TO veriguard;

--
-- Name: teams_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.teams_tags (
    team_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.teams_tags OWNER TO veriguard;

--
-- Name: tokens; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.tokens (
    token_id character varying(255) NOT NULL,
    token_user character varying(255) DEFAULT NULL::character varying,
    token_value character varying(255) NOT NULL,
    token_created_at timestamp with time zone NOT NULL
);


ALTER TABLE public.tokens OWNER TO veriguard;

--
-- Name: user_events; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.user_events (
    user_event_id character varying(255) NOT NULL,
    user_id character varying(255),
    user_event_type character varying(50) NOT NULL,
    user_event_payload jsonb,
    user_event_created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.user_events OWNER TO veriguard;

--
-- Name: users; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.users (
    user_id character varying(255) NOT NULL,
    user_organization character varying(255) DEFAULT NULL::character varying,
    user_firstname character varying(255),
    user_lastname character varying(255),
    user_email character varying(255) NOT NULL,
    user_phone character varying(255) DEFAULT NULL::character varying,
    user_phone2 character varying(255) DEFAULT NULL::character varying,
    user_pgp_key text,
    user_password character varying(255) DEFAULT NULL::character varying,
    user_admin boolean NOT NULL,
    user_status smallint NOT NULL,
    user_lang character varying(255) DEFAULT NULL::character varying,
    user_created_at timestamp with time zone DEFAULT now() NOT NULL,
    user_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    user_country character varying(255),
    user_city character varying(255),
    user_theme character varying(255)
);


ALTER TABLE public.users OWNER TO veriguard;

--
-- Name: users_groups; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.users_groups (
    group_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


ALTER TABLE public.users_groups OWNER TO veriguard;

--
-- Name: users_tags; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.users_tags (
    user_id character varying(255) NOT NULL,
    tag_id character varying(255) NOT NULL
);


ALTER TABLE public.users_tags OWNER TO veriguard;

--
-- Name: users_teams; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.users_teams (
    team_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


ALTER TABLE public.users_teams OWNER TO veriguard;

--
-- Name: variables; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.variables (
    variable_id character varying(255) NOT NULL,
    variable_key character varying(255) NOT NULL,
    variable_value character varying(255),
    variable_description text,
    variable_type character varying(255) NOT NULL,
    variable_exercise character varying(255) DEFAULT NULL::character varying,
    variable_created_at timestamp with time zone DEFAULT now() NOT NULL,
    variable_updated_at timestamp with time zone DEFAULT now() NOT NULL,
    variable_scenario character varying(255) DEFAULT NULL::character varying
);


ALTER TABLE public.variables OWNER TO veriguard;

--
-- Name: veriguard_sandboxes; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.veriguard_sandboxes (
    veriguard_sandbox_id character varying(255) NOT NULL,
    veriguard_sandbox_name character varying(255) NOT NULL,
    veriguard_sandbox_description text,
    veriguard_sandbox_network_policy character varying(255) NOT NULL,
    veriguard_sandbox_network_rules jsonb DEFAULT '[]'::jsonb NOT NULL,
    veriguard_sandbox_auto_restore_enabled boolean NOT NULL,
    veriguard_sandbox_supported_sample_types jsonb DEFAULT '[]'::jsonb NOT NULL,
    veriguard_sandbox_status character varying(255) NOT NULL,
    veriguard_sandbox_created_at timestamp with time zone DEFAULT now() NOT NULL,
    veriguard_sandbox_updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.veriguard_sandboxes OWNER TO veriguard;

--
-- Name: vulnerabilities; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.vulnerabilities (
    vulnerability_id character varying(255) NOT NULL,
    vulnerability_external_id character varying(255) NOT NULL,
    vulnerability_source_identifier character varying(255),
    vulnerability_published timestamp with time zone,
    vulnerability_description text,
    vulnerability_vuln_status character varying(255) DEFAULT 'ANALYZED'::character varying,
    vulnerability_cvss_v31 numeric(3,1),
    vulnerability_cisa_exploit_add timestamp with time zone,
    vulnerability_cisa_action_due timestamp with time zone,
    vulnerability_cisa_required_action text,
    vulnerability_cisa_vulnerability_name text,
    vulnerability_remediation text,
    vulnerability_created_at timestamp with time zone DEFAULT now(),
    vulnerability_updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT chk_cvss_range CHECK (((vulnerability_cvss_v31 >= 0.0) AND (vulnerability_cvss_v31 <= 10.0)))
);


ALTER TABLE public.vulnerabilities OWNER TO veriguard;

--
-- Name: vulnerabilities_cwes; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.vulnerabilities_cwes (
    vulnerability_id character varying(255) NOT NULL,
    cwe_id character varying(255) NOT NULL
);


ALTER TABLE public.vulnerabilities_cwes OWNER TO veriguard;

--
-- Name: vulnerability_reference_urls; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.vulnerability_reference_urls (
    vulnerability_id character varying(255) NOT NULL,
    vulnerability_reference_url text NOT NULL
);


ALTER TABLE public.vulnerability_reference_urls OWNER TO veriguard;

--
-- Name: widgets; Type: TABLE; Schema: public; Owner: veriguard
--

CREATE TABLE public.widgets (
    widget_id character varying(255) NOT NULL,
    widget_type character varying(255) NOT NULL,
    widget_config jsonb,
    widget_layout jsonb,
    widget_custom_dashboard character varying(255),
    widget_created_at timestamp with time zone DEFAULT now(),
    widget_updated_at timestamp with time zone DEFAULT now()
);


ALTER TABLE public.widgets OWNER TO veriguard;

--
-- Name: agents agents_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.agents
    ADD CONSTRAINT agents_pkey PRIMARY KEY (agent_id);


--
-- Name: asset_agent_jobs asset_agent_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.asset_agent_jobs
    ADD CONSTRAINT asset_agent_pkey PRIMARY KEY (asset_agent_id);


--
-- Name: asset_groups_assets asset_groups_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.asset_groups_assets
    ADD CONSTRAINT asset_groups_assets_pkey PRIMARY KEY (asset_group_id, asset_id);


--
-- Name: asset_groups asset_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.asset_groups
    ADD CONSTRAINT asset_groups_pkey PRIMARY KEY (asset_group_id);


--
-- Name: asset_groups_tags asset_groups_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.asset_groups_tags
    ADD CONSTRAINT asset_groups_tags_pkey PRIMARY KEY (asset_group_id, tag_id);


--
-- Name: assets assets_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.assets
    ADD CONSTRAINT assets_pkey PRIMARY KEY (asset_id);


--
-- Name: assets_tags assets_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.assets_tags
    ADD CONSTRAINT assets_tags_pkey PRIMARY KEY (asset_id, tag_id);


--
-- Name: catalog_connectors catalog_connectors_catalog_connector_slug_key; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.catalog_connectors
    ADD CONSTRAINT catalog_connectors_catalog_connector_slug_key UNIQUE (catalog_connector_slug);


--
-- Name: catalog_connectors catalog_connectors_catalog_connector_title_key; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.catalog_connectors
    ADD CONSTRAINT catalog_connectors_catalog_connector_title_key UNIQUE (catalog_connector_title);


--
-- Name: catalog_connectors catalog_connectors_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.catalog_connectors
    ADD CONSTRAINT catalog_connectors_pkey PRIMARY KEY (catalog_connector_id);


--
-- Name: challenges_documents challenges_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenges_documents
    ADD CONSTRAINT challenges_documents_pkey PRIMARY KEY (challenge_id, document_id);


--
-- Name: challenges_flags challenges_flags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenges_flags
    ADD CONSTRAINT challenges_flags_pkey PRIMARY KEY (flag_id);


--
-- Name: challenges challenges_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenges
    ADD CONSTRAINT challenges_pkey PRIMARY KEY (challenge_id);


--
-- Name: challenges_tags challenges_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenges_tags
    ADD CONSTRAINT challenges_tags_pkey PRIMARY KEY (challenge_id, tag_id);


--
-- Name: channels channels_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.channels
    ADD CONSTRAINT channels_pkey PRIMARY KEY (channel_id);


--
-- Name: collectors collector_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.collectors
    ADD CONSTRAINT collector_pkey PRIMARY KEY (collector_id);


--
-- Name: comchecks comchecks_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.comchecks
    ADD CONSTRAINT comchecks_pkey PRIMARY KEY (comcheck_id);


--
-- Name: comchecks_statuses comchecks_statuses_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.comchecks_statuses
    ADD CONSTRAINT comchecks_statuses_pkey PRIMARY KEY (status_id);


--
-- Name: communications communications_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.communications
    ADD CONSTRAINT communications_pkey PRIMARY KEY (communication_id);


--
-- Name: communications_users communications_users_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.communications_users
    ADD CONSTRAINT communications_users_pkey PRIMARY KEY (communication_id, user_id);


--
-- Name: connector_instance_configurations connector_instance_configuration_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.connector_instance_configurations
    ADD CONSTRAINT connector_instance_configuration_pkey PRIMARY KEY (connector_instance_configuration_id);


--
-- Name: connector_instance_logs connector_instance_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.connector_instance_logs
    ADD CONSTRAINT connector_instance_logs_pkey PRIMARY KEY (connector_instance_log_id);


--
-- Name: connector_instances connector_instances_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.connector_instances
    ADD CONSTRAINT connector_instances_pkey PRIMARY KEY (connector_instance_id);


--
-- Name: catalog_connectors_configuration connectors_configuration_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.catalog_connectors_configuration
    ADD CONSTRAINT connectors_configuration_pkey PRIMARY KEY (connector_configuration_id);


--
-- Name: contract_output_elements contract_output_elements_contract_output_element_key_contra_key; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.contract_output_elements
    ADD CONSTRAINT contract_output_elements_contract_output_element_key_contra_key UNIQUE (contract_output_element_key, contract_output_element_output_parser_id);


--
-- Name: contract_output_elements contract_output_elements_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.contract_output_elements
    ADD CONSTRAINT contract_output_elements_pkey PRIMARY KEY (contract_output_element_id);


--
-- Name: contract_output_elements_tags contract_output_elements_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.contract_output_elements_tags
    ADD CONSTRAINT contract_output_elements_tags_pkey PRIMARY KEY (contract_output_element_id, tag_id);


--
-- Name: custom_dashboards_parameters custom_dashboards_parameters_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.custom_dashboards_parameters
    ADD CONSTRAINT custom_dashboards_parameters_pkey PRIMARY KEY (custom_dashboards_parameter_id);


--
-- Name: custom_dashboards custom_dashboards_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.custom_dashboards
    ADD CONSTRAINT custom_dashboards_pkey PRIMARY KEY (custom_dashboard_id);


--
-- Name: vulnerability_reference_urls cve_reference_urls_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.vulnerability_reference_urls
    ADD CONSTRAINT cve_reference_urls_pkey PRIMARY KEY (vulnerability_id, vulnerability_reference_url);


--
-- Name: vulnerabilities cves_cve_external_id_key; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.vulnerabilities
    ADD CONSTRAINT cves_cve_external_id_key UNIQUE (vulnerability_external_id);


--
-- Name: vulnerabilities_cwes cves_cwes_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.vulnerabilities_cwes
    ADD CONSTRAINT cves_cwes_pkey PRIMARY KEY (vulnerability_id, cwe_id);


--
-- Name: vulnerabilities cves_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.vulnerabilities
    ADD CONSTRAINT cves_pkey PRIMARY KEY (vulnerability_id);


--
-- Name: cwes cwes_cwe_external_id_key; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.cwes
    ADD CONSTRAINT cwes_cwe_external_id_key UNIQUE (cwe_external_id);


--
-- Name: cwes cwes_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.cwes
    ADD CONSTRAINT cwes_pkey PRIMARY KEY (cwe_id);


--
-- Name: datapacks datapacks_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.datapacks
    ADD CONSTRAINT datapacks_pkey PRIMARY KEY (datapack_id);


--
-- Name: detection_remediations detection_remediation_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.detection_remediations
    ADD CONSTRAINT detection_remediation_pkey PRIMARY KEY (detection_remediation_id);


--
-- Name: documents documents_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_pkey PRIMARY KEY (document_id);


--
-- Name: documents_tags documents_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.documents_tags
    ADD CONSTRAINT documents_tags_pkey PRIMARY KEY (document_id, tag_id);


--
-- Name: domains domains_domain_name_key; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.domains
    ADD CONSTRAINT domains_domain_name_key UNIQUE (domain_name);


--
-- Name: domains domains_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.domains
    ADD CONSTRAINT domains_pkey PRIMARY KEY (domain_id);


--
-- Name: evaluations evaluations_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.evaluations
    ADD CONSTRAINT evaluations_pkey PRIMARY KEY (evaluation_id);


--
-- Name: execution_traces execution_traces_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.execution_traces
    ADD CONSTRAINT execution_traces_pkey PRIMARY KEY (execution_trace_id);


--
-- Name: executors executor_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.executors
    ADD CONSTRAINT executor_pkey PRIMARY KEY (executor_id);


--
-- Name: exercises_documents exercises_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_documents
    ADD CONSTRAINT exercises_documents_pkey PRIMARY KEY (exercise_id, document_id);


--
-- Name: exercises exercises_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises
    ADD CONSTRAINT exercises_pkey PRIMARY KEY (exercise_id);


--
-- Name: exercises_tags exercises_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_tags
    ADD CONSTRAINT exercises_tags_pkey PRIMARY KEY (exercise_id, tag_id);


--
-- Name: exercises_teams exercises_teams_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_teams
    ADD CONSTRAINT exercises_teams_pkey PRIMARY KEY (exercise_id, team_id);


--
-- Name: exercises_teams_users exercises_teams_users_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_teams_users
    ADD CONSTRAINT exercises_teams_users_pkey PRIMARY KEY (exercise_id, team_id, user_id);


--
-- Name: injects_expectations expectations_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations
    ADD CONSTRAINT expectations_pkey PRIMARY KEY (inject_expectation_id);


--
-- Name: findings_assets findings_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_assets
    ADD CONSTRAINT findings_assets_pkey PRIMARY KEY (finding_id, asset_id);


--
-- Name: findings findings_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings
    ADD CONSTRAINT findings_pkey PRIMARY KEY (finding_id);


--
-- Name: findings_tags findings_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_tags
    ADD CONSTRAINT findings_tags_pkey PRIMARY KEY (finding_id, tag_id);


--
-- Name: findings_teams findings_teams_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_teams
    ADD CONSTRAINT findings_teams_pkey PRIMARY KEY (finding_id, team_id);


--
-- Name: findings_users findings_users_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_users
    ADD CONSTRAINT findings_users_pkey PRIMARY KEY (finding_id, user_id);


--
-- Name: grants grants_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.grants
    ADD CONSTRAINT grants_pkey PRIMARY KEY (grant_id);


--
-- Name: groups groups_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT groups_pkey PRIMARY KEY (group_id);


--
-- Name: groups_roles groups_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.groups_roles
    ADD CONSTRAINT groups_roles_pkey PRIMARY KEY (role_id, group_id);


--
-- Name: import_mappers import_mappers_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.import_mappers
    ADD CONSTRAINT import_mappers_pkey PRIMARY KEY (mapper_id);


--
-- Name: indexing_status indexing_status_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.indexing_status
    ADD CONSTRAINT indexing_status_pkey PRIMARY KEY (indexing_status_type);


--
-- Name: inject_importers inject_importers_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.inject_importers
    ADD CONSTRAINT inject_importers_pkey PRIMARY KEY (importer_id);


--
-- Name: injects_tests_statuses inject_test_status_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_tests_statuses
    ADD CONSTRAINT inject_test_status_pkey PRIMARY KEY (status_id);


--
-- Name: injectors_contracts injector_contract_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts
    ADD CONSTRAINT injector_contract_pkey PRIMARY KEY (injector_contract_id);


--
-- Name: injectors injector_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors
    ADD CONSTRAINT injector_pkey PRIMARY KEY (injector_id);


--
-- Name: injectors_contracts_attack_patterns injectors_contracts_attack_patterns_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts_attack_patterns
    ADD CONSTRAINT injectors_contracts_attack_patterns_pkey PRIMARY KEY (attack_pattern_id, injector_contract_id);


--
-- Name: injectors_contracts_domains injectors_contracts_domains_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts_domains
    ADD CONSTRAINT injectors_contracts_domains_pkey PRIMARY KEY (injector_contract_id, domain_id);


--
-- Name: injectors_contracts injectors_contracts_injector_contract_external_id_key; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts
    ADD CONSTRAINT injectors_contracts_injector_contract_external_id_key UNIQUE (injector_contract_external_id);


--
-- Name: injectors_contracts_vulnerabilities injectors_contracts_vulnerabilities_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts_vulnerabilities
    ADD CONSTRAINT injectors_contracts_vulnerabilities_pkey PRIMARY KEY (injector_contract_id, vulnerability_id);


--
-- Name: injects_asset_groups injects_asset_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_asset_groups
    ADD CONSTRAINT injects_asset_groups_pkey PRIMARY KEY (inject_id, asset_group_id);


--
-- Name: injects_assets injects_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_assets
    ADD CONSTRAINT injects_assets_pkey PRIMARY KEY (inject_id, asset_id);


--
-- Name: injects_dependencies injects_dependencies_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_dependencies
    ADD CONSTRAINT injects_dependencies_pkey PRIMARY KEY (inject_parent_id, inject_children_id);


--
-- Name: injects_documents injects_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_documents
    ADD CONSTRAINT injects_documents_pkey PRIMARY KEY (inject_id, document_id);


--
-- Name: injects_expectations_traces injects_expectations_traces_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations_traces
    ADD CONSTRAINT injects_expectations_traces_pkey PRIMARY KEY (inject_expectation_trace_id);


--
-- Name: injects injects_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects
    ADD CONSTRAINT injects_pkey PRIMARY KEY (inject_id);


--
-- Name: injects_statuses injects_statuses_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_statuses
    ADD CONSTRAINT injects_statuses_pkey PRIMARY KEY (status_id);


--
-- Name: injects_tags injects_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_tags
    ADD CONSTRAINT injects_tags_pkey PRIMARY KEY (inject_id, tag_id);


--
-- Name: injects_teams injects_teams_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_teams
    ADD CONSTRAINT injects_teams_pkey PRIMARY KEY (inject_id, team_id);


--
-- Name: kill_chain_phases kill_chain_phases_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.kill_chain_phases
    ADD CONSTRAINT kill_chain_phases_pkey PRIMARY KEY (phase_id);


--
-- Name: lessons_answers lessons_answers_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_answers
    ADD CONSTRAINT lessons_answers_pkey PRIMARY KEY (lessons_answer_id);


--
-- Name: lessons_categories lessons_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_categories
    ADD CONSTRAINT lessons_categories_pkey PRIMARY KEY (lessons_category_id);


--
-- Name: lessons_categories_teams lessons_categories_teams_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_categories_teams
    ADD CONSTRAINT lessons_categories_teams_pkey PRIMARY KEY (team_id, lessons_category_id);


--
-- Name: lessons_questions lessons_questions_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_questions
    ADD CONSTRAINT lessons_questions_pkey PRIMARY KEY (lessons_question_id);


--
-- Name: lessons_template_categories lessons_template_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_template_categories
    ADD CONSTRAINT lessons_template_categories_pkey PRIMARY KEY (lessons_template_category_id);


--
-- Name: lessons_template_questions lessons_template_questions_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_template_questions
    ADD CONSTRAINT lessons_template_questions_pkey PRIMARY KEY (lessons_template_question_id);


--
-- Name: lessons_templates lessons_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_templates
    ADD CONSTRAINT lessons_templates_pkey PRIMARY KEY (lessons_template_id);


--
-- Name: logs logs_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.logs
    ADD CONSTRAINT logs_pkey PRIMARY KEY (log_id);


--
-- Name: logs_tags logs_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.logs_tags
    ADD CONSTRAINT logs_tags_pkey PRIMARY KEY (log_id, tag_id);


--
-- Name: mitigations_attack_patterns mitigations_attack_patterns_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.mitigations_attack_patterns
    ADD CONSTRAINT mitigations_attack_patterns_pkey PRIMARY KEY (mitigation_id, attack_pattern_id);


--
-- Name: mitigations mitigations_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.mitigations
    ADD CONSTRAINT mitigations_pkey PRIMARY KEY (mitigation_id);


--
-- Name: notification_rules notification_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.notification_rules
    ADD CONSTRAINT notification_rules_pkey PRIMARY KEY (notification_rule_id);


--
-- Name: objectives objectives_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.objectives
    ADD CONSTRAINT objectives_pkey PRIMARY KEY (objective_id);


--
-- Name: organizations organizations_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_pkey PRIMARY KEY (organization_id);


--
-- Name: organizations_tags organizations_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.organizations_tags
    ADD CONSTRAINT organizations_tags_pkey PRIMARY KEY (organization_id, tag_id);


--
-- Name: output_parsers output_parsers_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.output_parsers
    ADD CONSTRAINT output_parsers_pkey PRIMARY KEY (output_parser_id);


--
-- Name: parameters parameters_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.parameters
    ADD CONSTRAINT parameters_pkey PRIMARY KEY (parameter_id);


--
-- Name: pauses pauses_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.pauses
    ADD CONSTRAINT pauses_pkey PRIMARY KEY (pause_id);


--
-- Name: payloads_attack_patterns payloads_attack_patterns_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads_attack_patterns
    ADD CONSTRAINT payloads_attack_patterns_pkey PRIMARY KEY (attack_pattern_id, payload_id);


--
-- Name: payloads_domains payloads_domains_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads_domains
    ADD CONSTRAINT payloads_domains_pkey PRIMARY KEY (payload_id, domain_id);


--
-- Name: payloads payloads_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads
    ADD CONSTRAINT payloads_pkey PRIMARY KEY (payload_id);


--
-- Name: payloads_tags payloads_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads_tags
    ADD CONSTRAINT payloads_tags_pkey PRIMARY KEY (payload_id, tag_id);


--
-- Name: challenge_attempts pk_challenge_attempts; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenge_attempts
    ADD CONSTRAINT pk_challenge_attempts PRIMARY KEY (challenge_id, inject_status_id, user_id);


--
-- Name: articles pkey_articles; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT pkey_articles PRIMARY KEY (article_id);


--
-- Name: articles_documents pkey_articles_documents; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.articles_documents
    ADD CONSTRAINT pkey_articles_documents PRIMARY KEY (article_id, document_id);


--
-- Name: attack_patterns pkey_attack_patterns; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.attack_patterns
    ADD CONSTRAINT pkey_attack_patterns PRIMARY KEY (attack_pattern_id);


--
-- Name: attack_patterns_kill_chain_phases pkey_attack_patterns_kill_chain_phases; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.attack_patterns_kill_chain_phases
    ADD CONSTRAINT pkey_attack_patterns_kill_chain_phases PRIMARY KEY (attack_pattern_id, phase_id);


--
-- Name: regex_groups regex_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.regex_groups
    ADD CONSTRAINT regex_groups_pkey PRIMARY KEY (regex_group_id);


--
-- Name: report_informations report_informations_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.report_informations
    ADD CONSTRAINT report_informations_pkey PRIMARY KEY (report_informations_id);


--
-- Name: report_informations report_informations_report_id_report_informations_type_key; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.report_informations
    ADD CONSTRAINT report_informations_report_id_report_informations_type_key UNIQUE (report_id, report_informations_type);


--
-- Name: report_inject_comment report_inject_comment_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.report_inject_comment
    ADD CONSTRAINT report_inject_comment_pkey PRIMARY KEY (report_id, inject_id);


--
-- Name: reports_exercises reports_exercises_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.reports_exercises
    ADD CONSTRAINT reports_exercises_pkey PRIMARY KEY (report_id, exercise_id);


--
-- Name: reports reports_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_pkey PRIMARY KEY (report_id);


--
-- Name: roles_capabilities roles_capabilities_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.roles_capabilities
    ADD CONSTRAINT roles_capabilities_pkey PRIMARY KEY (role_id, capability);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);


--
-- Name: rule_attributes rule_attributes_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.rule_attributes
    ADD CONSTRAINT rule_attributes_pkey PRIMARY KEY (attribute_id);


--
-- Name: scenarios_exercises scenario_exercise_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_exercises
    ADD CONSTRAINT scenario_exercise_pkey PRIMARY KEY (scenario_id, exercise_id);


--
-- Name: scenarios_documents scenarios_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_documents
    ADD CONSTRAINT scenarios_documents_pkey PRIMARY KEY (scenario_id, document_id);


--
-- Name: scenarios scenarios_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios
    ADD CONSTRAINT scenarios_pkey PRIMARY KEY (scenario_id);


--
-- Name: scenarios_tags scenarios_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_tags
    ADD CONSTRAINT scenarios_tags_pkey PRIMARY KEY (scenario_id, tag_id);


--
-- Name: scenarios_teams scenarios_teams_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_teams
    ADD CONSTRAINT scenarios_teams_pkey PRIMARY KEY (scenario_id, team_id);


--
-- Name: scenarios_teams_users scenarios_teams_users_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_teams_users
    ADD CONSTRAINT scenarios_teams_users_pkey PRIMARY KEY (scenario_id, team_id, user_id);


--
-- Name: security_coverages security_coverage_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.security_coverages
    ADD CONSTRAINT security_coverage_pkey PRIMARY KEY (security_coverage_id);


--
-- Name: security_coverage_send_job security_coverage_send_job_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.security_coverage_send_job
    ADD CONSTRAINT security_coverage_send_job_pkey PRIMARY KEY (security_coverage_send_job_id);


--
-- Name: security_coverage_send_job security_coverage_send_job_security_coverage_send_job_simu_key1; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.security_coverage_send_job
    ADD CONSTRAINT security_coverage_send_job_security_coverage_send_job_simu_key1 UNIQUE (security_coverage_send_job_simulation);


--
-- Name: tag_rule_asset_groups tag_rule_asset_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.tag_rule_asset_groups
    ADD CONSTRAINT tag_rule_asset_groups_pkey PRIMARY KEY (tag_rule_id, asset_group_id);


--
-- Name: tag_rules tag_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.tag_rules
    ADD CONSTRAINT tag_rules_pkey PRIMARY KEY (tag_rule_id);


--
-- Name: tags tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.tags
    ADD CONSTRAINT tags_pkey PRIMARY KEY (tag_id);


--
-- Name: teams teams_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_pkey PRIMARY KEY (team_id);


--
-- Name: teams_tags teams_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.teams_tags
    ADD CONSTRAINT teams_tags_pkey PRIMARY KEY (team_id, tag_id);


--
-- Name: tokens tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.tokens
    ADD CONSTRAINT tokens_pkey PRIMARY KEY (token_id);


--
-- Name: veriguard_sandboxes uk_veriguard_sandboxes_name; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.veriguard_sandboxes
    ADD CONSTRAINT uk_veriguard_sandboxes_name UNIQUE (veriguard_sandbox_name);


--
-- Name: findings unique_finding_constraint; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings
    ADD CONSTRAINT unique_finding_constraint UNIQUE (finding_inject_id, finding_type, finding_value, finding_field);


--
-- Name: injects_expectations_traces unique_injects_expectations_traces_constraint; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations_traces
    ADD CONSTRAINT unique_injects_expectations_traces_constraint UNIQUE (inject_expectation_trace_expectation, inject_expectation_trace_source_id, inject_expectation_trace_alert_link, inject_expectation_trace_alert_name);


--
-- Name: notification_rules uq_notification_rule; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.notification_rules
    ADD CONSTRAINT uq_notification_rule UNIQUE (notification_resource_id, notification_rule_trigger, user_id, notification_rule_type);


--
-- Name: user_events user_events_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.user_events
    ADD CONSTRAINT user_events_pkey PRIMARY KEY (user_event_id);


--
-- Name: users_groups users_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.users_groups
    ADD CONSTRAINT users_groups_pkey PRIMARY KEY (group_id, user_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- Name: users_tags users_tags_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.users_tags
    ADD CONSTRAINT users_tags_pkey PRIMARY KEY (user_id, tag_id);


--
-- Name: users_teams users_teams_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.users_teams
    ADD CONSTRAINT users_teams_pkey PRIMARY KEY (team_id, user_id);


--
-- Name: variables variables_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.variables
    ADD CONSTRAINT variables_pkey PRIMARY KEY (variable_id);


--
-- Name: veriguard_sandboxes veriguard_sandboxes_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.veriguard_sandboxes
    ADD CONSTRAINT veriguard_sandboxes_pkey PRIMARY KEY (veriguard_sandbox_id);


--
-- Name: widgets widgets_pkey; Type: CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.widgets
    ADD CONSTRAINT widgets_pkey PRIMARY KEY (widget_id);


--
-- Name: assets_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX assets_unique ON public.assets USING btree (asset_external_reference);


--
-- Name: collectors_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX collectors_unique ON public.collectors USING btree (collector_type);


--
-- Name: executors_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX executors_unique ON public.executors USING btree (executor_type);


--
-- Name: grant_resource_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX grant_resource_unique ON public.grants USING btree (grant_group, grant_resource, grant_name);


--
-- Name: idx_64adc7d620b0bd5e; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_64adc7d620b0bd5e ON public.grants USING btree (grant_group);


--
-- Name: idx_6cb0696c157d9150; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_6cb0696c157d9150 ON public.objectives USING btree (objective_exercise);


--
-- Name: idx_96e1b96c7983aee; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_96e1b96c7983aee ON public.injects_teams USING btree (inject_id);


--
-- Name: idx_96e1b96ccb0ca5a3; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_96e1b96ccb0ca5a3 ON public.injects_teams USING btree (team_id);


--
-- Name: idx_a25f787295a4a46f; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_a25f787295a4a46f ON public.comchecks_statuses USING btree (status_comcheck);


--
-- Name: idx_a25f7872b5957bdd; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_a25f7872b5957bdd ON public.comchecks_statuses USING btree (status_user);


--
-- Name: idx_a60839b2e20fc097; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_a60839b2e20fc097 ON public.injects USING btree (inject_user);


--
-- Name: idx_aa5a118eef97e32b; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_aa5a118eef97e32b ON public.tokens USING btree (token_user);


--
-- Name: idx_agent_assets; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_agent_assets ON public.agents USING btree (agent_asset);


--
-- Name: idx_articles_channel_exercise; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_articles_channel_exercise ON public.articles USING btree (article_channel, article_exercise);


--
-- Name: idx_articles_documents_article; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_articles_documents_article ON public.articles_documents USING btree (article_id);


--
-- Name: idx_articles_documents_document; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_articles_documents_document ON public.articles_documents USING btree (document_id);


--
-- Name: idx_articles_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_articles_id ON public.articles USING btree (article_id);


--
-- Name: idx_asset_agent_jobs; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_asset_agent_jobs ON public.asset_agent_jobs USING btree (asset_agent_id);


--
-- Name: idx_asset_groups; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_asset_groups ON public.asset_groups USING btree (asset_group_id);


--
-- Name: idx_asset_groups_assets_asset; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_asset_groups_assets_asset ON public.asset_groups_assets USING btree (asset_id);


--
-- Name: idx_asset_groups_assets_asset_group; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_asset_groups_assets_asset_group ON public.asset_groups_assets USING btree (asset_group_id);


--
-- Name: idx_assets; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_assets ON public.assets USING btree (asset_id);


--
-- Name: idx_assets_tags_asset; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_assets_tags_asset ON public.assets_tags USING btree (asset_id);


--
-- Name: idx_assets_tags_tag; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_assets_tags_tag ON public.assets_tags USING btree (tag_id);


--
-- Name: idx_attack_patterns; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_attack_patterns ON public.attack_patterns USING btree (attack_pattern_id);


--
-- Name: idx_attack_patterns_external_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX idx_attack_patterns_external_id ON public.attack_patterns USING btree (attack_pattern_external_id);


--
-- Name: idx_attack_patterns_kill_chain_phases_attack_pattern; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_attack_patterns_kill_chain_phases_attack_pattern ON public.attack_patterns_kill_chain_phases USING btree (attack_pattern_id);


--
-- Name: idx_attack_patterns_kill_chain_phases_kill_chain_phase; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_attack_patterns_kill_chain_phases_kill_chain_phase ON public.attack_patterns_kill_chain_phases USING btree (phase_id);


--
-- Name: idx_attack_patterns_stix_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX idx_attack_patterns_stix_id ON public.attack_patterns USING btree (attack_pattern_stix_id);


--
-- Name: idx_challenge_attempt_challenge_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_challenge_attempt_challenge_id ON public.challenge_attempts USING btree (challenge_id);


--
-- Name: idx_challenge_attempt_inject_status_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_challenge_attempt_inject_status_id ON public.challenge_attempts USING btree (inject_status_id);


--
-- Name: idx_challenge_attempt_user_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_challenge_attempt_user_id ON public.challenge_attempts USING btree (user_id);


--
-- Name: idx_challenge_documents_challenge; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_challenge_documents_challenge ON public.challenges_documents USING btree (challenge_id);


--
-- Name: idx_challenge_documents_document; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_challenge_documents_document ON public.challenges_documents USING btree (document_id);


--
-- Name: idx_challenges; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_challenges ON public.challenges USING btree (challenge_id);


--
-- Name: idx_challenges_flags; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_challenges_flags ON public.challenges_flags USING btree (flag_id);


--
-- Name: idx_channels; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_channels ON public.channels USING btree (channel_id);


--
-- Name: idx_cil_instance_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_cil_instance_id ON public.connector_instance_logs USING btree (connector_instance_id);


--
-- Name: idx_collectors; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_collectors ON public.collectors USING btree (collector_id);


--
-- Name: idx_comchecks; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_comchecks ON public.comchecks USING btree (comcheck_exercise);


--
-- Name: idx_communication_subject; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_communication_subject ON public.communications USING btree (communication_subject);


--
-- Name: idx_communications; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_communications ON public.communications USING btree (communication_id);


--
-- Name: idx_communications_users_communication; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_communications_users_communication ON public.communications_users USING btree (communication_id);


--
-- Name: idx_communications_users_user; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_communications_users_user ON public.communications_users USING btree (user_id);


--
-- Name: idx_conf_instance_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_conf_instance_id ON public.connector_instance_configurations USING btree (connector_instance_id);


--
-- Name: idx_conf_key; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_conf_key ON public.connector_instance_configurations USING btree (connector_instance_configuration_key);


--
-- Name: idx_conf_value_gin; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_conf_value_gin ON public.connector_instance_configurations USING gin (connector_instance_configuration_value);


--
-- Name: idx_contract_output_elements_tags_contract_output_elements; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_contract_output_elements_tags_contract_output_elements ON public.contract_output_elements_tags USING btree (contract_output_element_id);


--
-- Name: idx_contract_output_elements_tags_tag; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_contract_output_elements_tags_tag ON public.contract_output_elements_tags USING btree (tag_id);


--
-- Name: idx_custom_dashboards_parameters; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_custom_dashboards_parameters ON public.custom_dashboards_parameters USING btree (custom_dashboards_parameter_id);


--
-- Name: idx_detection_remediation_collector_type; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_detection_remediation_collector_type ON public.detection_remediations USING btree (detection_remediation_collector_type);


--
-- Name: idx_detection_remediation_payload; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_detection_remediation_payload ON public.detection_remediations USING btree (detection_remediation_payload_id);


--
-- Name: idx_domains_domain_name; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_domains_domain_name ON public.domains USING btree (domain_name);


--
-- Name: idx_evaluations; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_evaluations ON public.evaluations USING btree (evaluation_id);


--
-- Name: idx_executors; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_executors ON public.executors USING btree (executor_id);


--
-- Name: idx_exercises_documents_document; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_exercises_documents_document ON public.exercises_documents USING btree (document_id);


--
-- Name: idx_exercises_documents_inject; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_exercises_documents_inject ON public.exercises_documents USING btree (exercise_id);


--
-- Name: idx_exercises_teams_exercise; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_exercises_teams_exercise ON public.exercises_teams USING btree (exercise_id);


--
-- Name: idx_exercises_teams_team; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_exercises_teams_team ON public.exercises_teams USING btree (team_id);


--
-- Name: idx_exercises_teams_users_exercise; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_exercises_teams_users_exercise ON public.exercises_teams_users USING btree (exercise_id);


--
-- Name: idx_exercises_teams_users_team; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_exercises_teams_users_team ON public.exercises_teams_users USING btree (team_id);


--
-- Name: idx_exercises_teams_users_user; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_exercises_teams_users_user ON public.exercises_teams_users USING btree (user_id);


--
-- Name: idx_f08fc65c9cfd383c; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_f08fc65c9cfd383c ON public.logs USING btree (log_user);


--
-- Name: idx_f08fc65cc0891ec3; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_f08fc65cc0891ec3 ON public.logs USING btree (log_exercise);


--
-- Name: idx_ff8ab7e0a76ed395; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_ff8ab7e0a76ed395 ON public.users_groups USING btree (user_id);


--
-- Name: idx_ff8ab7e0fe54d947; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_ff8ab7e0fe54d947 ON public.users_groups USING btree (group_id);


--
-- Name: idx_finding_inject; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_finding_inject ON public.findings USING btree (finding_inject_id);


--
-- Name: idx_findings_tags_finding; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_findings_tags_finding ON public.findings_tags USING btree (finding_id);


--
-- Name: idx_findings_tags_tag; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_findings_tags_tag ON public.findings_tags USING btree (tag_id);


--
-- Name: idx_fingings_assets_asset; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_fingings_assets_asset ON public.findings_assets USING btree (asset_id);


--
-- Name: idx_fingings_assets_finding; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_fingings_assets_finding ON public.findings_assets USING btree (finding_id);


--
-- Name: idx_fingings_teams_finding; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_fingings_teams_finding ON public.findings_teams USING btree (finding_id);


--
-- Name: idx_fingings_teams_team; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_fingings_teams_team ON public.findings_teams USING btree (team_id);


--
-- Name: idx_fingings_users_finding; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_fingings_users_finding ON public.findings_users USING btree (finding_id);


--
-- Name: idx_fingings_users_user; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_fingings_users_user ON public.findings_users USING btree (user_id);


--
-- Name: idx_flag_challenge; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_flag_challenge ON public.challenges_flags USING btree (flag_challenge);


--
-- Name: idx_grant_resource; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_grant_resource ON public.grants USING btree (grant_resource);


--
-- Name: idx_grant_resource_type; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_grant_resource_type ON public.grants USING btree (grant_resource_type);


--
-- Name: idx_grant_resource_type_resource; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_grant_resource_type_resource ON public.grants USING btree (grant_resource_type, grant_resource);


--
-- Name: idx_icd_domain_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_icd_domain_id ON public.injectors_contracts_domains USING btree (domain_id);


--
-- Name: idx_icd_injector_contract_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_icd_injector_contract_id ON public.injectors_contracts_domains USING btree (injector_contract_id);


--
-- Name: idx_import_mappers; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_import_mappers ON public.import_mappers USING btree (mapper_id);


--
-- Name: idx_indexing_status_type; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_indexing_status_type ON public.indexing_status USING btree (indexing_status_type);


--
-- Name: idx_inject_expectation_agent_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_expectation_agent_id ON public.injects_expectations USING btree (agent_id);


--
-- Name: idx_inject_expectation_asset_group_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_expectation_asset_group_id ON public.injects_expectations USING btree (asset_group_id);


--
-- Name: idx_inject_expectation_asset_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_expectation_asset_id ON public.injects_expectations USING btree (asset_id);


--
-- Name: idx_inject_expectation_exercise_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_expectation_exercise_id ON public.injects_expectations USING btree (exercise_id);


--
-- Name: idx_inject_expectation_inject_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_expectation_inject_id ON public.injects_expectations USING btree (inject_id);


--
-- Name: idx_inject_expectation_team_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_expectation_team_id ON public.injects_expectations USING btree (team_id);


--
-- Name: idx_inject_expectation_trace_expectation; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_expectation_trace_expectation ON public.injects_expectations_traces USING btree (inject_expectation_trace_expectation);


--
-- Name: idx_inject_expectation_trace_source_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_expectation_trace_source_id ON public.injects_expectations_traces USING btree (inject_expectation_trace_source_id);


--
-- Name: idx_inject_expectation_user_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_expectation_user_id ON public.injects_expectations USING btree (user_id);


--
-- Name: idx_inject_importers; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_importers ON public.inject_importers USING btree (importer_id);


--
-- Name: idx_inject_importers_injector_contracts; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_importers_injector_contracts ON public.inject_importers USING btree (importer_injector_contract_id);


--
-- Name: idx_inject_inject_injector_contract; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_inject_injector_contract ON public.injects USING btree (inject_injector_contract);


--
-- Name: idx_inject_test_inject; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_inject_test_inject ON public.injects_tests_statuses USING btree (status_inject);


--
-- Name: idx_injector_contract_injector; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injector_contract_injector ON public.injectors_contracts USING btree (injector_id);


--
-- Name: idx_injectors; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injectors ON public.injectors USING btree (injector_id);


--
-- Name: idx_injectors_contracts; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injectors_contracts ON public.injectors_contracts USING btree (injector_contract_id);


--
-- Name: idx_injectors_contracts_attack_patterns_contract; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injectors_contracts_attack_patterns_contract ON public.injectors_contracts_attack_patterns USING btree (injector_contract_id);


--
-- Name: idx_injectors_contracts_attack_patterns_pattern; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injectors_contracts_attack_patterns_pattern ON public.injectors_contracts_attack_patterns USING btree (attack_pattern_id);


--
-- Name: idx_injectors_contracts_vulnerabilities_contract; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injectors_contracts_vulnerabilities_contract ON public.injectors_contracts_vulnerabilities USING btree (injector_contract_id);


--
-- Name: idx_injectors_contracts_vulnerabilities_vuln; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injectors_contracts_vulnerabilities_vuln ON public.injectors_contracts_vulnerabilities USING btree (vulnerability_id);


--
-- Name: idx_injects_asset_groups_asset_groups; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injects_asset_groups_asset_groups ON public.injects_asset_groups USING btree (asset_group_id);


--
-- Name: idx_injects_asset_groups_inject; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injects_asset_groups_inject ON public.injects_asset_groups USING btree (inject_id);


--
-- Name: idx_injects_assets_asset; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injects_assets_asset ON public.injects_assets USING btree (asset_id);


--
-- Name: idx_injects_assets_inject; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injects_assets_inject ON public.injects_assets USING btree (inject_id);


--
-- Name: idx_injects_dependencies; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injects_dependencies ON public.injects_dependencies USING btree (inject_children_id);


--
-- Name: idx_injects_documents_document; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injects_documents_document ON public.injects_documents USING btree (document_id);


--
-- Name: idx_injects_documents_inject; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injects_documents_inject ON public.injects_documents USING btree (inject_id);


--
-- Name: idx_injects_expectations_inject_agent; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_injects_expectations_inject_agent ON public.injects_expectations USING btree (inject_id, agent_id);


--
-- Name: idx_kill_chain_phases; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_kill_chain_phases ON public.kill_chain_phases USING btree (phase_id);


--
-- Name: idx_kill_chain_phases_stix_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX idx_kill_chain_phases_stix_id ON public.kill_chain_phases USING btree (phase_stix_id);


--
-- Name: idx_lessons_answer_question; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_answer_question ON public.lessons_answers USING btree (lessons_answer_question);


--
-- Name: idx_lessons_answer_user; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_answer_user ON public.lessons_answers USING btree (lessons_answer_user);


--
-- Name: idx_lessons_answers; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_answers ON public.lessons_answers USING btree (lessons_answer_id);


--
-- Name: idx_lessons_categories; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_categories ON public.lessons_categories USING btree (lessons_category_id);


--
-- Name: idx_lessons_categories_audiences_audience; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_categories_audiences_audience ON public.lessons_categories_teams USING btree (team_id);


--
-- Name: idx_lessons_categories_audiences_category; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_categories_audiences_category ON public.lessons_categories_teams USING btree (lessons_category_id);


--
-- Name: idx_lessons_category_exercise; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_category_exercise ON public.lessons_categories USING btree (lessons_category_exercise);


--
-- Name: idx_lessons_question_category; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_question_category ON public.lessons_questions USING btree (lessons_question_category);


--
-- Name: idx_lessons_questions; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_questions ON public.lessons_questions USING btree (lessons_question_id);


--
-- Name: idx_lessons_template_categories; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_template_categories ON public.lessons_template_categories USING btree (lessons_template_category_id);


--
-- Name: idx_lessons_template_category_template; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_template_category_template ON public.lessons_template_categories USING btree (lessons_template_category_template);


--
-- Name: idx_lessons_template_question_category; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_template_question_category ON public.lessons_template_questions USING btree (lessons_template_question_category);


--
-- Name: idx_lessons_template_questions; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_template_questions ON public.lessons_template_questions USING btree (lessons_template_question_id);


--
-- Name: idx_lessons_templates; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_lessons_templates ON public.lessons_templates USING btree (lessons_template_id);


--
-- Name: idx_mitigations; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_mitigations ON public.mitigations USING btree (mitigation_id);


--
-- Name: idx_mitigations_attack_patterns_attack_pattern; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_mitigations_attack_patterns_attack_pattern ON public.mitigations_attack_patterns USING btree (attack_pattern_id);


--
-- Name: idx_mitigations_attack_patterns_mitigation; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_mitigations_attack_patterns_mitigation ON public.mitigations_attack_patterns USING btree (mitigation_id);


--
-- Name: idx_null_exercise_and_scenario; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_null_exercise_and_scenario ON public.injects USING btree (inject_id) WHERE ((inject_scenario IS NULL) AND (inject_exercise IS NULL));


--
-- Name: idx_pauses; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_pauses ON public.pauses USING btree (pause_id);


--
-- Name: idx_payloads; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_payloads ON public.payloads USING btree (payload_id);


--
-- Name: idx_payloads_attack_patterns_contract; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_payloads_attack_patterns_contract ON public.payloads_attack_patterns USING btree (payload_id);


--
-- Name: idx_payloads_attack_patterns_pattern; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_payloads_attack_patterns_pattern ON public.payloads_attack_patterns USING btree (attack_pattern_id);


--
-- Name: idx_payloads_domains_domain_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_payloads_domains_domain_id ON public.payloads_domains USING btree (domain_id);


--
-- Name: idx_payloads_domains_payload_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_payloads_domains_payload_id ON public.payloads_domains USING btree (payload_id);


--
-- Name: idx_payloads_tags_payload; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_payloads_tags_payload ON public.payloads_tags USING btree (payload_id);


--
-- Name: idx_payloads_tags_tag; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_payloads_tags_tag ON public.payloads_tags USING btree (tag_id);


--
-- Name: idx_pg_trgm_asset_groups_asset_group_name; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_pg_trgm_asset_groups_asset_group_name ON public.asset_groups USING gin (to_tsvector('simple'::regconfig, (asset_group_name)::text));


--
-- Name: idx_pg_trgm_assets_asset_name; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_pg_trgm_assets_asset_name ON public.assets USING gin (to_tsvector('simple'::regconfig, (asset_name)::text));


--
-- Name: idx_pg_trgm_exercises_exercise_name; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_pg_trgm_exercises_exercise_name ON public.exercises USING gin (to_tsvector('simple'::regconfig, (exercise_name)::text));


--
-- Name: idx_pg_trgm_organizations_organization_name; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_pg_trgm_organizations_organization_name ON public.organizations USING gin (to_tsvector('simple'::regconfig, (organization_name)::text));


--
-- Name: idx_pg_trgm_scenarios_scenario_name; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_pg_trgm_scenarios_scenario_name ON public.scenarios USING gin (to_tsvector('simple'::regconfig, (scenario_name)::text));


--
-- Name: idx_pg_trgm_teams_team_name; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_pg_trgm_teams_team_name ON public.teams USING gin (to_tsvector('simple'::regconfig, (team_name)::text));


--
-- Name: idx_pg_trgm_users_user_email; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_pg_trgm_users_user_email ON public.users USING gin (to_tsvector('simple'::regconfig, (user_email)::text));


--
-- Name: idx_report_informations; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_report_informations ON public.report_informations USING btree (report_id);


--
-- Name: idx_report_inject_comment_inject; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_report_inject_comment_inject ON public.report_inject_comment USING btree (report_id);


--
-- Name: idx_report_inject_comment_report; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_report_inject_comment_report ON public.report_inject_comment USING btree (inject_id);


--
-- Name: idx_reports; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_reports ON public.reports USING btree (report_id);


--
-- Name: idx_reports_exercises_exercise; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_reports_exercises_exercise ON public.reports_exercises USING btree (exercise_id);


--
-- Name: idx_reports_exercises_report; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_reports_exercises_report ON public.reports_exercises USING btree (report_id);


--
-- Name: idx_resource_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_resource_id ON public.notification_rules USING btree (notification_resource_id);


--
-- Name: idx_rule_attributes; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_rule_attributes ON public.rule_attributes USING btree (attribute_id);


--
-- Name: idx_rule_attributes_inject_importers; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_rule_attributes_inject_importers ON public.rule_attributes USING btree (attribute_inject_importer_id);


--
-- Name: idx_scenario_exercise_exercise; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_scenario_exercise_exercise ON public.scenarios_exercises USING btree (exercise_id);


--
-- Name: idx_scenario_exercise_scenario; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_scenario_exercise_scenario ON public.scenarios_exercises USING btree (scenario_id);


--
-- Name: idx_scenarios_documents_document; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_scenarios_documents_document ON public.scenarios_documents USING btree (document_id);


--
-- Name: idx_scenarios_documents_scenario; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_scenarios_documents_scenario ON public.scenarios_documents USING btree (scenario_id);


--
-- Name: idx_scenarios_teams_scenario; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_scenarios_teams_scenario ON public.scenarios_teams USING btree (scenario_id);


--
-- Name: idx_scenarios_teams_team; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_scenarios_teams_team ON public.scenarios_teams USING btree (team_id);


--
-- Name: idx_scenarios_teams_users_scenario; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_scenarios_teams_users_scenario ON public.scenarios_teams_users USING btree (scenario_id);


--
-- Name: idx_scenarios_teams_users_team; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_scenarios_teams_users_team ON public.scenarios_teams_users USING btree (team_id);


--
-- Name: idx_scenarios_teams_users_user; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_scenarios_teams_users_user ON public.scenarios_teams_users USING btree (user_id);


--
-- Name: idx_security_coverage_bundle_hash_md5; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX idx_security_coverage_bundle_hash_md5 ON public.security_coverages USING btree (security_coverage_bundle_hash_md5);


--
-- Name: idx_tag_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_tag_id ON public.tag_rules USING btree (tag_id);


--
-- Name: idx_teams; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_teams ON public.teams USING btree (team_id);


--
-- Name: idx_user_events_type_created_at; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_user_events_type_created_at ON public.user_events USING btree (user_event_type, user_event_created_at);


--
-- Name: idx_user_events_user_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_user_events_user_id ON public.user_events USING btree (user_id);


--
-- Name: idx_users_organization; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_users_organization ON public.users USING btree (user_organization);


--
-- Name: idx_users_teams_team; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_users_teams_team ON public.users_teams USING btree (team_id);


--
-- Name: idx_users_teams_user; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_users_teams_user ON public.users_teams USING btree (user_id);


--
-- Name: idx_users_user_email_lower_case; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_users_user_email_lower_case ON public.users USING btree (lower((user_email)::text));


--
-- Name: idx_variable_exercise; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_variable_exercise ON public.variables USING btree (variable_exercise);


--
-- Name: idx_variable_scenario; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_variable_scenario ON public.variables USING btree (variable_scenario);


--
-- Name: idx_veriguard_sandboxes_status; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_veriguard_sandboxes_status ON public.veriguard_sandboxes USING btree (veriguard_sandbox_status);


--
-- Name: idx_vulnerabilities_cvss; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_vulnerabilities_cvss ON public.vulnerabilities USING btree (vulnerability_cvss_v31);


--
-- Name: idx_vulnerabilities_cwes_cwe_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_vulnerabilities_cwes_cwe_id ON public.vulnerabilities_cwes USING btree (cwe_id);


--
-- Name: idx_vulnerabilities_cwes_vulnerability_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_vulnerabilities_cwes_vulnerability_id ON public.vulnerabilities_cwes USING btree (vulnerability_id);


--
-- Name: idx_vulnerabilities_published; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_vulnerabilities_published ON public.vulnerabilities USING btree (vulnerability_published);


--
-- Name: idx_vulnerability_reference_urls_vulnerability_id; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE INDEX idx_vulnerability_reference_urls_vulnerability_id ON public.vulnerability_reference_urls USING btree (vulnerability_id);


--
-- Name: injector_contract_payload_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX injector_contract_payload_unique ON public.injectors_contracts USING btree (injector_contract_payload, injector_id);


--
-- Name: injectors_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX injectors_unique ON public.injectors USING btree (injector_type);


--
-- Name: kill_chain_phases_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX kill_chain_phases_unique ON public.kill_chain_phases USING btree (phase_name, phase_kill_chain_name);


--
-- Name: mitigations_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX mitigations_unique ON public.mitigations USING btree (mitigation_external_id);


--
-- Name: payloads_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX payloads_unique ON public.payloads USING btree (payload_external_id);


--
-- Name: tag_name_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX tag_name_unique ON public.tags USING btree (tag_name);


--
-- Name: tokens_value_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX tokens_value_unique ON public.tokens USING btree (token_value);


--
-- Name: uniq_658a47a864e0dbd; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX uniq_658a47a864e0dbd ON public.injects_statuses USING btree (status_inject);


--
-- Name: unique_security_platform_name_type_ci_idx; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX unique_security_platform_name_type_ci_idx ON public.assets USING btree (lower((asset_name)::text), security_platform_type) WHERE ((asset_type)::text = 'SecurityPlatform'::text);


--
-- Name: users_email_unique; Type: INDEX; Schema: public; Owner: veriguard
--

CREATE UNIQUE INDEX users_email_unique ON public.users USING btree (user_email);


--
-- Name: findings_assets after_delete_update_asset_updated_at; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_delete_update_asset_updated_at AFTER DELETE ON public.findings_assets FOR EACH ROW EXECUTE FUNCTION public.update_asset_updated_at_after_delete_finding();


--
-- Name: injects_assets after_delete_update_asset_updated_at; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_delete_update_asset_updated_at AFTER DELETE ON public.injects_assets FOR EACH ROW EXECUTE FUNCTION public.update_asset_updated_at_after_delete_inject();


--
-- Name: exercises_teams after_delete_update_exercise_updated_at; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_delete_update_exercise_updated_at AFTER DELETE ON public.exercises_teams FOR EACH ROW EXECUTE FUNCTION public.update_exercise_updated_at_after_delete_team();


--
-- Name: injects_dependencies after_delete_update_inject_updated_at; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_delete_update_inject_updated_at AFTER DELETE ON public.injects_dependencies FOR EACH ROW EXECUTE FUNCTION public.update_inject_updated_at_after_delete_inject_child();


--
-- Name: injects_teams after_delete_update_inject_updated_at; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_delete_update_inject_updated_at AFTER DELETE ON public.injects_teams FOR EACH ROW EXECUTE FUNCTION public.update_inject_updated_at_after_delete_team();


--
-- Name: injectors_contracts_attack_patterns after_delete_update_injector_contract_updated_at; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_delete_update_injector_contract_updated_at AFTER DELETE ON public.injectors_contracts_attack_patterns FOR EACH ROW EXECUTE FUNCTION public.update_injector_contract_updated_at();


--
-- Name: injectors_contracts_vulnerabilities after_delete_update_injector_contract_updated_at; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_delete_update_injector_contract_updated_at AFTER DELETE ON public.injectors_contracts_vulnerabilities FOR EACH ROW EXECUTE FUNCTION public.update_injector_contract_updated_at();


--
-- Name: scenarios_teams after_delete_update_scenario_updated_at; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_delete_update_scenario_updated_at AFTER DELETE ON public.scenarios_teams FOR EACH ROW EXECUTE FUNCTION public.update_scenario_updated_at_after_delete_team();


--
-- Name: exercises after_insert_exercise; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_insert_exercise AFTER INSERT ON public.exercises FOR EACH ROW EXECUTE FUNCTION public.update_launch_order_trigger();


--
-- Name: injectors_contracts_attack_patterns after_insert_update_injector_contract_updated_at; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_insert_update_injector_contract_updated_at AFTER INSERT ON public.injectors_contracts_attack_patterns FOR EACH ROW EXECUTE FUNCTION public.update_injector_contract_updated_at();


--
-- Name: injectors_contracts_vulnerabilities after_insert_update_injector_contract_updated_at; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_insert_update_injector_contract_updated_at AFTER INSERT ON public.injectors_contracts_vulnerabilities FOR EACH ROW EXECUTE FUNCTION public.update_injector_contract_updated_at();


--
-- Name: exercises after_update_exercise_start_date; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER after_update_exercise_start_date AFTER UPDATE OF exercise_start_date ON public.exercises FOR EACH ROW EXECUTE FUNCTION public.update_launch_order_trigger();


--
-- Name: scenarios trg_delete_scenario_notification_rules; Type: TRIGGER; Schema: public; Owner: veriguard
--

CREATE TRIGGER trg_delete_scenario_notification_rules AFTER DELETE ON public.scenarios FOR EACH ROW EXECUTE FUNCTION public.delete_notification_rules_for_scenario();


--
-- Name: agents agent_asset_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.agents
    ADD CONSTRAINT agent_asset_id_fk FOREIGN KEY (agent_asset) REFERENCES public.assets(asset_id) ON DELETE CASCADE;


--
-- Name: agents agent_executor_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.agents
    ADD CONSTRAINT agent_executor_id_fk FOREIGN KEY (agent_executor) REFERENCES public.executors(executor_id) ON DELETE CASCADE;


--
-- Name: agents agent_inject_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.agents
    ADD CONSTRAINT agent_inject_id_fk FOREIGN KEY (agent_inject) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: agents agent_parent_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.agents
    ADD CONSTRAINT agent_parent_id_fk FOREIGN KEY (agent_parent) REFERENCES public.agents(agent_id) ON DELETE CASCADE;


--
-- Name: asset_agent_jobs asset_agent_agent_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.asset_agent_jobs
    ADD CONSTRAINT asset_agent_agent_fk FOREIGN KEY (asset_agent_agent) REFERENCES public.agents(agent_id) ON DELETE CASCADE;


--
-- Name: asset_agent_jobs asset_agent_inject_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.asset_agent_jobs
    ADD CONSTRAINT asset_agent_inject_fk FOREIGN KEY (asset_agent_inject) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: asset_groups_assets asset_group_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.asset_groups_assets
    ADD CONSTRAINT asset_group_id_fk FOREIGN KEY (asset_group_id) REFERENCES public.asset_groups(asset_group_id) ON DELETE CASCADE;


--
-- Name: asset_groups_tags asset_group_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.asset_groups_tags
    ADD CONSTRAINT asset_group_id_fk FOREIGN KEY (asset_group_id) REFERENCES public.asset_groups(asset_group_id) ON DELETE CASCADE;


--
-- Name: injects_asset_groups asset_group_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_asset_groups
    ADD CONSTRAINT asset_group_id_fk FOREIGN KEY (asset_group_id) REFERENCES public.asset_groups(asset_group_id) ON DELETE CASCADE;


--
-- Name: tag_rule_asset_groups asset_group_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.tag_rule_asset_groups
    ADD CONSTRAINT asset_group_id_fk FOREIGN KEY (asset_group_id) REFERENCES public.asset_groups(asset_group_id) ON DELETE CASCADE;


--
-- Name: asset_groups_assets asset_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.asset_groups_assets
    ADD CONSTRAINT asset_id_fk FOREIGN KEY (asset_id) REFERENCES public.assets(asset_id) ON DELETE CASCADE;


--
-- Name: assets_tags asset_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.assets_tags
    ADD CONSTRAINT asset_id_fk FOREIGN KEY (asset_id) REFERENCES public.assets(asset_id) ON DELETE CASCADE;


--
-- Name: findings_assets asset_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_assets
    ADD CONSTRAINT asset_id_fk FOREIGN KEY (asset_id) REFERENCES public.assets(asset_id) ON DELETE CASCADE;


--
-- Name: injects_assets asset_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_assets
    ADD CONSTRAINT asset_id_fk FOREIGN KEY (asset_id) REFERENCES public.assets(asset_id) ON DELETE CASCADE;


--
-- Name: injectors_contracts_attack_patterns attack_pattern_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts_attack_patterns
    ADD CONSTRAINT attack_pattern_id_fk FOREIGN KEY (attack_pattern_id) REFERENCES public.attack_patterns(attack_pattern_id) ON DELETE CASCADE;


--
-- Name: mitigations_attack_patterns attack_pattern_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.mitigations_attack_patterns
    ADD CONSTRAINT attack_pattern_id_fk FOREIGN KEY (attack_pattern_id) REFERENCES public.attack_patterns(attack_pattern_id) ON DELETE CASCADE;


--
-- Name: payloads_attack_patterns attack_pattern_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads_attack_patterns
    ADD CONSTRAINT attack_pattern_id_fk FOREIGN KEY (attack_pattern_id) REFERENCES public.attack_patterns(attack_pattern_id) ON DELETE CASCADE;


--
-- Name: attack_patterns attack_pattern_parent_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.attack_patterns
    ADD CONSTRAINT attack_pattern_parent_fk FOREIGN KEY (attack_pattern_parent) REFERENCES public.attack_patterns(attack_pattern_id) ON DELETE CASCADE;


--
-- Name: catalog_connectors_configuration catalog_connectors_configurat_connector_configuration_cata_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.catalog_connectors_configuration
    ADD CONSTRAINT catalog_connectors_configurat_connector_configuration_cata_fkey FOREIGN KEY (connector_configuration_catalog_id) REFERENCES public.catalog_connectors(catalog_connector_id) ON DELETE CASCADE;


--
-- Name: challenges_documents challenge_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenges_documents
    ADD CONSTRAINT challenge_id_fk FOREIGN KEY (challenge_id) REFERENCES public.challenges(challenge_id);


--
-- Name: challenges_tags challenge_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenges_tags
    ADD CONSTRAINT challenge_id_fk FOREIGN KEY (challenge_id) REFERENCES public.challenges(challenge_id) ON DELETE CASCADE;


--
-- Name: payloads collector_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads
    ADD CONSTRAINT collector_fk FOREIGN KEY (payload_collector) REFERENCES public.collectors(collector_id) ON DELETE CASCADE;


--
-- Name: communications_users communication_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.communications_users
    ADD CONSTRAINT communication_id_fk FOREIGN KEY (communication_id) REFERENCES public.communications(communication_id) ON DELETE CASCADE;


--
-- Name: connector_instance_configurations connector_instance_configurations_connector_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.connector_instance_configurations
    ADD CONSTRAINT connector_instance_configurations_connector_instance_id_fkey FOREIGN KEY (connector_instance_id) REFERENCES public.connector_instances(connector_instance_id) ON DELETE CASCADE;


--
-- Name: connector_instance_logs connector_instance_logs_connector_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.connector_instance_logs
    ADD CONSTRAINT connector_instance_logs_connector_instance_id_fkey FOREIGN KEY (connector_instance_id) REFERENCES public.connector_instances(connector_instance_id) ON DELETE CASCADE;


--
-- Name: connector_instances connector_instances_connector_instance_catalog_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.connector_instances
    ADD CONSTRAINT connector_instances_connector_instance_catalog_id_fkey FOREIGN KEY (connector_instance_catalog_id) REFERENCES public.catalog_connectors(catalog_connector_id) ON DELETE CASCADE;


--
-- Name: contract_output_elements_tags contract_output_element_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.contract_output_elements_tags
    ADD CONSTRAINT contract_output_element_id_fk FOREIGN KEY (contract_output_element_id) REFERENCES public.contract_output_elements(contract_output_element_id) ON DELETE CASCADE;


--
-- Name: contract_output_elements contract_output_element_output_parser_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.contract_output_elements
    ADD CONSTRAINT contract_output_element_output_parser_id_fk FOREIGN KEY (contract_output_element_output_parser_id) REFERENCES public.output_parsers(output_parser_id) ON DELETE CASCADE;


--
-- Name: custom_dashboards_parameters custom_dashboards_parameters_custom_dashboard_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.custom_dashboards_parameters
    ADD CONSTRAINT custom_dashboards_parameters_custom_dashboard_id_fkey FOREIGN KEY (custom_dashboard_id) REFERENCES public.custom_dashboards(custom_dashboard_id) ON DELETE CASCADE;


--
-- Name: widgets custom_dashboards_pkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.widgets
    ADD CONSTRAINT custom_dashboards_pkey FOREIGN KEY (widget_custom_dashboard) REFERENCES public.custom_dashboards(custom_dashboard_id) ON DELETE CASCADE;


--
-- Name: detection_remediations detection_remediations_detection_remediation_payload_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.detection_remediations
    ADD CONSTRAINT detection_remediations_detection_remediation_payload_id_fkey FOREIGN KEY (detection_remediation_payload_id) REFERENCES public.payloads(payload_id) ON DELETE CASCADE;


--
-- Name: challenges_documents document_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenges_documents
    ADD CONSTRAINT document_id_fk FOREIGN KEY (document_id) REFERENCES public.documents(document_id) ON DELETE CASCADE;


--
-- Name: documents_tags document_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.documents_tags
    ADD CONSTRAINT document_id_fk FOREIGN KEY (document_id) REFERENCES public.documents(document_id) ON DELETE CASCADE;


--
-- Name: exercises_documents document_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_documents
    ADD CONSTRAINT document_id_fk FOREIGN KEY (document_id) REFERENCES public.documents(document_id) ON DELETE CASCADE;


--
-- Name: injects_documents document_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_documents
    ADD CONSTRAINT document_id_fk FOREIGN KEY (document_id) REFERENCES public.documents(document_id) ON DELETE CASCADE;


--
-- Name: scenarios_documents document_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_documents
    ADD CONSTRAINT document_id_fk FOREIGN KEY (document_id) REFERENCES public.documents(document_id) ON DELETE CASCADE;


--
-- Name: payloads executable_file_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads
    ADD CONSTRAINT executable_file_fk FOREIGN KEY (executable_file) REFERENCES public.documents(document_id) ON DELETE CASCADE;


--
-- Name: execution_traces execution_traces_execution_agent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.execution_traces
    ADD CONSTRAINT execution_traces_execution_agent_id_fkey FOREIGN KEY (execution_agent_id) REFERENCES public.agents(agent_id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;


--
-- Name: execution_traces execution_traces_execution_inject_status_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.execution_traces
    ADD CONSTRAINT execution_traces_execution_inject_status_id_fkey FOREIGN KEY (execution_inject_status_id) REFERENCES public.injects_statuses(status_id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;


--
-- Name: execution_traces execution_traces_execution_inject_test_status_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.execution_traces
    ADD CONSTRAINT execution_traces_execution_inject_test_status_id_fkey FOREIGN KEY (execution_inject_test_status_id) REFERENCES public.injects_tests_statuses(status_id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;


--
-- Name: exercises exercise_custom_dashboard_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises
    ADD CONSTRAINT exercise_custom_dashboard_fk FOREIGN KEY (exercise_custom_dashboard) REFERENCES public.custom_dashboards(custom_dashboard_id) ON DELETE SET NULL;


--
-- Name: exercises_documents exercise_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_documents
    ADD CONSTRAINT exercise_id_fk FOREIGN KEY (exercise_id) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: exercises_tags exercise_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_tags
    ADD CONSTRAINT exercise_id_fk FOREIGN KEY (exercise_id) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: scenarios_exercises exercise_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_exercises
    ADD CONSTRAINT exercise_id_fk FOREIGN KEY (exercise_id) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: payloads file_drop_file_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads
    ADD CONSTRAINT file_drop_file_fk FOREIGN KEY (file_drop_file) REFERENCES public.documents(document_id) ON DELETE CASCADE;


--
-- Name: findings_assets finding_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_assets
    ADD CONSTRAINT finding_id_fk FOREIGN KEY (finding_id) REFERENCES public.findings(finding_id) ON DELETE CASCADE;


--
-- Name: findings_tags finding_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_tags
    ADD CONSTRAINT finding_id_fk FOREIGN KEY (finding_id) REFERENCES public.findings(finding_id) ON DELETE CASCADE;


--
-- Name: findings_teams finding_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_teams
    ADD CONSTRAINT finding_id_fk FOREIGN KEY (finding_id) REFERENCES public.findings(finding_id) ON DELETE CASCADE;


--
-- Name: findings_users finding_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_users
    ADD CONSTRAINT finding_id_fk FOREIGN KEY (finding_id) REFERENCES public.findings(finding_id) ON DELETE CASCADE;


--
-- Name: findings finding_inject_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings
    ADD CONSTRAINT finding_inject_id_fk FOREIGN KEY (finding_inject_id) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: comchecks fk_4e039727729413d0; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.comchecks
    ADD CONSTRAINT fk_4e039727729413d0 FOREIGN KEY (comcheck_exercise) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: grants fk_64adc7d620b0bd5e; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.grants
    ADD CONSTRAINT fk_64adc7d620b0bd5e FOREIGN KEY (grant_group) REFERENCES public.groups(group_id) ON DELETE CASCADE;


--
-- Name: injects_statuses fk_658a47a864e0dbd; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_statuses
    ADD CONSTRAINT fk_658a47a864e0dbd FOREIGN KEY (status_inject) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: objectives fk_6cb0696c157d9150; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.objectives
    ADD CONSTRAINT fk_6cb0696c157d9150 FOREIGN KEY (objective_exercise) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: injects_teams fk_96e1b96c7983aee; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_teams
    ADD CONSTRAINT fk_96e1b96c7983aee FOREIGN KEY (inject_id) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: injects_teams fk_96e1b96ccb0ca5a3; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_teams
    ADD CONSTRAINT fk_96e1b96ccb0ca5a3 FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: comchecks_statuses fk_a25f787295a4a46f; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.comchecks_statuses
    ADD CONSTRAINT fk_a25f787295a4a46f FOREIGN KEY (status_comcheck) REFERENCES public.comchecks(comcheck_id) ON DELETE CASCADE;


--
-- Name: comchecks_statuses fk_a25f7872b5957bdd; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.comchecks_statuses
    ADD CONSTRAINT fk_a25f7872b5957bdd FOREIGN KEY (status_user) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: tokens fk_aa5a118eef97e32b; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.tokens
    ADD CONSTRAINT fk_aa5a118eef97e32b FOREIGN KEY (token_user) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: injects_expectations fk_agent; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations
    ADD CONSTRAINT fk_agent FOREIGN KEY (agent_id) REFERENCES public.agents(agent_id) ON DELETE CASCADE;


--
-- Name: injects_expectations fk_article; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations
    ADD CONSTRAINT fk_article FOREIGN KEY (article_id) REFERENCES public.articles(article_id) ON DELETE CASCADE;


--
-- Name: articles fk_article_channel; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT fk_article_channel FOREIGN KEY (article_channel) REFERENCES public.channels(channel_id) ON DELETE CASCADE;


--
-- Name: articles fk_article_exercise; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT fk_article_exercise FOREIGN KEY (article_exercise) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: articles_documents fk_article_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.articles_documents
    ADD CONSTRAINT fk_article_id FOREIGN KEY (article_id) REFERENCES public.articles(article_id) ON DELETE CASCADE;


--
-- Name: injects_expectations fk_asset; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations
    ADD CONSTRAINT fk_asset FOREIGN KEY (asset_id) REFERENCES public.assets(asset_id) ON DELETE CASCADE;


--
-- Name: injects_expectations fk_asset_group; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations
    ADD CONSTRAINT fk_asset_group FOREIGN KEY (asset_group_id) REFERENCES public.asset_groups(asset_group_id) ON DELETE CASCADE;


--
-- Name: attack_patterns_kill_chain_phases fk_attack_pattern_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.attack_patterns_kill_chain_phases
    ADD CONSTRAINT fk_attack_pattern_id FOREIGN KEY (attack_pattern_id) REFERENCES public.attack_patterns(attack_pattern_id) ON DELETE CASCADE;


--
-- Name: injects_expectations fk_challenge; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations
    ADD CONSTRAINT fk_challenge FOREIGN KEY (challenge_id) REFERENCES public.challenges(challenge_id) ON DELETE CASCADE;


--
-- Name: challenge_attempts fk_challenge_attempt_challenge; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenge_attempts
    ADD CONSTRAINT fk_challenge_attempt_challenge FOREIGN KEY (challenge_id) REFERENCES public.challenges(challenge_id) ON DELETE CASCADE;


--
-- Name: challenge_attempts fk_challenge_attempt_inject_status; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenge_attempts
    ADD CONSTRAINT fk_challenge_attempt_inject_status FOREIGN KEY (inject_status_id) REFERENCES public.injects_statuses(status_id) ON DELETE CASCADE;


--
-- Name: challenge_attempts fk_challenge_attempt_user; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenge_attempts
    ADD CONSTRAINT fk_challenge_attempt_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: collectors fk_collector_security_platform; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.collectors
    ADD CONSTRAINT fk_collector_security_platform FOREIGN KEY (collector_security_platform) REFERENCES public.assets(asset_id) ON DELETE SET NULL;


--
-- Name: communications fk_communication_inject; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.communications
    ADD CONSTRAINT fk_communication_inject FOREIGN KEY (communication_inject) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: vulnerability_reference_urls fk_cve_refurl; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.vulnerability_reference_urls
    ADD CONSTRAINT fk_cve_refurl FOREIGN KEY (vulnerability_id) REFERENCES public.vulnerabilities(vulnerability_id) ON DELETE CASCADE;


--
-- Name: vulnerabilities_cwes fk_cves_cwes_cve; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.vulnerabilities_cwes
    ADD CONSTRAINT fk_cves_cwes_cve FOREIGN KEY (vulnerability_id) REFERENCES public.vulnerabilities(vulnerability_id) ON DELETE CASCADE;


--
-- Name: vulnerabilities_cwes fk_cves_cwes_cwe; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.vulnerabilities_cwes
    ADD CONSTRAINT fk_cves_cwes_cwe FOREIGN KEY (cwe_id) REFERENCES public.cwes(cwe_id) ON DELETE CASCADE;


--
-- Name: injects fk_depends_from_another; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects
    ADD CONSTRAINT fk_depends_from_another FOREIGN KEY (inject_depends_from_another) REFERENCES public.injects(inject_id) ON DELETE SET NULL;


--
-- Name: articles_documents fk_document_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.articles_documents
    ADD CONSTRAINT fk_document_id FOREIGN KEY (document_id) REFERENCES public.documents(document_id) ON DELETE CASCADE;


--
-- Name: evaluations fk_evaluation_objective; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.evaluations
    ADD CONSTRAINT fk_evaluation_objective FOREIGN KEY (evaluation_objective) REFERENCES public.objectives(objective_id) ON DELETE CASCADE;


--
-- Name: evaluations fk_evaluation_user; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.evaluations
    ADD CONSTRAINT fk_evaluation_user FOREIGN KEY (evaluation_user) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: variables fk_exercice_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.variables
    ADD CONSTRAINT fk_exercice_id FOREIGN KEY (variable_exercise) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: exercise_mails_reply_to fk_exercise_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercise_mails_reply_to
    ADD CONSTRAINT fk_exercise_id FOREIGN KEY (exercise_id) REFERENCES public.exercises(exercise_id);


--
-- Name: exercises_teams fk_exercise_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_teams
    ADD CONSTRAINT fk_exercise_id FOREIGN KEY (exercise_id) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: exercises_teams_users fk_exercise_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_teams_users
    ADD CONSTRAINT fk_exercise_id FOREIGN KEY (exercise_id) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: exercises fk_exercise_logo_dark; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises
    ADD CONSTRAINT fk_exercise_logo_dark FOREIGN KEY (exercise_logo_dark) REFERENCES public.documents(document_id) ON DELETE SET NULL;


--
-- Name: exercises fk_exercise_logo_light; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises
    ADD CONSTRAINT fk_exercise_logo_light FOREIGN KEY (exercise_logo_light) REFERENCES public.documents(document_id) ON DELETE SET NULL;


--
-- Name: exercises fk_exercise_security_coverage; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises
    ADD CONSTRAINT fk_exercise_security_coverage FOREIGN KEY (exercise_security_coverage) REFERENCES public.security_coverages(security_coverage_id) ON DELETE SET NULL;


--
-- Name: injects_expectations fk_expectation_exercise; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations
    ADD CONSTRAINT fk_expectation_exercise FOREIGN KEY (exercise_id) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: injects_expectations fk_expectation_inject; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations
    ADD CONSTRAINT fk_expectation_inject FOREIGN KEY (inject_id) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: injects_expectations fk_expectations_team; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations
    ADD CONSTRAINT fk_expectations_team FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: injects_expectations fk_expectations_user; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations
    ADD CONSTRAINT fk_expectations_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: logs fk_f08fc65c9cfd383c; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.logs
    ADD CONSTRAINT fk_f08fc65c9cfd383c FOREIGN KEY (log_user) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: logs fk_f08fc65cc0891ec3; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.logs
    ADD CONSTRAINT fk_f08fc65cc0891ec3 FOREIGN KEY (log_exercise) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: users_groups fk_ff8ab7e0a76ed395; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.users_groups
    ADD CONSTRAINT fk_ff8ab7e0a76ed395 FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: users_groups fk_ff8ab7e0fe54d947; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.users_groups
    ADD CONSTRAINT fk_ff8ab7e0fe54d947 FOREIGN KEY (group_id) REFERENCES public.groups(group_id) ON DELETE CASCADE;


--
-- Name: challenges_flags fk_flag_challenge; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenges_flags
    ADD CONSTRAINT fk_flag_challenge FOREIGN KEY (flag_challenge) REFERENCES public.challenges(challenge_id) ON DELETE CASCADE;


--
-- Name: injectors_contracts_domains fk_icd_domain; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts_domains
    ADD CONSTRAINT fk_icd_domain FOREIGN KEY (domain_id) REFERENCES public.domains(domain_id) ON DELETE CASCADE;


--
-- Name: injectors_contracts_domains fk_icd_injector_contract; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts_domains
    ADD CONSTRAINT fk_icd_injector_contract FOREIGN KEY (injector_contract_id) REFERENCES public.injectors_contracts(injector_contract_id) ON DELETE CASCADE;


--
-- Name: injects fk_inject_exercise; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects
    ADD CONSTRAINT fk_inject_exercise FOREIGN KEY (inject_exercise) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: injects fk_injects_user_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects
    ADD CONSTRAINT fk_injects_user_id FOREIGN KEY (inject_user) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: lessons_answers fk_lessons_answer_question; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_answers
    ADD CONSTRAINT fk_lessons_answer_question FOREIGN KEY (lessons_answer_question) REFERENCES public.lessons_questions(lessons_question_id) ON DELETE CASCADE;


--
-- Name: lessons_answers fk_lessons_answer_user; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_answers
    ADD CONSTRAINT fk_lessons_answer_user FOREIGN KEY (lessons_answer_user) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: lessons_categories fk_lessons_category_exercise; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_categories
    ADD CONSTRAINT fk_lessons_category_exercise FOREIGN KEY (lessons_category_exercise) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: lessons_questions fk_lessons_question_category; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_questions
    ADD CONSTRAINT fk_lessons_question_category FOREIGN KEY (lessons_question_category) REFERENCES public.lessons_categories(lessons_category_id) ON DELETE CASCADE;


--
-- Name: lessons_template_categories fk_lessons_template_category_template; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_template_categories
    ADD CONSTRAINT fk_lessons_template_category_template FOREIGN KEY (lessons_template_category_template) REFERENCES public.lessons_templates(lessons_template_id) ON DELETE CASCADE;


--
-- Name: lessons_template_questions fk_lessons_template_question_category; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_template_questions
    ADD CONSTRAINT fk_lessons_template_question_category FOREIGN KEY (lessons_template_question_category) REFERENCES public.lessons_template_categories(lessons_template_category_id) ON DELETE CASCADE;


--
-- Name: channels fk_media_logo_dark; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.channels
    ADD CONSTRAINT fk_media_logo_dark FOREIGN KEY (channel_logo_dark) REFERENCES public.documents(document_id) ON DELETE SET NULL;


--
-- Name: channels fk_media_logo_light; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.channels
    ADD CONSTRAINT fk_media_logo_light FOREIGN KEY (channel_logo_light) REFERENCES public.documents(document_id) ON DELETE SET NULL;


--
-- Name: pauses fk_pause_exercise; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.pauses
    ADD CONSTRAINT fk_pause_exercise FOREIGN KEY (pause_exercise) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: payloads_domains fk_payloads_domains_domain; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads_domains
    ADD CONSTRAINT fk_payloads_domains_domain FOREIGN KEY (domain_id) REFERENCES public.domains(domain_id) ON DELETE CASCADE;


--
-- Name: payloads_domains fk_payloads_domains_payload; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads_domains
    ADD CONSTRAINT fk_payloads_domains_payload FOREIGN KEY (payload_id) REFERENCES public.payloads(payload_id) ON DELETE CASCADE;


--
-- Name: attack_patterns_kill_chain_phases fk_phase_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.attack_patterns_kill_chain_phases
    ADD CONSTRAINT fk_phase_id FOREIGN KEY (phase_id) REFERENCES public.kill_chain_phases(phase_id) ON DELETE CASCADE;


--
-- Name: detection_remediations fk_remediation_collector_type; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.detection_remediations
    ADD CONSTRAINT fk_remediation_collector_type FOREIGN KEY (detection_remediation_collector_type) REFERENCES public.collectors(collector_type) ON DELETE CASCADE;


--
-- Name: scenario_mails_reply_to fk_scenario_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenario_mails_reply_to
    ADD CONSTRAINT fk_scenario_id FOREIGN KEY (scenario_id) REFERENCES public.scenarios(scenario_id);


--
-- Name: assets fk_security_platform_logo_dark; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.assets
    ADD CONSTRAINT fk_security_platform_logo_dark FOREIGN KEY (security_platform_logo_dark) REFERENCES public.documents(document_id) ON DELETE SET NULL;


--
-- Name: assets fk_security_platform_logo_light; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.assets
    ADD CONSTRAINT fk_security_platform_logo_light FOREIGN KEY (security_platform_logo_light) REFERENCES public.documents(document_id) ON DELETE SET NULL;


--
-- Name: exercises_teams fk_team_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_teams
    ADD CONSTRAINT fk_team_id FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: exercises_teams_users fk_team_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_teams_users
    ADD CONSTRAINT fk_team_id FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: users_teams fk_team_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.users_teams
    ADD CONSTRAINT fk_team_id FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: teams fk_teams_organizations; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT fk_teams_organizations FOREIGN KEY (team_organization) REFERENCES public.organizations(organization_id) ON DELETE SET NULL;


--
-- Name: exercises_teams_users fk_user_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_teams_users
    ADD CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: users_teams fk_user_id; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.users_teams
    ADD CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: users fk_users_organizations; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_organizations FOREIGN KEY (user_organization) REFERENCES public.organizations(organization_id) ON DELETE SET NULL;


--
-- Name: groups_roles group_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.groups_roles
    ADD CONSTRAINT group_id_fk FOREIGN KEY (group_id) REFERENCES public.groups(group_id);


--
-- Name: injects_expectations_traces inject_expectation_trace_source_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations_traces
    ADD CONSTRAINT inject_expectation_trace_source_id_fk FOREIGN KEY (inject_expectation_trace_source_id) REFERENCES public.assets(asset_id) ON DELETE CASCADE;


--
-- Name: injects_asset_groups inject_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_asset_groups
    ADD CONSTRAINT inject_id_fk FOREIGN KEY (inject_id) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: injects_assets inject_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_assets
    ADD CONSTRAINT inject_id_fk FOREIGN KEY (inject_id) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: injects_documents inject_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_documents
    ADD CONSTRAINT inject_id_fk FOREIGN KEY (inject_id) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: injects_tags inject_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_tags
    ADD CONSTRAINT inject_id_fk FOREIGN KEY (inject_id) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: inject_importers inject_importers_injector_contract_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.inject_importers
    ADD CONSTRAINT inject_importers_injector_contract_id_fkey FOREIGN KEY (importer_injector_contract_id) REFERENCES public.injectors_contracts(injector_contract_id) ON DELETE CASCADE;


--
-- Name: inject_importers inject_importers_mapper_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.inject_importers
    ADD CONSTRAINT inject_importers_mapper_id_fkey FOREIGN KEY (importer_mapper_id) REFERENCES public.import_mappers(mapper_id) ON DELETE SET NULL;


--
-- Name: injects_tests_statuses inject_test_status_inject_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_tests_statuses
    ADD CONSTRAINT inject_test_status_inject_id_fkey FOREIGN KEY (status_inject) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: injects injector_contract_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects
    ADD CONSTRAINT injector_contract_fk FOREIGN KEY (inject_injector_contract) REFERENCES public.injectors_contracts(injector_contract_id) ON DELETE SET NULL;


--
-- Name: injectors_contracts injector_contract_payload_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts
    ADD CONSTRAINT injector_contract_payload_fk FOREIGN KEY (injector_contract_payload) REFERENCES public.payloads(payload_id) ON DELETE CASCADE;


--
-- Name: injectors_contracts injector_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts
    ADD CONSTRAINT injector_id_fk FOREIGN KEY (injector_id) REFERENCES public.injectors(injector_id) ON DELETE CASCADE;


--
-- Name: injectors_contracts_attack_patterns injectors_contracts_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts_attack_patterns
    ADD CONSTRAINT injectors_contracts_id_fk FOREIGN KEY (injector_contract_id) REFERENCES public.injectors_contracts(injector_contract_id) ON DELETE CASCADE;


--
-- Name: injectors_contracts_vulnerabilities injectors_contracts_vulnerabilities_injector_contract_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts_vulnerabilities
    ADD CONSTRAINT injectors_contracts_vulnerabilities_injector_contract_id_fkey FOREIGN KEY (injector_contract_id) REFERENCES public.injectors_contracts(injector_contract_id) ON DELETE CASCADE;


--
-- Name: injectors_contracts_vulnerabilities injectors_contracts_vulnerabilities_vulnerability_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injectors_contracts_vulnerabilities
    ADD CONSTRAINT injectors_contracts_vulnerabilities_vulnerability_id_fkey FOREIGN KEY (vulnerability_id) REFERENCES public.vulnerabilities(vulnerability_id) ON DELETE CASCADE;


--
-- Name: injects_dependencies injects_dependencies_inject_children_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_dependencies
    ADD CONSTRAINT injects_dependencies_inject_children_id_fkey FOREIGN KEY (inject_children_id) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: injects_dependencies injects_dependencies_inject_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_dependencies
    ADD CONSTRAINT injects_dependencies_inject_parent_id_fkey FOREIGN KEY (inject_parent_id) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: injects_expectations_traces injects_expectations_traces_expectation_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_expectations_traces
    ADD CONSTRAINT injects_expectations_traces_expectation_fkey FOREIGN KEY (inject_expectation_trace_expectation) REFERENCES public.injects_expectations(inject_expectation_id) ON DELETE CASCADE;


--
-- Name: lessons_categories_teams lessons_category_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_categories_teams
    ADD CONSTRAINT lessons_category_id_fk FOREIGN KEY (lessons_category_id) REFERENCES public.lessons_categories(lessons_category_id) ON DELETE CASCADE;


--
-- Name: logs_tags log_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.logs_tags
    ADD CONSTRAINT log_id_fk FOREIGN KEY (log_id) REFERENCES public.logs(log_id) ON DELETE CASCADE;


--
-- Name: mitigations_attack_patterns mitigation_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.mitigations_attack_patterns
    ADD CONSTRAINT mitigation_id_fk FOREIGN KEY (mitigation_id) REFERENCES public.mitigations(mitigation_id) ON DELETE CASCADE;


--
-- Name: organizations_tags organization_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.organizations_tags
    ADD CONSTRAINT organization_id_fk FOREIGN KEY (organization_id) REFERENCES public.organizations(organization_id) ON DELETE CASCADE;


--
-- Name: output_parsers output_parser_payload_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.output_parsers
    ADD CONSTRAINT output_parser_payload_id_fk FOREIGN KEY (output_parser_payload_id) REFERENCES public.payloads(payload_id) ON DELETE CASCADE;


--
-- Name: payloads_tags payload_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads_tags
    ADD CONSTRAINT payload_id_fk FOREIGN KEY (payload_id) REFERENCES public.payloads(payload_id);


--
-- Name: payloads_attack_patterns payloads_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads_attack_patterns
    ADD CONSTRAINT payloads_id_fk FOREIGN KEY (payload_id) REFERENCES public.payloads(payload_id) ON DELETE CASCADE;


--
-- Name: regex_groups regex_group_contract_output_element_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.regex_groups
    ADD CONSTRAINT regex_group_contract_output_element_id_fk FOREIGN KEY (regex_group_contract_output_element_id) REFERENCES public.contract_output_elements(contract_output_element_id) ON DELETE CASCADE;


--
-- Name: report_informations report_informations_report_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.report_informations
    ADD CONSTRAINT report_informations_report_id_fkey FOREIGN KEY (report_id) REFERENCES public.reports(report_id) ON DELETE CASCADE;


--
-- Name: report_inject_comment report_inject_comment_inject_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.report_inject_comment
    ADD CONSTRAINT report_inject_comment_inject_id_fkey FOREIGN KEY (inject_id) REFERENCES public.injects(inject_id) ON DELETE CASCADE;


--
-- Name: report_inject_comment report_inject_comment_report_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.report_inject_comment
    ADD CONSTRAINT report_inject_comment_report_id_fkey FOREIGN KEY (report_id) REFERENCES public.reports(report_id) ON DELETE CASCADE;


--
-- Name: reports_exercises reports_exercises_exercise_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.reports_exercises
    ADD CONSTRAINT reports_exercises_exercise_id_fkey FOREIGN KEY (exercise_id) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: reports_exercises reports_exercises_report_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.reports_exercises
    ADD CONSTRAINT reports_exercises_report_id_fkey FOREIGN KEY (report_id) REFERENCES public.reports(report_id) ON DELETE CASCADE;


--
-- Name: groups_roles role_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.groups_roles
    ADD CONSTRAINT role_id_fk FOREIGN KEY (role_id) REFERENCES public.roles(role_id);


--
-- Name: roles_capabilities role_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.roles_capabilities
    ADD CONSTRAINT role_id_fk FOREIGN KEY (role_id) REFERENCES public.roles(role_id);


--
-- Name: rule_attributes rule_attributes_importer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.rule_attributes
    ADD CONSTRAINT rule_attributes_importer_id_fkey FOREIGN KEY (attribute_inject_importer_id) REFERENCES public.inject_importers(importer_id) ON DELETE CASCADE;


--
-- Name: scenarios scenario_custom_dashboard_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios
    ADD CONSTRAINT scenario_custom_dashboard_fk FOREIGN KEY (scenario_custom_dashboard) REFERENCES public.custom_dashboards(custom_dashboard_id) ON DELETE SET NULL;


--
-- Name: articles scenario_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.articles
    ADD CONSTRAINT scenario_fk FOREIGN KEY (article_scenario) REFERENCES public.scenarios(scenario_id) ON DELETE CASCADE;


--
-- Name: injects scenario_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects
    ADD CONSTRAINT scenario_fk FOREIGN KEY (inject_scenario) REFERENCES public.scenarios(scenario_id) ON DELETE CASCADE;


--
-- Name: lessons_categories scenario_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_categories
    ADD CONSTRAINT scenario_fk FOREIGN KEY (lessons_category_scenario) REFERENCES public.scenarios(scenario_id) ON DELETE CASCADE;


--
-- Name: objectives scenario_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.objectives
    ADD CONSTRAINT scenario_fk FOREIGN KEY (objective_scenario) REFERENCES public.scenarios(scenario_id) ON DELETE CASCADE;


--
-- Name: scenarios_documents scenario_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_documents
    ADD CONSTRAINT scenario_id_fk FOREIGN KEY (scenario_id) REFERENCES public.scenarios(scenario_id);


--
-- Name: scenarios_exercises scenario_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_exercises
    ADD CONSTRAINT scenario_id_fk FOREIGN KEY (scenario_id) REFERENCES public.scenarios(scenario_id) ON DELETE CASCADE;


--
-- Name: scenarios_tags scenario_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_tags
    ADD CONSTRAINT scenario_id_fk FOREIGN KEY (scenario_id) REFERENCES public.scenarios(scenario_id) ON DELETE CASCADE;


--
-- Name: scenarios_teams scenario_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_teams
    ADD CONSTRAINT scenario_id_fk FOREIGN KEY (scenario_id) REFERENCES public.scenarios(scenario_id);


--
-- Name: scenarios_teams_users scenario_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_teams_users
    ADD CONSTRAINT scenario_id_fk FOREIGN KEY (scenario_id) REFERENCES public.scenarios(scenario_id) ON DELETE CASCADE;


--
-- Name: variables scenario_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.variables
    ADD CONSTRAINT scenario_id_fk FOREIGN KEY (variable_scenario) REFERENCES public.scenarios(scenario_id) ON DELETE CASCADE;


--
-- Name: security_coverage_send_job security_coverage_send_job_security_coverage_send_job_sim_fkey1; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.security_coverage_send_job
    ADD CONSTRAINT security_coverage_send_job_security_coverage_send_job_sim_fkey1 FOREIGN KEY (security_coverage_send_job_simulation) REFERENCES public.exercises(exercise_id) ON DELETE CASCADE;


--
-- Name: security_coverages security_coverages_security_coverage_scenario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.security_coverages
    ADD CONSTRAINT security_coverages_security_coverage_scenario_fkey FOREIGN KEY (security_coverage_scenario) REFERENCES public.scenarios(scenario_id) ON DELETE SET NULL;


--
-- Name: asset_groups_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.asset_groups_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: assets_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.assets_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: challenges_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.challenges_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: contract_output_elements_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.contract_output_elements_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: documents_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.documents_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: exercises_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.exercises_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: findings_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: injects_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.injects_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: logs_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.logs_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: organizations_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.organizations_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: payloads_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.payloads_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id);


--
-- Name: scenarios_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: tag_rules tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.tag_rules
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id);


--
-- Name: teams_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.teams_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: users_tags tag_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.users_tags
    ADD CONSTRAINT tag_id_fk FOREIGN KEY (tag_id) REFERENCES public.tags(tag_id) ON DELETE CASCADE;


--
-- Name: tag_rule_asset_groups tag_rule_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.tag_rule_asset_groups
    ADD CONSTRAINT tag_rule_id_fk FOREIGN KEY (tag_rule_id) REFERENCES public.tag_rules(tag_rule_id);


--
-- Name: findings_teams team_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_teams
    ADD CONSTRAINT team_id_fk FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: lessons_categories_teams team_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.lessons_categories_teams
    ADD CONSTRAINT team_id_fk FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: scenarios_teams team_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_teams
    ADD CONSTRAINT team_id_fk FOREIGN KEY (team_id) REFERENCES public.teams(team_id);


--
-- Name: scenarios_teams_users team_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_teams_users
    ADD CONSTRAINT team_id_fk FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: teams_tags team_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.teams_tags
    ADD CONSTRAINT team_id_fk FOREIGN KEY (team_id) REFERENCES public.teams(team_id) ON DELETE CASCADE;


--
-- Name: communications_users user_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.communications_users
    ADD CONSTRAINT user_id_fk FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: findings_users user_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.findings_users
    ADD CONSTRAINT user_id_fk FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: scenarios_teams_users user_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.scenarios_teams_users
    ADD CONSTRAINT user_id_fk FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: users_tags user_id_fk; Type: FK CONSTRAINT; Schema: public; Owner: veriguard
--

ALTER TABLE ONLY public.users_tags
    ADD CONSTRAINT user_id_fk FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
--


