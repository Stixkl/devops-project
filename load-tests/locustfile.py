"""
CircleGuard Load & Stress Tests
Run: locust -f locustfile.py --host=http://localhost:8180
Headless CI: locust -f locustfile.py --host=http://localhost:8180 --headless -u 20 -r 5 -t 60s
"""

from locust import HttpUser, task, between, events
import random
import uuid
import os

AUTH_HOST = os.getenv("AUTH_HOST", "http://localhost:8180")
DASHBOARD_HOST = os.getenv("DASHBOARD_HOST", "http://localhost:8084")
GATEWAY_HOST = os.getenv("GATEWAY_HOST", "http://localhost:8087")
FORM_HOST = os.getenv("FORM_HOST", "http://localhost:8086")

# Seeded users from V2__seed_test_users.sql
TEST_CREDENTIALS = [
    {"username": "health_user", "password": "password"},
    {"username": "staff_guard", "password": "password"},
    {"username": "super_admin", "password": "password"},
]


class AuthenticatedUser(HttpUser):
    """
    Simulates authenticated user flows: login → analytics → survey submission.
    """
    host = AUTH_HOST
    wait_time = between(1, 3)
    token = None

    def on_start(self):
        """Obtain JWT on session start."""
        creds = random.choice(TEST_CREDENTIALS)
        with self.client.post(
            "/api/v1/auth/login",
            json={"username": creds["username"], "password": creds["password"]},
            catch_response=True,
            name="auth/login [setup]",
        ) as resp:
            if resp.status_code == 200:
                self.token = resp.json().get("token")
                resp.success()
            else:
                resp.failure(f"Login failed: {resp.status_code}")
                self.token = None

    def _auth_headers(self):
        if self.token:
            return {"Authorization": f"Bearer {self.token}"}
        return {}

    @task(4)
    def get_analytics_summary(self):
        import requests
        requests.get(f"{DASHBOARD_HOST}/api/v1/analytics/summary", timeout=5)

    @task(3)
    def get_health_board(self):
        import requests
        requests.get(f"{DASHBOARD_HOST}/api/v1/analytics/health-board", timeout=5)

    @task(3)
    def get_time_series(self):
        import requests
        period = random.choice(["hourly", "daily"])
        limit = random.choice([24, 7, 30])
        requests.get(
            f"{DASHBOARD_HOST}/api/v1/analytics/time-series",
            params={"period": period, "limit": limit},
            timeout=5,
        )

    @task(2)
    def get_department_stats(self):
        import requests
        dept = random.choice(["Engineering", "Medicine", "Law", "Business", "Arts"])
        requests.get(
            f"{DASHBOARD_HOST}/api/v1/analytics/department/{dept}",
            timeout=5,
        )

    @task(2)
    def validate_qr_gate(self):
        import requests
        fake_token = str(uuid.uuid4())
        requests.post(
            f"{GATEWAY_HOST}/api/v1/gate/validate",
            json={"token": fake_token},
            timeout=5,
        )

    @task(1)
    def submit_health_survey(self):
        import requests
        anonymous_id = str(uuid.uuid4())
        requests.post(
            f"{FORM_HOST}/api/v1/surveys",
            json={
                "anonymousId": anonymous_id,
                "hasFever": random.choice([True, False]),
                "hasCough": random.choice([True, False]),
            },
            timeout=5,
        )

    @task(1)
    def visitor_handoff(self):
        """Visitor QR handoff — auth-service endpoint."""
        anonymous_id = str(uuid.uuid4())
        self.client.post(
            "/api/v1/auth/visitor/handoff",
            json={"anonymousId": anonymous_id},
            name="auth/visitor/handoff",
        )


class StressUser(HttpUser):
    """
    High-intensity stress: minimal think time, login + analytics spike.
    """
    host = AUTH_HOST
    wait_time = between(0.1, 0.5)
    token = None

    def on_start(self):
        resp = self.client.post(
            "/api/v1/auth/login",
            json={"username": "super_admin", "password": "password"},
            name="auth/login [stress-setup]",
        )
        if resp.status_code == 200:
            self.token = resp.json().get("token")

    @task(3)
    def spike_login(self):
        creds = random.choice(TEST_CREDENTIALS)
        self.client.post(
            "/api/v1/auth/login",
            json={"username": creds["username"], "password": creds["password"]},
            name="auth/login [stress]",
        )

    @task(2)
    def spike_visitor_handoff(self):
        self.client.post(
            "/api/v1/auth/visitor/handoff",
            json={"anonymousId": str(uuid.uuid4())},
            name="auth/visitor/handoff [stress]",
        )

    @task(1)
    def spike_invalid_login(self):
        """Test auth resilience under invalid credential flood."""
        self.client.post(
            "/api/v1/auth/login",
            json={"username": "attacker", "password": "wrongpass"},
            name="auth/login [invalid-stress]",
        )
