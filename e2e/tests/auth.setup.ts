import { test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { authenticateReader, currentTheme } from './support';

test('tạo phiên độc giả dùng chung mà không vượt rate limit', async ({ page }) => {
  await authenticateReader(page);
  const authDir = path.join(__dirname, '..', '.auth');
  fs.mkdirSync(authDir, { recursive: true });
  await page.context().storageState({ path: path.join(authDir, `${currentTheme()}.json`) });
});
