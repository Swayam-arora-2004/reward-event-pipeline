#!/usr/bin/env python3
"""Load test: send N purchase events (about 10% repeated eventIds) and check the database.

Usage: python3 scripts/load_test.py [N] [--base-url http://127.0.0.1:8080]
Needs: the app running (mvn spring-boot:run) and `docker compose up -d`. Standard library only.

The expected total is computed HERE from the unique events (sum of amount // 10), never from
the app or the database, so the comparison is independent. Every run uses its own id prefix,
so old data in the database does not affect the result.
"""
import argparse
import json
import random
import secrets
import subprocess
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

RUPEES_PER_POINT = 10
DUPLICATE_SHARE = 0.10
MEMBER_COUNT = 50
MIN_AMOUNT, MAX_AMOUNT = 1, 5000
WORKERS = 8
DRAIN_TIMEOUT_SECONDS = 120


def build_events(total, run_id):
    unique_count = total - int(total * DUPLICATE_SHARE)
    unique = []
    for i in range(unique_count):
        unique.append({
            "eventId": f"load-{run_id}-e{i}",
            "memberId": f"load-{run_id}-m{random.randrange(MEMBER_COUNT)}",
            "amountRupees": random.randint(MIN_AMOUNT, MAX_AMOUNT),
        })
    repeats = [random.choice(unique) for _ in range(total - unique_count)]
    to_send = unique + repeats
    random.shuffle(to_send)
    return unique, to_send


def post(base_url, event):
    request = urllib.request.Request(
        base_url + "/purchases", data=json.dumps(event).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def query_db(sql):
    """Runs a read-only SQL statement inside the postgres container as the app user."""
    shell = 'PGPASSWORD="$APP_DB_PASSWORD" psql -U "$APP_DB_USER" -d "$POSTGRES_DB" -tA -c "$0"'
    result = subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "sh", "-c", shell, sql],
        capture_output=True, text=True, check=True)
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("total", nargs="?", type=int, default=1000)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    args = parser.parse_args()

    run_id = secrets.token_hex(4)  # hex only, so it is safe inside the SQL below
    unique, to_send = build_events(args.total, run_id)
    expected_total = sum(e["amountRupees"] // RUPEES_PER_POINT for e in unique)

    started = time.time()
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        statuses = list(pool.map(lambda e: post(args.base_url, e), to_send))
    send_seconds = time.time() - started
    rejected = sum(1 for s in statuses if s != 202)

    prefix = f"load-{run_id}-"
    deadline = time.time() + DRAIN_TIMEOUT_SECONDS
    processed = 0
    while time.time() < deadline:
        processed = int(query_db(f"SELECT count(*) FROM processed_event WHERE event_id LIKE '{prefix}%'"))
        if processed >= len(unique):
            break
        time.sleep(0.5)
    elapsed = time.time() - started

    credited_total = int(query_db(
        f"SELECT COALESCE(SUM(balance), 0) FROM member_points WHERE member_id LIKE '{prefix}%'"))
    double_credits = max(0, credited_total - expected_total)

    print(f"run_id                 : {run_id}")
    print(f"events sent            : {len(to_send)} (rejected by API: {rejected})")
    print(f"unique events          : {len(unique)}")
    print(f"duplicates sent        : {len(to_send) - len(unique)}")
    print(f"processed_event rows   : {processed}")
    print(f"expected_total         : {expected_total}")
    print(f"credited_total         : {credited_total}")
    print(f"double-credits         : {double_credits}")
    print(f"send seconds           : {send_seconds:.2f}")
    print(f"elapsed seconds (total): {elapsed:.2f}")
    ok = rejected == 0 and processed == len(unique) and credited_total == expected_total
    print("RESULT                 : " + ("PASS" if ok else "FAIL"))
    raise SystemExit(0 if ok else 1)


if __name__ == "__main__":
    main()
