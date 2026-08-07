// TV QR login token exchange.
// Consumes an approved tv_login_sessions row and mints a user session.
// Note: auth.admin.createSession is not available in supabase-js; we mint via
// admin generateLink + verifyOtp (hashed_token).

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    if (req.method !== "POST") {
      return jsonResponse({ error: "Method not allowed" }, 405);
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    if (!supabaseUrl || !serviceRoleKey || !anonKey) {
      return jsonResponse({ error: "Server misconfigured" }, 500);
    }

    const body = await req.json();
    const code = typeof body?.code === "string" ? body.code.trim() : "";
    const deviceNonce =
      typeof body?.device_nonce === "string" ? body.device_nonce.trim() : "";
    if (!code || !deviceNonce) {
      return jsonResponse({ error: "code and device_nonce are required" }, 400);
    }

    const admin = createClient(supabaseUrl, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    // Peek before consume so a mint failure does not burn the QR session.
    const { data: peekRows, error: peekError } = await admin
      .from("tv_login_sessions")
      .select("status, approved_user_id, expires_at")
      .eq("code", code.toUpperCase())
      .eq("device_nonce", deviceNonce)
      .limit(1);

    if (peekError) {
      return jsonResponse({ error: peekError.message }, 400);
    }

    const peek = Array.isArray(peekRows) ? peekRows[0] : null;
    if (!peek) {
      return jsonResponse({ error: "TV login session not found" }, 400);
    }
    if (peek.expires_at && new Date(peek.expires_at).getTime() <= Date.now()) {
      return jsonResponse({ error: "TV login session expired" }, 400);
    }
    if (peek.status !== "approved" || !peek.approved_user_id) {
      return jsonResponse(
        { error: `TV login session is not approved (status=${peek.status})` },
        400,
      );
    }

    const approvedUserId = peek.approved_user_id as string;

    const { data: userData, error: userError } = await admin.auth.admin
      .getUserById(approvedUserId);
    if (userError || !userData?.user?.email) {
      return jsonResponse(
        {
          error:
            userError?.message ??
            "Approved user has no email; cannot mint TV session",
        },
        500,
      );
    }

    const { data: linkData, error: linkError } = await admin.auth.admin
      .generateLink({
        type: "magiclink",
        email: userData.user.email,
      });

    const hashedToken = linkData?.properties?.hashed_token;
    if (linkError || !hashedToken) {
      return jsonResponse(
        {
          error:
            linkError?.message ??
            "Failed to generate auth link for approved TV login",
        },
        500,
      );
    }

    const anon = createClient(supabaseUrl, anonKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: verified, error: verifyError } = await anon.auth.verifyOtp({
      token_hash: hashedToken,
      type: "email",
    });

    if (verifyError || !verified?.session) {
      return jsonResponse(
        {
          error:
            verifyError?.message ??
            "Failed to create auth session for approved TV login",
        },
        500,
      );
    }

    const session = verified.session;
    if (!session.access_token || !session.refresh_token || !session.expires_in) {
      return jsonResponse({ error: "Incomplete session payload" }, 500);
    }

    // Mark used only after mint succeeds so retries remain possible.
    const { error: consumeError } = await admin.rpc(
      "consume_tv_login_session",
      {
        p_code: code,
        p_device_nonce: deviceNonce,
      },
    );
    if (consumeError) {
      // Tokens are already minted; still return them so the TV can sign in.
      console.error("consume_tv_login_session failed after mint:", consumeError);
    }

    return jsonResponse({
      access_token: session.access_token,
      refresh_token: session.refresh_token,
      token_type: session.token_type ?? "bearer",
      expires_in: session.expires_in,
      user: session.user ?? null,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown error";
    return jsonResponse({ error: message }, 500);
  }
});

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}
