-- User profile rows are scoped to the owning auth.users record.
create table public.account_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default 'Listener'
    constraint account_profiles_display_name_check check (char_length(display_name) between 1 and 60),
  avatar_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.account_profiles enable row level security;
create policy "Users can create their own profile"
  on public.account_profiles for insert to authenticated
  with check ((select auth.uid()) = id);
create policy "Users can read their own profile"
  on public.account_profiles for select to authenticated
  using ((select auth.uid()) = id);
create policy "Users can update their own profile"
  on public.account_profiles for update to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);
grant all on table public.account_profiles to authenticated, service_role;
