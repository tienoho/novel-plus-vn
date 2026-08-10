import { expect, test } from '@playwright/test';
import {
  apiJson,
  assertSecureAuthState,
  assertVietnameseDocument,
  BOOK_ID,
  BOOK_NAME,
  currentTheme,
  gotoOk,
  PAID_CHAPTER_ID,
  PAID_CONTENT,
  TICKET_CHAPTER_ID,
  TICKET_CONTENT,
  VOTE_BOOK_ID,
} from './support';

test.describe('Độc giả đã đăng nhập', () => {
  test('JWT chỉ tồn tại trong cookie HttpOnly và hồ sơ trả metadata', async ({ page }) => {
    await gotoOk(page, '/');
    await assertSecureAuthState(page);
    const profile = await apiJson<any>(page, '/user/userInfo');
    expect(profile.__httpStatus).toBe(200);
    expect(profile.code).toBe(200);
    expect(profile.data.username).toMatch(/^090100000[1-4]$/);
    expect(JSON.stringify(profile)).not.toMatch(/accessToken|refreshToken|jwt/i);
  });

  test('tủ sách và lịch sử đọc hiển thị dữ liệu của độc giả', async ({ page }) => {
    await gotoOk(page, '/user/favorites.html');
    await expect(page.locator('body')).toContainText(BOOK_NAME);
    await assertVietnameseDocument(page);

    await gotoOk(page, '/user/read_history.html');
    await expect(page.locator('body')).toContainText(BOOK_NAME);
    await assertVietnameseDocument(page);
  });

  test('Vé đọc và catalog thuê bao tải được', async ({ page }) => {
    await gotoOk(page, '/user/reading_tickets.html');
    await expect(page.locator('#readingTicketBalance')).toContainText(/[12]/);
    await expect(page.locator('#readingTicketPlans')).toContainText('Gói E2E Tháng');
    await assertVietnameseDocument(page);

    const account = await apiJson<any>(page, '/user/reading-tickets');
    expect(account.code).toBe(200);
    expect(Number(account.data.availableBalance)).toBeGreaterThanOrEqual(1);

    const plans = await apiJson<any>(page, '/user/reading-subscriptions/plans');
    expect(plans.code).toBe(200);
    expect(plans.data.some((plan: any) => plan.planCode === 'E2E_MONTHLY')).toBe(true);
  });

  test('mở một chương VIP bằng Vé đọc có idempotency', async ({ page }, testInfo) => {
    const requestId = `ticket-${currentTheme()}-${testInfo.project.name}`;
    const unlock = await apiJson<any>(page,
      `/book/${BOOK_ID}/chapter/${TICKET_CHAPTER_ID}/reading-ticket-unlock`, {
        method: 'POST',
        body: { clientRequestId: requestId },
      });
    expect(unlock.__httpStatus).toBe(200);
    expect(unlock.code).toBe(200);

    await gotoOk(page, `/book/${BOOK_ID}/${TICKET_CHAPTER_ID}.html`);
    await expect(page.locator('body')).toContainText(TICKET_CONTENT);
    await expect(page.locator('[data-reader-action="buy-chapter"]')).toHaveCount(0);
  });

  test('mua chương VIP bằng Xu chỉ ghi nhận một lần', async ({ page }) => {
    await gotoOk(page, `/book/${BOOK_ID}/${PAID_CHAPTER_ID}.html`);
    const buyButton = page.locator('[data-reader-action="buy-chapter"]');
    if (await buyButton.count()) {
      await buyButton.click();
      await page.waitForLoadState('domcontentloaded');
    }
    await expect(page.locator('body')).toContainText(PAID_CONTENT);

    const wallet = await apiJson<any>(page, '/user/wallet/transactions?page=1&limit=20');
    expect(wallet.code).toBe(200);
    const purchases = wallet.data.items.filter((item: any) => item.businessType === 'CHAPTER_PURCHASE');
    expect(purchases).toHaveLength(1);
  });

  test('kỳ thường và kỳ đặc biệt chạy song song, vote dùng seasonId', async ({ page }, testInfo) => {
    const seasons = await apiJson<any>(page, '/book/monthly-ticket-seasons');
    expect(seasons.code).toBe(200);
    expect(seasons.data.map((season: any) => season.seasonType)).toEqual(
      expect.arrayContaining(['REGULAR', 'SPECIAL']),
    );
    const special = seasons.data.find((season: any) => season.seasonType === 'SPECIAL');
    expect(special).toBeTruthy();

    const summary = await apiJson<any>(page,
      `/book/${VOTE_BOOK_ID}/monthly-ticket-summary?seasonId=${special.seasonId}`);
    expect(summary.code).toBe(200);
    expect(summary.data.eligible).toBe(true);

    const vote = await apiJson<any>(page, `/book/${VOTE_BOOK_ID}/monthly-ticket-votes`, {
      method: 'POST',
      body: {
        seasonId: special.seasonId,
        amount: 1,
        clientRequestId: `vote-${currentTheme()}-${testInfo.project.name}`,
      },
    });
    expect(vote.__httpStatus).toBe(200);
    expect(vote.code).toBe(200);
    expect(Number(vote.data.bookTotal)).toBeGreaterThanOrEqual(1);
  });

  test('khu vực tác giả hoạt động với tài khoản tác giả', async ({ page }, testInfo) => {
    test.skip(currentTheme() !== 'green' || testInfo.project.name !== 'desktop-chromium',
      'Chỉ cần một luồng tác giả production-like; bốn theme đã được bao phủ ở front công khai/độc giả.');
    const runtimeErrors: string[] = [];
    page.on('pageerror', error => runtimeErrors.push(error.message));
    await gotoOk(page, '/author/index.html');
    await assertVietnameseDocument(page);
    await expect(page.locator('body')).toContainText(/tác phẩm|sáng tác|tác giả/i);

    await gotoOk(page, '/author/draft_list.html');
    await expect(page.locator('#draftEmpty')).toBeVisible();
    await assertVietnameseDocument(page);

    await gotoOk(page, `/author/author_analytics.html?bookId=${BOOK_ID}`);
    await expect(page.locator('#analyticsContent')).toBeVisible();
    await assertVietnameseDocument(page);
    expect(runtimeErrors).toEqual([]);
  });
});
