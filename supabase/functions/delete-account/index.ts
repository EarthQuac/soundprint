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

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json({ code: "method_not_allowed", message: "Use POST." }, 405);

  const authorization = request.headers.get("Authorization") ?? "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) return json({ code: "unauthorized", message: "Sign in to continue." }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  if (!supabaseUrl || !serviceRoleKey || !anonKey) {
    return json({ code: "server_configuration_error", message: "Account deletion is temporarily unavailable." }, 500);
  }

  const accessToken = match[1];
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

  let body: { action?: unknown; email?: unknown; token?: unknown; confirmation?: unknown };
  try {
    body = await request.json();
  } catch {
    return json({ code: "invalid_body", message: "A valid request is required." }, 400);
  }

  if (body.action === "verify") {
    const email = typeof body.email === "string" ? body.email.trim().toLowerCase() : "";
    const token = typeof body.token === "string" ? body.token.trim() : "";
    if (email !== user.email.toLowerCase()) {
      return json({ code: "email_mismatch", message: "Enter the email address associated with this SoundPrint account." }, 403);
    }
    if (!/^\d{6}$/.test(token)) {
      return json({ code: "invalid_code", message: "Enter the six-digit verification code." }, 400);
    }

    const verifier = createClient(supabaseUrl, anonKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
    const { data: verified, error: verifyError } = await verifier.auth.verifyOtp({
      email,
      token,
      type: "email",
    });
    if (verifyError || !verified.user || verified.user.id !== user.id) {
      return json({ code: "verification_failed", message: "That code is invalid or expired. Request a new six-digit code and try again." }, 400);
    }

    const verifiedAt = Math.floor(Date.now() / 1000);
    const { error: updateError } = await admin.auth.admin.updateUserById(user.id, {
      app_metadata: {
        ...user.app_metadata,
        soundprint_delete_verified_at: verifiedAt,
      },
    });
    if (updateError) {
      console.error("delete-account verification stamp failed", { userId: user.id, message: updateError.message });
      return json({ code: "verification_record_failed", message: "We couldn't record your verification. Please request a new code." }, 500);
    }
    return json({ success: true, verified: true, expires_in_seconds: 600 });
  }

  if (body.action !== "delete") {
    return json({ code: "invalid_action", message: "Choose a valid account action." }, 400);
  }
  if (body.confirmation !== "SOUNDPRINT/DELETE") {
    return json({ code: "confirmation_required", message: "Type SOUNDPRINT/DELETE exactly to confirm." }, 400);
  }

  const verifiedAt = Number(user.app_metadata?.soundprint_delete_verified_at ?? 0);
  const now = Math.floor(Date.now() / 1000);
  if (!Number.isFinite(verifiedAt) || verifiedAt <= 0 || verifiedAt > now + 30 || now - verifiedAt > 10 * 60) {
    return json({ code: "recent_verification_required", message: "Your email verification has expired. Request a new six-digit code and verify your email again." }, 401);
  }

  const { error: deleteError } = await admin.auth.admin.deleteUser(user.id);
  if (deleteError) {
    console.error("delete-account failed", { userId: user.id, message: deleteError.message });
    return json({ code: "delete_failed", message: "We couldn't delete the account. Please try again." }, 500);
  }

  return json({ success: true, message: "Your SoundPrint account has been permanently deleted." });
});
