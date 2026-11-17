-- =====================================================================
-- UniTask: limits on groups and group tasks
-- Run in: Supabase dashboard, SQL Editor. Run each STEP on its own, in order.
-- =====================================================================
--
-- LIMITS
--   * 8 groups owned (created) per person. Joining other people's groups is not limited.
--   * 200 tasks per group.
-- To change a number later, edit it in the function below and in
-- GroupRepository.kt (MAX_GROUPS_PER_OWNER, MAX_TASKS_PER_GROUP), then run the step again.
--
-- WHY IN THE DATABASE
-- The app adds group tasks straight into the group_tasks table, so a check that lives only in the app
-- could be skipped or could lose a race. These checks run inside Postgres. A transaction lock per owner
-- (groups) and per group (tasks) makes two requests at the same moment count correctly.
--
-- EXISTING DATA
-- Nothing is deleted. A person or group that is already over a limit keeps what it has; only new
-- groups and new tasks are refused. Deleting a group or a task frees a slot at once.
--
-- ERROR TEXT (the app looks for these exact words)
--   group_limit_reached   task_limit_reached


-- =====================================================================
-- STEP 1: look first (changes nothing)
-- Shows the highest numbers right now. Both should be well under the limits.
-- =====================================================================
select
  (select max(c) from (select count(*) c from groups group by owner_id) a) as max_groups_owned,
  (select max(c) from (select count(*) c from group_tasks group by group_id) b) as max_tasks_in_group;


-- =====================================================================
-- STEP 2: create_group refuses a 9th group
-- CREATE OR REPLACE keeps the permissions set in function_privileges.sql.
-- =====================================================================
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

  -- Limit: 8 groups owned per person. The lock makes two taps at the same moment count one by one.
  perform pg_advisory_xact_lock(hashtext('create_group:' || auth.uid()::text));
  if (select count(*) from groups where owner_id = auth.uid()) >= 8 then
    raise exception 'group_limit_reached';
  end if;

  if exists (select 1 from groups where username = uname) then
    raise exception 'username_taken';
  end if;
  insert into groups (id, username, name, owner_id, join_code)
    values (gid, uname, left(trim(p_name), 60), auth.uid(), code);
  insert into group_members (group_id, user_id, display_name, role)
    values (gid, auth.uid(), left(coalesce(nullif(trim(p_display_name), ''), 'Student'), 60), 'owner');
  return query select gid, code;
end $function$;


-- =====================================================================
-- STEP 3: transfer_group_ownership cannot push the new owner over 8
-- Without this, someone could hand a group to a friend who is already at the limit.
-- =====================================================================
CREATE OR REPLACE FUNCTION public.transfer_group_ownership(p_group_id uuid, p_new_owner_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  if not is_group_owner(p_group_id) then raise exception 'only owner can transfer ownership'; end if;
  if not exists (select 1 from group_members where group_id = p_group_id and user_id = p_new_owner_id) then
    raise exception 'target user is not a member of the group';
  end if;

  -- Limit: the new owner may own at most 8 groups.
  perform pg_advisory_xact_lock(hashtext('create_group:' || p_new_owner_id::text));
  if (select count(*) from groups where owner_id = p_new_owner_id) >= 8 then
    raise exception 'group_limit_reached';
  end if;

  update group_members set role = 'admin' where group_id = p_group_id and user_id = auth.uid();
  update group_members set role = 'owner' where group_id = p_group_id and user_id = p_new_owner_id;
  update groups set owner_id = p_new_owner_id where id = p_group_id;
end $function$;


-- =====================================================================
-- STEP 4: a group cannot hold more than 200 tasks
-- Runs before every new group task is saved (after t_gtasks_identity, by name order).
-- =====================================================================
CREATE OR REPLACE FUNCTION public.enforce_group_task_limit()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  perform pg_advisory_xact_lock(hashtext('group_tasks:' || new.group_id::text));
  if (select count(*) from public.group_tasks where group_id = new.group_id) >= 200 then
    raise exception 'task_limit_reached';
  end if;
  return new;
end $function$;

drop trigger if exists t_gtasks_limit on public.group_tasks;
create trigger t_gtasks_limit before insert on public.group_tasks
  for each row execute function public.enforce_group_task_limit();


-- =====================================================================
-- STEP 5 (optional test): confirm the trigger and both functions exist
-- Expected: 3 rows.
-- =====================================================================
select 'trigger' as kind, tgname as name from pg_trigger where tgname = 't_gtasks_limit'
union all
select 'function', proname from pg_proc
where pronamespace = 'public'::regnamespace
  and proname in ('enforce_group_task_limit', 'create_group');
