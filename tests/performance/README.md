# Performance Tests - Circle Guard

## Overview
Load and performance tests using Locust for the 6 Circle Guard microservices.

## Services Tested
- auth-service (port 8180)
- gateway-service (port 8087)
- form-service (port 8086)
- notification-service (port 8082)
- promotion-service (port 8088)
- identity-service (port 8083)

## User Classes
- **AuthServiceUser**: Login, visitor handoff, health checks
- **GatewayServiceUser**: Token validation, status lookups, health checks
- **FormServiceUser**: Survey submission (healthy/symptom), questionnaire retrieval
- **NotificationServiceUser**: Priority alerts, circle-fenced notifications
- **RampUpUser**: Mixed operations for gradual load increase
- **SpikeUser**: Burst of rapid login requests

## Quick Start

### Install dependencies
```bash
pip install -r requirements.txt
```

### Run all tests (headless)
```bash
locust -f locustfile.py --headless -u 50 -r 5 -t 5m --host http://localhost:8087 --html report.html
```

### Run specific user class
```bash
locust -f locustfile.py --headless -u 100 -r 10 --run-time 10m --host http://localhost:8087 --class-picker AuthServiceUser --html report.html
```

### Distributed load test
```bash
# Master
locust -f locustfile.py --master --bind-host 0.0.0.0

# Workers (run on separate machines)
locust -f locustfile.py --worker --master-host <master-ip>
```

### Stress test
```bash
locust -f locust_stress_test.py --headless -u 500 -r 50 --run-time 5m --host http://localhost:8087 --html stress_report.html
```

## Expected Results (to fill after execution)
| Metric | Auth | Gateway | Form | Notification |
|--------|------|---------|------|-------------|
| Avg Response Time | | | | |
| p95 Response Time | | | | |
| p99 Response Time | | | | |
| Throughput (RPS) | | | | |
| Error Rate | | | | |

## CI/CD Integration
Las pruebas de rendimiento corren en GitHub Actions (job `performance-test` de `.github/workflows/ci.yml`). Equivalente mínimo:
```yaml
performance-test:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-python@v5
      with: { python-version: '3.11' }
    - run: pip install locust
    - run: |
        locust -f tests/performance/locustfile.py --headless \
          -u 100 -r 10 --run-time 5m --host http://stage-api --html report.html
    - uses: actions/upload-artifact@v4
      if: always()
      with:
        name: performance-report
        path: report.html
```
