import { createClient } from "npm:@supabase/supabase-js@2.115.0";
import { exportHandler } from "./handler.ts";

Deno.serve(exportHandler((authorization) => createClient(
  Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!,
  { global: { headers: { Authorization: authorization } }, auth: { persistSession: false } },
)));
