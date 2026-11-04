# Build your own UniTask: database, sign-in and APK

This guide takes you from zero to your own copy of UniTask running on your phone, with **your own** database. You do not need to be an Android developer. Every step says exactly what to click or paste.

**Last checked:** 1 November 2026, against UniTask v3.0.0. Dashboards change names now and then. If a button is not where this guide says, look for the same words nearby.

## What you will end up with

| Piece | What it is | Who owns it |
| --- | --- | --- |
| Supabase project | The database and login system behind groups, chat and cloud backup | You |
| Google sign-in | The "Sign in with Google" button in the app | You |
| Signing key | A file that proves your APK builds come from you | You |
| The APK | The installable app file | You |

**Do you even need this?** If you only want to use UniTask, download the APK from the [Releases page](https://github.com/ahmad-maqsood/unitask/releases/latest) and skip this guide. Everything except groups, chat and cloud backup works offline with no account. This guide is for people who want to run their own copy, change the code, or learn how it fits together.

**Time needed (rough estimate for a first try):** about 1.5 to 2 hours. Most of it is waiting for pages to load and for Google settings to take effect.

## The big picture

```
Your phone (UniTask app)
   |-- signs in with --> Google  --> gives the app an "ID token"
   |-- sends token to --> Supabase (your project)
                            |-- checks the token (Authentication)
                            |-- stores data in tables (Database)
                            |-- decides who sees which rows (Row Level Security)
```

Words you will meet:

- **Supabase**: a free service that gives you a Postgres database plus a login system.
- **SQL**: the language used to create database tables. You will paste ready-made SQL, so you do not have to write any.
- **Row Level Security (RLS)**: rules inside the database that say "a user can only see their own tasks" and "only group members can read group chat". This is what keeps the app safe even though the public key is inside the APK.
- **SHA-1 fingerprint**: a short code that identifies your signing key. Google uses it to check that a sign-in request really comes from your app.
- **Keystore (.jks file)**: the file that holds your signing key. Lose it and you can never update an installed app. Back it up.

## Before you start

You need:

1. A **Google account** (for Google Cloud and for testing sign-in).
2. A free **[Supabase](https://supabase.com/)** account.
3. A free **[GitHub](https://github.com/)** account.
4. An **Android phone** (Android 7.0 or newer) to test on.
5. A computer. For the easiest route (building on GitHub) you need nothing installed except **Java** to create the signing key (Step 4). For building on your own computer you also need **Android Studio**.

Keep a note file open. You will collect these values as you go:

```
Supabase project URL:          https://xxxxxxxx.supabase.co
Supabase publishable key:      sb_publishable_...
Google Web client ID:          ....apps.googleusercontent.com
Google Web client secret:      GOCSPX-...
Your app id (package name):    com.yourname.unitask
SHA-1 (release key):           AA:BB:CC:...
Keystore password:             (choose one)
```

> **Safety rule:** never paste a Supabase **secret** key (`sb_secret_...`) or a `service_role` key into the app or into GitHub code. Only the **publishable** (or legacy `anon`) key goes in the app. It is designed to be public because the RLS rules protect the data.

---

## Step 1: Get the code

1. Open [github.com/ahmad-maqsood/unitask](https://github.com/ahmad-maqsood/unitask).
2. Click **Fork** (top right), then **Create fork**. You now have your own copy under your account.

To edit files on your computer too, install [Git](https://git-scm.com/downloads) and run:

```bash
git clone https://github.com/YOUR-USERNAME/unitask.git
cd unitask
```

(Replace `YOUR-USERNAME`.) You can also edit files directly on GitHub with the pencil icon, which is fine for the small edits in Step 5.

---

## Step 2: Create your Supabase project

1. Go to [supabase.com](https://supabase.com/) and sign in.
2. Click **New project**.
3. Fill in:
   - **Name**: anything, for example `unitask`.
   - **Database password**: click **Generate a password** and save it in your note file. You will rarely need it, but you cannot read it again later.
   - **Region**: pick the one closest to your users.
   - **Plan**: Free.
4. Click **Create new project** and wait about 2 minutes until the dashboard finishes loading.

### Copy the project URL and key

1. At the top of the dashboard click **Connect**.
2. Copy the **Project URL** (looks like `https://abcdefgh.supabase.co`).
3. Copy the **Publishable key** (starts with `sb_publishable_`). If you cannot find it there, open **Settings, API Keys**.

Put both in your note file.

> **About the old "anon" key:** older tutorials, and the original UniTask code, use a long key starting with `eyJ...` called the `anon` key. Supabase is retiring it by the end of 2026 and replacing it with the publishable key. Supabase's own Kotlin quickstart now uses the publishable key, so use that. In the app code the setting is still *named* `SUPABASE_ANON_KEY`. Just paste your publishable key as its value.

**If something goes wrong:**

| Problem | Fix |
| --- | --- |
| The project is stuck on "Setting up" for more than 5 minutes | Refresh the page. If it is still stuck, delete the project and create a new one. |
| You cannot find the publishable key | Open **Settings, API Keys** and look at the **Publishable and secret API keys** tab. |
| Only the legacy `anon` key is shown | That also works. Paste it instead. |

---

## Step 3: Build the database

You will paste **8 blocks of SQL** into the Supabase SQL Editor, one at a time, **in order**. Each block is a different layer, and later blocks depend on earlier ones.

### How to run a block

1. In Supabase, click **SQL Editor** in the left menu.
2. Click **New query**.
3. Copy a block below (use the copy button on the code box) and paste it into the editor.
4. Make sure **no text is highlighted** in the editor, because Supabase runs only the highlighted part if something is selected.
5. Click **Run** (or press `Ctrl+Enter`).
6. You should see **Success. No rows returned**. If you see red text, go to the troubleshooting list after Block 8.

Do the blocks in this order. Do not skip any unless it says optional.

### Block 1 of 8: tables

This creates the 10 tables that hold your data (tasks, courses, groups, chat messages and so on) and the indexes that keep them fast.

```sql
-- BLOCK 1 of 8: tables
create table public.announcements (
  id          bigint generated by default as identity primary key,
  title       text not null,
  body        text not null,
  user_id     uuid,                                  -- empty = shown to everyone
  created_at  timestamptz not null default now(),
  expires_at  timestamptz,                           -- empty = never expires
  enabled     boolean not null default true
);

create table public.courses (
  user_id     uuid not null default auth.uid() references auth.users(id) on delete cascade,
  doc_id      text not null,
  code        text not null check (char_length(code) between 1 and 30),
  name        text not null check (char_length(name) between 1 and 100),
  instructor  text not null default '',
  color_hex   text not null default '',
  location    text not null default '',
  updated_at  timestamptz not null default now(),
  primary key (user_id, doc_id)
);

create table public.tasks (
  user_id               uuid not null default auth.uid() references auth.users(id) on delete cascade,
  doc_id                text not null,
  course_code           text not null default '',
  title                 text not null check (char_length(title) between 1 and 200),
  description           text not null default '',
  due_date_epoch_millis bigint not null,
  due_time_hour         int not null default 0 check (due_time_hour between 0 and 23),
  due_time_minute       int not null default 0 check (due_time_minute between 0 and 59),
  priority              text not null check (priority in ('LOW','MEDIUM','HIGH')),
  status                text not null check (status in ('PENDING','COMPLETED')),
  reminder_option       text not null default 'NONE',
  updated_at            timestamptz not null default now(),
  repeat_rule           text not null default 'NONE',
  completed_at_millis   bigint not null default 0,
  repeat_anchor_day     int not null default 0 check (repeat_anchor_day between 0 and 31),
  primary key (user_id, doc_id)
);

create table public.slots (
  user_id      uuid not null default auth.uid() references auth.users(id) on delete cascade,
  doc_id       text not null,
  course_code  text not null check (char_length(course_code) between 1 and 30),
  day_of_week  int  not null check (day_of_week between 1 and 7),
  start_hour   int  not null check (start_hour between 0 and 23),
  start_minute int  not null check (start_minute between 0 and 59),
  end_hour     int  not null check (end_hour between 0 and 23),
  end_minute   int  not null check (end_minute between 0 and 59),
  classroom    text not null default '',
  instructor   text not null default '',
  updated_at   timestamptz not null default now(),
  primary key (user_id, doc_id)
);

create table public.groups (
  id         uuid primary key default gen_random_uuid(),
  username   text not null unique
             check (username = lower(username) and char_length(username) between 3 and 30),
  name       text not null check (char_length(name) between 1 and 60),
  owner_id   uuid not null references auth.users(id) on delete cascade,
  join_code  text not null,
  created_at timestamptz not null default now()
);

create table public.group_members (
  group_id     uuid not null references public.groups(id) on delete cascade,
  user_id      uuid not null references auth.users(id) on delete cascade,
  display_name text not null default 'Student',
  role         text not null default 'member' check (role in ('owner','admin','member')),
  joined_at    timestamptz not null default now(),
  primary key (group_id, user_id)
);

create table public.group_tasks (
  id                    uuid primary key default gen_random_uuid(),
  group_id              uuid not null references public.groups(id) on delete cascade,
  title                 text not null check (char_length(title) between 1 and 200),
  description           text,
  course_label          text,
  due_date_epoch_millis bigint not null,
  due_time_hour         int not null default 0,
  due_time_minute       int not null default 0,
  priority              text not null default 'MEDIUM',
  reminder_options      text,
  assigned_to           uuid[] not null default '{}',
  created_by            uuid not null default auth.uid() references auth.users(id),
  created_by_name       text not null default 'Student',
  created_at            timestamptz not null default now(),
  updated_at            timestamptz not null default now()
);

create table public.group_courses (
  id          uuid primary key default gen_random_uuid(),
  group_id    uuid not null references public.groups(id) on delete cascade,
  code        text not null,
  name        text not null,
  instructor  text not null default '',
  color_hex   text not null default '',
  created_by  uuid not null default auth.uid() references auth.users(id),
  created_at  timestamptz not null default now()
);

create table public.group_done (
  user_id   uuid not null default auth.uid() references auth.users(id) on delete cascade,
  group_id  uuid not null references public.groups(id) on delete cascade,
  task_id   uuid not null references public.group_tasks(id) on delete cascade,
  done_at   timestamptz not null default now(),
  primary key (user_id, task_id)
);

create table public.group_messages (
  id             bigint generated by default as identity primary key,
  group_id       uuid not null references public.groups(id) on delete cascade,
  sender_id      uuid not null default auth.uid() references auth.users(id),
  sender_name    text not null default 'Student',
  text           text not null check (char_length(text) between 1 and 500),
  created_at     timestamptz not null default now(),
  reply_to_id    bigint,
  reply_to_name  text,
  reply_to_text  text
);

-- indexes
create unique index group_courses_unique_code
  on public.group_courses (group_id, upper(regexp_replace(code, '[^A-Za-z0-9]', '', 'g')));
create index group_courses_group_id_idx          on public.group_courses (group_id);
create index group_done_user_id_group_id_idx     on public.group_done (user_id, group_id);
create index group_members_user_id_idx           on public.group_members (user_id);
create index group_messages_group_id_created_at_idx on public.group_messages (group_id, created_at desc);
create index group_tasks_group_id_idx            on public.group_tasks (group_id);
```

### Block 2 of 8: helper functions

Small functions that the security rules in Block 3 call, for example "is this person a member of this group?". They must exist before the rules do.

```sql
-- BLOCK 2 of 8: small helper functions that the security rules use
create or replace function public.is_member(gid uuid)
returns boolean
language sql stable security definer
set search_path to 'public'
as $$
  select exists (select 1 from group_members where group_id = gid and user_id = auth.uid());
$$;

create or replace function public.is_group_admin(gid uuid)
returns boolean
language sql stable security definer
set search_path to 'public'
as $$
  select exists (select 1 from group_members
                 where group_id = gid and user_id = auth.uid() and role in ('owner','admin'));
$$;

create or replace function public.is_group_owner(gid uuid)
returns boolean
language sql stable security definer
set search_path to 'public'
as $$
  select exists (select 1 from group_members
                 where group_id = gid and user_id = auth.uid() and role = 'owner');
$$;

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin new.updated_at = now(); return new; end $$;
```

### Block 3 of 8: security rules (Row Level Security)

This switches on RLS for every table and adds the rules. **Never skip this block.** Without it, anyone who has your public key could read everything.

```sql
-- BLOCK 3 of 8: row level security (who may see and change which rows)
alter table public.announcements  enable row level security;
alter table public.courses        enable row level security;
alter table public.group_courses  enable row level security;
alter table public.group_done     enable row level security;
alter table public.group_members  enable row level security;
alter table public.group_messages enable row level security;
alter table public.group_tasks    enable row level security;
alter table public.groups         enable row level security;
alter table public.slots          enable row level security;
alter table public.tasks          enable row level security;

-- public read: announcements
create policy "read my announcements" on public.announcements for select to anon, authenticated
  using ((enabled = true) and ((expires_at is null or expires_at > now())) and ((user_id is null) or (user_id = auth.uid())));

-- personal data: only the owner
create policy own_courses on public.courses for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy own_tasks on public.tasks for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy own_slots on public.slots for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy own_group_done on public.group_done for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- group courses
create policy gcourses_read on public.group_courses for select to authenticated
  using (is_member(group_id));
create policy gcourses_insert on public.group_courses for insert to authenticated
  with check (is_member(group_id) and created_by = auth.uid());
create policy gcourses_update on public.group_courses for update to authenticated
  using (created_by = auth.uid() or is_group_admin(group_id))
  with check (created_by = auth.uid() or is_group_admin(group_id));
create policy gcourses_delete on public.group_courses for delete to authenticated
  using (created_by = auth.uid() or is_group_admin(group_id));

-- group tasks
create policy gtasks_read on public.group_tasks for select to authenticated
  using (is_member(group_id));
create policy gtasks_insert on public.group_tasks for insert to authenticated
  with check (is_member(group_id) and created_by = auth.uid());
create policy gtasks_update on public.group_tasks for update to authenticated
  using (created_by = auth.uid() or is_group_admin(group_id))
  with check (created_by = auth.uid() or is_group_admin(group_id));
create policy gtasks_delete on public.group_tasks for delete to authenticated
  using (created_by = auth.uid() or is_group_admin(group_id));

-- group chat
create policy gmsg_read on public.group_messages for select to authenticated
  using (is_member(group_id));
create policy gmsg_insert on public.group_messages for insert to authenticated
  with check (is_member(group_id) and sender_id = auth.uid());
create policy gmsg_delete on public.group_messages for delete to authenticated
  using (sender_id = auth.uid() or is_group_admin(group_id));

-- group members (no insert rule: people join only through join_group())
create policy members_read on public.group_members for select to authenticated
  using (is_member(group_id));
create policy members_role on public.group_members for update to authenticated
  using (is_group_owner(group_id)) with check (is_group_owner(group_id));
create policy members_delete on public.group_members for delete to authenticated
  using (user_id = auth.uid() or (is_group_admin(group_id) and role <> 'owner'));

-- groups (no insert rule: groups are made only through create_group())
create policy groups_read on public.groups for select to authenticated
  using (is_member(id));
create policy groups_update on public.groups for update to authenticated
  using (is_group_owner(id)) with check (is_group_owner(id));
create policy groups_delete on public.groups for delete to authenticated
  using (is_group_owner(id));
```

### Block 4 of 8: the app's functions and triggers

Functions the app calls to create or join a group, change group ownership, and delete an account. The triggers keep the "last updated" time correct and stop people from pretending to be someone else in chat.

```sql
-- BLOCK 4 of 8: the app's own functions and automatic triggers
create or replace function public.create_group(p_username text, p_name text, p_display_name text)
returns table(group_id uuid, join_code text)
language plpgsql security definer
set search_path to 'public'
as $$
declare
  gid uuid := gen_random_uuid();
  code text := upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8));
  uname text := lower(trim(p_username));
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  if exists (select 1 from groups where username = uname) then
    raise exception 'username_taken';
  end if;
  insert into groups (id, username, name, owner_id, join_code)
    values (gid, uname, left(trim(p_name), 60), auth.uid(), code);
  insert into group_members (group_id, user_id, display_name, role)
    values (gid, auth.uid(), left(coalesce(nullif(trim(p_display_name), ''), 'Student'), 60), 'owner');
  return query select gid, code;
end $$;

create or replace function public.join_group(p_username text, p_join_code text, p_display_name text)
returns uuid
language plpgsql security definer
set search_path to 'public'
as $$
declare g groups;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  select * into g from groups where username = lower(trim(p_username));
  if not found or g.join_code <> upper(trim(p_join_code)) then
    raise exception 'invalid_group_or_code';
  end if;
  insert into group_members (group_id, user_id, display_name, role)
    values (g.id, auth.uid(), left(coalesce(nullif(trim(p_display_name), ''), 'Student'), 60), 'member')
    on conflict (group_id, user_id) do nothing;
  return g.id;
end $$;

create or replace function public.update_group_profile(p_group_id uuid, p_name text, p_username text)
returns void
language plpgsql security definer
set search_path to 'public'
as $$
declare
  uname text := lower(trim(p_username));
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  if not is_group_owner(p_group_id) then raise exception 'only owner can update group profile'; end if;
  if char_length(uname) < 3 or char_length(uname) > 30 then raise exception 'username must be between 3 and 30 characters'; end if;
  if exists (select 1 from groups where username = uname and id <> p_group_id) then
    raise exception 'username_taken';
  end if;
  update groups
  set name = left(trim(p_name), 60),
      username = uname
  where id = p_group_id;
end $$;

create or replace function public.regenerate_group_code(p_group_id uuid)
returns text
language plpgsql security definer
set search_path to 'public'
as $$
declare
  new_code text := upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8));
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  if not is_group_owner(p_group_id) then raise exception 'only owner can regenerate join code'; end if;
  update groups
  set join_code = new_code
  where id = p_group_id;
  return new_code;
end $$;

create or replace function public.transfer_group_ownership(p_group_id uuid, p_new_owner_id uuid)
returns void
language plpgsql security definer
set search_path to 'public'
as $$
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  if not is_group_owner(p_group_id) then raise exception 'only owner can transfer ownership'; end if;
  if not exists (select 1 from group_members where group_id = p_group_id and user_id = p_new_owner_id) then
    raise exception 'target user is not a member of the group';
  end if;
  update group_members set role = 'admin' where group_id = p_group_id and user_id = auth.uid();
  update group_members set role = 'owner' where group_id = p_group_id and user_id = p_new_owner_id;
  update groups set owner_id = p_new_owner_id where id = p_group_id;
end $$;

-- "Delete account" button in the app
create or replace function public.delete_my_account()
returns void
language plpgsql security definer
set search_path to 'public'
as $$
declare
  v_user_id uuid := auth.uid();
  v_group record;
  v_new_owner_id uuid;
begin
  if v_user_id is null then
    raise exception 'Not authenticated';
  end if;

  -- 1. Personal data
  delete from public.group_done where user_id = v_user_id;
  delete from public.tasks      where user_id = v_user_id;
  delete from public.courses    where user_id = v_user_id;
  delete from public.slots      where user_id = v_user_id;

  -- 2. Owned groups: hand over to the oldest admin, else the oldest member, else delete the group
  for v_group in select id from public.groups where owner_id = v_user_id loop
    v_new_owner_id := null;

    select user_id into v_new_owner_id
    from public.group_members
    where group_id = v_group.id and user_id <> v_user_id and role = 'admin'
    order by joined_at asc
    limit 1;

    if v_new_owner_id is null then
      select user_id into v_new_owner_id
      from public.group_members
      where group_id = v_group.id and user_id <> v_user_id
      order by joined_at asc
      limit 1;
    end if;

    if v_new_owner_id is not null then
      update public.group_members set role = 'owner'
      where group_id = v_group.id and user_id = v_new_owner_id;
      update public.groups set owner_id = v_new_owner_id
      where id = v_group.id;
    else
      delete from public.groups where id = v_group.id;
    end if;
  end loop;

  -- 3. Membership rows
  delete from public.group_members where user_id = v_user_id;

  -- 4. Content the user wrote in groups that still exist
  delete from public.group_messages where sender_id  = v_user_id;
  delete from public.group_tasks    where created_by = v_user_id;
  delete from public.group_courses  where created_by = v_user_id;

  -- 5. The login itself
  delete from auth.users where id = v_user_id;
end $$;

-- triggers: keep "updated_at" fresh and stop people faking who wrote something
create or replace function public.enforce_message_identity()
returns trigger
language plpgsql security definer
set search_path to 'public'
as $$
begin
  new.sender_id = auth.uid();
  return new;
end $$;

create or replace function public.enforce_task_identity()
returns trigger
language plpgsql security definer
set search_path to 'public'
as $$
begin
  if TG_OP = 'INSERT' then
    new.created_by = auth.uid();
  end if;
  return new;
end $$;

create trigger t_courses_touch   before update on public.courses     for each row execute function public.touch_updated_at();
create trigger t_tasks_touch     before update on public.tasks       for each row execute function public.touch_updated_at();
create trigger t_slots_touch     before update on public.slots       for each row execute function public.touch_updated_at();
create trigger t_gtasks_touch    before update on public.group_tasks for each row execute function public.touch_updated_at();
create trigger t_gmsg_identity   before insert on public.group_messages for each row execute function public.enforce_message_identity();
create trigger t_gtasks_identity before insert or update on public.group_tasks for each row execute function public.enforce_task_identity();
```

### Block 5 of 8: permissions

**This block matters on new projects.** Since 30 October 2026, Supabase no longer lets the app reach new tables until you grant permission explicitly. Without this block you get `permission denied for table` errors in the app. The grants are kept as small as possible, and the security rules from Block 3 still decide which rows each person can see.

```sql
-- BLOCK 5 of 8: permissions (needed on new Supabase projects)
-- Grants say WHAT a role may try. The row level security from block 3 still decides WHICH rows.

-- anyone (even before login) may read announcements
grant select on public.announcements to anon, authenticated;

-- signed-in users
grant select, insert, update, delete on public.courses, public.tasks, public.slots, public.group_done to authenticated;
grant select, insert, update, delete on public.group_courses, public.group_tasks to authenticated;
grant select, insert, delete         on public.group_messages to authenticated;
grant select, update, delete         on public.group_members  to authenticated;
grant select, update, delete         on public.groups         to authenticated;

-- the dashboard / server side key
grant all on all tables in schema public to service_role;

-- functions the app calls: signed-in users only
revoke execute on function public.is_member(uuid), public.is_group_admin(uuid), public.is_group_owner(uuid),
  public.create_group(text, text, text), public.join_group(text, text, text),
  public.update_group_profile(uuid, text, text), public.regenerate_group_code(uuid),
  public.transfer_group_ownership(uuid, uuid), public.delete_my_account()
  from public, anon;

grant execute on function public.is_member(uuid), public.is_group_admin(uuid), public.is_group_owner(uuid),
  public.create_group(text, text, text), public.join_group(text, text, text),
  public.update_group_profile(uuid, text, text), public.regenerate_group_code(uuid),
  public.transfer_group_ownership(uuid, uuid), public.delete_my_account()
  to authenticated;
```

### Block 6 of 8: live updates

Lets the app receive group changes and chat messages instantly instead of only when it refreshes.

```sql
-- BLOCK 6 of 8: live updates (the app listens to these tables)
alter publication supabase_realtime add table
  public.courses, public.tasks, public.slots, public.group_members,
  public.group_tasks, public.group_courses, public.group_messages, public.group_done;
```

### Block 7 of 8 (optional): delete old chat automatically

Keeps your free database small by deleting chat messages older than 30 days every night. Skip it if you want chat to be kept forever.

**First** switch the scheduler on: in Supabase open **Database, Extensions**, search for **pg_cron** and switch it on. **Then** run:

```sql
-- BLOCK 7 of 8 (optional): delete chat messages older than 30 days, every day at 03:00
-- First enable the extension: Database > Extensions > search "pg_cron" > switch it on.
select cron.schedule(
  'delete-old-chat',
  '0 3 * * *',
  $$delete from public.group_messages where created_at < now() - interval '30 days'$$
);
```

### Block 8 of 8 (optional): a test announcement

The announcements table can stay empty. This block adds one example announcement so you can confirm your phone is really talking to **your** database. You can delete the rows later in **Table Editor**.

```sql
-- BLOCK 8 of 8 (optional): one example announcement
insert into public.announcements (title, body)
values ('Hello from my server', 'If you can read this, your database is connected.');
```

### Check that everything worked

Run this one:

```sql
-- CHECK: expect 10 tables, 8 live tables and 12 functions
select
  (select count(*) from information_schema.tables
    where table_schema = 'public' and table_type = 'BASE TABLE')                as tables_expect_10,
  (select count(*) from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public')               as live_tables_expect_8,
  (select count(*) from information_schema.routines
    where routine_schema = 'public')                                             as functions_expect_12;
```

You should see `10`, `8` and `12`. Also open **Table Editor** in the left menu: you should see 10 tables.

### Later: adding your own announcements

- **Announcements** (the bell): add rows to `announcements`. Leave `user_id` empty to show it to everyone, or paste one person's user id (find it under **Authentication, Users**) to show it to that person only. Set `expires_at` to make it disappear on its own.

### Start over (only if you want to wipe the tables)

If a block failed halfway and things are messy, run this to delete the UniTask tables and functions, then repeat Blocks 1 to 6. **It deletes all UniTask data in this database.** It does not touch your logins.

```sql
-- START OVER (only if you want to wipe the UniTask tables and run the blocks again)
-- This deletes ALL UniTask data in this database. It does not touch logins.
drop table if exists
  public.group_done, public.group_messages, public.group_courses, public.group_tasks,
  public.group_members, public.groups, public.slots, public.tasks, public.courses,
  public.announcements
  cascade;

drop function if exists
  public.is_member(uuid), public.is_group_admin(uuid), public.is_group_owner(uuid),
  public.touch_updated_at(), public.create_group(text, text, text),
  public.join_group(text, text, text), public.update_group_profile(uuid, text, text),
  public.regenerate_group_code(uuid), public.transfer_group_ownership(uuid, uuid),
  public.delete_my_account(), public.enforce_message_identity(), public.enforce_task_identity()
  cascade;
```

### Troubleshooting Step 3

| What you see | What it means | Fix |
| --- | --- | --- |
| `relation "public.xxx" does not exist` | A block ran before the one it depends on, or an earlier block failed | Run the "Start over" snippet, then Blocks 1 to 6 in order |
| `function auth.uid() does not exist` | You are not running this in a Supabase project (for example a plain local Postgres) | Run it in the Supabase SQL Editor |
| `relation "xxx" already exists` or `already exists` | You ran a block twice | Ignore it, or use "Start over" if you want a clean run |
| `relation "public.xxx" is already member of publication` | Block 6 ran twice | Safe to ignore |
| `extension "pg_cron" is not available` or `schema "cron" does not exist` | You ran Block 7 before switching on pg_cron | Enable it in **Database, Extensions**, then run Block 7 again |
| The editor shows a warning about "destructive operations" | Only the Start over snippet triggers this | Confirm only if you really want to wipe the tables |
| Nothing happens when you click Run | Some text is highlighted, or the editor is empty | Click once in the editor, press `Ctrl+A` then `Ctrl+V` to paste the whole block again, and click Run |

---

## Step 4: Create your signing key

Android only installs and updates apps that are signed. You make one key once and use it for every release. **If you lose this file or its password, you can never update an installed copy of your app.**

You need Java's `keytool`. If you have Android Studio it is already on your computer:

- **Windows:** `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`
- **macOS:** `/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool`
- **Linux or if you installed a JDK:** just `keytool`

Open a terminal in your project folder and run (the key's alias must be exactly `upload`, because the build file looks for that name):

```bash
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

On Windows PowerShell with Android Studio's copy, put the full path in quotes and add `&` in front:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

It asks several questions:

1. **Keystore password**: choose one and save it in your note file. (You will not see characters while typing. That is normal.)
2. Name, organisation, city and so on: type anything, or press Enter to skip.
3. **Key password**: if it offers "same as keystore password", press **Enter**. Using the **same password for both** avoids a common error.

This creates `my-upload-key.jks`. **Back it up now** (a USB stick or private cloud storage). It is already in `.gitignore`, so Git will not upload it. Never commit it.

### Get the SHA-1 for Google (you use it in Step 6d)

```bash
keytool -list -v -keystore my-upload-key.jks -alias upload
```

Enter the password. Copy the line that starts with **SHA1:** (it looks like `SHA1: AA:BB:CC:...`) into your note file. You will paste it into Google in Step 6d.

For a **debug** build from Android Studio, the debug key's SHA-1 is:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

On Windows the file is `C:\Users\YOUR-NAME\.android\debug.keystore`.

### Troubleshooting Step 4

| What you see | Fix |
| --- | --- |
| `keytool` is not recognised | Use the full path shown above, or install a JDK 17 and reopen the terminal. |
| You forgot the password | There is no recovery. Create a new key and, if you already published, you will need to publish the app as a new install. |

---

## Step 5: Put your values in the code

Open `app/build.gradle.kts`. Find the block that starts with `defaultConfig`. Change these lines (near line 16 and lines 21 to 23 in v3.0.0). You do not have the Google Web client ID yet. Leave that line as it is for now and fill it in at the end of Step 6.

```kotlin
applicationId = "com.yourname.unitask"
buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"YOUR-WEB-CLIENT-ID.apps.googleusercontent.com\"")
buildConfigField("String", "SUPABASE_URL", "\"https://YOUR-PROJECT.supabase.co\"")
buildConfigField("String", "SUPABASE_ANON_KEY", "\"sb_publishable_YOUR_KEY\"")
```

Keep the backslash-quote pairs (`\"`) exactly as they are. They are part of the syntax. Only replace the text between them.

**Why change `applicationId`?** Android allows only one app per id on a phone. If you keep the original id and a friend already has the real UniTask installed, your build (signed with a different key) will refuse to install over it. Pick something unique like `com.yourname.unitask`. Use lowercase letters, numbers and dots, with at least two parts. **Use this exact id in the Android client in Step 6d.**

### Change the links that point to the original project

The app has a few links to the original author's repository and feedback forms. If you skip this, your app's "Check for update" button will talk about the **original** app's releases. Use your editor's search (`Ctrl+Shift+F` in VS Code or Android Studio) for `ahmad-maqsood` and change:

| File | What to change |
| --- | --- |
| `app/src/main/java/com/example/util/UpdateChecker.kt` | The two GitHub URLs near the top (`LATEST_URL` and `RELEASES_PAGE`): use your own `username/repo` |
| `app/src/main/java/com/example/ui/screens/SettingsSheet.kt` | `SOURCE_CODE_URL`, the subtitle text with the repo name, and the two Google Form links for "Report a problem" and "Request a feature". Those forms belong to the original author, so replace them with your own forms or links. |

Save the files and commit them to your fork.

> **Is it safe to commit the URL and key?** Yes, the project URL and the **publishable** key are meant to be public. They sit inside every APK anyway. What must stay private: the Supabase **secret** or `service_role` key, your keystore file, and your passwords.

---

## Step 6: Set up Google sign-in

The app uses Google's sign-in sheet. Google gives the app a proof of identity (an "ID token"), and Supabase checks that proof. You need to set up both sides, and they must agree on the same **Web client ID**.

This is the longest step. Take it slowly.

### 6a. Create a Google Cloud project

1. Open [console.cloud.google.com](https://console.cloud.google.com/) and sign in.
2. Click the project picker at the top, then **New project**.
3. Name it `unitask`, click **Create**, then make sure it is selected in the picker.

### 6b. Set up the consent screen

1. Open the left menu and go to **APIs and Services, then OAuth consent screen** (Google now shows this as **Google Auth Platform**). If you see a **Get started** button, click it.
2. **App name**: `UniTask` (or your own name). **User support email**: your email. Click **Next**.
3. **Audience**: choose **External**. Click **Next**.
4. **Contact information**: your email. Click **Next**, agree to the policy, then **Create**.
5. Open the **Audience** page.
   - While **Publishing status** says **Testing**, only the Gmail accounts you add under **Test users** can sign in.
   - To let anyone sign in, click **Publish app** and confirm. The app only asks for basic profile and email details, which normally does not need Google's extra review. If Google asks for review or shows an "unverified app" screen, see the troubleshooting table below.

### 6c. Create the Web client (the important one)

1. In Google Auth Platform open **Clients**, then **Create client**.
2. **Application type**: **Web application**. **Name**: `UniTask web`.
3. Leave the redirect list empty for now. You will add one in 6e.
4. Click **Create**. A box shows the **Client ID** and **Client secret**. Copy both into your note file.

> Do not confuse the types. The **Web** client ID is the one that goes into the app and into Supabase. The **Android** clients you create next only exist so Google can verify your app's identity.

### 6d. Create the Android clients

Google needs the package name and the SHA-1 of every key that signs a build you will sign in with. If you build on GitHub (Step 7, Option A), that is your **release key** from Step 4, and you already copied its SHA-1.

1. **Clients, Create client**.
2. **Application type**: **Android**. **Name**: `UniTask Android release`.
3. **Package name**: your app id from Step 5 (for example `com.yourname.unitask`). It must match the app **exactly**.
4. **SHA-1 certificate fingerprint**: paste the value from Step 4, for example `AA:BB:CC:...`.
5. Click **Create**.

If you also test debug builds from Android Studio, make a second Android client (`UniTask Android debug`) with the same package name and the **debug** key's SHA-1 (the command is in Step 4).

Google can take **5 minutes to a few hours** to start accepting a new Android client. If sign-in fails right after creating it, wait and try again.

### 6e. Turn on Google in Supabase

1. In Supabase open **Authentication**, then **Sign In / Providers** (older dashboards call this **Providers**), then **Google**.
2. Switch **Enable Sign in with Google** on.
3. **Client IDs**: paste your **Web** client ID. (If you ever add more IDs, separate them with commas and keep the Web client first.)
4. **Client Secret**: paste your Web client secret.
5. Copy the **Callback URL** shown on that page (it looks like `https://abcdefgh.supabase.co/auth/v1/callback`).
6. Click **Save**.
7. Back in Google Cloud, open your **Web** client, add that Callback URL under **Authorized redirect URIs**, and **Save**.

### 6f. Put the Web client ID in the app

Open `app/build.gradle.kts` again and replace the placeholder on the `GOOGLE_WEB_CLIENT_ID` line with your **Web** client ID from 6c. Save and commit the change.

### Troubleshooting Step 6

| What you see in the app or in Google | Cause | Fix |
| --- | --- | --- |
| Sign-in sheet says "Developer error" or shows code 10 | The Android client does not match your build | Check the package name matches your app id exactly, and the SHA-1 belongs to the key that signed this APK. Wait up to a few hours after creating it. |
| "No credentials available" | No Google account on the phone, or the Android client is missing | Add a Google account in the phone's settings. Recheck Step 6d. |
| "Access blocked" or "This app is not verified / has not completed verification" | Consent screen is still in **Testing**, or is not verified | Add your Gmail under **Audience, Test users**, or click **Publish app**. |
| "Unacceptable audience in id_token" or "invalid audience" | The Client ID in Supabase differs from the one in the app | Use the **same Web client ID** in Supabase (Step 6e) and in the app (Step 6f). |
| Error mentions a **nonce** | Supabase is checking a nonce the app does not send | In the Supabase Google provider page, switch on **Skip nonce checks** (if your dashboard shows that switch), then Save. |
| "Signups not allowed for this instance" | New signups are switched off | **Authentication, Sign In / Providers**, make sure new user signups are allowed. |
| Sign-in works in one build but not another | Debug and release builds have different SHA-1 values | Add a separate Android client for each (Step 6d). |

---

## Step 7: Build the APK

Choose **one** option. **Option A** needs nothing installed on your computer and is the one that is known to work for this project. **Option B** builds on your own computer.

### Option A (recommended): build on GitHub

GitHub builds the APK for free using the workflow already in the project (`.github/workflows/build-release-apk.yml`).

**A1. Switch Actions on in your fork.** Open your fork, click the **Actions** tab, and if you see a green button, click **I understand my workflows, go ahead and enable them**. (Forks start with workflows turned off.)

**A2. Turn your keystore into text.** GitHub secrets hold text, so convert the file:

- **Windows PowerShell** (copies the result to your clipboard):

  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks")) | Set-Clipboard
  ```

- **Linux:**

  ```bash
  base64 -w 0 my-upload-key.jks
  ```

- **macOS:**

  ```bash
  base64 -i my-upload-key.jks
  ```

**A3. Add three secrets.** In your fork open **Settings, Secrets and variables, Actions, New repository secret** and add these (names must match exactly):

| Name | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The long base64 text from A2 |
| `STORE_PASSWORD` | Your keystore password |
| `KEY_PASSWORD` | Your key password (the same as the keystore password if you pressed Enter in Step 4) |

**A4. Run the build.** Open the **Actions** tab, click **Build release APK** on the left, click **Run workflow**, keep the branch as `main`, and click the green **Run workflow** button. The first build usually takes roughly 5 to 15 minutes. A green tick means success.

**A5. Download the APK.** Click the finished run. The top of the page says **Building UniTask** followed by your version name and version code. Scroll to **Artifacts** and click the one named after your version, for example **unitask-3.2.0-16**. You get a zip. Unzip it, and inside is the `.apk` file.

### Option B: build on your own computer with Android Studio

1. Install the latest stable [Android Studio](https://developer.android.com/studio). The project needs a recent version because it uses a new Android Gradle Plugin. If Android Studio says the project needs a newer version, update Android Studio.
2. **File, Open**, and choose the project folder. Wait for **Gradle sync** to finish (the first time it downloads a lot, so you need internet). The status bar at the bottom shows progress.
3. **To test quickly (debug APK):** **Build, Build Bundle(s) / APK(s), Build APK(s)**. When it finishes, click **locate** in the popup. The file is `app/build/outputs/apk/debug/app-debug.apk`. Remember: sign-in from this build needs the **debug** SHA-1 registered in Step 6d.
4. **For a release APK:** **Build, Generate Signed App Bundle / APK**, choose **APK**, **Next**, then **Choose existing...** and pick `my-upload-key.jks`. Enter the passwords, set **Key alias** to `upload`, **Next**, choose **release**, **Create**. The file lands in `app/release/` or `app/build/outputs/apk/release/`. If the wizard complains about the signing setup, use Option A instead.

Free-memory tip: if Android Studio is too heavy for your computer, close other programs, or use Option A, which does the heavy building on GitHub's servers.

> Command-line users can run `gradlew assembleRelease` (Windows) or `./gradlew assembleRelease` (macOS and Linux) from the project folder, with the environment variables `STORE_PASSWORD`, `KEY_PASSWORD` and, if the keystore is not in the project root, `KEYSTORE_PATH`.

### Troubleshooting Step 7

| What you see | Cause | Fix |
| --- | --- | --- |
| Actions tab has no "Build release APK" | Workflows are not enabled in the fork | Do A1 |
| `Secret KEYSTORE_BASE64 is missing` | The secret is missing or misspelled | Redo A3 and check the name letter by letter |
| `Keystore was tampered with, or password was incorrect` | Wrong `STORE_PASSWORD` | Update the secret with the correct password |
| `Cannot recover key` | Wrong `KEY_PASSWORD`, or the alias is not `upload` | Fix the secret, or recreate the key with `-alias upload` (Step 4) |
| Build fails with `Unresolved reference` or `Compilation error` | A code edit has a typo, often in the quotes of Step 5 | Open the failed run, click the red step, and read the first line starting with `e:`. It names the file and line. |
| Build fails at `buildConfigField` | A broken `\"` pair in `app/build.gradle.kts` | Compare your lines with the example in Step 5 |
| Android Studio: "Keystore file ... not found for signing config 'debugConfig'" | The debug key file does not exist on your computer yet | Create it: `keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"` and place `debug.keystore` in the project root (it is already ignored by Git). Then run its SHA-1 command from Step 4 for Google. |
| Android Studio: Gradle sync fails with a Java version error | Wrong JDK | **File, Settings, Build Tools, Gradle**, set **Gradle JDK** to 17 or newer |
| Gradle sync is very slow or fails to download | Slow or blocked network | Retry, or switch networks. Use Option A. |

---

## Step 8: Install and test

1. Copy the `.apk` to your phone (USB cable, Google Drive, or a messaging app).
2. Tap the file. If Android asks, allow **Install unknown apps** for the app you opened it from.
3. If Google Play Protect warns about an unknown app, tap **More details, Install anyway**. This warning is normal for apps not from the Play Store.
4. Open UniTask. Without signing in, tasks, courses and the timetable already work.

### Test checklist (about 10 minutes)

1. **Sign in:** Settings, Cloud backup, Sign in with Google. It should succeed.
2. **Database connection:** in Supabase open **Authentication, Users**. Your account should be listed.
3. **Your own server:** if you ran Block 8, open the bell in the app. You should see "Hello from my server".
4. **Backup:** add a course, wait a few seconds, then open **Table Editor, courses** in Supabase. Your course should appear as a row.
5. **Groups:** create a group. Check **Table Editor, groups**. Join it from a second account (use another phone or a friend) and send a chat message.

### Troubleshooting Step 8

| What you see | Cause | Fix |
| --- | --- | --- |
| "App not installed as package conflicts with an existing package" | An app with the same id but a different signing key is already installed | Uninstall the old one first (this deletes its local data, so use local backup first), or give your build a different `applicationId` (Step 5) |
| "App not installed" when updating your own build | You signed with a different keystore than last time | Always use the same `my-upload-key.jks` |
| Sign-in fails | See the Step 6 troubleshooting table | Check Web client ID, package name and SHA-1 first |
| Sign-in works but groups show errors like `permission denied` or `42501` | Block 5 did not run | Run Block 5 again |
| Groups work, but chat does not appear live | Block 6 did not run | Run Block 6 |
| Everything fails with 401 or "Invalid API key" | Wrong key or URL in `build.gradle.kts` | Re-copy both from **Connect** in Supabase and rebuild |
| Cloud features stop working after a week of not using the app | The free Supabase project paused for inactivity | Open the Supabase dashboard and click **Restore project**. See the keep-alive section below. |
| "Check for update" talks about the wrong app | You skipped the link changes in Step 5 | Edit `UpdateChecker.kt` and rebuild |

---

## Optional: keep your free Supabase project awake

Free Supabase projects pause after a period of inactivity (currently about a week). The project includes a workflow, `.github/workflows/supabase-keepalive.yml`, that visits your database every 3 days.

1. In your fork add two more repository secrets (same place as Step 7, A3): `SUPABASE_URL` and `SUPABASE_ANON_KEY` (your publishable key goes in the second one).
2. In `.github/workflows/supabase-keepalive.yml` change the address that is pinged from `groups` to `announcements`:

   ```
   curl -fsS "$SUPABASE_URL/rest/v1/announcements?select=id&limit=1" \
   ```

   The database from this guide only lets signed-out visitors read `announcements`, so pinging `groups` would be refused.
3. Run it once by hand: **Actions, Supabase keep-alive, Run workflow**. A green tick means it works.
4. If it fails with 401 and you used a publishable key, delete the line `-H "Authorization: Bearer $SUPABASE_ANON_KEY"` so only the `apikey` header remains. Publishable keys are meant for the `apikey` header.

GitHub switches off scheduled workflows in a public repository after 60 days without any repository activity. If the keep-alive stops, open the Actions tab and re-enable it.

---

## Releasing updates later

1. In `app/build.gradle.kts` raise `versionCode` by 1 and change `versionName` (for example `3.0.0` to `3.0.1`).
2. Run the build again (Step 7). The run page shows which version it is building, and the downloaded file is named with it, so you can check that you raised the right numbers.
3. On GitHub create a **Release** whose **tag** is `v` plus the version name (for example `v3.0.1`), and attach the APK.

The in-app **Check for update** button reads only the release **tag**, so the title and file name do not matter. Always sign with the **same keystore**, otherwise phones refuse the update.

When you change the database later (new column, new table), keep a copy of the SQL you ran in your repo so you always know what your live database looks like.

---

## Quick reference

| Thing | Where it lives |
| --- | --- |
| Project URL, publishable key | Supabase, **Connect** button |
| Tables and rows | Supabase, **Table Editor** |
| Run SQL | Supabase, **SQL Editor** |
| Users who signed in | Supabase, **Authentication, Users** |
| Web client ID and secret | Google Cloud, **Google Auth Platform, Clients** |
| Test users, publish status | Google Cloud, **Google Auth Platform, Audience** |
| App settings to edit | `app/build.gradle.kts` |
| Build workflow | `.github/workflows/build-release-apk.yml` |
| Keep-alive workflow | `.github/workflows/supabase-keepalive.yml` |
| Your signing key | `my-upload-key.jks` (never commit, always back up) |

### Still stuck?

[Open an issue](https://github.com/ahmad-maqsood/unitask/issues) and include: which step, the exact error text, and a screenshot. **Never** post your passwords, keystore, or any `sb_secret_` / `service_role` key.
