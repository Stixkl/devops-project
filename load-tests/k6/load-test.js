import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const loginDuration = new Trend('login_duration');
const analyticsDuration = new Trend('analytics_duration');

const AUTH_HOST = __ENV.AUTH_HOST || 'http://localhost:8180';
const DASHBOARD_HOST = __ENV.DASHBOARD_HOST || 'http://localhost:8084';
const GATEWAY_HOST = __ENV.GATEWAY_HOST || 'http://localhost:8087';
const FORM_HOST = __ENV.FORM_HOST || 'http://localhost:8086';

export const options = {
    stages: [
        { duration: '30s', target: 10 },
        { duration: '1m',  target: 20 },
        { duration: '30s', target: 50 },
        { duration: '1m',  target: 50 },
        { duration: '30s', target: 0  },
    ],
    thresholds: {
        errors:               ['rate<0.1'],
        http_req_duration:    ['p(95)<500'],
        login_duration:       ['p(95)<1000'],
        analytics_duration:   ['p(95)<300'],
    },
};

const TEST_USERS = [
    { username: 'health_user', password: 'password' },
    { username: 'staff_guard', password: 'password' },
    { username: 'super_admin', password: 'password' },
];

function login() {
    const user = TEST_USERS[Math.floor(Math.random() * TEST_USERS.length)];
    const start = Date.now();

    const res = http.post(
        `${AUTH_HOST}/api/v1/auth/login`,
        JSON.stringify({ username: user.username, password: user.password }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    loginDuration.add(Date.now() - start);
    errorRate.add(res.status !== 200);

    check(res, {
        'login 200':          (r) => r.status === 200,
        'token present':      (r) => r.json('token') !== undefined,
        'anonymousId present': (r) => r.json('anonymousId') !== undefined,
        'type is Bearer':     (r) => r.json('type') === 'Bearer',
    });

    return res.status === 200 ? res.json('token') : null;
}

function analyticsGroup(token) {
    const headers = token
        ? { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` }
        : { 'Content-Type': 'application/json' };

    group('analytics', () => {
        const start = Date.now();

        const summary = http.get(`${DASHBOARD_HOST}/api/v1/analytics/summary`, { headers });
        analyticsDuration.add(Date.now() - start);
        errorRate.add(summary.status !== 200);
        check(summary, { 'summary 200': (r) => r.status === 200 });

        const board = http.get(`${DASHBOARD_HOST}/api/v1/analytics/health-board`, { headers });
        errorRate.add(board.status !== 200);
        check(board, { 'health-board 200': (r) => r.status === 200 });

        const depts = ['Engineering', 'Medicine', 'Law', 'Business'];
        const dept = depts[Math.floor(Math.random() * depts.length)];
        const deptStats = http.get(`${DASHBOARD_HOST}/api/v1/analytics/department/${dept}`, { headers });
        errorRate.add(deptStats.status !== 200);

        const timeSeries = http.get(
            `${DASHBOARD_HOST}/api/v1/analytics/time-series?period=hourly&limit=24`,
            { headers }
        );
        errorRate.add(timeSeries.status !== 200);
        check(timeSeries, { 'time-series 200': (r) => r.status === 200 });
    });
}

function gateValidation() {
    group('gate-validation', () => {
        const fakeToken = `fake-token-${Math.random().toString(36).substr(2, 9)}`;
        const res = http.post(
            `${GATEWAY_HOST}/api/v1/gate/validate`,
            JSON.stringify({ token: fakeToken }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        check(res, {
            'gate validate responds': (r) => r.status === 200 || r.status === 400,
            'returns valid field':    (r) => r.json('valid') !== undefined || r.status === 400,
        });
    });
}

function surveySubmission() {
    group('survey-submission', () => {
        const anonymousId = `${Math.random().toString(36).substr(2, 8)}-0000-0000-0000-000000000000`;
        const res = http.post(
            `${FORM_HOST}/api/v1/surveys`,
            JSON.stringify({
                anonymousId: `00000000-0000-0000-0000-${Math.floor(Math.random() * 1e12).toString().padStart(12, '0')}`,
                hasFever: Math.random() < 0.3,
                hasCough: Math.random() < 0.2,
            }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        errorRate.add(res.status !== 200);
        check(res, { 'survey submitted': (r) => r.status === 200 });
    });
}

function visitorHandoff() {
    group('visitor-handoff', () => {
        const anonymousId = '00000000-0000-0000-0000-' + Math.floor(Math.random() * 1e12).toString().padStart(12, '0');
        const res = http.post(
            `${AUTH_HOST}/api/v1/auth/visitor/handoff`,
            JSON.stringify({ anonymousId }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        errorRate.add(res.status !== 200);
        check(res, {
            'handoff 200':         (r) => r.status === 200,
            'handoffPayload set':  (r) => r.json('handoffPayload') !== undefined,
        });
    });
}

export default function () {
    const token = login();
    sleep(0.5);

    analyticsGroup(token);
    sleep(0.3);

    gateValidation();
    sleep(0.3);

    surveySubmission();
    sleep(0.3);

    visitorHandoff();
    sleep(1);
}
