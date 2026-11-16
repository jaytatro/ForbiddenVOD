-- =====================================================================
--  Forbidden VOD control panel — database setup
--  Paste this whole file into Supabase → SQL Editor → New query → Run.
--  Safe to run again: it only creates what is missing.
-- =====================================================================

create extension if not exists pgcrypto;

-- People allowed into the control panel (you, and anyone you add later)
create table if not exists public.admins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

-- Plans: which addons a customer's TV gets, and how many TVs they may use
create table if not exists public.plans (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  addons jsonb not null default '[]'::jsonb,       -- array of manifest URLs
  max_devices int not null default 2,
  created_at timestamptz not null default now()
);

-- Customers / activation codes
create table if not exists public.codes (
  code text primary key,                           -- e.g. FVOD-7K2Q-M9XD
  customer_name text,
  customer_email text,
  notes text,
  plan_id uuid references public.plans(id) on delete set null,
  max_devices int,                                 -- null = use the plan's number
  expires_at timestamptz,                          -- null = never expires
  enabled boolean not null default true,
  created_at timestamptz not null default now()
);

-- TVs that have used a code
create table if not exists public.devices (
  id uuid primary key default gen_random_uuid(),
  code text not null references public.codes(code) on delete cascade on update cascade,
  device_id text not null,
  device_name text,
  app_version text,
  blocked boolean not null default false,
  first_seen timestamptz not null default now(),
  last_seen timestamptz not null default now(),
  unique (code, device_id)
);

-- Messages shown on customers' TVs
create table if not exists public.announcements (
  id uuid primary key default gen_random_uuid(),
  message text not null,
  plan_id uuid references public.plans(id) on delete cascade,  -- null = everyone
  active boolean not null default true,
  created_at timestamptz not null default now()
);

-- One row of app-wide settings
create table if not exists public.app_settings (
  id int primary key default 1 check (id = 1),
  support_text text default 'Contact your provider for help.',
  min_version int default 0,
  update_url text default 'https://github.com/jaytatro/ForbiddenVOD/releases/latest/download/ForbiddenVOD.apk'
);
insert into public.app_settings (id) values (1) on conflict do nothing;

-- ---------------------------------------------------------------------
--  Security: only admins can read or change anything directly.
--  TVs never touch the tables; they call fv_checkin() below.
-- ---------------------------------------------------------------------
create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.admins where user_id = auth.uid());
$$;

alter table public.admins        enable row level security;
alter table public.plans         enable row level security;
alter table public.codes         enable row level security;
alter table public.devices       enable row level security;
alter table public.announcements enable row level security;
alter table public.app_settings  enable row level security;

do $$
declare t text;
begin
  foreach t in array array['plans','codes','devices','announcements','app_settings'] loop
    execute format('drop policy if exists admin_all on public.%I', t);
    execute format('create policy admin_all on public.%I for all to authenticated using (public.is_admin()) with check (public.is_admin())', t);
  end loop;
end $$;
drop policy if exists admin_read on public.admins;
create policy admin_read on public.admins for select to authenticated using (public.is_admin());

revoke all on public.admins, public.plans, public.codes, public.devices, public.announcements, public.app_settings from anon;

-- ---------------------------------------------------------------------
--  fv_checkin: called by the TV app on activation and every few hours.
--  Returns the customer's status, addons and messages.
-- ---------------------------------------------------------------------
create or replace function public.fv_checkin(p_code text, p_device_id text, p_device_name text default null, p_app_version text default null)
returns json
language plpgsql security definer set search_path = public as $$
declare
  c   public.codes;
  p   public.plans;
  d   public.devices;
  lim int;
  used int;
  s   public.app_settings;
begin
  select * into s from public.app_settings where id = 1;
  select * into c from public.codes where code = upper(trim(p_code));
  if not found then
    return json_build_object('ok', false, 'reason', 'invalid', 'support', s.support_text);
  end if;
  if not c.enabled then
    return json_build_object('ok', false, 'reason', 'disabled', 'support', s.support_text);
  end if;
  if c.expires_at is not null and c.expires_at < now() then
    return json_build_object('ok', false, 'reason', 'expired', 'expires_at', c.expires_at, 'support', s.support_text);
  end if;

  select * into p from public.plans where id = c.plan_id;
  lim := coalesce(c.max_devices, p.max_devices, 2);

  select * into d from public.devices where code = c.code and device_id = p_device_id;
  if found then
    if d.blocked then
      return json_build_object('ok', false, 'reason', 'device_blocked', 'support', s.support_text);
    end if;
    update public.devices set last_seen = now(),
      device_name = coalesce(p_device_name, device_name), app_version = coalesce(p_app_version, app_version)
      where id = d.id;
  else
    select count(*) into used from public.devices where code = c.code and not blocked;
    if used >= lim then
      return json_build_object('ok', false, 'reason', 'device_limit', 'limit', lim, 'support', s.support_text);
    end if;
    insert into public.devices (code, device_id, device_name, app_version)
      values (c.code, p_device_id, p_device_name, p_app_version);
  end if;

  return json_build_object(
    'ok', true,
    'code', c.code,
    'customer', c.customer_name,
    'expires_at', c.expires_at,
    'plan', p.name,
    'addons', coalesce(p.addons, '[]'::jsonb),
    'announcements', coalesce((
        select json_agg(json_build_object('id', a.id, 'message', a.message) order by a.created_at desc)
        from public.announcements a
        where a.active and (a.plan_id is null or a.plan_id = c.plan_id)), '[]'::json),
    'support', s.support_text,
    'min_version', s.min_version,
    'update_url', s.update_url
  );
end $$;

revoke all on function public.fv_checkin(text, text, text, text) from public;
grant execute on function public.fv_checkin(text, text, text, text) to anon, authenticated;

-- ---------------------------------------------------------------------
--  LAST STEP — make yourself the admin.
--  1) Supabase → Authentication → Users → Add user (your email + a password)
--  2) Put that email below and run just this line:
--
--  insert into public.admins (user_id) select id from auth.users where email = 'YOUR-EMAIL@example.com' on conflict do nothing;
-- ---------------------------------------------------------------------
