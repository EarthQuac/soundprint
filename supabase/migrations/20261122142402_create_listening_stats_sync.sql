create table if not exists public.listening_history (
    id uuid primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    track_id text not null,
    event_type text not null check (event_type in ('PLAY','SKIP','LISTEN')),
    "timestamp" bigint not null,
    position_ms bigint not null default 0 check (position_ms >= 0),
    duration_ms bigint not null default 0 check (duration_ms >= 0),
    created_at timestamptz not null default now()
);
create index if not exists listening_history_user_timestamp_idx on public.listening_history(user_id,"timestamp" desc);
alter table public.listening_history enable row level security;
drop policy if exists "Users can read their listening history" on public.listening_history;
create policy "Users can read their listening history" on public.listening_history for select to authenticated using ((select auth.uid())=user_id);
drop policy if exists "Users can add their listening history" on public.listening_history;
create policy "Users can add their listening history" on public.listening_history for insert to authenticated with check ((select auth.uid())=user_id);
drop policy if exists "Users can update their listening history" on public.listening_history;
create policy "Users can update their listening history" on public.listening_history for update to authenticated using ((select auth.uid())=user_id) with check ((select auth.uid())=user_id);
drop policy if exists "Users can delete their listening history" on public.listening_history;
create policy "Users can delete their listening history" on public.listening_history for delete to authenticated using ((select auth.uid())=user_id);
grant select,insert,update,delete on public.listening_history to authenticated;
revoke all on public.listening_history from anon;
