import { expect, request as playwrightRequest, test } from '@playwright/test';
import { currentTheme, gotoOk } from './support';

async function adminPage(browser: Parameters<Parameters<typeof test>[0]>[0]['browser'], session: string) {
  const adminURL = process.env.PLAYWRIGHT_ADMIN_URL ?? 'https://admin.localhost:14443';
  const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'vi-VN' });
  await context.addCookies([{
    name: 'JSESSIONID', value: session, url: adminURL, httpOnly: true, secure: true, sameSite: 'Lax',
  }]);
  const page = await context.newPage();
  return { adminURL, context, page };
}

async function activeConfig(page: import('@playwright/test').Page): Promise<Record<string, unknown>> {
  return page.evaluate(async () => {
    const response = await fetch('/novel/gamification/settings/active', { credentials: 'same-origin' });
    const body = await response.json();
    return body.data.config;
  });
}

async function waitForActive(page: import('@playwright/test').Page, revisionCode: string) {
  await expect.poll(async () => String((await activeConfig(page)).revisionCode), {
    timeout: 20_000,
  }).toBe(revisionCode);
}

async function acceptPrompt(page: import('@playwright/test').Page, value: string, action: () => Promise<void>) {
  page.once('dialog', dialog => dialog.accept(value));
  await action();
}

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

  test('maker-checker vận hành runtime và policy qua giao diện Admin', async ({ browser }) => {
    const makerSession = process.env.E2E_ADMIN_MAKER_SESSION;
    const checkerSession = process.env.E2E_ADMIN_CHECKER_SESSION;
    expect(makerSession).toBeTruthy();
    expect(checkerSession).toBeTruthy();
    const maker = await adminPage(browser, makerSession!);
    const checker = await adminPage(browser, checkerSession!);
    try {
      await gotoOk(maker.page, `${maker.adminURL}/novel/gamification/settings`);
      await expect(maker.page.locator('#activeRevision')).toHaveText('e2e-enabled');
      await expect(maker.page.locator('#jobBatchSize')).toHaveAttribute('min', '1');
      await expect(maker.page.locator('#jobBatchSize')).toHaveAttribute('max', '100000');

      await acceptPrompt(maker.page, 'Import ENV để kiểm tra diff trên giao diện E2E', async () => {
        await maker.page.locator('#importEnvButton').click();
      });
      await expect(maker.page.locator('#selectedStatus')).toHaveText('Bản nháp');
      await expect(maker.page.locator('#diffKeys tr')).not.toHaveCount(0);
      const importedRevisionCode = await maker.page.locator('#selectedRevision').innerText();

      await acceptPrompt(maker.page, 'Clone ACTIVE để kiểm tra lifecycle maker-checker', async () => {
        await maker.page.locator('#cloneActiveButton').click();
      });
      await expect(maker.page.locator('#selectedRevision')).not.toHaveText(importedRevisionCode);
      await maker.page.locator('#jobBatchSize').fill('501');
      await maker.page.locator('#changeReason').fill('Điều chỉnh batch worker bằng Playwright E2E');
      await maker.page.locator('#saveDraftButton').click();
      await expect(maker.page.locator('#diffKeys')).toContainText('Kích thước batch worker');
      const revisionCode = await maker.page.locator('#selectedRevision').innerText();
      await maker.page.locator('#changeReason').fill('Gửi revision tuning để checker phê duyệt');
      await maker.page.locator('#submitButton').click();
      await expect(maker.page.locator('#selectedStatus')).toHaveText('Chờ phê duyệt');

      await gotoOk(checker.page, `${checker.adminURL}/novel/gamification/settings`);
      const queueRow = checker.page.locator('#approvalQueue tr', { hasText: revisionCode });
      await expect(queueRow).toContainText('#1');
      await queueRow.locator('button').click();
      await checker.page.locator('#changeReason').fill('Checker độc lập phê duyệt tuning E2E');
      await checker.page.locator('#approveButton').click();
      await expect(checker.page.locator('#selectedStatus')).toHaveText('Đã phê duyệt');
      await checker.page.locator('#changeReason').fill('Hẹn revision tuning kích hoạt ngay trong E2E');
      await expect(checker.page.locator('#scheduleButton')).toBeEnabled();
      await acceptPrompt(checker.page, new Date(Date.now() + 4_000).toISOString(), async () => {
        await checker.page.locator('#scheduleButton').click();
      });
      await waitForActive(checker.page, revisionCode);
      const activated = await activeConfig(checker.page);
      expect(String(activated.createdBy)).toBe('1');
      expect(String(activated.approvedBy)).toBe('990102');
      expect(activated.jobBatchSize).toBe(501);

      await checker.page.reload({ waitUntil: 'domcontentloaded' });
      const originalRow = checker.page.locator('#revisionHistory tr', { hasText: 'e2e-enabled' });
      await originalRow.locator('button').click();
      await checker.page.locator('#changeReason').fill('Rollback về snapshot E2E ban đầu sau kiểm thử');
      await checker.page.locator('#rollbackButton').click();
      await expect(checker.page.locator('#selectedRevision')).not.toHaveText('e2e-enabled');
      const rollbackCode = await checker.page.locator('#selectedRevision').innerText();
      await checker.page.locator('#changeReason').fill('Gửi revision rollback để khôi phục fixture E2E');
      await checker.page.locator('#submitButton').click();
      await checker.page.locator('#changeReason').fill('Phê duyệt rollback tuning rủi ro thấp');
      await checker.page.locator('#approveButton').click();
      await checker.page.locator('#changeReason').fill('Hẹn rollback kích hoạt để khôi phục fixture');
      await acceptPrompt(checker.page, new Date(Date.now() + 4_000).toISOString(), async () => {
        await checker.page.locator('#scheduleButton').click();
      });
      await waitForActive(checker.page, rollbackCode);
      expect((await activeConfig(checker.page)).jobBatchSize).toBe(500);

      await gotoOk(maker.page, `${maker.adminURL}/novel/gamification/policy-studio`);
      await maker.page.locator('input[name="policyVersion"]').fill('e2e-v2');
      await maker.page.locator('input[name="sourceVersion"]').fill('v1');
      await maker.page.locator('input[name="reason"]').fill('Tạo policy v2 bằng Playwright E2E');
      await maker.page.locator('#createPolicyForm button[type="submit"]').click();
      await expect(maker.page.locator('#policyHeading')).toContainText('e2e-v2');
      await maker.page.locator('#actionReason').fill('Bổ sung rule chống lạm dụng bắt buộc cho policy v2');
      await maker.page.locator('a[href="#abuseTab"]').click();
      const abuseForm = maker.page.locator('#abuseForm');
      await expect(abuseForm).toBeVisible();
      await abuseForm.locator('[name="reviewScoreThreshold"]').fill('50');
      await abuseForm.locator('[name="ruleCode"]').fill('DEVICE_VELOCITY_E2E');
      await abuseForm.locator('[name="metricName"]').selectOption('DEVICE_VOTES');
      await abuseForm.locator('[name="thresholdValue"]').fill('20');
      await abuseForm.locator('[name="windowMinutes"]').fill('60');
      await abuseForm.locator('[name="score"]').fill('25');
      await abuseForm.locator('button[type="submit"]').click();
      await expect(maker.page.locator('#abusePreview')).toContainText('DEVICE_VELOCITY_E2E');
      await maker.page.locator('#actionReason').fill('Gửi policy v2 để checker độc lập phê duyệt');
      await maker.page.locator('#submitPolicy').click();
      await expect(maker.page.locator('#policyHeading')).toContainText('Chờ phê duyệt');

      await gotoOk(checker.page, `${checker.adminURL}/novel/gamification/policy-studio`);
      await checker.page.locator('#policyList tr', { hasText: 'e2e-v2' }).locator('button').click();
      await checker.page.locator('#actionReason').fill('Checker phê duyệt policy v2 trong E2E');
      await checker.page.locator('#approvePolicy').click();
      await expect(checker.page.locator('#policyHeading')).toContainText('Đã phê duyệt');
      await checker.page.locator('#actionReason').fill('Phát hành policy v2 sau maker-checker');
      await checker.page.locator('#publishPolicy').click();
      await expect(checker.page.locator('#policyHeading')).toContainText('Đã phát hành');
    } finally {
      await maker.context.close();
      await checker.context.close();
    }
  });
});
