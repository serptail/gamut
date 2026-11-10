create or replace function public.enforce_quota() returns trigger
language plpgsql security definer set search_path = '' as $$
declare n integer; lim integer;
begin
  if tg_table_name = 'playlists' then
    if exists (select 1 from public.playlists where id = new.id and deleted_at is null) then return new; end if;
    select count(*) into n from public.playlists where user_id = new.user_id and deleted_at is null; lim := 200;
  elsif tg_table_name = 'playlist_songs' then
    if exists (select 1 from public.playlist_songs where playlist_id = new.playlist_id and song_id = new.song_id and deleted_at is null) then return new; end if;
    select count(*) into n from public.playlist_songs where playlist_id = new.playlist_id and deleted_at is null; lim := 5000;
  elsif tg_table_name = 'favorites' then
    if exists (select 1 from public.favorites where user_id = new.user_id and song_id = new.song_id and deleted_at is null) then return new; end if;
    select count(*) into n from public.favorites where user_id = new.user_id and deleted_at is null; lim := 20000;
  elsif tg_table_name = 'listening_history' then
    select count(*) into n from public.listening_history where user_id = new.user_id and played_at > now() - interval '1 day'; lim := 2000;
  elsif tg_table_name = 'artist_follows' then
    if exists (select 1 from public.artist_follows where user_id = new.user_id and provider = new.provider
               and provider_artist_id = new.provider_artist_id and deleted_at is null) then return new; end if;
    select count(*) into n from public.artist_follows where user_id = new.user_id and deleted_at is null; lim := 5000;
  end if;
  if n >= lim then raise exception 'quota exceeded for %', tg_table_name using errcode = '54000'; end if;
  return new;
end $$;
