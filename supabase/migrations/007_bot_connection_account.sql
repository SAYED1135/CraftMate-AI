-- ============================================================================
-- CraftMate AI — Platform connection account (bot-identity linking)
-- Target Project Ref: fucsztovkkilyboinhrv
-- Run AFTER 001-006 in: Supabase Dashboard -> SQL Editor
-- The plugin path is retired from the product; servers now link through a
-- platform-owned connection account that the owner adds to the server with
-- granted privileges. The Worker verifies by a real join + privilege check.
-- ============================================================================

alter table public.servers
  add column if not exists bot_username text,
  add column if not exists bot_verified_at timestamptz,
  add column if not exists bot_verify_status text not null default 'none'
    check (bot_verify_status in ('none', 'requested', 'verifying', 'verified', 'failed')),
  add column if not exists bot_verify_detail text;

create index if not exists idx_servers_bot_verify
  on public.servers (bot_verify_status);
