insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 204800, array['image/webp'])
on conflict (id) do update set public = true, file_size_limit = 204800, allowed_mime_types = array['image/webp'];

create policy "avatar select own" on storage.objects for select to authenticated
  using (bucket_id = 'avatars' and name = (select auth.uid())::text || '.webp');
create policy "avatar insert own" on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and name = (select auth.uid())::text || '.webp');
create policy "avatar update own" on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and name = (select auth.uid())::text || '.webp')
  with check (bucket_id = 'avatars' and name = (select auth.uid())::text || '.webp');
create policy "avatar delete own" on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and name = (select auth.uid())::text || '.webp');
