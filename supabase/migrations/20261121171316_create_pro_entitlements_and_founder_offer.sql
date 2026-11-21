create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
create table if not exists private.pro_entitlements (
 id uuid primary key default gen_random_uuid(),
 user_id uuid not null references auth.users(id) on delete cascade,
 source text not null check (source in ('founder','trial','lifetime','monthly','annual','admin')),
 starts_at timestamptz not null default now(), ends_at timestamptz, revoked_at timestamptz,
 created_at timestamptz not null default now(), metadata jsonb not null default '{}'::jsonb,
 constraint pro_entitlements_valid_period check (ends_at is null or ends_at > starts_at)
);
create index if not exists pro_entitlements_user_active_idx on private.pro_entitlements(user_id, starts_at desc) where revoked_at is null;
create unique index if not exists pro_entitlements_one_founder_per_user on private.pro_entitlements(user_id) where source='founder' and revoked_at is null;
create table if not exists private.pro_founder_offer (
 singleton boolean primary key default true check (singleton),
 capacity integer not null default 100 check (capacity between 1 and 10000),
 enabled boolean not null default true, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
insert into private.pro_founder_offer(singleton,capacity,enabled) values(true,100,true) on conflict(singleton) do nothing;
create table if not exists private.pro_founder_claims (
 id bigint generated always as identity primary key,
 user_id uuid not null unique references auth.users(id) on delete cascade,
 entitlement_id uuid not null unique references private.pro_entitlements(id) on delete restrict,
 claimed_at timestamptz not null default now()
);
create index if not exists pro_founder_claims_claimed_at_idx on private.pro_founder_claims(claimed_at);
alter table private.pro_entitlements enable row level security;
alter table private.pro_founder_offer enable row level security;
alter table private.pro_founder_claims enable row level security;
revoke all on all tables in schema private from public, anon, authenticated;
revoke all on all sequences in schema private from public, anon, authenticated;

create or replace function public.claim_soundprint_founder_access(p_user_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
 v_offer private.pro_founder_offer%rowtype;
 v_entitlement private.pro_entitlements%rowtype;
 v_confirmed_at timestamptz;
 v_claim_count bigint;
begin
 if p_user_id is null then raise exception 'user_id_required' using errcode='22023'; end if;
 select u.email_confirmed_at into v_confirmed_at from auth.users u where u.id=p_user_id;
 if not found then raise exception 'account_not_found' using errcode='P0002'; end if;
 if v_confirmed_at is null then raise exception 'verified_email_required' using errcode='42501'; end if;
 select * into v_entitlement from private.pro_entitlements e
  where e.user_id=p_user_id and e.revoked_at is null and (e.ends_at is null or e.ends_at>pg_catalog.now())
  order by e.starts_at desc limit 1;
 if found then return pg_catalog.jsonb_build_object('claimed',v_entitlement.source='founder','already_entitled',true,'source',v_entitlement.source,'ends_at',v_entitlement.ends_at); end if;
 select * into v_offer from private.pro_founder_offer where singleton=true for update;
 if not found or not v_offer.enabled then return pg_catalog.jsonb_build_object('claimed',false,'reason','offer_unavailable'); end if;
 select count(*) into v_claim_count from private.pro_founder_claims;
 if v_claim_count>=v_offer.capacity then return pg_catalog.jsonb_build_object('claimed',false,'reason','sold_out','capacity',v_offer.capacity); end if;
 insert into private.pro_entitlements(user_id,source,metadata)
  values(p_user_id,'founder',pg_catalog.jsonb_build_object('offer','first_100_founders')) returning * into v_entitlement;
 insert into private.pro_founder_claims(user_id,entitlement_id) values(p_user_id,v_entitlement.id);
 return pg_catalog.jsonb_build_object('claimed',true,'already_entitled',false,'source','founder','claim_number',v_claim_count+1,'capacity',v_offer.capacity,'ends_at',null);
exception when unique_violation then
 select * into v_entitlement from private.pro_entitlements e where e.user_id=p_user_id and e.source='founder' and e.revoked_at is null order by e.starts_at desc limit 1;
 if found then return pg_catalog.jsonb_build_object('claimed',true,'already_entitled',true,'source','founder','ends_at',null); end if;
 raise;
end; $$;
revoke all on function public.claim_soundprint_founder_access(uuid) from public,anon,authenticated;
grant execute on function public.claim_soundprint_founder_access(uuid) to service_role;
