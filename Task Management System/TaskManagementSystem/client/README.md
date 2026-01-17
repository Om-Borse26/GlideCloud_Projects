# Client (React + Vite)

This folder contains the React + Vite frontend.

For full end-to-end setup (MongoDB + backend + client), see the repo root README.md.

## Local Development

1. Create a local env file:

   Copy-Item .env.example .env

2. Install dependencies and start the dev server:

   npm install
   npm run dev

The dev server proxies `/api` to `VITE_API_PROXY_TARGET`.

## Environment

See `.env.example`:

- `VITE_API_PROXY_TARGET`: dev proxy target for `/api` (example: http://localhost:8081)
- `VITE_API_BASE_URL`: optional base URL when deploying client separately from the API

## Quality Checks

    npm run lint
    npm run build
