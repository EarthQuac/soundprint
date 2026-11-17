import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function readAuthTime(accessToken: string): number | null {
  try {
    const encoded = accessToken.split(".")[1];
    if (!encoded) return null;
    const normalized = encoded.replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=")));
    return typeof payload.auth_time === "number" ? payload.auth_time : null;
  } catch {
    return null;
  }
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json({ code: "method_not_allowed", message: "Use POST." }, 405);

  const authorization = request.headers.get("Authorization") ?? "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) return json({ code: "unauthorized", message: "Sign in again to continue." }, 401);
  const accessToken = match[1];

  const authTime = readAuthTime(accessToken);
  const now = Math.floor(Date.now() / 1000);
  if (authTime === null || authTime > now + 30 || now - authTime > 10 * 60) {
    return json({ code: "recent_verification_required", message: "Your verification has expired. Request a new six-digit code and verify your email again." }, 401);
  }

  let body: { confirmation?: unknown };
  try {
    body = await request.json();
  } catch {
    return json({ code: "invalid_body", message: "A valid confirmation is required." }, 400);
  }
  if (body.confirmation !== "SOUNDPRINT/DELETE") {
    return json({ code: "confirmation_required", message: "Type SOUNDPRINT/DELETE exactly to confirm." }, 400);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    return json({ code: "server_configuration_error", message: "Account deletion is temporarily unavailable." }, 500);
  }

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { data: { user }, error: userError } = await admin.auth.getUser(accessToken);
  if (userError || !user) {
    return json({ code: "unauthorized", message: "Your session is invalid. Sign in again." }, 401);
  }
  if (!user.email || !user.email_confirmed_at) {
    return json({ code: "email_not_verified", message: "Verify the account email before deleting this account." }, 403);
  }

  const { error: deleteError } = await admin.auth.admin.deleteUser(user.id);
  if (deleteError) {
    console.error("delete-account failed", { userId: user.id, message: deleteError.message });
    return json({ code: "delete_failed", message: "We couldn't delete the account. Please try again." }, 500);
  }

  return json({ success: true, message: "Your SoundPrint account has been permanently deleted." });
});
