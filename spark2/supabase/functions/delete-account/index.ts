// Deploy only to Spark's chosen Supabase project. No client contains admin keys.
// verify_jwt=false is intentional for modern key compatibility: this handler
// validates EVERY bearer token with the Auth server before doing anything.
const base = Deno.env.get("SUPABASE_URL")!;
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const jsonResponse = (status: number, message: string) => new Response(JSON.stringify({ message }), {
  status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
});
async function admin(path: string, method: string, body?: unknown) {
  const result = await fetch(`${base}/${path}`, {
    method, headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!result.ok) throw new Error("Cleanup step failed. Retry deletion or contact the project administrator.");
  return result;
}
Deno.serve(async (request) => {
  if (request.method !== "POST") return jsonResponse(405, "Use POST.");
  if (!base || !serviceKey) return jsonResponse(503, "Deletion is not configured.");
  const authorization = request.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) return jsonResponse(401, "Sign in first.");
  try {
    const auth = await fetch(`${base}/auth/v1/user`, { headers: { apikey: serviceKey, Authorization: authorization } });
    if (!auth.ok) return jsonResponse(401, "Sign in again before deleting your account.");
    const user = await auth.json();
    if (!user.id || !user.email || !/^[a-f0-9-]{36}$/.test(user.id)) return jsonResponse(403, "A verified email/password account is required.");
    const body = await request.json().catch(() => ({}));
    if (body.confirmation !== "DELETE" || typeof body.password !== "string" || body.password.length > 128) return jsonResponse(400, "Confirm deletion with your current password.");
    // Fresh server-side password verification, not user-editable JWT metadata.
    const reauth = await fetch(`${base}/auth/v1/token?grant_type=password`, {
      method: "POST", headers: { apikey: serviceKey, "Content-Type": "application/json" },
      body: JSON.stringify({ email: user.email, password: body.password }),
    });
    if (!reauth.ok) return jsonResponse(403, "Password verification failed. Please try again.");
    const verified = await reauth.json();
    if (verified.user?.id !== user.id) return jsonResponse(403, "Account verification failed.");

    // Tombstone FIRST: all policies deny this account and its media immediately,
    // even while an already-issued access token has not yet expired.
    await admin(`rest/v1/profiles?id=eq.${user.id}`, "PATCH", { deleting_at: new Date().toISOString() });
    const revoked = await fetch(`${base}/auth/v1/logout?scope=global`, {
      method: "POST", headers: { apikey: serviceKey, Authorization: `Bearer ${verified.access_token}` },
    });
    if (!revoked.ok && revoked.status !== 401 && revoked.status !== 403) throw new Error("Session revocation needs a retry.");

    // Delete physical Storage objects through Storage API, NEVER SQL-delete
    // storage.objects metadata (which would leave the actual bytes behind).
    for (const bucket of ["spark-media", "spark-avatars", "spark-videos", "spark-chat"]) {
      let complete = false;
      for (let batch = 0; batch < 20; batch++) {
        const listed = await admin(`storage/v1/object/list/${bucket}`, "POST", {
          prefix: `${user.id}/`, limit: 100, offset: 0, sortBy: { column: "name", order: "asc" },
        });
        const objects: Array<{ name: string }> = await listed.json();
        if (objects.length === 0) { complete = true; break; }
        if (objects.some((object) => !/^[a-f0-9-]{36}\.(jpg|mp4)$/.test(object.name))) throw new Error("Unexpected storage path. Ask the project administrator to complete cleanup.");
        await admin(`storage/v1/object/${bucket}`, "DELETE", { prefixes: objects.map((object) => `${user.id}/${object.name}`) });
      }
      if (!complete) return jsonResponse(503, "Cleanup is in progress. Retry deletion to process the remaining photos.");
    }
    await admin(`auth/v1/admin/users/${user.id}`, "DELETE");
    return jsonResponse(200, "Account and stored media deleted.");
  } catch {
    // Never log passwords, tokens, email addresses or upstream response bodies.
    return jsonResponse(503, "Deletion could not finish. Your content may already be hidden. Retry deletion with your password or ask the project administrator.");
  }
});
