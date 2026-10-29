-- =====================================================================
-- UniTask Supabase setup, captured from the live project.
-- =====================================================================
-- Everything in PART A is copied from the real project.
-- Columns are shown as comments because the query output has no keys or
-- identity settings. Policies are written as CREATE POLICY statements
-- built from the pg_policies output. This file is documentation: do not
-- run it on the live project.
-- =====================================================================


-- =====================================================================
-- PART A1: tables and columns (query 1)   name  type  null  default
-- =====================================================================
-- announcements:  id bigint NOT NULL | title text NOT NULL | body text NOT NULL
--                 user_id uuid NULL (NULL = shown to everyone) | created_at timestamptz now()
--                 expires_at timestamptz NULL | enabled boolean true
-- app_features:   id bigint NOT NULL | sort_order int 0 | title text | description text
--                 icon text 'star' | enabled boolean true
-- courses:        user_id uuid auth.uid() | doc_id text | code text | name text
--                 instructor text '' | color_hex text '' | location text '' | updated_at timestamptz now()
-- tasks:          user_id uuid auth.uid() | doc_id text | course_code text '' | title text
--                 description text '' | due_date_epoch_millis bigint | due_time_hour int 0
--                 due_time_minute int 0 | priority text | status text | reminder_option text 'NONE'
--                 updated_at timestamptz now() | repeat_rule text 'NONE'
-- slots:          user_id uuid auth.uid() | doc_id text | course_code text | day_of_week int
--                 start_hour int | start_minute int | end_hour int | end_minute int
--                 classroom text '' | instructor text '' | updated_at timestamptz now()
-- groups:         id uuid gen_random_uuid() | username text | name text | owner_id uuid
--                 join_code text | created_at timestamptz now()
-- group_members:  group_id uuid | user_id uuid | display_name text 'Student' | role text 'member'
--                 joined_at timestamptz now()
-- group_tasks:    id uuid gen_random_uuid() | group_id uuid | title text | description text NULL
--                 course_label text NULL | due_date_epoch_millis bigint | due_time_hour int 0
--                 due_time_minute int 0 | priority text 'MEDIUM' | reminder_options text NULL
--                 assigned_to uuid[] '{}' | created_by uuid auth.uid() | created_by_name text 'Student'
--                 created_at timestamptz now() | updated_at timestamptz now()
-- group_courses:  id uuid gen_random_uuid() | group_id uuid | code text | name text
--                 instructor text '' | color_hex text '' | created_by uuid auth.uid()
--                 created_at timestamptz now()
-- group_done:     user_id uuid auth.uid() | group_id uuid | task_id uuid | done_at timestamptz now()
-- group_messages: id bigint | group_id uuid | sender_id uuid auth.uid() | sender_name text 'Student'
--                 text text | created_at timestamptz now()
--                 reply_to_id bigint NULL | reply_to_name text NULL | reply_to_text text NULL


-- =====================================================================
-- PART A2: row level security is ON for all 11 tables (query 2)
-- =====================================================================
alter table announcements  enable row level security;
alter table app_features   enable row level security;
alter table courses        enable row level security;
alter table group_courses  enable row level security;
alter table group_done     enable row level security;
alter table group_members  enable row level security;
alter table group_messages enable row level security;
alter table group_tasks    enable row level security;
alter table groups         enable row level security;
alter table slots          enable row level security;
alter table tasks          enable row level security;


-- =====================================================================
-- PART A3: policies (query 3)
-- =====================================================================
-- Note: every policy except the two public read ones is for signed-in
-- users only. A request made before the login session is ready gets ZERO
-- rows back (not an error). The app must wait for a session before reading.

create policy "read my announcements" on announcements for select to anon, authenticated
  using ((enabled = true) and ((expires_at is null or expires_at > now())) and ((user_id is null) or (user_id = auth.uid())));
create policy "anyone can read features" on app_features for select to anon, authenticated
  using (enabled = true);

create policy own_courses on courses for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy own_tasks on tasks for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy own_slots on slots for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy own_group_done on group_done for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy gcourses_read on group_courses for select to authenticated
  using (is_member(group_id));
create policy gcourses_insert on group_courses for insert to authenticated
  with check (is_member(group_id) and created_by = auth.uid());
create policy gcourses_update on group_courses for update to authenticated
  using (created_by = auth.uid() or is_group_admin(group_id))
  with check (created_by = auth.uid() or is_group_admin(group_id));
create policy gcourses_delete on group_courses for delete to authenticated
  using (created_by = auth.uid() or is_group_admin(group_id));

create policy gtasks_read on group_tasks for select to authenticated
  using (is_member(group_id));
create policy gtasks_insert on group_tasks for insert to authenticated
  with check (is_member(group_id) and created_by = auth.uid());
create policy gtasks_update on group_tasks for update to authenticated
  using (created_by = auth.uid() or is_group_admin(group_id))
  with check (created_by = auth.uid() or is_group_admin(group_id));
create policy gtasks_delete on group_tasks for delete to authenticated
  using (created_by = auth.uid() or is_group_admin(group_id));

create policy gmsg_read on group_messages for select to authenticated
  using (is_member(group_id));
create policy gmsg_insert on group_messages for insert to authenticated
  with check (is_member(group_id) and sender_id = auth.uid());
create policy gmsg_delete on group_messages for delete to authenticated
  using (sender_id = auth.uid() or is_group_admin(group_id));

create policy members_read on group_members for select to authenticated
  using (is_member(group_id));
create policy members_role on group_members for update to authenticated
  using (is_group_owner(group_id)) with check (is_group_owner(group_id));
create policy members_delete on group_members for delete to authenticated
  using (user_id = auth.uid() or (is_group_admin(group_id) and role <> 'owner'));
-- no INSERT policy on group_members: people join only through join_group()

create policy groups_read on groups for select to authenticated
  using (is_member(id));
create policy groups_update on groups for update to authenticated
  using (is_group_owner(id)) with check (is_group_owner(id));
create policy groups_delete on groups for delete to authenticated
  using (is_group_owner(id));
-- no INSERT policy on groups: groups are made only through create_group()


-- =====================================================================
-- PART A4: functions (query 4)
-- =====================================================================
CREATE OR REPLACE FUNCTION public.is_member(gid uuid)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select exists (select 1 from group_members where group_id = gid and user_id = auth.uid());
$function$;

CREATE OR REPLACE FUNCTION public.is_group_admin(gid uuid)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select exists (select 1 from group_members
                 where group_id = gid and user_id = auth.uid() and role in ('owner','admin'));
$function$;

CREATE OR REPLACE FUNCTION public.is_group_owner(gid uuid)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select exists (select 1 from group_members
                 where group_id = gid and user_id = auth.uid() and role = 'owner');
$function$;

CREATE OR REPLACE FUNCTION public.touch_updated_at()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin new.updated_at = now(); return new; end $function$;

CREATE OR REPLACE FUNCTION public.create_group(p_username text, p_name text, p_display_name text)
 RETURNS TABLE(group_id uuid, join_code text)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
end $function$;

CREATE OR REPLACE FUNCTION public.join_group(p_username text, p_join_code text, p_display_name text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
end $function$;

-- =====================================================================
-- PART A5: triggers in the public schema (query 5)
-- =====================================================================
CREATE TRIGGER t_courses_touch BEFORE UPDATE ON public.courses FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER t_tasks_touch   BEFORE UPDATE ON public.tasks   FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER t_slots_touch   BEFORE UPDATE ON public.slots   FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER t_gtasks_touch  BEFORE UPDATE ON public.group_tasks FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
-- (other triggers in the output belong to Supabase's storage and realtime schemas)


-- =====================================================================
-- PART A6: indexes (query 6)
-- =====================================================================
CREATE UNIQUE INDEX announcements_pkey ON public.announcements USING btree (id);
CREATE UNIQUE INDEX app_features_pkey ON public.app_features USING btree (id);
CREATE UNIQUE INDEX courses_pkey ON public.courses USING btree (user_id, doc_id);
CREATE UNIQUE INDEX group_courses_pkey ON public.group_courses USING btree (id);
CREATE INDEX group_courses_group_id_idx ON public.group_courses USING btree (group_id);
CREATE UNIQUE INDEX group_courses_unique_code ON public.group_courses USING btree (group_id, upper(regexp_replace(code, '[^A-Za-z0-9]'::text, ''::text, 'g'::text)));
CREATE INDEX group_done_user_id_group_id_idx ON public.group_done USING btree (user_id, group_id);
CREATE UNIQUE INDEX group_done_pkey ON public.group_done USING btree (user_id, task_id);
CREATE UNIQUE INDEX group_members_pkey ON public.group_members USING btree (group_id, user_id);
CREATE INDEX group_members_user_id_idx ON public.group_members USING btree (user_id);
CREATE UNIQUE INDEX group_messages_pkey ON public.group_messages USING btree (id);
CREATE INDEX group_messages_group_id_created_at_idx ON public.group_messages USING btree (group_id, created_at DESC);
CREATE UNIQUE INDEX group_tasks_pkey ON public.group_tasks USING btree (id);
CREATE INDEX group_tasks_group_id_idx ON public.group_tasks USING btree (group_id);
CREATE UNIQUE INDEX groups_username_key ON public.groups USING btree (username);
CREATE UNIQUE INDEX groups_pkey ON public.groups USING btree (id);
CREATE UNIQUE INDEX slots_pkey ON public.slots USING btree (user_id, doc_id);
CREATE UNIQUE INDEX tasks_pkey ON public.tasks USING btree (user_id, doc_id);


-- =====================================================================
-- PART A7: tables sent live to the app, publication supabase_realtime (query 7)
-- =====================================================================
-- courses, tasks, slots, group_members, group_tasks, group_courses, group_messages, group_done

-- =====================================================================
-- PART A8: Foreign keys and check constraints 
-- =====================================================================
| table_name     | conname                       | definition                                                                                                  |
| -------------- | ----------------------------- | ----------------------------------------------------------------------------------------------------------- |
| courses        | courses_code_check            | CHECK (((char_length(code) >= 1) AND (char_length(code) <= 30)))                                            |
| courses        | courses_name_check            | CHECK (((char_length(name) >= 1) AND (char_length(name) <= 100)))                                           |
| courses        | courses_pkey                  | PRIMARY KEY (user_id, doc_id)                                                                               |
| courses        | courses_user_id_fkey          | FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE                                           |
| tasks          | tasks_due_time_hour_check     | CHECK (((due_time_hour >= 0) AND (due_time_hour <= 23)))                                                    |
| tasks          | tasks_due_time_minute_check   | CHECK (((due_time_minute >= 0) AND (due_time_minute <= 59)))                                                |
| tasks          | tasks_pkey                    | PRIMARY KEY (user_id, doc_id)                                                                               |
| tasks          | tasks_priority_check          | CHECK ((priority = ANY (ARRAY['LOW'::text, 'MEDIUM'::text, 'HIGH'::text])))                                 |
| tasks          | tasks_status_check            | CHECK ((status = ANY (ARRAY['PENDING'::text, 'COMPLETED'::text])))                                          |
| tasks          | tasks_title_check             | CHECK (((char_length(title) >= 1) AND (char_length(title) <= 200)))                                         |
| tasks          | tasks_user_id_fkey            | FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE                                           |
| slots          | slots_course_code_check       | CHECK (((char_length(course_code) >= 1) AND (char_length(course_code) <= 30)))                              |
| slots          | slots_day_of_week_check       | CHECK (((day_of_week >= 1) AND (day_of_week <= 7)))                                                         |
| slots          | slots_end_hour_check          | CHECK (((end_hour >= 0) AND (end_hour <= 23)))                                                              |
| slots          | slots_end_minute_check        | CHECK (((end_minute >= 0) AND (end_minute <= 59)))                                                          |
| slots          | slots_pkey                    | PRIMARY KEY (user_id, doc_id)                                                                               |
| slots          | slots_start_hour_check        | CHECK (((start_hour >= 0) AND (start_hour <= 23)))                                                          |
| slots          | slots_start_minute_check      | CHECK (((start_minute >= 0) AND (start_minute <= 59)))                                                      |
| slots          | slots_user_id_fkey            | FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE                                           |
| groups         | groups_name_check             | CHECK (((char_length(name) >= 1) AND (char_length(name) <= 60)))                                            |
| groups         | groups_owner_id_fkey          | FOREIGN KEY (owner_id) REFERENCES auth.users(id) ON DELETE CASCADE                                          |
| groups         | groups_pkey                   | PRIMARY KEY (id)                                                                                            |
| groups         | groups_username_check         | CHECK (((username = lower(username)) AND ((char_length(username) >= 3) AND (char_length(username) <= 30)))) |
| groups         | groups_username_key           | UNIQUE (username)                                                                                           |
| group_members  | group_members_group_id_fkey   | FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE                                              |
| group_members  | group_members_pkey            | PRIMARY KEY (group_id, user_id)                                                                             |
| group_members  | group_members_role_check      | CHECK ((role = ANY (ARRAY['owner'::text, 'admin'::text, 'member'::text])))                                  |
| group_members  | group_members_user_id_fkey    | FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE                                           |
| group_tasks    | group_tasks_created_by_fkey   | FOREIGN KEY (created_by) REFERENCES auth.users(id)                                                          |
| group_tasks    | group_tasks_group_id_fkey     | FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE                                              |
| group_tasks    | group_tasks_pkey              | PRIMARY KEY (id)                                                                                            |
| group_tasks    | group_tasks_title_check       | CHECK (((char_length(title) >= 1) AND (char_length(title) <= 200)))                                         |
| group_courses  | group_courses_created_by_fkey | FOREIGN KEY (created_by) REFERENCES auth.users(id)                                                          |
| group_courses  | group_courses_group_id_fkey   | FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE                                              |
| group_courses  | group_courses_pkey            | PRIMARY KEY (id)                                                                                            |
| group_messages | group_messages_group_id_fkey  | FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE                                              |
| group_messages | group_messages_pkey           | PRIMARY KEY (id)                                                                                            |
| group_messages | group_messages_sender_id_fkey | FOREIGN KEY (sender_id) REFERENCES auth.users(id)                                                           |
| group_messages | group_messages_text_check     | CHECK (((char_length(text) >= 1) AND (char_length(text) <= 500)))                                           |
| group_done     | group_done_group_id_fkey      | FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE                                              |
| group_done     | group_done_pkey               | PRIMARY KEY (user_id, task_id)                                                                              |
| group_done     | group_done_task_id_fkey       | FOREIGN KEY (task_id) REFERENCES group_tasks(id) ON DELETE CASCADE                                          |
| group_done     | group_done_user_id_fkey       | FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE                                           |
| app_features   | app_features_pkey             | PRIMARY KEY (id)                                                                                            |
| announcements  | announcements_pkey            | PRIMARY KEY (id)                                                                                            |

-- =====================================================================
-- PART A9: scheduled messages' deletion job (pg_cron)
-- =====================================================================
-- delete-old-chat, daily at 03:00: delete from group_messages older than 30 days

-- =====================================================================
-- PART B: queries that print the live setup again (run one at a time)
-- =====================================================================

-- 1) Tables and columns
select table_name, column_name, data_type, is_nullable, column_default
from information_schema.columns
where table_schema = 'public'
order by table_name, ordinal_position;

-- 2) Which tables have row level security switched on
select c.relname as table_name, c.relrowsecurity as rls_on
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r'
order by c.relname;

-- 3) All row level security policies
select tablename, policyname, cmd, roles, qual, with_check
from pg_policies
where schemaname = 'public'
order by tablename, policyname;

-- 4) Functions (create_group, join_group and any helpers), full source
select pg_get_functiondef(p.oid) as definition
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public';

-- 5) Triggers
select pg_get_triggerdef(oid) as definition
from pg_trigger
where not tgisinternal;

-- 6) Indexes and unique constraints
select indexdef from pg_indexes where schemaname = 'public' order by tablename;

-- 7) Tables sent live to the app (realtime)
select tablename from pg_publication_tables where pubname = 'supabase_realtime';

-- 8) Foreign keys and check constraints 
select conrelid::regclass as table_name, conname, pg_get_constraintdef(oid) as definition
from pg_constraint
where connamespace = 'public'::regnamespace
order by 1, 2;

-- 9) scheduled job(automatic 30 days old messages' deletion) (pg_cron)
-- Go to Database -> Extensions -> Search pg_cron -> Enable it and run:-
 select cron.schedule('delete-old-chat', '0 3 * * *',
  $$delete from public.group_messages where created_at < now() - interval '30 days'$$);
