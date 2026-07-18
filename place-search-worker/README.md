# Daegu bus place-search Worker

Naver Local Search credentials stay in Cloudflare secrets. The Android app calls only `GET /v1/places?q=...`.

## Local verification

```powershell
npm install
npm run types
npm test
npm run typecheck
npm run check:types
npm run deploy:dry
```

Copy `.dev.vars.example` to `.dev.vars` only for local development. Never commit credentials.

## Production secrets and deploy

```powershell
npx wrangler secret put NAVER_SEARCH_CLIENT_ID
npx wrangler secret put NAVER_SEARCH_CLIENT_SECRET
npx wrangler deploy
```

The Worker limits clients to 30 requests per minute, returns at most five Daegu results, and caches normalized queries for ten minutes.
