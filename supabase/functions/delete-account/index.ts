import { createClient } from "npm:@supabase/supabase-js@2";
import { corsHeaders, json } from "../_shared/cors.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const url = Deno.env.get("SUPABASE_URL")!;
  const userClient = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
  const { data: { user } } = await userClient.auth.getUser();
  if (!user) return json({ error: "unauthorized" }, 401);
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });

  const { data: allowed } = await admin.rpc("take_rate_limit", {
    p_user: user.id, p_bucket: "delete_account", p_max: 5, p_window_seconds: 3600,
  });
  if (allowed !== true) return json({ error: "rate_limited" }, 429);

  let avatarError: string | null = null;
  for (let attempt = 0; attempt < 2; attempt++) {
    const { error } = await admin.storage.from("avatars").remove([`${user.id}.webp`]);
    avatarError = error?.message ?? null;
    if (!avatarError) break;
    console.error("avatar cleanup failed", attempt, avatarError);
  }
  if (avatarError) return json({ error: "delete_failed" }, 500);

  const { error } = await admin.auth.admin.deleteUser(user.id);
  if (error) { console.error("deleteUser failed", error.message); return json({ error: "delete_failed" }, 500); }
  return json({ ok: true });
});
