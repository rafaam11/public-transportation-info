import { env } from "cloudflare:workers";
import { createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { afterEach, describe, expect, it, vi } from "vitest";
import worker, { type SearchEnv } from "../src/index";

const testEnv: SearchEnv = {
  PLACE_RATE_LIMITER: env.PLACE_RATE_LIMITER,
  NAVER_SEARCH_CLIENT_ID: "test-client-id",
  NAVER_SEARCH_CLIENT_SECRET: "test-client-secret",
};

async function invoke(input: string, init?: RequestInit, environment: SearchEnv = testEnv): Promise<Response> {
  const context = createExecutionContext();
  const response = await worker.fetch(new Request(input, init), environment, context);
  await waitOnExecutionContext(context);
  return response;
}

describe("place search worker", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("rejects unsupported methods and short queries before calling upstream", async () => {
    const upstream = vi.fn();
    vi.stubGlobal("fetch", upstream);

    expect((await invoke("https://worker.test/v1/places?q=대", { method: "GET" })).status).toBe(400);
    expect((await invoke("https://worker.test/v1/places?q=동대구", { method: "POST" })).status).toBe(405);
    expect(upstream).not.toHaveBeenCalled();
  });

  it("returns sanitized Daegu places with WGS84 coordinates", async () => {
    const upstream = vi.fn().mockImplementation(() => Promise.resolve(
      Response.json({
        items: [
          {
            title: "<b>동대구역</b>",
            category: "교통,수송>기차역",
            address: "대구광역시 동구 신암동",
            roadAddress: "대구광역시 동구 동대구로 550",
            mapx: "1286279200",
            mapy: "358796120",
          },
          {
            title: "서울역",
            category: "교통,수송>기차역",
            address: "서울특별시 용산구",
            roadAddress: "서울특별시 용산구 한강대로 405",
            mapx: "1269700000",
            mapy: "375540000",
          },
        ],
      }),
    ));
    vi.stubGlobal("fetch", upstream);

    const response = await invoke("https://worker.test/v1/places?q=동대구역");
    const body = await response.json<{ items: Array<Record<string, string | number>> }>();

    expect(response.status).toBe(200);
    expect(response.headers.get("cache-control")).toContain("max-age=600");
    expect(body.items).toEqual([
      {
        name: "동대구역",
        category: "교통,수송>기차역",
        address: "대구광역시 동구 신암동",
        roadAddress: "대구광역시 동구 동대구로 550",
        latitude: 35.879612,
        longitude: 128.62792,
      },
    ]);
    expect(JSON.stringify(body)).not.toContain("test-client-secret");
    expect(String(upstream.mock.calls[0]?.[0])).toContain("query=%EB%8F%99%EB%8C%80%EA%B5%AC%EC%97%AD+%EB%8C%80%EA%B5%AC");
  });

  it("maps upstream failures to a stable gateway error", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation(
      () => Promise.resolve(new Response("quota exceeded", { status: 429 })),
    ));

    const response = await invoke("https://worker.test/v1/places?q=수성못");

    expect(response.status).toBe(502);
    expect(await response.json()).toEqual({ error: "place_search_unavailable" });
  });

  it("rejects a client when the rate-limit binding is exhausted", async () => {
    const upstream = vi.fn();
    vi.stubGlobal("fetch", upstream);
    const limitedEnv: SearchEnv = {
      ...testEnv,
      PLACE_RATE_LIMITER: {
        limit: vi.fn().mockResolvedValue({ success: false }),
      } as RateLimit,
    };

    const response = await invoke("https://worker.test/v1/places?q=동대구역", {
      headers: { "cf-connecting-ip": "203.0.113.10" },
    }, limitedEnv);

    expect(response.status).toBe(429);
    expect(response.headers.get("retry-after")).toBe("60");
    expect(upstream).not.toHaveBeenCalled();
  });
});
