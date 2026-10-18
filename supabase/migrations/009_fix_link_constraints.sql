-- CraftMate migration 009: allow panel/bot linking values in legacy checks.
-- Fixes HTTP 400 (check_violation) when saving bot-linked servers and when
-- the Worker writes the newer verification states.
do $$
declare c text;
begin
  for c in
    select conname from pg_constraint
    where conrelid = 'public.servers'::regclass and contype = 'c'
      and pg_get_constraintdef(oid) like '%link_method%'
  loop
    execute 'alter table public.servers drop constraint ' || quote_ident(c);
  end loop;
  for c in
    select conname from pg_constraint
    where conrelid = 'public.servers'::regclass and contype = 'c'
      and pg_get_constraintdef(oid) like '%bot_verify_status%'
  loop
    execute 'alter table public.servers drop constraint ' || quote_ident(c);
  end loop;
end $$;

alter table public.servers
  add constraint servers_link_method_check
  check (link_method in ('plugin', 'manual', 'later', 'bot', 'panel'));

alter table public.servers
  add constraint servers_bot_verify_status_check
  check (bot_verify_status in ('none', 'waiting_server', 'check_requested', 'checking', 'requested', 'verifying', 'verified', 'failed'));
