-- ============================================================================
-- CraftMate AI — Server control sessions + plugin task queue (Connector 0.5)
-- Target Project Ref: fucsztovkkilyboinhrv
-- Run AFTER 001-005 in: Supabase Dashboard -> SQL Editor
--
-- The Worker (service_role, server-side only) creates and routes tasks.
-- Browsers may read only their own task history through RLS; task creation is
-- intentionally not granted directly to browser clients.
-- ============================================================================

alter table public.servers
  add column if not exists control_enabled boolean not null default false,
  add column if not exists control_mode text not null default 'off'
    check (control_mode in ('off', 'full_instant')),
  add column if not exists control_activated_at timestamptz,
  add column if not exists control_expires_at timestamptz,
  add column if not exists control_updated_at timestamptz;

create table if not exists public.server_tasks (
  id uuid primary key default gen_random_uuid(),
  server_id uuid not null references public.servers(id) on delete cascade,
  owner_id uuid not null references auth.users(id) on delete cascade,
  conversation_id uuid,
  type text not null,
  payload jsonb not null default '{}'::jsonb,
  risk text not null default 'read'
    check (risk in ('read', 'safe', 'write', 'high')),
  status text not null default 'QUEUED'
    check (status in ('PROPOSED', 'AWAITING_APPROVAL', 'QUEUED', 'CLAIMED', 'RUNNING', 'VERIFYING', 'SUCCEEDED', 'FAILED', 'ROLLED_BACK', 'EXPIRED', 'CANCELLED')),
  proposal_hash text,
  idempotency_key text not null,
  backup_ref text,
  restart_required boolean not null default false,
  result jsonb,
  error_code text,
  expires_at timestamptz not null default (now() + interval '5 minutes'),
  created_at timestamptz not null default now(),
  claimed_at timestamptz,
  finished_at timestamptz,
  unique (server_id, idempotency_key)
);

create index if not exists idx_server_tasks_poll
  on public.server_tasks (server_id, status, created_at);
create index if not exists idx_server_tasks_owner_created
  on public.server_tasks (owner_id, created_at desc);
create index if not exists idx_servers_control_expiry
  on public.servers (control_enabled, control_expires_at);

alter table public.server_tasks enable row level security;

drop policy if exists "Users view own server tasks" on public.server_tasks;
create policy "Users view own server tasks" on public.server_tasks
  for select using (auth.uid() = owner_id);
