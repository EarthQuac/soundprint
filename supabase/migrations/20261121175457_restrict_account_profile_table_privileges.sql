-- Keep profile access limited to each authenticated user's own row.
-- The existing RLS policies enforce row ownership; these grants remove
-- table-wide operations that bypass RLS (notably TRUNCATE).
revoke all privileges on table public.account_profiles from public, anon, authenticated;
grant select, update on table public.account_profiles to authenticated;
