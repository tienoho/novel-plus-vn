'use strict';

const fs = require('node:fs');
const { performance } = require('node:perf_hooks');

const BASE_URL = (process.env.LOAD_BASE_URL || 'https://localhost:15443').replace(/\/$/, '');
const VIRTUAL_USERS = positiveInt('LOAD_VUS', 500);
const RAMP_SECONDS = nonNegativeInt('LOAD_RAMP_SECONDS', 30);
const DURATION_SECONDS = positiveInt('LOAD_DURATION_SECONDS', 120);
const THINK_TIME_MS = nonNegativeInt('LOAD_THINK_TIME_MS', 1000);
const REQUEST_TIMEOUT_MS = positiveInt('LOAD_REQUEST_TIMEOUT_MS', 10000);
const REPORT_PATH = process.env.LOAD_REPORT_PATH || '';
const BOOK_ID = process.env.LOAD_BOOK_ID || '990000000000000001';
const CHAPTER_ID = process.env.LOAD_CHAPTER_ID || '990000000000000010';
const RUN_ID = normalizeRunId(process.env.LOAD_RUN_ID || new Date().toISOString().replace(/\D/g, '').slice(0, 14));

const READ_P95_LIMIT_MS = positiveInt('LOAD_READ_P95_LIMIT_MS', 750);
const WRITE_P95_LIMIT_MS = positiveInt('LOAD_WRITE_P95_LIMIT_MS', 1500);
const ERROR_RATE_LIMIT = positiveNumber('LOAD_ERROR_RATE_LIMIT', 0.01);

const metrics = {
    read: metricBucket(),
    discover: metricBucket(),
    write: metricBucket(),
};

let operationSequence = 0;

function metricBucket() {
    return { requests: 0, success: 0, failed: 0, durationMs: [], errors: {} };
}

function positiveInt(name, fallback) {
    const value = Number(process.env[name] ?? fallback);
    if (!Number.isInteger(value) || value < 1) {
        throw new Error(`${name} phải là số nguyên dương`);
    }
    return value;
}

function nonNegativeInt(name, fallback) {
    const value = Number(process.env[name] ?? fallback);
    if (!Number.isInteger(value) || value < 0) {
        throw new Error(`${name} phải là số nguyên không âm`);
    }
    return value;
}

function positiveNumber(name, fallback) {
    const value = Number(process.env[name] ?? fallback);
    if (!Number.isFinite(value) || value <= 0) {
        throw new Error(`${name} phải là số dương`);
    }
    return value;
}

function normalizeRunId(value) {
    if (!/^[A-Za-z0-9_-]{4,32}$/.test(value)) {
        throw new Error('LOAD_RUN_ID chỉ được gồm chữ, số, gạch ngang hoặc gạch dưới (4-32 ký tự)');
    }
    return value;
}

function operationFor(sequence) {
    const slot = sequence % 10;
    if (slot < 7) return 'read';
    if (slot < 9) return 'discover';
    return 'write';
}

function endpointFor(operation) {
    if (operation === 'read') {
        return {
            url: `${BASE_URL}/book/${BOOK_ID}/${CHAPTER_ID}.html`,
            init: { method: 'GET', headers: { Accept: 'text/html' } },
        };
    }
    if (operation === 'discover') {
        const page = 1 + (operationSequence % 3);
        return {
            url: `${BASE_URL}/book/searchByPage?curr=${page}&limit=20&bookName=Sao`,
            init: { method: 'GET', headers: { Accept: 'application/json' } },
        };
    }
    return {
        url: `${BASE_URL}/book/addVisitCount`,
        init: {
            method: 'POST',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
            },
            body: new URLSearchParams({ bookId: BOOK_ID }).toString(),
        },
    };
}

async function request(operation) {
    const bucket = metrics[operation];
    bucket.requests += 1;
    const startedAt = performance.now();
    let response;
    let errorKey = '';
    try {
        const endpoint = endpointFor(operation);
        response = await fetch(endpoint.url, {
            ...endpoint.init,
            signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
        });
        const body = await response.text();
        if (!response.ok) {
            errorKey = `http_${response.status}`;
        } else if (operation !== 'read') {
            const payload = JSON.parse(body);
            if (payload.code !== 200) {
                errorKey = `business_${payload.code ?? 'missing'}`;
            }
        } else if (!body || !/<html[\s>]/i.test(body)) {
            errorKey = 'invalid_html';
        }
    } catch (error) {
        errorKey = error && error.name === 'TimeoutError' ? 'timeout' : 'network_or_parse';
    } finally {
        bucket.durationMs.push(performance.now() - startedAt);
    }

    if (errorKey) {
        bucket.failed += 1;
        bucket.errors[errorKey] = (bucket.errors[errorKey] || 0) + 1;
    } else {
        bucket.success += 1;
    }
}

async function virtualUser(vuId, fullLoadEnd) {
    if (RAMP_SECONDS > 0) {
        await sleep(Math.floor((vuId - 1) * RAMP_SECONDS * 1000 / Math.max(VIRTUAL_USERS - 1, 1)));
    }
    while (Date.now() < fullLoadEnd) {
        const sequence = operationSequence++;
        await request(operationFor(sequence));
        if (THINK_TIME_MS > 0) {
            const jitter = Math.floor((vuId * 37 + sequence * 17) % Math.max(THINK_TIME_MS / 2, 1));
            await sleep(THINK_TIME_MS + jitter);
        }
    }
}

function sleep(milliseconds) {
    return new Promise(resolve => setTimeout(resolve, milliseconds));
}

function percentile(values, percentileValue) {
    if (values.length === 0) return 0;
    const ordered = [...values].sort((left, right) => left - right);
    const index = Math.min(ordered.length - 1, Math.ceil(percentileValue * ordered.length) - 1);
    return ordered[index];
}

function summarize(bucket) {
    return {
        requests: bucket.requests,
        success: bucket.success,
        failed: bucket.failed,
        errorRate: bucket.requests === 0 ? 0 : bucket.failed / bucket.requests,
        p50Ms: round(percentile(bucket.durationMs, 0.50)),
        p95Ms: round(percentile(bucket.durationMs, 0.95)),
        p99Ms: round(percentile(bucket.durationMs, 0.99)),
        maxMs: round(Math.max(0, ...bucket.durationMs)),
        errors: bucket.errors,
    };
}

function round(value) {
    return Math.round(value * 100) / 100;
}

async function main() {
    const startedAt = new Date();
    const fullLoadEnd = Date.now() + (RAMP_SECONDS + DURATION_SECONDS) * 1000;
    process.stdout.write(
        `Bắt đầu load test ${VIRTUAL_USERS} VU; ramp ${RAMP_SECONDS}s; giữ tải ${DURATION_SECONDS}s; runId=${RUN_ID}\n`,
    );
    await Promise.all(Array.from({ length: VIRTUAL_USERS }, (_, index) => virtualUser(index + 1, fullLoadEnd)));

    const read = summarize(metrics.read);
    const discover = summarize(metrics.discover);
    const write = summarize(metrics.write);
    const totalRequests = read.requests + discover.requests + write.requests;
    const totalFailed = read.failed + discover.failed + write.failed;
    const totalErrorRate = totalRequests === 0 ? 0 : totalFailed / totalRequests;
    const actualMix = {
        read: totalRequests === 0 ? 0 : read.requests / totalRequests,
        discover: totalRequests === 0 ? 0 : discover.requests / totalRequests,
        write: totalRequests === 0 ? 0 : write.requests / totalRequests,
    };

    const assertions = {
        hasRequests: totalRequests > 0,
        readP95: read.p95Ms < READ_P95_LIMIT_MS,
        discoverP95: discover.p95Ms < READ_P95_LIMIT_MS,
        writeP95: write.p95Ms < WRITE_P95_LIMIT_MS,
        errorRate: totalErrorRate < ERROR_RATE_LIMIT,
        workloadMix:
            Math.abs(actualMix.read - 0.70) <= 0.01 &&
            Math.abs(actualMix.discover - 0.20) <= 0.01 &&
            Math.abs(actualMix.write - 0.10) <= 0.01,
    };

    const report = {
        schemaVersion: 1,
        runId: RUN_ID,
        baseUrl: BASE_URL,
        startedAt: startedAt.toISOString(),
        finishedAt: new Date().toISOString(),
        configuration: {
            virtualUsers: VIRTUAL_USERS,
            rampSeconds: RAMP_SECONDS,
            durationSeconds: DURATION_SECONDS,
            thinkTimeMs: THINK_TIME_MS,
            requestTimeoutMs: REQUEST_TIMEOUT_MS,
            thresholds: {
                readP95Ms: READ_P95_LIMIT_MS,
                writeP95Ms: WRITE_P95_LIMIT_MS,
                errorRate: ERROR_RATE_LIMIT,
            },
        },
        total: {
            requests: totalRequests,
            failed: totalFailed,
            errorRate: totalErrorRate,
        },
        workloadMix: actualMix,
        categories: { read, discover, write },
        assertions,
        passed: Object.values(assertions).every(Boolean),
    };

    const serialized = `${JSON.stringify(report, null, 2)}\n`;
    process.stdout.write(serialized);
    if (REPORT_PATH) {
        fs.writeFileSync(REPORT_PATH, serialized, { encoding: 'utf8', mode: 0o600 });
    }
    if (!report.passed) {
        process.exitCode = 1;
    }
}

main().catch(error => {
    process.stderr.write(`${error && error.stack ? error.stack : error}\n`);
    process.exitCode = 1;
});
