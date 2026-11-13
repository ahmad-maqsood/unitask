-- =====================================================================
-- Remove group chat from the database (run in Supabase SQL editor)
--
-- WHEN TO RUN THIS
-- Run it only after the app version without group chat is released and your classmates have
-- updated. Older versions of the app still read the group_messages table, so dropping it earlier
-- only makes the chat tab of those old versions stop working.
--
-- WHAT IT DOES
-- 1. Replaces delete_my_account() with the same function minus the group_messages line.
--    (This MUST happen first, otherwise deleting an account would fail once the table is gone.)
-- 2. Stops the daily "delete-old-chat" job (only if pg_cron is enabled).
-- 3. Drops the chat triggers, the two chat functions and the group_messages table.
--    Dropping the table also removes its row level security policies, its indexes and its
--    realtime publication entry.
--
-- THIS DELETES ALL CHAT MESSAGES PERMANENTLY.
-- Optional: look at what exists first (changes nothing):
--   select count(*) as messages, count(distinct group_id) as groups from public.group_messages;
-- =====================================================================

begin;

-- 1) delete_my_account without the chat line (everything else is unchanged)
CREATE OR REPLACE FUNCTION public.delete_my_account()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
    v_user_id uuid := auth.uid();
    v_group record;
    v_new_owner_id uuid;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    -- 1. Personal data
    DELETE FROM public.group_done WHERE user_id = v_user_id;
    DELETE FROM public.tasks      WHERE user_id = v_user_id;
    DELETE FROM public.courses    WHERE user_id = v_user_id;
    DELETE FROM public.slots      WHERE user_id = v_user_id;
    DELETE FROM public.notes      WHERE user_id = v_user_id;

    -- 2. Owned groups: hand over to the oldest admin, else the oldest member, else delete the group
    FOR v_group IN SELECT id FROM public.groups WHERE owner_id = v_user_id LOOP
        v_new_owner_id := NULL;

        SELECT user_id INTO v_new_owner_id
        FROM public.group_members
        WHERE group_id = v_group.id AND user_id <> v_user_id AND role = 'admin'
        ORDER BY joined_at ASC
        LIMIT 1;

        IF v_new_owner_id IS NULL THEN
            SELECT user_id INTO v_new_owner_id
            FROM public.group_members
            WHERE group_id = v_group.id AND user_id <> v_user_id
            ORDER BY joined_at ASC
            LIMIT 1;
        END IF;

        IF v_new_owner_id IS NOT NULL THEN
            UPDATE public.group_members SET role = 'owner'
            WHERE group_id = v_group.id AND user_id = v_new_owner_id;
            UPDATE public.groups SET owner_id = v_new_owner_id
            WHERE id = v_group.id;
        ELSE
            DELETE FROM public.groups WHERE id = v_group.id;
        END IF;
    END LOOP;

    -- 3. Membership rows
    DELETE FROM public.group_members WHERE user_id = v_user_id;

    -- 4. Content the user authored in groups that still exist
    DELETE FROM public.group_tasks    WHERE created_by = v_user_id;
    DELETE FROM public.group_courses  WHERE created_by = v_user_id;

    -- 5. The login itself
    DELETE FROM auth.users WHERE id = v_user_id;
END;
$$;

-- 2) Stop the daily cleanup job, if it exists (does nothing when pg_cron is off or the job is missing)
do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.unschedule(jobid) from cron.job where jobname = 'delete-old-chat';
  end if;
end $$;

-- 3) Triggers, functions and the table itself
drop trigger  if exists t_gmsg_trim     on public.group_messages;
drop trigger  if exists t_gmsg_identity on public.group_messages;
drop function if exists public.trim_group_messages();
drop function if exists public.enforce_message_identity();
drop table    if exists public.group_messages;

commit;

-- Check afterwards (both should return no rows):
--   select tablename from pg_tables where schemaname = 'public' and tablename = 'group_messages';
--   select jobname from cron.job where jobname = 'delete-old-chat';
