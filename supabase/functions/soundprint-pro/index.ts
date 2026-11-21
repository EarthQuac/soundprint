import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.57.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

function reply(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return reply(405, { error: "method_not_allowed" });

  const authorization = req.headers.get("Authorization") ?? "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) return reply(401, { error: "authentication_required" });

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) {
    console.error("Missing required Supabase function environment variables.");
    return reply(503, { error: "service_unavailable" });
  }

  const token = match[1];
  const authClient = createClient(url, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: authData, error: authError } = await authClient.auth.getUser(token);
  if (authError || !authData.user) return reply(401, { error: "invalid_session" });

  const user = authData.user;
  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  let body: { action?: string };
  try {
    body = await req.json();
  } catch {
    return reply(400, { error: "invalid_json" });
  }

  if (body.action === "status") {
    const { data, error } = await admin.rpc("get_soundprint_pro_status", {
      p_user_id: user.id,
    });
    if (error) {
      console.error("Pro status RPC failed:", error.message);
      return reply(503, { error: "status_unavailable" });
    }
    return reply(200, { ok: true, status: data });
  }

  if (body.action === "claim-founder") {
    if (!user.email_confirmed_at) {
      return reply(403, { error: "verified_email_required" });
    }
    const { data, error } = await admin.rpc("claim_soundprint_founder_access", {
      p_user_id: user.id,
    });
    if (error) {
      const denied = error.code === "42501";
      console.error("Founder claim RPC failed:", error.message);
      return reply(denied ? 403 : 503, {
        error: denied ? "verified_email_required" : "founder_claim_unavailable",
      });
    }
    return reply(200, { ok: true, result: data });
  }

  return reply(400, { error: "unknown_action" });
});
