-- ============================================================================
-- CraftMate AI — Explicit RLS for connector pairing tokens
-- Target Project Ref: fucsztovkkilyboinhrv
-- Run AFTER 001, 002, 003 and 004 in: Supabase Dashboard -> SQL Editor
--
-- Purpose:
-- The dashboard (authenticated Supabase user) must be able to create and read
-- pairing tokens only for servers owned by that user. The Cloudflare Worker
-- uses service_role server-side and bypasses RLS for the plugin claim flow.
-- Making INSERT WITH CHECK explicit removes ambiguity from the older
-- FOR ALL ... USING policy and makes pairing failures diagnosable.
-- ============================================================================

alter table public.connector_tokens enable row level security;

drop policy if exists "Users manage connector tokens of own servers" on public.connector_tokens;
drop policy if exists "Users read connector tokens of own servers" on public.connector_tokens;
drop policy if exists "Users create connector tokens for own servers" on public.connector_tokens;
drop policy if exists "Users update connector tokens of own servers" on public.connector_tokens;
drop policy if exists "Users delete connector tokens of own servers" on public.connector_tokens;

create policy "Users read connector tokens of own servers"
  on public.connector_tokens
  for select
  to authenticated
  using (
    exists (
      select 1 from public.servers s
      where s.id = connector_tokens.server_id
        and s.owner_id = auth.uid()
    )
  );

create policy "Users create connector tokens for own servers"
  on public.connector_tokens
  for insert
  to authenticated
  with check (
    exists (
      select 1 from public.servers s
      where s.id = connector_tokens.server_id
        and s.owner_id = auth.uid()
    )
  );

create policy "Users update connector tokens of own servers"
  on public.connector_tokens
  for update
  to authenticated
  using (
    exists (
      select 1 from public.servers s
      where s.id = connector_tokens.server_id
        and s.owner_id = auth.uid()
    )
  )
  with check (
    exists (
      select 1 from public.servers s
      where s.id = connector_tokens.server_id
        and s.owner_id = auth.uid()
    )
  );

create policy "Users delete connector tokens of own servers"
  on public.connector_tokens
  for delete
  to authenticated
  using (
    exists (
      select 1 from public.servers s
      where s.id = connector_tokens.server_id
        and s.owner_id = auth.uid()
    )
  );

create index if not exists idx_connector_tokens_active_code
  on public.connector_tokens (pairing_code)
  where is_revoked = false;
