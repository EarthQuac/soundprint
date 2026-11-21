-- Create and lock down account profile maintenance triggers.
create or replace function public.set_account_profile_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.updated_at = pg_catalog.now();
  return new;
end;
$$;
revoke all on function public.set_account_profile_updated_at() from public, anon, authenticated;
grant execute on function public.set_account_profile_updated_at() to service_role;
create trigger account_profiles_updated_at
before update on public.account_profiles
for each row execute function public.set_account_profile_updated_at();

create or replace function public.handle_new_soundprint_user()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  requested_name text;
begin
  requested_name := coalesce(
    nullif(btrim(new.raw_user_meta_data ->> 'display_name'), ''),
    nullif(btrim(new.raw_user_meta_data ->> 'full_name'), ''),
    nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
    'Listener'
  );
  insert into public.account_profiles (id, display_name)
  values (new.id, left(requested_name, 60))
  on conflict (id) do nothing;
  return new;
end;
$$;
revoke all on function public.handle_new_soundprint_user() from public, anon, authenticated;
grant execute on function public.handle_new_soundprint_user() to service_role;
create trigger on_auth_user_created_soundprint
after insert on auth.users
for each row execute function public.handle_new_soundprint_user();
