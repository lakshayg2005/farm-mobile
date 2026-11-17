# farm-mobile

Mobile apps for the Farm Platform, built by **Team 1**: the **customer app** (Produce tab for V1 buying, Land tab for V2 plot reservation) and the **delivery app** (pooled routes, pickup and proof of delivery).

All data comes from the shared backend ([farm-backend](https://github.com/lakshayg2005/farm-backend)) through the API gateway at `/api/v1`.
The typed API client is generated from the backend's OpenAPI specs (`pnpm gen:api`), and a mock server built from the same specs (`pnpm mock`) lets screens be built before a service is ready.

## Stack

React Native with Expo, TypeScript, Expo Router, TanStack Query, react-native-maps, Expo Notifications, EAS Build, pnpm workspace.

## Layout

```text
apps/customer/      customer app (Produce + Land tabs)
apps/delivery/      delivery partner app
packages/ui/        shared components (Button, Text, Card)
packages/api/       generated API client + auth + Prism mock script
```

## Getting Started

### Prerequisites

- Node 20+, pnpm 9+
- Expo Go on your phone **or** an iOS/Android simulator

### Install

```bash
pnpm install
```

### Run an app

```bash
# customer app
cd apps/customer && pnpm start

# delivery app
cd apps/delivery && pnpm start
```

Scan the QR code with Expo Go (Android) or the Camera app (iOS).

### Mock API server

Spins up a Prism mock server on port 4010 from the backend OpenAPI spec:

```bash
pnpm mock
# or against a local spec file:
pnpm mock ./openapi.json
```

Then set `EXPO_PUBLIC_API_BASE_URL=http://localhost:4010/api/v1` in your `.env`.

### Generate the API client

```bash
pnpm gen:api
```

Writes `packages/api/src/schema.d.ts` from the live spec. Commit the result.

### Lint & type-check

```bash
pnpm lint
pnpm type-check
```

## Workflow

- `main` is protected: changes go in only through a pull request with 1 approval.
- One issue per branch: `feat/<area>-<short-name>` or `fix/<area>-<short-name>`. Merge within 2–3 days.
- Commit messages follow Conventional Commits: `feat:`, `fix:`, `chore:`, `docs:`, `test:`.
- Squash-merge PRs and write `Closes #<issue>` in the PR description.
- Never commit `.env`; copy `.env.example` instead.
- Issues are labelled `team-app` (Team 1) and `team-web` (Team 2). Pick your slot (`M1`–`M4`) and assign yourself.
