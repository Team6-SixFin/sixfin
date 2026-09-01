"""
야후 파이낸스 가용성 점검 — 수집기를 만들기 전에 이것부터 실행할 것.

    pip install yfinance pandas
    python check_yahoo.py

확인 항목
  1) 야후 접속이 되는가
  2) 5분봉 60일이 실제로 오는가          ← 계획의 전제
  3) 1분봉은 며칠까지 오는가              ← 30일 벽 실측
  4) 일봉 1년이 오는가                    ← 20일 최고가 계산용
  5) 한국 종목 분봉이 오는가              ← 안 되면 미국 종목 확정
"""
import time
import yfinance as yf

TESTS = [
    # (티커, period, interval, 설명)
    ("AAPL",      "5d",  "5m", "5분봉 5일   — 접속 확인"),
    ("AAPL",     "60d",  "5m", "5분봉 60일  ★ 계획의 전제"),
    ("AAPL",      "7d",  "1m", "1분봉 7일   — 1회 요청 한도"),
    ("AAPL",     "30d",  "1m", "1분봉 30일  — 전체 한도(빈 값이면 7일씩 페이징 필요)"),
    ("AAPL",      "1y",  "1d", "일봉 1년    ★ 20일 최고가 계산용"),
    ("MSFT",     "60d",  "5m", "다른 종목도 되는지"),
    ("005930.KS", "5d",  "5m", "한국 종목 분봉 (안 돼도 무방)"),
    ("005930.KS", "1y",  "1d", "한국 종목 일봉"),
]


def run(symbol, period, interval, desc):
    try:
        df = yf.download(
            symbol, period=period, interval=interval,
            auto_adjust=False, progress=False, threads=False,
        )
    except Exception as e:
        print(f"  ✗ ERROR  {type(e).__name__}: {str(e)[:90]}")
        return

    if df is None or len(df) == 0:
        print("  ✗ 비어 있음 (해당 조합은 제공되지 않음)")
        return

    first, last = df.index[0], df.index[-1]
    span = (last - first).days
    print(f"  ✓ {len(df):>6} rows | {span:>4}일치 | {first} ~ {last}")
    print(f"    tz={first.tzinfo} | columns={list(df.columns)[:6]}")


if __name__ == "__main__":
    print(f"yfinance {yf.__version__}\n" + "=" * 78)
    for symbol, period, interval, desc in TESTS:
        print(f"\n[{symbol}] {desc}")
        run(symbol, period, interval, desc)
        time.sleep(1.5)          # 429 방지
    print("\n" + "=" * 78)
    print("""
판정 기준
  · [AAPL 5분봉 60일] 이 성공하면      → 계획대로 진행
  · 실패하고 [1분봉 7일] 만 성공하면   → 1분봉 7일씩 페이징으로 30일 확보
  · 전부 실패하면                      → 일봉 리플레이로 전환 (팀에 즉시 공유)
  · [005930.KS 5분봉] 이 실패하면      → 미국 종목으로 확정 (예상된 결과)
""")
