export type DeletionDependencies = {
  authenticate(authorization: string): Promise<string | null>;
  lock(authorization: string): Promise<void>;
  purge(userId: string): Promise<void>;
  revoke(token: string): Promise<void>;
  removeUser(userId: string): Promise<void>;
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { "content-type": "application/json", "cache-control": "no-store" },
});
export function deletionHandler(deps: DeletionDependencies) {
  return async (req: Request): Promise<Response> => {
    if (req.method !== "POST") return json({ error: "POST required" }, 405);
    const authorization = req.headers.get("authorization") ?? "";
    if (!/^Bearer \S+$/i.test(authorization)) return json({ error: "Authentication required" }, 401);
    try {
      const userId = await deps.authenticate(authorization);
      if (!userId) return json({ error: "Invalid session" }, 401);
      const body = await req.json().catch(() => ({}));
      if (body.confirmation !== "DELETE MY CLOSET") return json({ error: "Exact confirmation phrase required" }, 400);
      await deps.lock(authorization);
      await deps.purge(userId);
      await deps.revoke(authorization.replace(/^Bearer /i, ""));
      await deps.removeUser(userId);
      return json({ deleted: true });
    } catch {
      return json({ error: "Deletion did not complete. Sign in and retry; contact support if it persists." }, 500);
    }
  };
}

type Storage = {
  list(path: string, options: { limit: number; offset: number; sortBy: { column: string; order: string } }): PromiseLike<{ data: { name: string; id?: string | null }[] | null; error: unknown }>;
  remove(paths: string[]): PromiseLike<{ error: unknown }>;
};
export async function purgeStorage(storage: Storage, userId: string): Promise<void> {
  if (!/^[0-9a-f-]{36}$/i.test(userId)) throw new Error("Invalid account identifier");
  const paths: string[] = [];
  async function walk(path: string, depth = 0) {
    if (depth > 32) throw new Error("Storage nesting exceeds safe limit");
    let offset = 0;
    while (true) {
      const { data, error } = await storage.list(path, { limit: 500, offset, sortBy: { column: "name", order: "asc" } });
      if (error || !data) throw new Error("Storage listing failed");
      if (!data.length) break;
      for (const row of data) {
        if (!row.name || row.name === "." || row.name === ".." || row.name.includes("/")) throw new Error("Invalid storage entry");
        const full = `${path}/${row.name}`;
        if (row.id) paths.push(full); else await walk(full, depth + 1);
        if (paths.length > 50000) throw new Error("Storage deletion requires a larger background job");
      }
      offset += data.length;
    }
  }
  await walk(userId);
  for (let start = 0; start < paths.length; start += 100) {
    const { error } = await storage.remove(paths.slice(start, start + 100));
    if (error) throw new Error("Storage removal failed");
  }
  paths.length = 0;
  await walk(userId);
  if (paths.length) throw new Error("Storage was not empty after removal");
}
