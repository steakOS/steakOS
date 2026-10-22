// SPDX-License-Identifier: Apache-2.0
import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.STEAKOS_UI_URL || 'http://127.0.0.1:9001'
const apiURL = process.env.STEAKOS_API_URL || 'http://127.0.0.1:6060'

export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  metadata: { apiURL },
})
