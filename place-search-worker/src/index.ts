export interface SearchEnv extends Env {
  NAVER_SEARCH_CLIENT_ID: string;
  NAVER_SEARCH_CLIENT_SECRET: string;
}

interface NaverPlaceItem {
  title: string;
  category: string;
  address: string;
  roadAddress: string;
  mapx: string;
  mapy: string;
}

interface PlaceItem {
  name: string;
  category: string;
  address: string;
  roadAddress: string;
  latitude: number;
  longitude: number;
}

const NAVER_SEARCH_ORIGIN = "https://openapi.naver.com";
const NAVER_SEARCH_PATH = "/v1/search/local.json";
const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };

export default {
  async fetch(request: Request, env: SearchEnv, ctx: ExecutionContext): Promise<Response> {
    try {
      const url = new URL(request.url);
      if (url.pathname !== "/v1/places") return json({ error: "not_found" }, 404);
      if (request.method !== "GET") {
        return json({ error: "method_not_allowed" }, 405, { allow: "GET" });
      }

      const query = normalizeQuery(url.searchParams.get("q"));
      if (query === null) return json({ error: "invalid_query" }, 400);

      const actor = request.headers.get("cf-connecting-ip") ?? "unknown-client";
      const rateLimit = await env.PLACE_RATE_LIMITER.limit({ key: `places:${actor}` });
      if (!rateLimit.success) return json({ error: "rate_limited" }, 429, { "retry-after": "60" });

      const cacheKey = new Request(`https://place-search-cache.invalid/v1/places?q=${encodeURIComponent(query)}`);
      const cached = await caches.default.match(cacheKey);
      if (cached !== undefined) return cached;

      const upstreamUrl = new URL(NAVER_SEARCH_PATH, NAVER_SEARCH_ORIGIN);
      upstreamUrl.searchParams.set("query", `${query} 대구`);
      upstreamUrl.searchParams.set("display", "5");
      upstreamUrl.searchParams.set("sort", "random");
      const upstream = await fetch(upstreamUrl, {
        headers: {
          "X-Naver-Client-Id": env.NAVER_SEARCH_CLIENT_ID,
          "X-Naver-Client-Secret": env.NAVER_SEARCH_CLIENT_SECRET,
          accept: "application/json",
        },
      });
      if (!upstream.ok) {
        console.error(JSON.stringify({ message: "naver place search failed", status: upstream.status }));
        return json({ error: "place_search_unavailable" }, 502);
      }

      const payload: unknown = await upstream.json();
      const items = parseNaverItems(payload).slice(0, 5);
      const response = json({ items }, 200, { "cache-control": "public, max-age=600, s-maxage=600" });
      ctx.waitUntil(caches.default.put(cacheKey, response.clone()));
      return response;
    } catch (error) {
      console.error(JSON.stringify({
        message: "place search request failed",
        error: error instanceof Error ? error.message : String(error),
      }));
      return json({ error: "place_search_unavailable" }, 502);
    }
  },
} satisfies ExportedHandler<SearchEnv>;

function normalizeQuery(value: string | null): string | null {
  const normalized = value?.trim().replace(/\s+/g, " ") ?? "";
  return inRange(normalized.length, 2, 50) ? normalized : null;
}

function parseNaverItems(payload: unknown): PlaceItem[] {
  if (!isRecord(payload) || !Array.isArray(payload.items)) return [];
  return payload.items.map(parseNaverItem).filter((item): item is PlaceItem => item !== null);
}

function parseNaverItem(value: unknown): PlaceItem | null {
  if (!isNaverPlaceItem(value)) return null;
  if (!`${value.address} ${value.roadAddress}`.includes("대구")) return null;
  const longitude = Number(value.mapx) / 10_000_000;
  const latitude = Number(value.mapy) / 10_000_000;
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null;
  if (!inRange(longitude, 128, 129.2) || !inRange(latitude, 35.3, 36.3)) return null;
  return {
    name: stripMarkup(value.title),
    category: stripMarkup(value.category),
    address: value.address.trim(),
    roadAddress: value.roadAddress.trim(),
    latitude,
    longitude,
  };
}

function isNaverPlaceItem(value: unknown): value is NaverPlaceItem {
  return isRecord(value) && ["title", "category", "address", "roadAddress", "mapx", "mapy"]
    .every((key) => typeof value[key] === "string");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function stripMarkup(value: string): string {
  return value.replace(/<[^>]*>/g, "").replace(/&amp;/g, "&").replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">").replace(/&quot;/g, "\"").replace(/&#39;/g, "'").trim();
}

function json(
  body: object,
  status: number,
  extraHeaders: Record<string, string> = {},
): Response {
  return Response.json(body, { status, headers: { ...JSON_HEADERS, ...extraHeaders } });
}

function inRange(value: number, minimum: number, maximum: number): boolean {
  return value >= minimum && value <= maximum;
}
