import { expect, request as playwrightRequest, test } from '@playwright/test';
import { currentTheme, gotoOk } from './support';

test.describe('Biên quản trị và crawler', () => {
  test.beforeEach(({}, testInfo) => {
    test.skip(currentTheme() !== 'green' || testInfo.project.name !== 'desktop-chromium',
      'Backoffice độc lập theme; chạy một lần trên Chromium desktop.');
  });

  test('admin bắt buộc captcha và phát hành trang tiếng Việt', async ({ browser }) => {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'vi-VN' });
    const page = await context.newPage();
    const adminURL = process.env.PLAYWRIGHT_ADMIN_URL ?? 'https://admin.localhost:14443';
    const response = await page.goto(`${adminURL}/login`, { waitUntil: 'domcontentloaded' });
    expect(response?.status()).toBe(200);
    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
    await expect(page.locator('input[name="username"]')).toBeVisible();
    await expect(page.locator('input[name="password"]')).toBeVisible();
    await expect(page.locator('input[name="verify"]')).toBeVisible();
    await expect(page.locator('#imgVerify')).toHaveAttribute('src', /\S+/);
    await context.close();
  });

  test('crawler đăng nhập thật với CSRF và mở được màn quản lý', async ({ browser }) => {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'vi-VN' });
    const page = await context.newPage();
    const crawlURL = process.env.PLAYWRIGHT_CRAWL_URL ?? 'https://crawl.localhost:14443';
    const crawlOrigin = new URL(crawlURL);
    const loopbackURL = `https://127.0.0.1:${crawlOrigin.port || '443'}`;
    const api = await playwrightRequest.newContext({
      baseURL: loopbackURL,
      extraHTTPHeaders: { Host: crawlOrigin.host },
      ignoreHTTPSErrors: true,
    });
    try {
      const apiLoginPage = await api.get('/login.html');
      expect(apiLoginPage.status()).toBe(200);
      const loginHtml = await apiLoginPage.text();
      const csrfToken = loginHtml.match(/name="_csrf"\s+value="([^"]+)"/)?.[1];
      expect(csrfToken).toBeTruthy();
      const apiLogin = await api.post('/login', {
        form: {
          _csrf: csrfToken!,
          username: process.env.E2E_CRAWLER_USERNAME ?? 'admin',
          password: process.env.E2E_CRAWLER_PASSWORD ?? 'E2eCrawler!2026Secure',
        },
        maxRedirects: 0,
      });
      expect(apiLogin.status()).toBe(302);
      const redirect = apiLogin.headers().location;
      expect(redirect).toBeTruthy();
      expect(new URL(redirect, crawlURL).origin).toBe(crawlOrigin.origin);
    } finally {
      await api.dispose();
    }
    const response = await page.goto(`${crawlURL}/login.html`, { waitUntil: 'domcontentloaded' });
    expect(response?.status()).toBe(200);
    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
    await page.locator('input[name="username"]').fill(process.env.E2E_CRAWLER_USERNAME ?? 'admin');
    await page.locator('input[name="password"]').fill(
      process.env.E2E_CRAWLER_PASSWORD ?? 'E2eCrawler!2026Secure',
    );
    const loginResponse = page.waitForResponse(item =>
      item.request().method() === 'POST' && new URL(item.url()).pathname === '/login');
    await page.locator('button[type="submit"]').click();
    expect((await loginResponse).status()).toBeLessThan(400);
    await page.goto(`${crawlURL}/`, { waitUntil: 'domcontentloaded' });
    expect(new URL(page.url()).pathname).not.toContain('login');
    await expect(page.locator('body')).toContainText(/Thu thập|Nguồn|Tác vụ/);
    const cookies = await context.cookies();
    expect(cookies.some(cookie => cookie.name === 'JSESSIONID' && cookie.httpOnly && cookie.secure)).toBe(true);
    await context.close();
  });
});
