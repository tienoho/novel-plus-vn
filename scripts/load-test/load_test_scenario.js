import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 20 },  // Ramp up to 20 users
        { duration: '1m', target: 50 },   // Sustained load with 50 VUs
        { duration: '30s', target: 0 },   // Ramp down to 0
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% of requests under 500ms
        http_req_failed: ['rate<0.01'],    // Error rate < 1%
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8083';
const ADMIN_URL = __ENV.ADMIN_URL || 'http://localhost:8080';

export default function () {
    // 1. Front Home Page
    let resHome = http.get(`${BASE_URL}/index.html`);
    check(resHome, {
        'front home status is 200': (r) => r.status === 200,
    });

    // 2. Front Payment Channels Endpoint
    let resChannels = http.get(`${BASE_URL}/pay/channels`);
    check(resChannels, {
        'pay channels status is 200': (r) => r.status === 200,
    });

    // 3. Actuator Health Check
    let resHealth = http.get(`${BASE_URL}/actuator/health`);
    check(resHealth, {
        'actuator health status is 200': (r) => r.status === 200,
    });

    // 4. Rate Limiter Load Test on Login Endpoint (Simulated)
    let loginPayload = JSON.stringify({
        username: `testuser_${__VU}`,
        password: 'password123',
    });
    let loginParams = {
        headers: { 'Content-Type': 'application/json' },
    };
    let resLogin = http.post(`${BASE_URL}/user/login`, loginPayload, loginParams);
    check(resLogin, {
        'login endpoint responded': (r) => r.status === 200 || r.status === 400 || r.status === 429,
    });

    sleep(1);
}
