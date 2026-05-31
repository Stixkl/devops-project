from locust import HttpUser, task, between, events
from locust.runners import MasterRunner
import random
import uuid
import json

class AuthServiceUser(HttpUser):
    wait_time = between(0.5, 2)
    host = "http://localhost:8180"

    def on_start(self):
        self.username = f"user_{random.randint(1000, 9999)}"
        self.password = "password123"

    @task(10)
    def login(self):
        with self.client.post(
            "/api/v1/auth/login",
            json={"username": self.username, "password": self.password},
            catch_response=True
        ) as response:
            if response.status_code == 200:
                response.success()
            elif response.status_code == 401:
                response.success()
            else:
                response.failure(f"Unexpected status: {response.status_code}")

    @task(3)
    def visitor_handoff(self):
        anonymous_id = str(uuid.uuid4())
        with self.client.post(
            "/api/v1/auth/visitor/handoff",
            json={"anonymousId": anonymous_id},
            catch_response=True
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Status: {response.status_code}")

    @task(2)
    def health_check(self):
        self.client.get("/actuator/health")

class GatewayServiceUser(HttpUser):
    wait_time = between(0.3, 1.5)
    host = "http://localhost:8087"

    def on_start(self):
        self.token = self._get_test_token()

    def _get_test_token(self):
        try:
            resp = self.client.post(
                "http://localhost:8180/api/v1/auth/visitor/handoff",
                json={"anonymousId": str(uuid.uuid4())}
            )
            if resp.status_code == 200:
                return resp.json().get("token", "")
        except Exception:
            pass
        return ""

    @task(8)
    def validate_gate_token(self):
        token = self.token or "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test"
        self.client.post(
            "/api/v1/gate/validate",
            json={"token": token},
            catch_response=True
        )

    @task(5)
    def health_check(self):
        self.client.get("/actuator/health")

    @task(3)
    def get_user_status(self):
        anonymous_id = str(uuid.uuid4())
        self.client.get(f"/api/v1/status/{anonymous_id}", catch_response=True)

class FormServiceUser(HttpUser):
    wait_time = between(1, 3)
    host = "http://localhost:8086"

    @task(6)
    def submit_healthy_survey(self):
        survey = {
            "userId": str(uuid.uuid4()),
            "responses": {
                str(uuid.uuid4()): "NO",
                str(uuid.uuid4()): "NO",
                str(uuid.uuid4()): "NO"
            },
            "submittedAt": 1234567890
        }
        self.client.post("/api/v1/surveys", json=survey, catch_response=True)

    @task(4)
    def submit_symptom_survey(self):
        survey = {
            "userId": str(uuid.uuid4()),
            "responses": {
                str(uuid.uuid4()): "YES",
                str(uuid.uuid4()): "NO"
            },
            "submittedAt": 1234567890
        }
        self.client.post("/api/v1/surveys", json=survey, catch_response=True)

    @task(3)
    def get_questionnaires(self):
        self.client.get("/api/v1/questionnaires/health", catch_response=True)

    @task(2)
    def health_check(self):
        self.client.get("/actuator/health")

class NotificationServiceUser(HttpUser):
    wait_time = between(2, 5)
    host = "http://localhost:8082"

    @task(5)
    def send_priority_alert(self):
        alert = {
            "anonymousId": str(uuid.uuid4()),
            "status": random.choice(["CONFIRMED", "SUSPECT"]),
            "affectedCount": random.randint(1, 50),
            "eventType": "CONFIRMED_CASE",
            "timestamp": 1234567890
        }
        self.client.post("/api/v1/notifications/priority", json=alert, catch_response=True)

    @task(3)
    def circle_fenced_notification(self):
        notification = {
            "circleId": str(uuid.uuid4()),
            "locationId": f"building-{random.randint(1,5)}-floor-{random.randint(1,3)}",
            "name": f"Team {random.randint(1, 20)}",
            "timestamp": 1234567890
        }
        self.client.post("/api/v1/notifications/circle-fenced", json=notification, catch_response=True)

    @task(2)
    def health_check(self):
        self.client.get("/actuator/health")

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print(f"Performance test starting with {environment.runner.__class__.__name__}")

@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    print("Performance test completed")

def print_stats(stats):
    print("\n=== PERFORMANCE TEST RESULTS ===")
    print(f"Total Requests: {stats.num_requests}")
    print(f"Failed Requests: {stats.num_failures}")
    print(f"Median Response Time: {stats.median_response_time}ms")
    print(f"95th Percentile: {stats.get_response_time_percentile(0.95)}ms")
    print(f"99th Percentile: {stats.get_response_time_percentile(0.99)}ms")
    print(f"Avg Response Time: {stats.avg_response_time}ms")
    print(f"Requests/s: {stats.total_rps}")
    print("=" * 35)