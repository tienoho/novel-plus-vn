import { expect, Page, Response, TestInfo } from '@playwright/test';

export const BOOK_ID = '990000000000000001';
export const VOTE_BOOK_ID = '990000000000000004';
export const FREE_CHAPTER_ID = '990000000000000010';
export const PAID_CHAPTER_ID = '990000000000000011';
export const TICKET_CHAPTER_ID = '990000000000000012';
export const BOOK_NAME = 'Hành Trình Sao Việt';
export const FREE_CONTENT = 'Nội dung chương miễn phí dành cho kiểm thử E2E.';
export const PAID_CONTENT = 'Nội dung chương VIP đã được mở khóa an toàn.';
export const TICKET_CONTENT = 'Nội dung chương được mở bằng Vé đọc.';

const PASSWORD = 'E2eReader!2026Secure';
const THEME_USERS: Record<string, string> = {
  green: '0901000001',
  orange: '0901000002',
  dark: '0901000003',
  blue: '0901000004',
};

export function currentTheme(): string {
  return process.env.E2E_THEME ?? 'green';
}

export function readerCredentials(): { username: string; password: string } {
  const theme = currentTheme();
  const username = THEME_USERS[theme];
  if (!username) {
    throw new Error(`Theme E2E không được hỗ trợ: ${theme}`);
  }
  return { username, password: PASSWORD };
}

export function isMobile(testInfo: TestInfo): boolean {
  return testInfo.project.name.startsWith('mobile-');
}

export function monitorRuntimeErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(`pageerror: ${error.message}`));
  page.on('console', message => {
    if (message.type() === 'error') {
      errors.push(`console: ${message.text()}`);
    }
  });
  return errors;
}

export async function assertNoRuntimeErrors(errors: string[]): Promise<void> {
  expect(errors, errors.join('\n')).toEqual([]);
}

export async function assertVietnameseDocument(page: Page): Promise<void> {
  await expect(page.locator('html')).toHaveAttribute('lang', /^vi(?:-|$)/i);
  await expect(page).toHaveTitle(/\S+/);
  const visibleText = await page.locator('body').innerText();
  expect(visibleText).not.toMatch(/#\{[A-Za-z0-9_.-]+}/);
  expect(visibleText).not.toMatch(/\?\?[A-Za-z0-9_.-]+\?\?/);
}

export async function gotoOk(page: Page, path: string): Promise<Response> {
  const response = await page.goto(path, { waitUntil: 'domcontentloaded' });
  expect(response, `Không nhận được response khi mở ${path}`).not.toBeNull();
  expect(response!.status(), `HTTP không thành công tại ${path}`).toBeLessThan(400);
  return response!;
}

export async function authenticateReader(page: Page): Promise<Record<string, unknown>> {
  const credentials = readerCredentials();
  await gotoOk(page, '/user/login.html');
  await page.locator('#txtUName, #loginName').first().fill(credentials.username);
  await page.locator('#txtPassword, #password').first().fill(credentials.password);

  const result = await page.evaluate(async input => {
    const response = await fetch('/user/login', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
      body: new URLSearchParams(input).toString(),
    });
    return { status: response.status, payload: await response.json() };
  }, credentials);
  expect(result.status).toBe(200);
  const payload = result.payload as Record<string, unknown>;
  expect(payload.code).toBe(200);
  expect(JSON.stringify(payload)).not.toMatch(/accessToken|refreshToken|jwt/i);
  await assertSecureAuthState(page);
  return payload;
}

export async function assertSecureAuthState(page: Page): Promise<void> {
  const cookies = await page.context().cookies();
  const access = cookies.find(cookie => cookie.name === 'NovelAccess');
  const refresh = cookies.find(cookie => cookie.name === 'NovelRefresh');
  const csrf = cookies.find(cookie => cookie.name === 'XSRF-TOKEN');
  expect(access).toMatchObject({ httpOnly: true, secure: true, sameSite: 'Lax' });
  expect(refresh).toMatchObject({ httpOnly: true, secure: true, sameSite: 'Strict' });
  expect(csrf).toMatchObject({ httpOnly: false, secure: true, sameSite: 'Lax' });

  const storage = await page.evaluate(() => ({
    local: Object.fromEntries(Object.keys(localStorage).map(key => [key, localStorage.getItem(key)])),
    session: Object.fromEntries(Object.keys(sessionStorage).map(key => [key, sessionStorage.getItem(key)])),
  }));
  expect(JSON.stringify(storage)).not.toMatch(/NovelAccess|NovelRefresh|eyJ[A-Za-z0-9_-]+\./);
}

export async function apiJson<T = Record<string, unknown>>(
  page: Page,
  path: string,
  init?: { method?: string; body?: unknown },
): Promise<T> {
  if (page.url() === 'about:blank') {
    await gotoOk(page, '/');
  }
  return page.evaluate(async ({ endpoint, request }) => {
    const method = request?.method ?? 'GET';
    const csrf = document.cookie.split('; ')
      .find(value => value.startsWith('XSRF-TOKEN='))
      ?.slice('XSRF-TOKEN='.length);
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (request?.body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase()) && csrf) {
      headers['X-XSRF-TOKEN'] = decodeURIComponent(csrf);
    }
    const response = await fetch(endpoint, {
      method,
      credentials: 'same-origin',
      headers,
      body: request?.body === undefined ? undefined : JSON.stringify(request.body),
    });
    const payload = await response.json();
    return { ...payload, __httpStatus: response.status };
  }, { endpoint: path, request: init }) as Promise<T>;
}
