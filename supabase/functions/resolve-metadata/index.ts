import { createClient } from "npm:@supabase/supabase-js@2";
import { corsHeaders, json } from "../_shared/cors.ts";

const PROVIDERS = new Set(["deezer", "itunes", "apple", "youtube"]);
const MAX_ITEMS = 50;
const ID_RE = /^[A-Za-z0-9_-]{1,32}$/;
const ART_HOST_SUFFIXES = [".dzcdn.net", ".mzstatic.com"];
const ART_HOSTS = new Set(["i.ytimg.com"]);

type Meta = {
  provider: string; id: string; title: string; durationMs: number; artwork: string | null;
  artist: { id: string; name: string } | null; album: { id: string; title: string } | null;
};

function safeArt(u: unknown): string | null {
  if (typeof u !== "string") return null;
  try {
    const url = new URL(u);
    if (url.protocol !== "https:") return null;
    const h = url.hostname.toLowerCase();
    return ART_HOSTS.has(h) || ART_HOST_SUFFIXES.some((s) => h.endsWith(s)) ? url.toString() : null;
  } catch { return null; }
}
const clip = (s: unknown, n: number) => String(s ?? "").slice(0, n);

async function getJson(url: string) {
  try {
    const r = await fetch(url, { signal: AbortSignal.timeout(6000) });
    return r.ok ? await r.json() : null;
  } catch { return null; }
}

async function fromDeezer(id: string): Promise<Meta | null> {
  const d = await getJson(`https://api.deezer.com/track/${id}`);
  if (!d || d.error || !d.id || !d.title) return null;
  const title = [d.title_short ?? d.title, d.title_version?.trim()].filter(Boolean).join(" ");
  return {
    provider: "deezer", id, title: clip(title, 300), durationMs: Math.max(0, Number(d.duration ?? 0)) * 1000,
    artwork: safeArt(d.album?.cover_xl ?? d.album?.cover_big),
    artist: d.artist?.id ? { id: String(d.artist.id), name: clip(d.artist.name, 200) } : null,
    album: d.album?.id ? { id: String(d.album.id), title: clip(d.album.title, 300) } : null,
  };
}

async function fromItunes(provider: string, id: string): Promise<Meta | null> {
  const d = await getJson(`https://itunes.apple.com/lookup?id=${id}&entity=song`);
  const t = d?.results?.find((r: any) => r.wrapperType === "track" && String(r.trackId) === id);
  if (!t) return null;
  const art = typeof t.artworkUrl100 === "string" ? t.artworkUrl100.replace(/\/\d+x\d+bb\./, "/600x600bb.") : null;
  return {
    provider, id, title: clip(t.trackName, 300), durationMs: Math.max(0, Number(t.trackTimeMillis ?? 0)),
    artwork: safeArt(art),
    artist: t.artistId ? { id: String(t.artistId), name: clip(t.artistName, 200) } : null,
    album: t.collectionId ? { id: String(t.collectionId), title: clip(t.collectionName, 300) } : null,
  };
}

async function fromYoutube(id: string): Promise<Meta | null> {
  if (!/^[A-Za-z0-9_-]{11}$/.test(id)) return null;
  const watch = encodeURIComponent(`https://www.youtube.com/watch?v=${id}`);
  const d = await getJson(`https://www.youtube.com/oembed?url=${watch}&format=json`);
  if (!d?.title) return null;
  const authorUrl = typeof d.author_url === "string" && d.author_url.startsWith("https://www.youtube.com/")
    ? d.author_url.slice(0, 200) : null;
  return {
    provider: "youtube", id, title: clip(d.title, 300), durationMs: 0,
    artwork: safeArt(`https://i.ytimg.com/vi/${id}/hqdefault.jpg`),
    artist: authorUrl ? { id: authorUrl, name: clip(String(d.author_name ?? "").replace(/ - Topic$/, ""), 200) } : null,
    album: null,
  };
}

const fetchMeta = (p: string, id: string) =>
  p === "deezer" ? fromDeezer(id) : p === "youtube" ? fromYoutube(id) : fromItunes(p, id);

async function pool<T, R>(items: T[], n: number, fn: (t: T) => Promise<R>): Promise<R[]> {
  const out: R[] = new Array(items.length);
  let i = 0;
  await Promise.all(Array.from({ length: Math.min(n, items.length) }, async () => {
    while (i < items.length) { const k = i++; out[k] = await fn(items[k]); }
  }));
  return out;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL")!;
  const userClient = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
  const { data: { user } } = await userClient.auth.getUser();
  if (!user) return json({ error: "unauthorized" }, 401);

  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
  const { data: allowed } = await admin.rpc("take_rate_limit", {
    p_user: user.id, p_bucket: "resolve", p_max: 30, p_window_seconds: 60,
  });
  if (allowed !== true) return json({ error: "rate_limited" }, 429);

  let body: any;
  try { body = await req.json(); } catch { return json({ error: "bad_json" }, 400); }
  const raw = Array.isArray(body?.items) ? body.items : null;
  if (!raw || raw.length === 0 || raw.length > MAX_ITEMS) return json({ error: "bad_items" }, 400);

  const wanted = new Map<string, { provider: string; id: string }>();
  for (const it of raw) {
    const provider = String(it?.provider ?? ""), id = String(it?.provider_id ?? "");
    if (!PROVIDERS.has(provider) || !ID_RE.test(id)) return json({ error: "bad_item" }, 400);
    wanted.set(`${provider}:${id}`, { provider, id });
  }

  const result: { id: string; provider: string; provider_song_id: string }[] = [];
  const byProvider = new Map<string, string[]>();
  for (const w of wanted.values()) byProvider.set(w.provider, [...(byProvider.get(w.provider) ?? []), w.id]);
  for (const [provider, ids] of byProvider) {
    const { data } = await admin.from("songs").select("id,provider,provider_song_id")
      .eq("provider", provider).in("provider_song_id", ids);
    for (const row of data ?? []) { result.push(row); wanted.delete(`${row.provider}:${row.provider_song_id}`); }
  }

  const failed: { provider: string; provider_id: string }[] = [];
  const metas: Meta[] = [];
  const fetched = await pool([...wanted.values()], 5, async (w) => ({ w, m: await fetchMeta(w.provider, w.id) }));
  for (const { w, m } of fetched) m ? metas.push(m) : failed.push({ provider: w.provider, provider_id: w.id });

  if (metas.length) {
    const artistRows = new Map<string, any>(), albumRows = new Map<string, any>();
    for (const m of metas) {
      if (m.artist) artistRows.set(`${m.provider}:${m.artist.id}`, { provider: m.provider, provider_artist_id: m.artist.id, name: m.artist.name || "Unknown artist" });
    }
    const artistId = new Map<string, string>();
    if (artistRows.size) {
      const { data, error } = await admin.from("artists").upsert([...artistRows.values()], { onConflict: "provider,provider_artist_id" }).select("id,provider,provider_artist_id");
      if (error) return json({ error: "db_error" }, 500);
      for (const a of data ?? []) artistId.set(`${a.provider}:${a.provider_artist_id}`, a.id);
    }
    for (const m of metas) {
      if (m.album) albumRows.set(`${m.provider}:${m.album.id}`, {
        provider: m.provider, provider_album_id: m.album.id, title: m.album.title || "Unknown album",
        artist_id: m.artist ? artistId.get(`${m.provider}:${m.artist.id}`) ?? null : null, artwork_url: m.artwork,
      });
    }
    const albumId = new Map<string, string>();
    if (albumRows.size) {
      const { data, error } = await admin.from("albums").upsert([...albumRows.values()], { onConflict: "provider,provider_album_id" }).select("id,provider,provider_album_id");
      if (error) return json({ error: "db_error" }, 500);
      for (const a of data ?? []) albumId.set(`${a.provider}:${a.provider_album_id}`, a.id);
    }
    const songRows = metas.map((m) => ({
      provider: m.provider, provider_song_id: m.id, title: m.title || "Unknown title", duration_ms: m.durationMs,
      artwork_url: m.artwork,
      artist_id: m.artist ? artistId.get(`${m.provider}:${m.artist.id}`) ?? null : null,
      album_id: m.album ? albumId.get(`${m.provider}:${m.album.id}`) ?? null : null,
    }));
    const { data, error } = await admin.from("songs").upsert(songRows, { onConflict: "provider,provider_song_id" }).select("id,provider,provider_song_id");
    if (error) return json({ error: "db_error" }, 500);
    result.push(...(data ?? []));
  }
  return json({ songs: result, failed });
});
