begin;
select plan(7);

insert into auth.users (id) values
  ('00000000-0000-0000-0000-00000000000a'), ('00000000-0000-0000-0000-00000000000b');
insert into public.songs (id, provider, provider_song_id, title)
  values ('11111111-1111-1111-1111-111111111111', 'deezer', '1', 'song');
select is((select count(*)::int from public.profiles), 2, 'trigger created profiles');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
insert into public.playlists (id, name) values ('22222222-2222-2222-2222-222222222222', 'mine');
insert into public.playlist_songs (playlist_id, song_id, position)
  values ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 0);
insert into public.favorites (song_id) values ('11111111-1111-1111-1111-111111111111');
select is((select count(*)::int from public.favorites), 1, 'A sees own favorite');
select throws_ok($$insert into public.songs (provider, provider_song_id, title) values ('deezer','2','x')$$,
  '42501', null, 'clients cannot write the shared cache');

select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
select is((select count(*)::int from public.playlists), 0, 'B cannot read A playlists');
select is((select count(*)::int from public.playlist_songs), 0, 'B cannot read A playlist songs');
select is((select count(*)::int from public.favorites), 0, 'B cannot read A favorites');
select throws_ok($$insert into public.playlist_songs (playlist_id, song_id, position)
  values ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 1)$$,
  '42501', null, 'B cannot add songs to A playlist');

select * from finish();
rollback;
