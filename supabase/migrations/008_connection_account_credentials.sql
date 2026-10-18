-- CraftMate migration 008: connection account credentials (encrypted at rest)
alter table public.servers add column if not exists bot_email text;
alter table public.servers add column if not exists bot_password_enc text;
comment on column public.servers.bot_password_enc is 'AES-GCM ciphertext only. Never plaintext. Decrypted solely for the secret-gated runner channel.';
