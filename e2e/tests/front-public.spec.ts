import { expect, test } from '@playwright/test';
import {
  assertNoRuntimeErrors,
  assertVietnameseDocument,
  BOOK_ID,
  BOOK_NAME,
  FREE_CHAPTER_ID,
  FREE_CONTENT,
  gotoOk,
  isMobile,
  monitorRuntimeErrors,
} from './support';

test.use({ storageState: { cookies: [], origins: [] } });

test.describe('Front công khai', () => {
  test('trang chủ render tiếng Việt và đúng lớp responsive', async ({ page }, testInfo) => {
    const errors = monitorRuntimeErrors(page);
    await gotoOk(page, '/');
    await assertVietnameseDocument(page);
    await expect(page.locator('body')).toContainText(/Trang chủ|Truyện/);
    if (isMobile(testInfo)) {
      await expect(page.locator('meta[name="viewport"]')).toHaveCount(1);
      expect(page.viewportSize()!.width).toBeLessThanOrEqual(430);
    } else {
      expect(page.viewportSize()!.width).toBeGreaterThanOrEqual(1200);
    }
    await assertNoRuntimeErrors(errors);
  });

  test('khám phá, chi tiết và chương miễn phí dùng fixture ổn định', async ({ page }) => {
    await gotoOk(page, '/book/bookclass.html?c=1&k=H%C3%A0nh%20Tr%C3%ACnh%20Sao%20Vi%E1%BB%87t');
    await assertVietnameseDocument(page);

    await gotoOk(page, `/book/${BOOK_ID}.html`);
    await assertVietnameseDocument(page);
    await expect(page.locator('body')).toContainText(BOOK_NAME);

    await gotoOk(page, `/book/${BOOK_ID}/${FREE_CHAPTER_ID}.html`);
    await assertVietnameseDocument(page);
    await expect(page.locator('body')).toContainText(FREE_CONTENT);
  });

  test('khách không đọc được API tài khoản', async ({ page }) => {
    await gotoOk(page, '/');
    const response = await page.request.get('/user/userInfo');
    expect(response.status()).toBe(200);
    const payload = await response.json();
    expect(payload.code).toBe(1001);
  });
});
