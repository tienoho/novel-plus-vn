import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'https://localhost:14443';
const artifactSuffix = process.env.E2E_THEME ?? 'local';
const authState = path.join(__dirname, '.auth', `${artifactSuffix}.json`);

export default defineConfig({
  testDir: './tests',
  outputDir: `./test-results/${artifactSuffix}`,
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  timeout: 45_000,
  expect: {
    timeout: 10_000,
  },
  reporter: process.env.CI
    ? [['line'], ['html', { outputFolder: `playwright-report/${artifactSuffix}`, open: 'never' }]]
    : [['line'], ['html', { outputFolder: `playwright-report/${artifactSuffix}`, open: 'never' }]],
  use: {
    baseURL,
    ignoreHTTPSErrors: true,
    launchOptions: {
      args: ['--ignore-certificate-errors'],
    },
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 30_000,
  },
  projects: [
    {
      name: 'auth-setup',
      testMatch: /auth\.setup\.ts/,
      use: {
        ...devices['Desktop Chrome'],
      },
    },
    {
      name: 'desktop-chromium',
      dependencies: ['auth-setup'],
      testIgnore: /auth\.setup\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 1000 },
        storageState: authState,
      },
    },
    {
      name: 'mobile-chromium',
      dependencies: ['auth-setup'],
      testIgnore: /auth\.setup\.ts/,
      use: {
        ...devices['Pixel 7'],
        storageState: authState,
      },
    },
  ],
});
