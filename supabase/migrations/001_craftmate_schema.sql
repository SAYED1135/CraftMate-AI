-- ============================================================================
-- CraftMate AI — Supabase PostgreSQL Schema (v0.2)
-- Target Project Ref: fucsztovkkilyboinhrv
-- ============================================================================

create extension if not exists "pgcrypto";

-- 1. جدول السيرفرات (Servers)
create table if not exists public.servers (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid references auth.users(id) on delete cascade,
  name text not null,
  host_provider text not null check (host_provider in ('play_hosting', 'falixnodes', 'mcserverhost', 'minefort', 'other')),
  server_software text not null default 'Paper',
  mc_version text default '1.21.1',
  java_version text default 'Java 21',
  max_ram_mb integer default 2048,
  status text not null default 'UNLINKED' check (status in ('ONLINE', 'HIBERNATING', 'OFFLINE', 'CRASHED', 'UNLINKED')),
  capabilities jsonb not null default '{}'::jsonb,
  environment_snapshot jsonb not null default '{}'::jsonb,
  last_heartbeat_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 2. جدول رموز الربط واعتمادات البلجن (Connector Tokens)
create table if not exists public.connector_tokens (
  id uuid primary key default gen_random_uuid(),
  server_id uuid not null references public.servers(id) on delete cascade,
  pairing_code text unique,
  pairing_expires_at timestamptz,
  token_hash text unique,
  is_revoked boolean not null default false,
  last_used_at timestamptz,
  created_at timestamptz not null default now()
);

-- 3. جدول التقارير التشخيصية (Diagnostic Reports)
create table if not exists public.diagnostic_reports (
  id uuid primary key default gen_random_uuid(),
  server_id uuid references public.servers(id) on delete cascade,
  owner_id uuid references auth.users(id) on delete cascade,
  host_provider text not null default 'other',
  source text not null default 'manual_upload' check (source in ('manual_upload', 'connector_snapshot', 'spark_report')),
  summary_ar text not null,
  summary_en text,
  confidence_score numeric(3,2) not null default 0.85,
  findings jsonb not null default '[]'::jsonb,
  bedrock_assessment jsonb not null default '{}'::jsonb,
  deduplicated_stats jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- 4. جدول خطط العمل والموافقات (Action Plans & Approvals)
create table if not exists public.action_plans (
  id uuid primary key default gen_random_uuid(),
  server_id uuid references public.servers(id) on delete cascade,
  report_id uuid references public.diagnostic_reports(id) on delete set null,
  owner_id uuid references auth.users(id) on delete cascade,
  title text not null,
  goal text not null,
  steps jsonb not null default '[]'::jsonb,
  rollback_plan jsonb not null default '[]'::jsonb,
  policy_check_passed boolean not null default true,
  policy_violations jsonb not null default '[]'::jsonb,
  status text not null default 'PENDING_APPROVAL' check (status in ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXECUTED', 'FAILED', 'ROLLED_BACK')),
  approved_by uuid references auth.users(id) on delete set null,
  approved_at timestamptz,
  expires_at timestamptz not null default (now() + interval '30 minutes'),
  execution_result jsonb,
  created_at timestamptz not null default now()
);

-- 5. جدول سجل التدقيق الأمني (Audit Logs)
create table if not exists public.audit_logs (
  id uuid primary key default gen_random_uuid(),
  server_id uuid references public.servers(id) on delete cascade,
  actor_id uuid references auth.users(id) on delete set null,
  action_type text not null,
  target_resource text not null,
  outcome text not null check (outcome in ('SUCCESS', 'BLOCKED_BY_POLICY', 'FAILED', 'PENDING')),
  redacted_metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- تفعيل Row Level Security (RLS) لعزل بيانات كل مستخدم
alter table public.servers enable row level security;
alter table public.connector_tokens enable row level security;
alter table public.diagnostic_reports enable row level security;
alter table public.action_plans enable row level security;
alter table public.audit_logs enable row level security;

-- سياسات الوصول للمستخدم المالك فقط
create policy "Users manage own servers" on public.servers
  for all using (auth.uid() = owner_id);

create policy "Users manage own diagnostic reports" on public.diagnostic_reports
  for all using (auth.uid() = owner_id);

create policy "Users manage own action plans" on public.action_plans
  for all using (auth.uid() = owner_id);

create policy "Users view own audit logs" on public.audit_logs
  for select using (auth.uid() = actor_id);
