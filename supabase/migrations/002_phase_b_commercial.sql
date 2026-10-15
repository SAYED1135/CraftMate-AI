-- ============================================================================
-- CraftMate AI — Phase B Commercial Schema (v0.3)
-- Target Project Ref: fucsztovkkilyboinhrv
-- Run AFTER 001_craftmate_schema.sql in: Supabase Dashboard -> SQL Editor
-- ============================================================================

-- 1. ملفات المستخدمين التجارية (Profiles) -----------------------------------
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique not null,
  display_code text unique not null,
  plan text not null default 'free' check (plan in ('free', 'pro', 'ultra')),
  locale text not null default 'ar' check (locale in ('ar', 'en')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 2. الخطط والاستحقاقات (Plans & Entitlements) -------------------------------
create table if not exists public.plans (
  plan text primary key check (plan in ('free', 'pro', 'ultra')),
  max_servers integer not null,
  max_diagnoses_month integer not null,
  retention_days integer not null,
  afk_bot_enabled boolean not null default false,
  updated_at timestamptz not null default now()
);

insert into public.plans (plan, max_servers, max_diagnoses_month, retention_days, afk_bot_enabled)
values
  ('free',  1, 10,   7,     false),
  ('pro',   5, 9999, 90,    false),
  ('ultra', 20, 9999, 36500, false)
on conflict (plan) do update set
  max_servers = excluded.max_servers,
  max_diagnoses_month = excluded.max_diagnoses_month,
  retention_days = excluded.retention_days,
  updated_at = now();

-- 3. قوائم الانتظار (Waitlists: beta / afk / pro) ------------------------------
create table if not exists public.waitlists (
  id uuid primary key default gen_random_uuid(),
  email text not null check (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
  product text not null check (product in ('beta', 'afk', 'pro', 'ultra')),
  locale text not null default 'ar',
  source text not null default 'website',
  created_at timestamptz not null default now(),
  unique (email, product)
);

-- 4. عدّادات الاستخدام الشهري (Usage Counters) --------------------------------
create table if not exists public.usage_counters (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  period text not null, -- YYYY-MM
  diagnoses_count integer not null default 0,
  ai_tokens integer not null default 0,
  updated_at timestamptz not null default now(),
  unique (owner_id, period)
);

-- 5. سجل التغييرات القابل للتراجع (Change Log) ---------------------------------
create table if not exists public.change_log (
  id uuid primary key default gen_random_uuid(),
  server_id uuid references public.servers(id) on delete cascade,
  owner_id uuid references auth.users(id) on delete cascade,
  action text not null,
  target_resource text,
  backup_ref text,
  reverted_at timestamptz,
  created_at timestamptz not null default now()
);

-- 6. توسعات جدول السيرفرات لرحلة Phase B --------------------------------------
alter table public.servers
  add column if not exists address text,
  add column if not exists edition text not null default 'java',
  add column if not exists link_method text not null default 'plugin'
    check (link_method in ('plugin', 'manual', 'later')),
  add column if not exists linked_at timestamptz;

-- 7. إنشاء ملف المستخدم تلقائياً بعد التسجيل ----------------------------------
create or replace function public.handle_new_craftmate_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, username, display_code, plan, locale)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'username', split_part(new.email, '@', 1), 'craftmate'),
    'CM-' || upper(substr(md5(random()::text || new.id::text), 1, 6)),
    'free',
    coalesce(new.raw_user_meta_data ->> 'locale', 'ar')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_craftmate on auth.users;
create trigger on_auth_user_created_craftmate
  after insert on auth.users
  for each row execute procedure public.handle_new_craftmate_user();

-- 8. Row Level Security --------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.plans enable row level security;
alter table public.waitlists enable row level security;
alter table public.usage_counters enable row level security;
alter table public.change_log enable row level security;

drop policy if exists "Users manage own profile" on public.profiles;
create policy "Users manage own profile" on public.profiles
  for all using (auth.uid() = id);

drop policy if exists "Plans are public readable" on public.plans;
create policy "Plans are public readable" on public.plans
  for select using (true);

-- قوائم الانتظار: أي زائر يقدر يسجل، ولا أحد يقدر يقرأ القائمة من العميل
drop policy if exists "Anyone can join waitlist" on public.waitlists;
create policy "Anyone can join waitlist" on public.waitlists
  for insert with check (true);

drop policy if exists "Users manage own usage" on public.usage_counters;
create policy "Users manage own usage" on public.usage_counters
  for all using (auth.uid() = owner_id);

drop policy if exists "Users manage own change log" on public.change_log;
create policy "Users manage own change log" on public.change_log
  for all using (auth.uid() = owner_id);

-- أكواد ربط البلجن: المالك يدير رموز سيرفراته فقط
drop policy if exists "Users manage connector tokens of own servers" on public.connector_tokens;
create policy "Users manage connector tokens of own servers" on public.connector_tokens
  for all using (
    exists (
      select 1 from public.servers s
      where s.id = connector_tokens.server_id
        and s.owner_id = auth.uid()
    )
  );
