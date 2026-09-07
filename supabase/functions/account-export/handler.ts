type Client = {
  auth: { getUser(): Promise<{ data: { user: { id: string; email?: string } | null }; error: unknown }> };
  rpc(name: string): PromiseLike<{ data: unknown; error: unknown }>;
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: {
    "content-type": "application/json",
    "cache-control": "no-store",
    "content-disposition": "attachment; filename=closetai-export.json",
  } });
}

export function exportHandler(clientFor: (authorization: string) => Client) {
  return async (req: Request): Promise<Response> => {
    if (req.method !== "POST") return json({ error: "POST required" }, 405);
    const authorization = req.headers.get("authorization") ?? "";
    if (!/^Bearer \S+$/i.test(authorization)) return json({ error: "Authentication required" }, 401);
    try {
      const client = clientFor(authorization);
      const { data: { user }, error: authError } = await client.auth.getUser();
      if (authError || !user) return json({ error: "Invalid session" }, 401);
      const { data, error } = await client.rpc("export_closetai_account");
      if (error || !data || typeof data !== "object") return json({ error: "Export failed. Please retry." }, 500);
      return json({ ...data, user: { id: user.id, email: user.email ?? null } });
    } catch {
      return json({ error: "Export failed. Please retry." }, 500);
    }
  };
}
