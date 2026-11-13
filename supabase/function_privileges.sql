-- =====================================================================
-- UniTask: only signed-in users may run the database functions
-- Run in: Supabase dashboard, SQL Editor. Run each STEP on its own, in order.
-- =====================================================================
--
-- WHY
-- By default Postgres lets everyone (the PUBLIC group) run a new function, and Supabase also
-- grants it to the "anon" role. The anon key is inside the APK, so anyone can call these functions
-- over the API without signing in. Every function already refuses a caller who is not signed in
-- (auth.uid() is null), so nothing leaks today. This closes the door at the permission level too,
-- so a future function that forgets that check is not exposed by accident.
--
-- WHAT CHANGES
-- Removes PUBLIC and anon access to 9 functions and keeps the "authenticated" role (signed-in
-- users). The app calls only create_group, join_group, update_group_profile,
-- regenerate_group_code, transfer_group_ownership and delete_my_account, always after sign-in.
-- is_member, is_group_admin and is_group_owner are used by the row level security policies, which
-- run as the signed-in user. The keep-alive workflow reads a table and is not affected.
--
-- NOT CHANGED
-- Trigger functions (touch_updated_at, enforce_message_identity, enforce_task_identity,
-- trim_group_messages). They cannot be called over the API and run as part of table writes.
-- Any function you added in the dashboard that is not listed here: STEP 1 shows it.


-- =====================================================================
-- STEP 1: look first (changes nothing)
-- Shows who can run each function in the public schema.
-- =====================================================================
select
  p.proname                                        as function,
  pg_get_function_identity_arguments(p.oid)        as arguments,
  has_function_privilege('anon', p.oid, 'EXECUTE')          as anon_can_run,
  has_function_privilege('authenticated', p.oid, 'EXECUTE') as signed_in_can_run
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
order by p.proname;
-- Expect anon_can_run = true for most rows (delete_my_account is already false).
-- If a function appears that is not in STEP 2 and the app calls it, tell Claude before running STEP 2.


-- =====================================================================
-- STEP 2: apply
-- =====================================================================
revoke all on function public.create_group(text, text, text)              from public, anon;
revoke all on function public.join_group(text, text, text)                from public, anon;
revoke all on function public.update_group_profile(uuid, text, text)      from public, anon;
revoke all on function public.regenerate_group_code(uuid)                 from public, anon;
revoke all on function public.transfer_group_ownership(uuid, uuid)        from public, anon;
revoke all on function public.is_member(uuid)                             from public, anon;
revoke all on function public.is_group_admin(uuid)                        from public, anon;
revoke all on function public.is_group_owner(uuid)                        from public, anon;
revoke all on function public.delete_my_account()                         from public, anon;

grant execute on function public.create_group(text, text, text)           to authenticated;
grant execute on function public.join_group(text, text, text)             to authenticated;
grant execute on function public.update_group_profile(uuid, text, text)   to authenticated;
grant execute on function public.regenerate_group_code(uuid)              to authenticated;
grant execute on function public.transfer_group_ownership(uuid, uuid)     to authenticated;
grant execute on function public.is_member(uuid)                          to authenticated;
grant execute on function public.is_group_admin(uuid)                     to authenticated;
grant execute on function public.is_group_owner(uuid)                     to authenticated;
grant execute on function public.delete_my_account()                      to authenticated;


-- =====================================================================
-- STEP 3: check (changes nothing, the transaction is rolled back)
-- 3a. Run STEP 1 again. Expect anon_can_run = false and signed_in_can_run = true for the 9 functions.
-- 3b. Run this block. It must fail with: permission denied for function create_group
-- =====================================================================
begin;
set local role anon;
select * from public.create_group('x', 'x', 'x');
rollback;

-- 3c. Then, in the app on your phone: open Groups, open a group, create or join a group, and open a
-- group task. If anything shows "permission denied" or fails, run the ROLLBACK block below and
-- send Claude the error.


-- =====================================================================
-- ROLLBACK (only if something broke): restores the old, open permissions
-- delete_my_account is left closed to anon because it was already closed before.
-- =====================================================================
-- grant execute on function public.create_group(text, text, text)           to public, anon;
-- grant execute on function public.join_group(text, text, text)             to public, anon;
-- grant execute on function public.update_group_profile(uuid, text, text)   to public, anon;
-- grant execute on function public.regenerate_group_code(uuid)              to public, anon;
-- grant execute on function public.transfer_group_ownership(uuid, uuid)     to public, anon;
-- grant execute on function public.is_member(uuid)                          to public, anon;
-- grant execute on function public.is_group_admin(uuid)                     to public, anon;
-- grant execute on function public.is_group_owner(uuid)                     to public, anon;
