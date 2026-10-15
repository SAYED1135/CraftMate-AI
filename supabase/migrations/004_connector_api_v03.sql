-- ============================================================================
-- CraftMate AI — Connector API v0.3 (real plugin claim + heartbeat)
-- Target Project Ref: fucsztovkkilyboinhrv
-- Run AFTER 001, 002 and 003 in: Supabase Dashboard -> SQL Editor
--
-- The Cloudflare Worker uses the service_role key server-side only.
-- That key must be the ROTATED one and must live in Cloudflare Secrets.
-- ============================================================================

alter table public.connector_tokens
  add column if not exists claimed_at timestamptz;

alter table public.servers
  add column if not exists connector_version text;

create index if not exists idx_connector_tokens_pairing_code
  on public.connector_tokens (pairing_code);

create index if not exists idx_connector_tokens_server_token
  on public.connector_tokens (server_id, token_hash);

create index if not exists idx_servers_owner_status
  on public.servers (owner_id, status);

create index if not exists idx_notifications_owner_created
  on public.notifications (owner_id, created_at desc);
