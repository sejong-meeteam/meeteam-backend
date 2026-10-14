import time
import csv
import requests
from concurrent.futures import ThreadPoolExecutor

# ================= [설정 영역] =================
BASE_URL = "https://api.meeteam.alom-sejong.com"
LOGIN_URL = f"{BASE_URL}/api/v1/auth/login/sejong"

# 실제 학번/비밀번호로 변경하세요
STUDENT_ID = "21013220"
PASSWORD = "19980611"

TOTAL_RUNS = 10
CONCURRENT_REQUESTS = 2
INTERVAL = 5
# ===============================================

def send_login_request(session):
    try:
        payload = {"studentId": STUDENT_ID, "password": PASSWORD}
        headers = {"Content-Type": "application/json"}
        response = session.post(LOGIN_URL, json=payload, headers=headers, timeout=30)
        return response.elapsed.total_seconds() * 1000, response.status_code
    except Exception as e:
        print(f"  [에러] {e}")
        return None, None

def execute_benchmark(session):
    print(f"\n{'='*60}")
    print(f"  [Warm-up 활성화] 테스트")
    print(f"{'='*60}")
    print(f"{'Run':<6} | {'Avg Latency (ms)':<18} | {'Status':<10}")
    print("-" * 60)

    latencies = []

    for run in range(1, TOTAL_RUNS + 1):
        with ThreadPoolExecutor(max_workers=CONCURRENT_REQUESTS) as executor:
            futures = [executor.submit(send_login_request, session) for _ in range(CONCURRENT_REQUESTS)]
            results = [f.result() for f in futures]
            run_latencies = [r[0] for r in results if r[0] is not None]
            status_codes = [r[1] for r in results if r[1] is not None]

        if run_latencies:
            avg_latency = sum(run_latencies) / len(run_latencies)
            status = status_codes[0] if status_codes else "N/A"
            print(f"Run {run:<3} | {avg_latency:>14.2f} ms   | {status}")
            latencies.append(round(avg_latency, 2))
        else:
            print(f"Run {run:<3} | {'Failed':>14}      | -")
            latencies.append(0)

        if run < TOTAL_RUNS:
            time.sleep(INTERVAL)

    return latencies

def main():
    print("\n" + "=" * 60)
    print("  Warm-up 활성화 상태 테스트")
    print("=" * 60)
    print(f"  - URL: {LOGIN_URL}")
    print(f"  - 동시 요청 수: {CONCURRENT_REQUESTS}")
    print(f"  - 총 측정 횟수: {TOTAL_RUNS}")
    print("=" * 60)
    print("\n서버를 재시작한 후 (app.warmup.enabled=true) 엔터를 누르세요...")
    input()

    session = requests.Session()
    results = execute_benchmark(session)

    avg = sum(r for r in results if r > 0) / len([r for r in results if r > 0]) if any(results) else 0
    print(f"\n평균 레이턴시: {avg:.2f} ms")

    # CSV 저장
    csv_filename = "warmup_enabled_result.csv"
    with open(csv_filename, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["Run", "Latency (ms)"])
        for idx, latency in enumerate(results, 1):
            writer.writerow([idx, latency])
    print(f"결과 저장: '{csv_filename}'")

    # 그래프
    try:
        import matplotlib.pyplot as plt
        runs = list(range(1, TOTAL_RUNS + 1))

        fig, ax = plt.subplots(figsize=(8, 5))
        ax.plot(runs, results, marker='o', color='blue', linestyle='-', linewidth=2)
        ax.set_xlabel('Run Number', fontsize=11)
        ax.set_ylabel('Latency (ms)', fontsize=11)
        ax.set_xlim(0, TOTAL_RUNS + 1)
        ax.set_ylim(0, 1000)
        ax.set_xticks(runs)
        ax.grid(True, linestyle='--', alpha=0.5)
        plt.tight_layout()
        plt.savefig("warmup_enabled_chart.png", dpi=300)
        print("그래프 저장: 'warmup_enabled_chart.png'")
        plt.close()
    except ImportError:
        print("matplotlib 미설치 - 그래프 생략")

if __name__ == "__main__":
    main()