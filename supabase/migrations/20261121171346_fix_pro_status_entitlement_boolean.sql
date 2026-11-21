create or replace function public.get_soundprint_pro_status(p_user_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
 v_entitlement private.pro_entitlements%rowtype; v_capacity integer; v_enabled boolean;
 v_claimed bigint; v_is_founder boolean; v_has_pro boolean:=false;
begin
 if p_user_id is null then raise exception 'user_id_required' using errcode='22023'; end if;
 select * into v_entitlement from private.pro_entitlements e
  where e.user_id=p_user_id and e.revoked_at is null and e.starts_at<=pg_catalog.now()
   and (e.ends_at is null or e.ends_at>pg_catalog.now()) order by e.starts_at desc limit 1;
 v_has_pro:=found;
 select capacity,enabled into v_capacity,v_enabled from private.pro_founder_offer where singleton=true;
 select count(*) into v_claimed from private.pro_founder_claims;
 select exists(select 1 from private.pro_founder_claims c where c.user_id=p_user_id) into v_is_founder;
 return pg_catalog.jsonb_build_object('has_pro',v_has_pro,
  'source',case when v_has_pro then v_entitlement.source else null end,
  'expires_at',case when v_has_pro then v_entitlement.ends_at else null end,
  'is_founder',v_is_founder,'founder_offer_enabled',coalesce(v_enabled,false),
  'founder_capacity',coalesce(v_capacity,100),'founder_claimed',v_claimed,
  'founder_remaining',greatest(coalesce(v_capacity,100)-v_claimed,0));
end; $$;
