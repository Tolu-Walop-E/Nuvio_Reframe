// Serves the TV QR approve page and proxies session approval.
// Android points TV_LOGIN_WEB_BASE_URL at:
//   https://<project>.supabase.co/functions/v1/tv-login

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

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

  if (req.method === "GET") {
    const html = renderPage({ supabaseUrl, anonKey });
    return new Response(html, {
      headers: {
        ...corsHeaders,
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "no-store",
      },
    });
  }

  if (req.method === "POST") {
    try {
      const body = await req.json();
      const accessToken =
        typeof body?.access_token === "string" ? body.access_token.trim() : "";
      const code = typeof body?.code === "string" ? body.code.trim() : "";
      const deviceNonce =
        typeof body?.device_nonce === "string" ? body.device_nonce.trim() : "";

      if (!accessToken || !code || !deviceNonce) {
        return json({ error: "access_token, code, and device_nonce are required" }, 400);
      }
      if (!supabaseUrl || !anonKey) {
        return json({ error: "Server misconfigured" }, 500);
      }

      const userClient = createClient(supabaseUrl, anonKey, {
        global: {
          headers: { Authorization: `Bearer ${accessToken}` },
        },
        auth: { persistSession: false, autoRefreshToken: false },
      });

      const { error } = await userClient.rpc("approve_tv_login_session", {
        p_code: code,
        p_device_nonce: deviceNonce,
      });

      if (error) {
        return json({ error: error.message }, 400);
      }

      return json({ ok: true });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Unknown error";
      return json({ error: message }, 500);
    }
  }

  return json({ error: "Method not allowed" }, 405);
});

function json(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}

function renderPage(cfg: { supabaseUrl: string; anonKey: string }): string {
  const url = JSON.stringify(cfg.supabaseUrl);
  const key = JSON.stringify(cfg.anonKey);
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Nuvio TV Sign-in</title>
  <script src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.49.1/dist/umd/supabase.min.js"></script>
  <style>
    :root {
      --bg0: #0b0d10;
      --bg1: #141820;
      --card: #1a1f29;
      --text: #f3f5f7;
      --muted: #9aa3b2;
      --accent: #e50914;
      --ok: #3dd68c;
      --warn: #f5c451;
      --border: #2a3140;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      font-family: "Segoe UI", system-ui, sans-serif;
      color: var(--text);
      background:
        radial-gradient(900px 500px at 10% -10%, #3a1218 0%, transparent 55%),
        radial-gradient(700px 420px at 100% 0%, #102038 0%, transparent 50%),
        linear-gradient(160deg, var(--bg0), var(--bg1));
      display: grid;
      place-items: center;
      padding: 24px;
    }
    .card {
      width: min(440px, 100%);
      background: color-mix(in srgb, var(--card) 92%, black);
      border: 1px solid var(--border);
      border-radius: 18px;
      padding: 28px 24px 24px;
      box-shadow: 0 24px 60px rgba(0,0,0,.45);
    }
    .brand {
      letter-spacing: .18em;
      text-transform: uppercase;
      font-size: 12px;
      color: var(--muted);
      margin-bottom: 10px;
    }
    h1 {
      margin: 0 0 8px;
      font-size: 28px;
      font-weight: 700;
    }
    p { margin: 0 0 18px; color: var(--muted); line-height: 1.45; }
    label {
      display: block;
      font-size: 13px;
      color: var(--muted);
      margin: 12px 0 6px;
    }
    input {
      width: 100%;
      border-radius: 10px;
      border: 1px solid var(--border);
      background: #0f131a;
      color: var(--text);
      padding: 12px 14px;
      font-size: 16px;
    }
    input:focus {
      outline: 2px solid color-mix(in srgb, var(--accent) 65%, white);
      border-color: transparent;
    }
    button {
      width: 100%;
      margin-top: 18px;
      border: 0;
      border-radius: 10px;
      background: var(--accent);
      color: white;
      font-weight: 650;
      font-size: 16px;
      padding: 13px 16px;
      cursor: pointer;
    }
    button:disabled { opacity: .55; cursor: default; }
    .status {
      margin-top: 14px;
      min-height: 1.3em;
      font-size: 14px;
      color: var(--muted);
    }
    .status.ok { color: var(--ok); }
    .status.err { color: #ff7b7b; }
    .meta {
      margin-top: 18px;
      padding-top: 14px;
      border-top: 1px solid var(--border);
      font-size: 12px;
      color: var(--muted);
      word-break: break-all;
    }
    .hidden { display: none; }
  </style>
</head>
<body>
  <main class="card">
    <div class="brand">Nuvio</div>
    <h1>Approve TV sign-in</h1>
    <p id="subtitle">Sign in to approve this television. Then return to your TV.</p>

    <form id="form">
      <label for="email">Email</label>
      <input id="email" name="email" type="email" autocomplete="username" required />
      <label for="password">Password</label>
      <input id="password" name="password" type="password" autocomplete="current-password" required />
      <button id="submit" type="submit">Approve on TV</button>
    </form>

    <div id="done" class="hidden">
      <p class="status ok">Approved. You can close this page and finish on your TV.</p>
    </div>

    <div id="status" class="status"></div>
    <div class="meta" id="meta"></div>
  </main>

  <script>
    const SUPABASE_URL = ${url};
    const SUPABASE_ANON_KEY = ${key};
    const params = new URLSearchParams(location.search);
    const code = (params.get("code") || "").trim();
    const nonce = (params.get("nonce") || "").trim();
    const meta = document.getElementById("meta");
    const statusEl = document.getElementById("status");
    const form = document.getElementById("form");
    const done = document.getElementById("done");
    const submit = document.getElementById("submit");

    meta.textContent = code
      ? ("Session code: " + code)
      : "Missing code/nonce. Open this page from the TV QR link.";

    if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
      setStatus("This page is misconfigured (missing Supabase keys).", true);
      form.classList.add("hidden");
    } else if (!code || !nonce) {
      setStatus("Open this page from the QR code shown on your TV.", true);
      form.classList.add("hidden");
    }

    const supabase = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
      auth: { persistSession: true, autoRefreshToken: true }
    });

    function setStatus(message, isError) {
      statusEl.textContent = message || "";
      statusEl.className = "status" + (isError ? " err" : "");
    }

    async function approveWithSession(accessToken) {
      const response = await fetch(location.pathname.replace(/\\/?$/, ""), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "apikey": SUPABASE_ANON_KEY
        },
        body: JSON.stringify({
          access_token: accessToken,
          code: code,
          device_nonce: nonce
        })
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(payload.error || ("Approve failed (" + response.status + ")"));
      }
    }

    async function tryExistingSession() {
      const { data } = await supabase.auth.getSession();
      const token = data.session && data.session.access_token;
      if (!token || !code || !nonce) return false;
      setStatus("Existing session found. Approving TV…");
      await approveWithSession(token);
      form.classList.add("hidden");
      done.classList.remove("hidden");
      setStatus("");
      return true;
    }

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!code || !nonce) return;
      submit.disabled = true;
      setStatus("Signing in…");
      try {
        const email = document.getElementById("email").value.trim();
        const password = document.getElementById("password").value;
        const { data, error } = await supabase.auth.signInWithPassword({ email, password });
        if (error) throw error;
        const token = data.session && data.session.access_token;
        if (!token) throw new Error("Sign-in succeeded but no session token was returned.");
        setStatus("Approving TV…");
        await approveWithSession(token);
        form.classList.add("hidden");
        done.classList.remove("hidden");
        setStatus("");
      } catch (error) {
        setStatus(error.message || String(error), true);
      } finally {
        submit.disabled = false;
      }
    });

    tryExistingSession().catch((error) => {
      setStatus(error.message || String(error), true);
    });
  </script>
</body>
</html>`;
}
