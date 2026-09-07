import { exportHandler } from "./handler.ts";
function assert(value: unknown) { if (!value) throw new Error("Assertion failed"); }
const request = () => new Request("https://example.invalid", { method: "POST", headers: { Authorization: "Bearer test" } });
Deno.test("unauthenticated exports cannot reach database", async () => {
  const handle = exportHandler(() => { throw new Error("Must not be called"); });
  assert((await handle(new Request("https://example.invalid", { method: "POST" }))).status === 401);
});
Deno.test("invalid sessions are rejected", async () => {
  const handle = exportHandler(() => ({ auth: { getUser: async () => ({ data: { user: null }, error: "invalid" }) }, rpc: () => { throw new Error("Must not query"); } }));
  assert((await handle(request())).status === 401);
});
Deno.test("query failures never return a partial successful export", async () => {
  const handle = exportHandler(() => ({ auth: { getUser: async () => ({ data: { user: { id: "a" } }, error: null }) }, rpc: async () => ({ data: null, error: "private database detail" }) }));
  const result = await handle(request());
  assert(result.status === 500);
  assert(!(await result.text()).includes("private database detail"));
});
Deno.test("successful export preserves complete snapshot and disables caching", async () => {
  const handle = exportHandler((authorization) => {
    assert(authorization === "Bearer test");
    return { auth: { getUser: async () => ({ data: { user: { id: "a", email: "a@example.invalid" } }, error: null }) }, rpc: async (name) => {
      assert(name === "export_closetai_account");
      return { data: { format: "ClosetAI Export v2", data: { wardrobe_items: Array.from({ length: 1001 }, (_, id) => ({ id })) } }, error: null };
    } };
  });
  const result = await handle(request());
  assert(result.status === 200 && result.headers.get("cache-control") === "no-store");
  const body = await result.json();
  assert(body.data.wardrobe_items.length === 1001 && body.user.id === "a");
});
