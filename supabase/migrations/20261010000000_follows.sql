create table public.artist_follows (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  provider text not null check (provider in ('deezer','itunes','apple','youtube')),
  provider_artist_id text not null check (provider_artist_id ~ '^[A-Za-z0-9_-]{1,32}$'),
  name text not null check (char_length(name) between 1 and 200),
  artwork_url text check (artwork_url is null or (char_length(artwork_url) <= 500 and artwork_url like 'https://%')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  primary key (user_id, provider, provider_artist_id)
);
create index artist_follows_sync on public.artist_follows (user_id, updated_at);
create trigger artist_follows_updated before insert or update on public.artist_follows
  for each row execute function public.set_updated_at();

alter table public.artist_follows enable row level security;
create policy "own follows" on public.artist_follows for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
revoke all on public.artist_follows from anon;
grant select, insert, update, delete on public.artist_follows to authenticated;

create or replace function public.enforce_quota() returns trigger
language plpgsql security definer set search_path = '' as $$
declare n integer; lim integer;
begin
  if tg_table_name = 'playlists' then
    select count(*) into n from public.playlists where user_id = new.user_id and deleted_at is null; lim := 200;
  elsif tg_table_name = 'playlist_songs' then
    select count(*) into n from public.playlist_songs where playlist_id = new.playlist_id and deleted_at is null; lim := 5000;
  elsif tg_table_name = 'favorites' then
    select count(*) into n from public.favorites where user_id = new.user_id and deleted_at is null; lim := 20000;
  elsif tg_table_name = 'listening_history' then
    select count(*) into n from public.listening_history where user_id = new.user_id and played_at > now() - interval '1 day'; lim := 2000;
  elsif tg_table_name = 'artist_follows' then
    select count(*) into n from public.artist_follows where user_id = new.user_id and deleted_at is null; lim := 5000;
  end if;
  if n >= lim then raise exception 'quota exceeded for %', tg_table_name using errcode = '54000'; end if;
  return new;
end $$;
create trigger artist_follows_quota before insert on public.artist_follows
  for each row execute function public.enforce_quota();

do $$
begin
  perform cron.schedule('gamut-purge-follows', '23 3 * * *',
    $job$delete from public.artist_follows where deleted_at < now() - interval '90 days';$job$);
exception when others then
  raise notice 'pg_cron unavailable (%); schedule the follows purge manually', sqlerrm;
end $$;
