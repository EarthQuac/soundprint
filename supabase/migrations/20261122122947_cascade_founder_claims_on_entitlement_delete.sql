-- Founder claim rows are owned by both the auth user and the entitlement.
-- Deleting an auth user cascades to their entitlement; the claim must cascade too
-- rather than RESTRICT blocking the parent deletion.
alter table private.pro_founder_claims
  drop constraint pro_founder_claims_entitlement_id_fkey;

alter table private.pro_founder_claims
  add constraint pro_founder_claims_entitlement_id_fkey
  foreign key (entitlement_id)
  references private.pro_entitlements(id)
  on delete cascade;
