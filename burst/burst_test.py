import argparse
import concurrent.futures
import json
import statistics
import time
import urllib.error
import urllib.request


def send_request(base_url, show_id, seat, index):
    url = f"{base_url}/shows/{show_id}/reserve"

    payload = {
        "seats": [seat],
        "idempotencyKey": f"burst-{index}"
    }

    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer burst-user-{index}",
            "Content-Type": "application/json"
        },
        method="POST"
    )

    start = time.perf_counter()

    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            status = response.status
            response.read()

    except urllib.error.HTTPError as error:
        status = error.code
        error.read()

    except Exception as error:
        return {
            "status": "ERROR",
            "latency_ms": (time.perf_counter() - start) * 1000,
            "error": str(error)
        }

    return {
        "status": status,
        "latency_ms": (time.perf_counter() - start) * 1000
    }


def main():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--base-url",
        default="http://localhost:8080"
    )

    parser.add_argument(
        "--show-id",
        required=True
    )

    parser.add_argument(
        "--seat",
        default="A1"
    )

    parser.add_argument(
        "--requests",
        type=int,
        default=500
    )

    parser.add_argument(
        "--workers",
        type=int,
        default=100
    )

    args = parser.parse_args()

    print()
    print("Seat Reservation Burst Test")
    print("===========================")
    print(f"URL       : {args.base_url}")
    print(f"Show      : {args.show_id}")
    print(f"Seat      : {args.seat}")
    print(f"Requests  : {args.requests}")
    print(f"Workers   : {args.workers}")
    print()

    start = time.perf_counter()

    results = []

    with concurrent.futures.ThreadPoolExecutor(
            max_workers=args.workers
    ) as executor:

        futures = [
            executor.submit(
                send_request,
                args.base_url,
                args.show_id,
                args.seat,
                i
            )
            for i in range(args.requests)
        ]

        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())

    duration = time.perf_counter() - start

    successful = sum(
        1 for result in results
        if result["status"] == 201
    )

    conflicts = sum(
        1 for result in results
        if result["status"] == 409
    )

    unauthorized = sum(
        1 for result in results
        if result["status"] == 401
    )

    server_errors = sum(
        1 for result in results
        if isinstance(result["status"], int)
        and 500 <= result["status"] < 600
    )

    errors = sum(
        1 for result in results
        if result["status"] == "ERROR"
    )

    latencies = [
        result["latency_ms"]
        for result in results
        if "latency_ms" in result
    ]

    print("RESULTS")
    print("=======")
    print(f"Duration       : {duration:.2f}s")
    print(f"Requests       : {len(results)}")
    print(f"201 Created    : {successful}")
    print(f"409 Conflict   : {conflicts}")
    print(f"401 Unauthorized: {unauthorized}")
    print(f"5xx            : {server_errors}")
    print(f"Client errors  : {errors}")

    if latencies:
        print(f"Min latency    : {min(latencies):.2f} ms")
        print(f"Avg latency    : {statistics.mean(latencies):.2f} ms")
        print(f"Max latency    : {max(latencies):.2f} ms")

    print()

    expected_conflicts = args.requests - 1

    if (
            successful == 1
            and conflicts == expected_conflicts
            and server_errors == 0
            and errors == 0
    ):
        print("PASS")
        print(
            "Exactly one request confirmed the hot seat; "
            "all others were rejected."
        )
    else:
        print("FAIL")
        print(
            f"Expected: 1 x 201, "
            f"{expected_conflicts} x 409, "
            f"0 x 5xx"
        )


if __name__ == "__main__":
    main()