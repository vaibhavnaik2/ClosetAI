import { createClient } from "npm:@supabase/supabase-js@2.115.0";
import { deletionHandler, purgeStorage } from "./handler.ts";
const url = Deno.env.get("SUPABASE_URL")!;
const caller = (authorization: string) => createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
  global: { headers: { Authorization: authorization } }, auth: { persistSession: false },
});
const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
Deno.serve(deletionHandler({
  authenticate: async (authorization) => {
    const { data: { user }, error } = await caller(authorization).auth.getUser();
    return error ? null : user?.id ?? null;
  },
  lock: async (authorization) => {
    const { data, error } = await caller(authorization).rpc("begin_closetai_deletion");
    if (error || data !== true) throw new Error("Could not lock account");
  },
  purge: (userId) => purgeStorage(admin.storage.from("wardrobe-private"), userId),
  revoke: async (token) => { const { error } = await admin.auth.admin.signOut(token, "global"); if (error) throw error; },
  removeUser: async (userId) => { const { error } = await admin.auth.admin.deleteUser(userId); if (error) throw error; },
}));
