create or replace function public.consume_password_reset_rate_limit(p_email_hash text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_now timestamptz := clock_timestamp();
    rate_limit_row public.password_reset_rate_limits%rowtype;
begin
    if p_email_hash !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid email hash';
    end if;

    delete from public.password_reset_rate_limits
    where last_requested_at < v_now - interval '30 days';

    insert into public.password_reset_rate_limits (email_hash, window_started_at)
    values (p_email_hash, v_now)
    on conflict (email_hash) do nothing;

    select * into rate_limit_row
    from public.password_reset_rate_limits
    where email_hash = p_email_hash
    for update;

    if rate_limit_row.last_requested_at is not null
       and rate_limit_row.last_requested_at > v_now - interval '60 seconds' then
        return false;
    end if;

    if rate_limit_row.window_started_at <= v_now - interval '24 hours' then
        update public.password_reset_rate_limits
        set window_started_at = v_now,
            last_requested_at = v_now,
            request_count = 1
        where email_hash = p_email_hash;
        return true;
    end if;

    if rate_limit_row.request_count >= 5 then
        return false;
    end if;

    update public.password_reset_rate_limits
    set last_requested_at = v_now,
        request_count = request_count + 1
    where email_hash = p_email_hash;

    return true;
end;
$$;