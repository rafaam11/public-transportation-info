import { cloudflareTest } from "@cloudflare/vitest-pool-workers";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.jsonc" },
      miniflare: {
        bindings: {
          NAVER_SEARCH_CLIENT_ID: "test-client-id",
          NAVER_SEARCH_CLIENT_SECRET: "test-client-secret",
        },
      },
    }),
  ],
});
