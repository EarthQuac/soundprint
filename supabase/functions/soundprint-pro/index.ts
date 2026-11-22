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

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) {
    console.error("Missing required Supabase function environment variables.");
    return reply(503, { error: "service_unavailable" });
  }

  let body: { action?: string };
  try {
    body = await req.json();
  } catch {
    return reply(400, { error: "invalid_json" });
  }

  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // Public, read-only availability: deliberately returns no account or entitlement details.
  if (body.action === "founder-availability") {
    const { data, error } = await admin.rpc("get_soundprint_pro_status", {
      p_user_id: null,
    });
    if (error) {
      console.error("Founder availability RPC failed:", error.message);
      return reply(503, { error: "status_unavailable" });
    }
    const status = data as Record<string, unknown>;
    return reply(200, {
      ok: true,
      availability: {
        founder_offer_enabled: status.founder_offer_enabled === true,
        founder_capacity: Number(status.founder_capacity ?? 100),
        founder_remaining: Number(status.founder_remaining ?? 0),
      },
    });
  }

  const authorization = req.headers.get("Authorization") ?? "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) return reply(401, { error: "authentication_required" });

  const authClient = createClient(url, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: authData, error: authError } = await authClient.auth.getUser(match[1]);
  if (authError || !authData.user) return reply(401, { error: "invalid_session" });

  const user = authData.user;
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
