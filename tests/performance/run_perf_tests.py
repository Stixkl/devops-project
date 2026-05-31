#!/usr/bin/env python3
"""
Circle Guard Performance Test Runner
Executes load tests and generates HTML reports
"""

import subprocess
import sys
import os
import datetime

SERVICES = {
    "auth-service": "http://localhost:8180",
    "gateway-service": "http://localhost:8087",
    "form-service": "http://localhost:8086",
    "notification-service": "http://localhost:8082",
    "promotion-service": "http://localhost:8088"
}

def run_performance_test(service_name, host, users=100, spawn_rate=10, duration="5m", html_report=None):
    """Run Locust performance test for a specific service"""
    print(f"\n{'='*60}")
    print(f"Running performance test for {service_name}")
    print(f"Target: {host}")
    print(f"Users: {users}, Spawn Rate: {spawn_rate}/s, Duration: {duration}")
    print(f"{'='*60}")

    if html_report is None:
        timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        html_report = f"report_{service_name}_{timestamp}.html"

    cmd = [
        sys.executable, "-m", "locust",
        "-f", "locustfile.py",
        "--headless",
        "--users", str(users),
        "--spawn-rate", str(spawn_rate),
        "--run-time", duration,
        "--host", host,
        "--html", html_report,
        "--csv", f"stats_{service_name}"
    ]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=int(duration[:-1]) * 60 + 120)
        print(result.stdout)
        if result.returncode == 0:
            print(f"✓ Test completed. Report saved: {html_report}")
            return True
        else:
            print(f"✗ Test failed: {result.stderr}")
            return False
    except subprocess.TimeoutExpired:
        print(f"✗ Test timed out after {duration}")
        return False
    except Exception as e:
        print(f"✗ Error running test: {e}")
        return False

def run_all_tests(users=100, spawn_rate=10, duration="5m"):
    """Run performance tests for all services"""
    print("\n" + "╔" + "═"*58 + "╗")
    print("║       Circle Guard Performance Test Suite              ║")
    print("╚" + "═"*58 + "╝")
    print(f"Start Time: {datetime.datetime.now()}")
    print(f"Configuration: {users} users, {spawn_rate}/s spawn rate, {duration} duration")

    results = {}
    for service_name, host in SERVICES.items():
        success = run_performance_test(service_name, host, users, spawn_rate, duration)
        results[service_name] = "PASS" if success else "FAIL"

    print("\n" + "═"*60)
    print("SUMMARY")
    print("═"*60)
    for service, status in results.items():
        icon = "✓" if status == "PASS" else "✗"
        print(f"{icon} {service}: {status}")

    passed = sum(1 for s in results.values() if s == "PASS")
    print(f"\nTotal: {passed}/{len(results)} tests passed")
    return all(results.values())

def parse_stats_csv(service_name):
    """Parse CSV statistics file"""
    csv_path = f"stats_{service_name}_stats.csv"
    if os.path.exists(csv_path):
        print(f"\nStatistics for {service_name}:")
        with open(csv_path, 'r') as f:
            lines = f.readlines()
            if len(lines) > 1:
                headers = lines[0].strip().split(',')
                values = lines[1].strip().split(',')
                for h, v in zip(headers, values):
                    print(f"  {h}: {v}")

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Circle Guard Performance Test Runner")
    parser.add_argument("-s", "--service", choices=list(SERVICES.keys()) + ["all"], default="all")
    parser.add_argument("-u", "--users", type=int, default=100)
    parser.add_argument("-r", "--spawn-rate", type=int, default=10)
    parser.add_argument("-d", "--duration", default="5m")
    parser.add_argument("--host", help="Override host URL")

    args = parser.parse_args()

    if args.service == "all":
        run_all_tests(args.users, args.spawn_rate, args.duration)
    else:
        host = args.host or SERVICES[args.service]
        run_performance_test(args.service, host, args.users, args.spawn_rate, args.duration)