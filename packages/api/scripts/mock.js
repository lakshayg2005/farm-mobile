#!/usr/bin/env node
/**
 * Prism mock server
 * Usage: pnpm mock [spec-url-or-path]
 *
 * Defaults to the backend OpenAPI spec at http://localhost/api/v1/openapi.json
 * Runs on port 4010 so apps can point EXPO_PUBLIC_API_BASE_URL=http://localhost:4010/api/v1
 */
const { execSync } = require('child_process');

const spec = process.argv[2] ?? 'http://localhost/api/v1/openapi.json';
const port = process.env.MOCK_PORT ?? '4010';

console.log(`Starting Prism mock on :${port} with spec: ${spec}`);

try {
  execSync(
    `npx @stoplight/prism-cli mock "${spec}" --port ${port} --host 0.0.0.0`,
    { stdio: 'inherit' }
  );
} catch (e) {
  process.exit(1);
}
