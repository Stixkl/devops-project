from locust import HttpUser, task, between, constant
import random
import uuid

class StressTestAuthService(HttpUser):
    wait_time = constant(0)
    host = "http://localhost:8180"

    @task(20)
    def rapid_login(self):
        user_num = random.randint(1, 10000)
        self.client.post(
            "/api/v1/auth/login",
            json={"username": f"user{user_num}", "password": "password123"},
            name="/api/v1/auth/login"
        )

class StressTestGateway(HttpUser):
    wait_time = constant(0)
    host = "http://localhost:8087"

    @task(30)
    def rapid_gate_validation(self):
        self.client.post(
            "/api/v1/gate/validate",
            json={"token": f"token_{uuid.uuid4()}"},
            name="/api/v1/gate/validate"
        )

class StressTestFormService(HttpUser):
    wait_time = constant(0)
    host = "http://localhost:8086"

    @task(15)
    def rapid_survey_submission(self):
        survey = {
            "userId": str(uuid.uuid4()),
            "responses": {str(uuid.uuid4()): random.choice(["YES", "NO"])},
            "submittedAt": 1234567890
        }
        self.client.post("/api/v1/surveys", json=survey, name="/api/v1/surveys")

class LoadSimulation(HttpUser):
    wait_time = between(0.1, 0.5)
    host = "http://localhost:8087"

    def on_start(self):
        try:
            resp = self.client.post(
                "http://localhost:8180/api/v1/auth/visitor/handoff",
                json={"anonymousId": str(uuid.uuid4())}
            )
            if resp.status_code == 200:
                self.token = resp.json().get("token", "")
            else:
                self.token = ""
        except Exception:
            self.token = ""

    @task(10)
    def mixed_operations(self):
        operations = [
            lambda: self.client.get("/actuator/health"),
            lambda: self.client.post("/api/v1/gate/validate",
                json={"token": self.token or str(uuid.uuid4())}),
            lambda: self.client.get("/api/v1/status/" + str(uuid.uuid4()), catch_response=True),
        ]
        random.choice(operations)()

print("""
╔═══════════════════════════════════════════════════════════════╗
║            Circle Guard Performance Test Suite                ║
║                                                               ║
║  Run with:                                                    ║
║  locust -f locustfile.py --headless -u 500 -r 50 --run-time 5m║
║                                                               ║
║  Or with custom host:                                         ║
║  locust -f locustfile.py --host=http://your-api.com          ║
║                                                               ║
║  For distributed test:                                       ║
║  locust -f locustfile.py --master                            ║
║  locust -f locustfile.py --worker --master-host=<master-ip>   ║
╚═══════════════════════════════════════════════════════════════╝
""")