-- ============================================================================
-- CraftMate AI — Phase C Platform Schema (Alerts, Posts, Notices, Tickets)
-- Target Project Ref: fucsztovkkilyboinhrv
-- Run AFTER 001 and 002 in: Supabase Dashboard -> SQL Editor
-- ============================================================================

-- 1. تنبيهات المستخدمين -------------------------------------------------------
create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  server_id uuid references public.servers(id) on delete cascade,
  type text not null default 'info' check (type in ('info', 'link', 'sleep', 'danger', 'settings')),
  title_ar text not null,
  title_en text,
  body_ar text,
  body_en text,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

-- 2. إعدادات تنبيه كل مستخدم ---------------------------------------------------
create table if not exists public.alert_settings (
  owner_id uuid primary key references auth.users(id) on delete cascade,
  in_app boolean not null default true,
  on_down boolean not null default true,
  on_high_diagnosis boolean not null default true,
  discord_webhook text,
  updated_at timestamptz not null default now()
);

-- 3. منشورات المجتمع ------------------------------------------------------------
create table if not exists public.posts (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid references auth.users(id) on delete set null,
  author_name text not null,
  title_ar text not null,
  title_en text,
  body_ar text not null,
  body_en text,
  is_pinned boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.post_likes (
  post_id uuid not null references public.posts(id) on delete cascade,
  owner_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, owner_id)
);

create table if not exists public.post_comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts(id) on delete cascade,
  owner_id uuid references auth.users(id) on delete set null,
  author_name text not null,
  body text not null,
  created_at timestamptz not null default now()
);

-- 4. إشعارات المطوّر ------------------------------------------------------------
create table if not exists public.notices (
  id uuid primary key default gen_random_uuid(),
  title_ar text not null,
  title_en text,
  body_ar text not null,
  body_en text,
  image_url text,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.notice_dismissals (
  notice_id uuid not null references public.notices(id) on delete cascade,
  owner_id uuid not null references auth.users(id) on delete cascade,
  dismissed_at timestamptz not null default now(),
  primary key (notice_id, owner_id)
);

-- 5. تذاكر الدعم -----------------------------------------------------------------
create table if not exists public.support_tickets (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  server_id uuid references public.servers(id) on delete set null,
  subject text not null,
  body text not null,
  status text not null default 'open' check (status in ('open', 'in-progress', 'closed')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 6. إعدادات ميزات المنصة (Feature Flags) ---------------------------------------
create table if not exists public.platform_settings (
  key text primary key,
  value jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

insert into public.platform_settings (key, value)
values ('feature_flags', '{"posts": true, "notices": true, "afkWaitlist": true, "support": true}'::jsonb)
on conflict (key) do nothing;

-- 7. إعدادات سيرفر قابلة للتراجع تُخزن كـ JSON ----------------------------------
alter table public.servers
  add column if not exists settings jsonb not null default
  '{"viewDistance":8,"simulationDistance":6,"maxPlayers":20,"difficulty":"normal","pvp":true}'::jsonb;

-- RLS ---------------------------------------------------------------------------
alter table public.notifications enable row level security;
alter table public.alert_settings enable row level security;
alter table public.posts enable row level security;
alter table public.post_likes enable row level security;
alter table public.post_comments enable row level security;
alter table public.notices enable row level security;
alter table public.notice_dismissals enable row level security;
alter table public.support_tickets enable row level security;
alter table public.platform_settings enable row level security;

drop policy if exists "Users manage own notifications" on public.notifications;
create policy "Users manage own notifications" on public.notifications
  for all using (auth.uid() = owner_id);

drop policy if exists "Users manage own alert settings" on public.alert_settings;
create policy "Users manage own alert settings" on public.alert_settings
  for all using (auth.uid() = owner_id);

drop policy if exists "Posts are readable by signed users" on public.posts;
create policy "Posts are readable by signed users" on public.posts
  for select using (auth.uid() is not null);

drop policy if exists "Users create own posts" on public.posts;
create policy "Users create own posts" on public.posts
  for insert with check (auth.uid() = owner_id);

drop policy if exists "Users update own posts" on public.posts;
create policy "Users update own posts" on public.posts
  for update using (auth.uid() = owner_id);

drop policy if exists "Users delete own posts" on public.posts;
create policy "Users delete own posts" on public.posts
  for delete using (auth.uid() = owner_id);

drop policy if exists "Users manage own post likes" on public.post_likes;
create policy "Users manage own post likes" on public.post_likes
  for all using (auth.uid() = owner_id);

drop policy if exists "Comments readable by signed users" on public.post_comments;
create policy "Comments readable by signed users" on public.post_comments
  for select using (auth.uid() is not null);

drop policy if exists "Users create own comments" on public.post_comments;
create policy "Users create own comments" on public.post_comments
  for insert with check (auth.uid() = owner_id);

drop policy if exists "Active notices readable by signed users" on public.notices;
create policy "Active notices readable by signed users" on public.notices
  for select using (auth.uid() is not null and is_active = true);

drop policy if exists "Users manage own notice dismissals" on public.notice_dismissals;
create policy "Users manage own notice dismissals" on public.notice_dismissals
  for all using (auth.uid() = owner_id);

drop policy if exists "Users manage own support tickets" on public.support_tickets;
create policy "Users manage own support tickets" on public.support_tickets
  for all using (auth.uid() = owner_id);

drop policy if exists "Platform settings readable by signed users" on public.platform_settings;
create policy "Platform settings readable by signed users" on public.platform_settings
  for select using (auth.uid() is not null);
