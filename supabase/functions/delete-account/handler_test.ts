import { deletionHandler, purgeStorage } from "./handler.ts";
const id = "00000000-0000-4000-8000-000000000001";
function assert(value: unknown) { if (!value) throw new Error("Assertion failed"); }
const request = (confirmation = "DELETE MY CLOSET") => new Request("https://example.invalid", { method: "POST", headers: { authorization: "Bearer test" }, body: JSON.stringify({ confirmation }) });
Deno.test("deletion orders lock, purge, revoke and account removal", async () => {
  const order: string[] = [];
  const handler = deletionHandler({ authenticate: async () => id, lock: async () => { order.push("lock"); }, purge: async () => { order.push("purge"); }, revoke: async () => { order.push("revoke"); }, removeUser: async () => { order.push("delete"); } });
  assert((await handler(request())).status === 200);
  assert(order.join(",") === "lock,purge,revoke,delete");
});
Deno.test("failed storage cleanup never deletes account or claims success", async () => {
  let deleted = false;
  const handler = deletionHandler({ authenticate: async () => id, lock: async () => {}, purge: async () => { throw new Error("storage failure"); }, revoke: async () => { throw new Error("must not revoke"); }, removeUser: async () => { deleted = true; } });
  assert((await handler(request())).status === 500 && !deleted);
});
Deno.test("confirmation must be exact before locking", async () => {
  let locked = false;
  const handler = deletionHandler({ authenticate: async () => id, lock: async () => { locked = true; }, purge: async () => {}, revoke: async () => {}, removeUser: async () => {} });
  assert((await handler(request("delete"))).status === 400 && !locked);
});
Deno.test("storage listing errors propagate", async () => {
  let removed = false;
  try {
    await purgeStorage({ list: async () => ({ data: null, error: "failed" }), remove: async () => { removed = true; return { error: null }; } }, id);
    throw new Error("Expected listing error");
  } catch (e) { assert((e as Error).message === "Storage listing failed" && !removed); }
});
Deno.test("storage traverses folders, pages and verifies cleanup", async () => {
  let erased = false;
  const removed: string[] = [];
  await purgeStorage({
    list: async (path, { offset }) => ({ data: erased || offset ? [] : path === id ? [{ name: "folder", id: null }] : [{ name: "photo.jpg", id: "file" }], error: null }),
    remove: async (paths) => { removed.push(...paths); erased = true; return { error: null }; },
  }, id);
  assert(removed[0] === `${id}/folder/photo.jpg`);
});
