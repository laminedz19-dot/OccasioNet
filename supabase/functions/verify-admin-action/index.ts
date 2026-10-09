// =============================================================================
// OccasioNet — Supabase Edge Function: verify-admin-action
// Provides server-side admin verification & short-lived signed receipt URLs
// =============================================================================
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.0";

Deno.serve(async (req: Request) => {
  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "UNAUTHORIZED: Missing bearer token" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

    const supabase = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
    });

    const { data: isAdmin, error: roleError } = await supabase.rpc("is_admin");
    if (roleError || !isAdmin) {
      return new Response(
        JSON.stringify({ error: "FORBIDDEN: المستخدم ليس مشرفاً مصرحاً له." }),
        { status: 403, headers: { "Content-Type": "application/json" } }
      );
    }

    const body = await req.json();
    if (body.action === "get_signed_receipt_url" && typeof body.storage_path === "string") {
      // Signed URL valid for 120 seconds only (no permanent public URLs for receipts)
      const { data, error } = await supabase.storage
        .from("payment-receipts")
        .createSignedUrl(body.storage_path, 120);

      if (error) {
        return new Response(JSON.stringify({ error: error.message }), {
          status: 400,
          headers: { "Content-Type": "application/json" },
        });
      }

      return new Response(JSON.stringify({ signed_url: data.signedUrl, expires_in: 120 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ is_admin: true }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
