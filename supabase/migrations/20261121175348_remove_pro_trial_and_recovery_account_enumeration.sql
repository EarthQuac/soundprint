-- Remove the one-time Pro trial path and close the public account-existence oracle.
-- No trial entitlements or trial claims existed at migration time.
drop function if exists public.start_soundprint_pro_trial(uuid);
drop table if exists private.pro_trial_claims;

delete from private.pro_entitlements where source = 'trial';
alter table private.pro_entitlements
  drop constraint if exists pro_entitlements_source_check;
alter table private.pro_entitlements
  add constraint pro_entitlements_source_check
  check (source in ('founder','lifetime','monthly','annual','admin'));

-- Recovery account existence is now handled internally by Supabase Auth.
-- The public SECURITY DEFINER RPC exposed an email-enumeration oracle and
-- persisted submitted email addresses unnecessarily.
drop function if exists public.soundprint_account_exists_for_recovery(text);
drop table if exists public.account_recovery_lookup_logs;
