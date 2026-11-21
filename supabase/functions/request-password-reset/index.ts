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

  let body: { email?: unknown };
  try {
    body = await req.json();
  } catch {
    return reply(400, { message: "Enter a valid email address." });
  }

  const email = typeof body.email === "string" ? body.email.trim().toLowerCase() : "";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 320) {
    return reply(400, { message: "Enter a valid email address." });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) {
    console.error("Password reset function is missing Supabase environment variables.");
    return reply(503, { message: "Password reset is temporarily unavailable. Please try again." });
  }

  const admin = createClient(url, serviceKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });

  // Search Auth users server-side. Never expose the service-role key to the Android app.
  let accountExists = false;
  for (let page = 1; page <= 100; page++) {
    const { data, error } = await admin.auth.admin.listUsers({ page, perPage: 1000 });
    if (error) {
      console.error("Password reset account lookup failed:", error.message);
      return reply(503, { message: "We couldn't check your account right now. Please try again." });
    }
    accountExists = data.users.some((user) => user.email?.trim().toLowerCase() === email);
    if (accountExists || data.users.length < 1000) break;
  }

  if (!accountExists) return reply(200, { sent: false });

  const auth = createClient(url, anonKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { error } = await auth.auth.resetPasswordForEmail(email, {
    redirectTo: "https://soundprint.anchorstuds.com/reset-password",
  });
  if (error) {
    console.error("Password reset email request failed:", error.message);
    return reply(503, { message: "We couldn't send your reset code right now. Please try again." });
  }

  return reply(200, { sent: true });
});
