import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import playwright from '../e2e/node_modules/playwright/index.js';

const { chromium } = playwright;

const scriptDir = path.dirname(fileURLToPath(import.meta.url));

function required(name) {
    const value = process.env[name]?.trim();
    if (!value) {
        throw new Error(`Thiếu biến môi trường ${name}`);
    }
    return value;
}

const baseUrl = required('VNPAY_SMOKE_BASE_URL').replace(/\/$/, '');
const username = required('VNPAY_SMOKE_USERNAME');
const password = required('VNPAY_SMOKE_PASSWORD');
const cardNumber = required('VNPAY_SANDBOX_CARD_NUMBER');
const cardHolder = required('VNPAY_SANDBOX_CARD_HOLDER');
const cardDate = required('VNPAY_SANDBOX_CARD_DATE');
const otp = required('VNPAY_SANDBOX_OTP');
const reportPath = required('VNPAY_SMOKE_REPORT_PATH');
const expectedTmnCode = required('VNPAY_SMOKE_TMN_CODE');
const expectedAmountVnd = Number(required('VNPAY_SMOKE_AMOUNT_VND'));
const ipnMode = process.env.VNPAY_SMOKE_IPN_MODE?.trim() || 'forward';
const providerIpnTimeoutMs = Number(process.env.VNPAY_SMOKE_PROVIDER_IPN_TIMEOUT_MS || '120000');

if (!/^\d{14,19}$/.test(cardNumber) || !/^\d{6}$/.test(otp)) {
    throw new Error('Thẻ hoặc OTP Sandbox không đúng định dạng');
}
if (!['forward', 'provider'].includes(ipnMode) || !Number.isInteger(providerIpnTimeoutMs)
    || providerIpnTimeoutMs < 10_000 || providerIpnTimeoutMs > 600_000) {
    throw new Error('Chế độ hoặc timeout IPN Sandbox không hợp lệ');
}

async function login(page) {
    const response = await page.goto(`${baseUrl}/user/login.html`, { waitUntil: 'domcontentloaded' });
    if (!response || response.status() >= 400) {
        throw new Error('Không mở được trang đăng nhập Novel Plus');
    }
    const result = await page.evaluate(async credentials => {
        const loginResponse = await fetch('/user/login', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
            body: new URLSearchParams(credentials).toString(),
        });
        return { status: loginResponse.status, payload: await loginResponse.json() };
    }, { username, password });
    if (result.status !== 200 || result.payload?.code !== 200) {
        throw new Error(`Đăng nhập reader Sandbox thất bại: HTTP ${result.status}`);
    }
}

async function createOrder(page) {
    const result = await page.evaluate(async payAmount => {
        const csrf = document.cookie.split('; ')
            .find(value => value.startsWith('XSRF-TOKEN='))
            ?.slice('XSRF-TOKEN='.length);
        const response = await fetch('/pay/vnpay', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                ...(csrf ? { 'X-XSRF-TOKEN': decodeURIComponent(csrf) } : {}),
            },
            body: new URLSearchParams({ payAmount: String(payAmount) }).toString(),
        });
        return { status: response.status, payload: await response.json() };
    }, expectedAmountVnd);
    if (result.status !== 200 || result.payload?.code !== 200 || !result.payload?.paymentUrl) {
        throw new Error(`Không tạo được đơn VNPAY Sandbox: HTTP ${result.status}`);
    }
    const paymentUrl = new URL(result.payload.paymentUrl);
    const outTradeNo = paymentUrl.searchParams.get('vnp_TxnRef');
    if (paymentUrl.host !== 'sandbox.vnpayment.vn' || !/^\d+$/.test(outTradeNo ?? '')) {
        throw new Error('URL thanh toán Sandbox không hợp lệ');
    }
    return { paymentUrl: paymentUrl.toString(), outTradeNo };
}

async function payWithTestCard(page, paymentUrl) {
    let signedReturnUrl;
    page.on('request', request => {
        const url = new URL(request.url());
        if (url.origin === baseUrl && url.pathname === '/pay/vnpay/return') {
            signedReturnUrl = url.toString();
        }
    });

    await page.goto(paymentUrl, { waitUntil: 'networkidle' });
    await page.locator('[data-bs-target="#accordionList2"]').click();
    await page.locator('#NCB').click();
    await page.waitForLoadState('domcontentloaded');
    await page.locator('#card_number_mask').fill(cardNumber);
    await page.locator('#cardHolder').fill(cardHolder);
    await page.locator('#cardDate').fill(cardDate.replace('/', ''));
    await page.locator('#btnContinue').click();

    let previousUrl = page.url();
    await Promise.all([
        page.waitForURL(url => url.toString() !== previousUrl, { timeout: 30_000 }),
        page.locator('#btnAgree').click(),
    ]);
    if (!/OTP/i.test(await page.title())) {
        throw new Error('VNPAY Sandbox không chuyển sang màn OTP');
    }
    await page.locator('#otpvalue').fill(otp);
    previousUrl = page.url();
    await Promise.all([
        page.waitForURL(url => url.toString() !== previousUrl, { timeout: 30_000 }),
        page.locator('#btnConfirm').click(),
    ]);
    await page.waitForURL(url => url.origin === baseUrl && url.pathname === '/pay/index.html', {
        timeout: 30_000,
    });
    if (!signedReturnUrl) {
        throw new Error('Không thu được payload Return URL do VNPAY ký');
    }
    return signedReturnUrl;
}

function signedIpnPath(signedReturnUrl) {
    const signed = new URL(signedReturnUrl);
    return `/pay/vnpay/ipn?${signed.searchParams.toString()}`;
}

async function callSignedIpn(page, signedReturnUrl) {
    return page.evaluate(async endpoint => {
        const response = await fetch(endpoint, { credentials: 'same-origin' });
        return { status: response.status, payload: await response.json() };
    }, signedIpnPath(signedReturnUrl));
}

async function forwardSignedIpn(page, signedReturnUrl) {
    const first = await callSignedIpn(page, signedReturnUrl);
    const replay = await callSignedIpn(page, signedReturnUrl);
    if (first.status !== 200 || first.payload?.RspCode !== '00') {
        throw new Error(`IPN ký hợp lệ không được settlement: ${first.payload?.RspCode ?? first.status}`);
    }
    if (replay.status !== 200 || replay.payload?.RspCode !== '02') {
        throw new Error(`Replay IPN không idempotent: ${replay.payload?.RspCode ?? replay.status}`);
    }
    return { firstRspCode: first.payload.RspCode, replayRspCode: replay.payload.RspCode };
}

async function waitForSuccess(page, outTradeNo, timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    do {
        const result = await page.evaluate(async value => {
            const response = await fetch(`/pay/status/${value}`, { credentials: 'same-origin' });
            return response.json();
        }, outTradeNo);
        if (result.status === 'SUCCESS') {
            return;
        }
        await page.waitForTimeout(1_000);
    } while (Date.now() < deadline);
    throw new Error('Đơn VNPAY không chuyển sang SUCCESS sau IPN');
}

const browser = await chromium.launch({ headless: true });
try {
    const page = await browser.newPage();
    await login(page);
    const order = await createOrder(page);
    const signedReturnUrl = await payWithTestCard(page, order.paymentUrl);
    const signed = new URL(signedReturnUrl);
    const responseCode = signed.searchParams.get('vnp_ResponseCode');
    const transactionStatus = signed.searchParams.get('vnp_TransactionStatus');
    const tmnCode = signed.searchParams.get('vnp_TmnCode');
    const amount = Number(signed.searchParams.get('vnp_Amount')) / 100;
    if (responseCode !== '00' || transactionStatus !== '00' || tmnCode !== expectedTmnCode
        || amount !== expectedAmountVnd || signed.searchParams.get('vnp_TxnRef') !== order.outTradeNo) {
        throw new Error('Return URL VNPAY không khớp đơn hoặc không thành công');
    }
    let ipn;
    let providerServerDeliveryVerified = false;
    if (ipnMode === 'provider') {
        await waitForSuccess(page, order.outTradeNo, providerIpnTimeoutMs);
        const replay = await callSignedIpn(page, signedReturnUrl);
        if (replay.status !== 200 || replay.payload?.RspCode !== '02') {
            throw new Error(`Không chứng minh được provider IPN/replay: ${replay.payload?.RspCode ?? replay.status}`);
        }
        ipn = { firstRspCode: 'PROVIDER_DELIVERED', replayRspCode: replay.payload.RspCode };
        providerServerDeliveryVerified = true;
    } else {
        ipn = await forwardSignedIpn(page, signedReturnUrl);
        await waitForSuccess(page, order.outTradeNo, 10_000);
    }
    const report = {
        passed: true,
        environment: 'VNPAY_SANDBOX',
        amountVnd: expectedAmountVnd,
        outTradeNo: order.outTradeNo,
        responseCode,
        transactionStatus,
        firstIpnRspCode: ipn.firstRspCode,
        replayIpnRspCode: ipn.replayRspCode,
        providerServerDeliveryVerified,
        note: providerServerDeliveryVerified
            ? 'Đơn đã SUCCESS trước khi harness replay payload; VNPAY server-delivered IPN được xác minh.'
            : 'Payload VNPAY ký được harness chuyển tiếp vào IPN local; chưa thay thế IPN HTTPS do VNPAY gọi.',
    };
    fs.mkdirSync(path.dirname(reportPath), { recursive: true });
    fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
    console.log(JSON.stringify({
        passed: report.passed,
        responseCode,
        transactionStatus,
        firstIpnRspCode: ipn.firstRspCode,
        replayIpnRspCode: ipn.replayRspCode,
        providerServerDeliveryVerified,
    }));
} finally {
    await browser.close();
}
