create extension if not exists citext with schema extensions;

create function public.set_updated_at() returns trigger
language plpgsql as $$
begin new.updated_at := now(); return new; end $$;

create function public.ping() returns text language sql stable as $$ select 'ok' $$;
grant execute on function public.ping() to anon, authenticated;

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username extensions.citext unique check (username is null or username ~ '^[A-Za-z0-9_]{3,24}$'),
  avatar_url text check (avatar_url is null or length(avatar_url) <= 500),
  settings jsonb not null default '{"schema_version":1}' check (pg_column_size(settings) <= 16384),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.profiles enable row level security;
create policy "own profile" on public.profiles
  for all to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));
create trigger profiles_updated before insert or update on public.profiles
  for each row execute function public.set_updated_at();

create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin insert into public.profiles(id) values (new.id); return new; end $$;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

create table public.artists (
  id uuid primary key default gen_random_uuid(),
  provider text not null check (provider in ('deezer','itunes','apple','youtube')),
  provider_artist_id text not null,
  name text not null,
  artwork_url text,
  metadata jsonb not null default '{}',
  updated_at timestamptz not null default now(),
  unique (provider, provider_artist_id)
);
create table public.albums (
  id uuid primary key default gen_random_uuid(),
  provider text not null check (provider in ('deezer','itunes','apple','youtube')),
  provider_album_id text not null,
  title text not null,
  artist_id uuid references public.artists(id) on delete set null,
  artwork_url text,
  metadata jsonb not null default '{}',
  updated_at timestamptz not null default now(),
  unique (provider, provider_album_id)
);
create table public.songs (
  id uuid primary key default gen_random_uuid(),
  provider text not null check (provider in ('deezer','itunes','apple','youtube')),
  provider_song_id text not null,
  title text not null,
  artist_id uuid references public.artists(id) on delete set null,
  album_id uuid references public.albums(id) on delete set null,
  duration_ms integer not null default 0,
  artwork_url text,
  external_ids jsonb not null default '{}',
  metadata jsonb not null default '{}',
  updated_at timestamptz not null default now(),
  unique (provider, provider_song_id)
);
create trigger artists_updated before insert or update on public.artists for each row execute function public.set_updated_at();
create trigger albums_updated  before insert or update on public.albums  for each row execute function public.set_updated_at();
create trigger songs_updated   before insert or update on public.songs   for each row execute function public.set_updated_at();

alter table public.artists enable row level security;
alter table public.albums  enable row level security;
alter table public.songs   enable row level security;
create policy "read artists" on public.artists for select to authenticated using (true);
create policy "read albums"  on public.albums  for select to authenticated using (true);
create policy "read songs"   on public.songs   for select to authenticated using (true);
revoke all on public.artists, public.albums, public.songs from anon, authenticated;
grant select on public.artists, public.albums, public.songs to authenticated;

create table public.favorites (
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  song_id uuid not null references public.songs(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  primary key (user_id, song_id)
);
create index favorites_sync on public.favorites (user_id, updated_at);

create table public.playlists (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 100),
  description text check (description is null or char_length(description) <= 500),
  artwork_url text,
  tags text not null default '' check (char_length(tags) <= 500),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index playlists_sync on public.playlists (user_id, updated_at);

create table public.playlist_songs (
  playlist_id uuid not null references public.playlists(id) on delete cascade,
  song_id uuid not null references public.songs(id) on delete cascade,
  position integer not null check (position >= 0),
  added_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  primary key (playlist_id, song_id)
);
create index playlist_songs_sync on public.playlist_songs (updated_at);

create table public.listening_history (
  id bigint generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  song_id uuid not null references public.songs(id) on delete cascade,
  played_at timestamptz not null default now(),
  metadata jsonb not null default '{}' check (pg_column_size(metadata) <= 2048),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index history_user_played on public.listening_history (user_id, played_at desc);

create trigger favorites_updated before insert or update on public.favorites for each row execute function public.set_updated_at();
create trigger playlists_updated before insert or update on public.playlists for each row execute function public.set_updated_at();
create trigger playlist_songs_updated before insert or update on public.playlist_songs for each row execute function public.set_updated_at();
create trigger history_updated before insert or update on public.listening_history for each row execute function public.set_updated_at();

alter table public.favorites enable row level security;
alter table public.playlists enable row level security;
alter table public.playlist_songs enable row level security;
alter table public.listening_history enable row level security;

create policy "own favorites" on public.favorites for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own playlists" on public.playlists for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own playlist songs" on public.playlist_songs for all to authenticated
  using (exists (select 1 from public.playlists p where p.id = playlist_id and p.user_id = (select auth.uid())))
  with check (exists (select 1 from public.playlists p where p.id = playlist_id and p.user_id = (select auth.uid())));
create policy "own history" on public.listening_history for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

revoke all on public.profiles, public.favorites, public.playlists, public.playlist_songs, public.listening_history from anon;

create function public.enforce_quota() returns trigger
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
  end if;
  if n >= lim then raise exception 'quota exceeded for %', tg_table_name using errcode = '54000'; end if;
  return new;
end $$;
create trigger playlists_quota before insert on public.playlists for each row execute function public.enforce_quota();
create trigger playlist_songs_quota before insert on public.playlist_songs for each row execute function public.enforce_quota();
create trigger favorites_quota before insert on public.favorites for each row execute function public.enforce_quota();
create trigger history_quota before insert on public.listening_history for each row execute function public.enforce_quota();

create table public.rate_limits (
  user_id uuid not null,
  bucket text not null,
  window_start timestamptz not null,
  count integer not null default 0,
  primary key (user_id, bucket, window_start)
);
alter table public.rate_limits enable row level security;
revoke all on public.rate_limits from anon, authenticated;

create function public.take_rate_limit(p_user uuid, p_bucket text, p_max integer, p_window_seconds integer)
returns boolean language plpgsql security definer set search_path = '' as $$
declare w timestamptz := to_timestamp(floor(extract(epoch from now()) / p_window_seconds) * p_window_seconds); c integer;
begin
  insert into public.rate_limits(user_id, bucket, window_start, count) values (p_user, p_bucket, w, 1)
  on conflict (user_id, bucket, window_start) do update set count = public.rate_limits.count + 1
  returning count into c;
  return c <= p_max;
end $$;
revoke execute on function public.take_rate_limit(uuid, text, integer, integer) from public, anon, authenticated;
grant execute on function public.take_rate_limit(uuid, text, integer, integer) to service_role;

do $$
begin
  create extension if not exists pg_cron;
  perform cron.schedule('gamut-purge', '17 3 * * *', $job$
    delete from public.favorites where deleted_at < now() - interval '90 days';
    delete from public.playlist_songs where deleted_at < now() - interval '90 days';
    delete from public.playlists where deleted_at < now() - interval '90 days';
    delete from public.listening_history where played_at < now() - interval '365 days';
    delete from public.rate_limits where window_start < now() - interval '1 day';
  $job$);
exception when others then
  raise notice 'pg_cron unavailable (%); schedule the purge manually', sqlerrm;
end $$;
