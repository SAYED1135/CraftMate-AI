-- CraftMate migration 010: panel API key for connection account (encrypted)
alter table public.servers add column if not exists bot_api_key_enc text;
