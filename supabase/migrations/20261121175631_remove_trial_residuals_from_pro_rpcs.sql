-- Remove the final references to trial-only schema from Pro RPCs.
create or replace function public.get_soundprint_pro_status(p_user_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_entitlement private.pro_entitlements%rowtype;
  v_capacity integer;
  v_enabled boolean;
  v_claimed bigint;
  v_is_founder boolean;
  v_has_pro boolean := false;
begin
  if p_user_id is null then raise exception 'user_id_required' using errcode = '22023'; end if;
  select * into v_entitlement from private.pro_entitlements e
  where e.user_id = p_user_id and e.revoked_at is null
    and e.starts_at <= pg_catalog.now()
    and (e.ends_at is null or e.ends_at > pg_catalog.now())
  order by e.starts_at desc limit 1;
  v_has_pro := found;
  select capacity, enabled into v_capacity, v_enabled from private.pro_founder_offer where singleton = true;
  select count(*) into v_claimed from private.pro_founder_claims;
  select exists(select 1 from private.pro_founder_claims c where c.user_id = p_user_id) into v_is_founder;
  return pg_catalog.jsonb_build_object(
    'has_pro', v_has_pro,
    'source', case when v_has_pro then v_entitlement.source else null end,
    'expires_at', case when v_has_pro then v_entitlement.ends_at else null end,
    'is_founder', v_is_founder,
    'founder_offer_enabled', coalesce(v_enabled, false),
    'founder_capacity', coalesce(v_capacity, 100),
    'founder_claimed', v_claimed,
    'founder_remaining', greatest(coalesce(v_capacity, 100) - v_claimed, 0)
  );
end;
$$;

create or replace function public.claim_soundprint_founder_access(p_user_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_offer private.pro_founder_offer%rowtype;
  v_entitlement private.pro_entitlements%rowtype;
  v_confirmed_at timestamptz;
  v_claim_count bigint;
begin
  if p_user_id is null then raise exception 'user_id_required' using errcode = '22023'; end if;
  select u.email_confirmed_at into v_confirmed_at from auth.users u where u.id = p_user_id;
  if not found then raise exception 'account_not_found' using errcode = 'P0002'; end if;
  if v_confirmed_at is null then raise exception 'verified_email_required' using errcode = '42501'; end if;
  select * into v_entitlement from private.pro_entitlements e
  where e.user_id = p_user_id and e.revoked_at is null
    and e.starts_at <= pg_catalog.now()
    and (e.ends_at is null or e.ends_at > pg_catalog.now())
  order by e.starts_at desc limit 1;
  if found then
    return pg_catalog.jsonb_build_object(
      'claimed', v_entitlement.source = 'founder',
      'already_entitled', true,
      'source', v_entitlement.source,
      'ends_at', v_entitlement.ends_at
    );
  end if;
  select * into v_offer from private.pro_founder_offer where singleton = true for update;
  if not found or not v_offer.enabled then return pg_catalog.jsonb_build_object('claimed', false, 'reason', 'offer_unavailable'); end if;
  select count(*) into v_claim_count from private.pro_founder_claims;
  if v_claim_count >= v_offer.capacity then
    return pg_catalog.jsonb_build_object('claimed', false, 'reason', 'sold_out', 'capacity', v_offer.capacity);
  end if;
  insert into private.pro_entitlements(user_id, source, metadata)
  values (p_user_id, 'founder', pg_catalog.jsonb_build_object('offer', 'first_100_founders'))
  returning * into v_entitlement;
  insert into private.pro_founder_claims(user_id, entitlement_id) values (p_user_id, v_entitlement.id);
  return pg_catalog.jsonb_build_object(
    'claimed', true, 'already_entitled', false, 'source', 'founder',
    'claim_number', v_claim_count + 1, 'capacity', v_offer.capacity, 'ends_at', null
  );
exception when unique_violation then
  select * into v_entitlement from private.pro_entitlements e
  where e.user_id = p_user_id and e.source = 'founder' and e.revoked_at is null
  order by e.starts_at desc limit 1;
  if found then return pg_catalog.jsonb_build_object('claimed', true, 'already_entitled', true, 'source', 'founder', 'ends_at', null); end if;
  raise;
end;
$$;
revoke all on function public.get_soundprint_pro_status(uuid) from public, anon, authenticated;
grant execute on function public.get_soundprint_pro_status(uuid) to service_role;
revoke all on function public.claim_soundprint_founder_access(uuid) from public, anon, authenticated;
grant execute on function public.claim_soundprint_founder_access(uuid) to service_role;
