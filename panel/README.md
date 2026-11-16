# Forbidden VOD — Control Panel

Manage customers from your phone: create activation codes, set expiry dates, limit TVs per
customer, push a plan's addons to every TV, send messages to TVs, and switch accounts off.

- **Panel website:** https://jaytatro.github.io/ForbiddenVOD/panel/
- **Database:** your own free [Supabase](https://supabase.com) project
- **TV app:** asks for an activation code on first launch once the panel is connected

## One-time setup (about 10 minutes)

1. **Create a Supabase project** at https://supabase.com (free plan is fine). Pick any name and a strong database password.
2. **Create the database:** in your project open **SQL Editor → New query**, paste everything from
   [`supabase.sql`](supabase.sql), and press **Run**.
3. **Create your login:** **Authentication → Users → Add user → Create new user** with your email and a password
   (tick *Auto Confirm User*).
4. **Make yourself admin:** back in **SQL Editor**, run this one line with your email:
   ```sql
   insert into public.admins (user_id) select id from auth.users where email = 'YOUR-EMAIL' on conflict do nothing;
   ```
5. **Turn off public sign-ups:** **Authentication → Sign In / Providers → Email** → turn off *Allow new users to sign up*.
6. **Send Claude two values** from **Project Settings → API** (or **Data API**):
   the **Project URL** and the **anon / publishable key**. (Both are meant to be public. Never share the
   *service_role / secret* key.) Claude wires them into the panel and the TV app and pushes a new APK.

## Everyday use

| To… | Do this |
|---|---|
| Add a customer | Customers → **+ New** → name, plan, length → **Create**. Send them the code. |
| Renew | Open the customer → **+1 month / +3 months / +1 year** |
| Stop someone | Open the customer → **Disable** (TVs lock at their next check, within 6 hours or on restart) |
| Free a TV slot | Open the customer → under TVs, **Remove** the old TV |
| Change everyone's addons | Plans → edit the plan's addon links → **Save** |
| Message every TV | TV messages → **+ New message** |

Customers add their own AIOStreams link (set up with their own TorBox) on their TV under **Addons**.
Don't put links containing your own TorBox key in a plan — every customer would be using your account.
