"""
SixFin — 시세 수집기 (Yahoo Finance → CSV)   [1분봉 30일 버전]

  pip install yfinance pandas
  python collect.py

산출물 (out/)
  stocks.csv         종목 마스터
  daily_candles.csv  일봉 + 진단 지표
  price_candles.csv  1분봉 + 진단 지표 + 전 종목 공통 seq   ← 재생 원본

설계 근거
  · 런타임에는 야후를 호출하지 않는다. 외부 의존을 이 스크립트 하나에 격리한다.
  · 진단 지표(20일 최고/최저, 5일 수익률)는 여기서 계산해 컬럼으로 박는다.
    체결 API가 부하 테스트 대상이므로 런타임 집계를 없애기 위함.
  · seq 는 종목별이 아니라 전 종목 공통이다. 가상 시계가 seq 하나를 올리면
    전 종목 현재가가 동시에 갱신된다.

1분봉 제약
  · 야후는 1분봉을 1회 요청당 7일까지만 제공하고, 전체 조회 한도는 30일이다.
    → start/end 를 7일씩 끊어 5회 호출한 뒤 이어붙인다.
  · 청크 단위로 캐시하므로 중간에 실패해도 성공분은 재다운로드하지 않는다.
"""
import os
import sys
import time
from datetime import datetime, timedelta, timezone

import pandas as pd
import yfinance as yf

# ─────────────────────────────────────────────────────────────
# 설정 — 팀에서 확정한 값으로 고정한다 (재현성)
# ─────────────────────────────────────────────────────────────
TICKERS = [
    ("AAPL",  "Apple Inc.",              "NASDAQ"),
    ("MSFT",  "Microsoft Corporation",   "NASDAQ"),
    ("NVDA",  "NVIDIA Corporation",      "NASDAQ"),
    ("AMZN",  "Amazon.com Inc.",         "NASDAQ"),
    ("GOOGL", "Alphabet Inc.",           "NASDAQ"),
    ("META",  "Meta Platforms Inc.",     "NASDAQ"),
    ("TSLA",  "Tesla Inc.",              "NASDAQ"),
    ("AVGO",  "Broadcom Inc.",           "NASDAQ"),
    ("NFLX",  "Netflix Inc.",            "NASDAQ"),
    ("AMD",   "Advanced Micro Devices",  "NASDAQ"),
    ("JPM",   "JPMorgan Chase & Co.",    "NYSE"),
    ("V",     "Visa Inc.",               "NYSE"),
    ("WMT",   "Walmart Inc.",            "NYSE"),
    ("KO",    "Coca-Cola Company",       "NYSE"),
    ("DIS",   "Walt Disney Company",     "NYSE"),
    ("BA",    "Boeing Company",          "NYSE"),
    ("NKE",   "Nike Inc.",               "NYSE"),
    ("PFE",   "Pfizer Inc.",             "NYSE"),
    ("XOM",   "Exxon Mobil Corporation", "NYSE"),
    ("CRM",   "Salesforce Inc.",         "NYSE"),
]

INTRADAY_INTERVAL = "1m"     # ★ 1분봉
INTRADAY_DAYS     = 30       # 야후 1분봉 전체 한도
CHUNK_DAYS        = 7        # 야후 1분봉 1회 요청 한도

DAILY_INTERVAL = "1d"
DAILY_PERIOD   = "1y"        # 20일 최고가 계산에 충분한 여유

EXCHANGE_TZ   = "America/New_York"
SESSION_START = "09:30"
SESSION_END   = "15:59"      # 1분봉 마지막 봉 시작 시각

WINDOW_HIGH_LOW = 20         # 직전 N거래일 최고/최저
WINDOW_RETURN   = 5          # 최근 N거래일 수익률

SLEEP_SEC = 2.0              # 요청 간 대기 (1분봉은 호출 수가 많아 넉넉히)
MAX_RETRY = 3
OUT_DIR   = "out"
CACHE_DIR = "out/_raw"       # 원본 캐시 — 재실행 시 재다운로드 방지


# ─────────────────────────────────────────────────────────────
# 다운로드
# ─────────────────────────────────────────────────────────────
def _flatten(df):
    """단일 티커인데 MultiIndex 로 오는 경우 평탄화."""
    if isinstance(df.columns, pd.MultiIndex):
        df = df.copy()
        df.columns = df.columns.get_level_values(0)
    return df


def _download(cache_key, **kwargs):
    """지수 백오프 재시도 + 캐시."""
    cache = f"{CACHE_DIR}/{cache_key}.pkl"
    if os.path.exists(cache):
        return pd.read_pickle(cache)

    delay = 3
    for attempt in range(1, MAX_RETRY + 1):
        try:
            df = yf.download(auto_adjust=False, progress=False, threads=False, **kwargs)
            if df is not None and len(df) > 0:
                df = _flatten(df)
                df.to_pickle(cache)
                return df
            print(f"      empty (attempt {attempt}/{MAX_RETRY})")
        except Exception as e:
            print(f"      {type(e).__name__}: {str(e)[:70]} ({attempt}/{MAX_RETRY})")
        if attempt < MAX_RETRY:
            time.sleep(delay)
            delay *= 2
    return None


def fetch_daily(symbol):
    return _download(f"{symbol}_1d_{DAILY_PERIOD}",
                     tickers=symbol, period=DAILY_PERIOD, interval=DAILY_INTERVAL)


def fetch_intraday_chunked(symbol):
    """1분봉은 7일씩 끊어 여러 번 호출한 뒤 이어붙인다."""
    end = datetime.now(timezone.utc).date() + timedelta(days=1)
    start = end - timedelta(days=INTRADAY_DAYS)

    frames, cur, n = [], start, 0
    while cur < end:
        nxt = min(cur + timedelta(days=CHUNK_DAYS), end)
        n += 1
        print(f"      chunk {n}: {cur} ~ {nxt}", end="  ")
        df = _download(f"{symbol}_{INTRADAY_INTERVAL}_{cur}_{nxt}",
                       tickers=symbol, start=str(cur), end=str(nxt),
                       interval=INTRADAY_INTERVAL)
        if df is None:
            print("→ 없음")
        else:
            print(f"→ {len(df)} rows")
            frames.append(df)
        cur = nxt
        time.sleep(SLEEP_SEC)

    if not frames:
        return None
    merged = pd.concat(frames)
    merged = merged[~merged.index.duplicated(keep="first")].sort_index()
    return merged


# ─────────────────────────────────────────────────────────────
# 전처리
# ─────────────────────────────────────────────────────────────
def to_utc(df):
    idx = pd.to_datetime(df.index)
    if idx.tz is None:
        idx = idx.tz_localize(EXCHANGE_TZ)
    out = df.copy()
    out.index = idx.tz_convert("UTC")
    return out


def clean(df):
    df = df.dropna(subset=["Open", "High", "Low", "Close"])
    df = df[~df.index.duplicated(keep="first")]
    df = df[df["Close"] > 0]
    return df.sort_index()


RENAME = {"Open": "open", "High": "high", "Low": "low",
          "Close": "close", "Volume": "volume"}


def build_daily(symbol):
    """일봉 + 진단 지표. 지표는 반드시 shift(1) 로 당일을 제외한다."""
    raw = fetch_daily(symbol)
    if raw is None:
        return None
    d = clean(to_utc(raw)).rename(columns=RENAME)[list(RENAME.values())]
    d["trade_date"] = d.index.tz_convert(EXCHANGE_TZ).date

    # ★ shift(1) — 당일 고가를 포함시키면 "고점 추격" 판정이 항상 참이 되어
    #   진단 규칙이 무의미해진다. 이 한 줄이 진단 정확도를 좌우한다.
    d["recent_20d_high"] = d["high"].shift(1).rolling(
        WINDOW_HIGH_LOW, min_periods=WINDOW_HIGH_LOW).max()
    d["recent_20d_low"] = d["low"].shift(1).rolling(
        WINDOW_HIGH_LOW, min_periods=WINDOW_HIGH_LOW).min()
    d["recent_5d_return"] = (d["close"] / d["close"].shift(WINDOW_RETURN) - 1) * 100

    d.insert(0, "symbol", symbol)
    return d.reset_index(drop=True)


def build_intraday(symbol, daily):
    """1분봉 + 일봉 지표 조인."""
    raw = fetch_intraday_chunked(symbol)
    if raw is None:
        return None
    m = clean(to_utc(raw))

    # 정규장만 남긴다 (시간외 데이터가 섞여 오는 경우가 있음)
    local = m.index.tz_convert(EXCHANGE_TZ)
    mask = (local.time >= pd.Timestamp(SESSION_START).time()) & \
           (local.time <= pd.Timestamp(SESSION_END).time())
    m = m[mask]
    if len(m) == 0:
        return None

    m = m.rename(columns=RENAME)[list(RENAME.values())]
    m = m.rename_axis("market_time_utc").reset_index()
    m["symbol"] = symbol
    m["trade_date"] = m["market_time_utc"].dt.tz_convert(EXCHANGE_TZ).dt.date

    return m.merge(
        daily[["trade_date", "recent_20d_high", "recent_20d_low", "recent_5d_return"]],
        on="trade_date", how="left",
    )


# ─────────────────────────────────────────────────────────────
def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(CACHE_DIR, exist_ok=True)

    print(f"1분봉 {INTRADAY_DAYS}일 / 종목 {len(TICKERS)}개")
    print(f"예상 호출 수: {len(TICKERS)} × (일봉 1 + 분봉 {-(-INTRADAY_DAYS // CHUNK_DAYS)}) "
          f"= 약 {len(TICKERS) * (1 + -(-INTRADAY_DAYS // CHUNK_DAYS))}회\n")

    daily_frames, intraday_frames, skipped = [], [], []

    for i, (symbol, name, market) in enumerate(TICKERS, start=1):
        print(f"[{i}/{len(TICKERS)}] {symbol}")

        d = build_daily(symbol)
        if d is None:
            print("    ✗ 일봉 실패 — 종목 제외")
            skipped.append(symbol)
            continue
        time.sleep(SLEEP_SEC)

        m = build_intraday(symbol, d)
        if m is None:
            print("    ✗ 분봉 실패 — 종목 제외")
            skipped.append(symbol)
            continue

        print(f"    일봉 {len(d):>4} / 분봉 {len(m):>7}")
        daily_frames.append(d)
        intraday_frames.append(m)

    if not intraday_frames:
        sys.exit("수집된 데이터가 없습니다. check_yahoo.py 로 가용성부터 확인하세요.")

    daily = pd.concat(daily_frames, ignore_index=True)
    m1 = pd.concat(intraday_frames, ignore_index=True)

    # ── 전 종목 공통 시간축으로 seq 부여 ─────────────────────
    # 종목별로 따로 매기면 거래일이 어긋날 때 재생이 틀어진다.
    # 모든 종목에 존재하는 타임스탬프만 남기고 거기에 번호를 매긴다.
    sets = [set(g["market_time_utc"]) for _, g in m1.groupby("symbol")]
    common = sorted(set.intersection(*sets))
    if not common:
        sys.exit("공통 시간축이 비어 있습니다. 종목별 거래일이 어긋났는지 확인하세요.")

    seq_map = {t: i for i, t in enumerate(common)}
    before = len(m1)
    m1 = m1[m1["market_time_utc"].isin(seq_map)].copy()
    print(f"\n공통 시간축 적용: {before} → {len(m1)} rows ({before - len(m1)} 건 제외)")

    m1["seq"] = m1["market_time_utc"].map(seq_map)
    m1 = m1.sort_values(["seq", "symbol"])

    # ── CSV 출력 ────────────────────────────────────────────
    PRICE_COLS = ["open", "high", "low", "close", "recent_20d_high", "recent_20d_low"]
    kept = set(m1["symbol"])

    pd.DataFrame(
        [{"symbol": s, "name": n, "market": mk, "currency": "USD", "is_active": True}
         for s, n, mk in TICKERS if s in kept]
    ).to_csv(f"{OUT_DIR}/stocks.csv", index=False)

    d_out = daily[daily["symbol"].isin(kept)].copy()
    for c in PRICE_COLS:
        d_out[c] = d_out[c].round(4)
    d_out["recent_5d_return"] = d_out["recent_5d_return"].round(2)
    d_out[["symbol", "trade_date", "open", "high", "low", "close", "volume",
           "recent_20d_high", "recent_20d_low", "recent_5d_return"]] \
        .to_csv(f"{OUT_DIR}/daily_candles.csv", index=False)

    p_out = m1.copy()
    p_out["market_time_utc"] = p_out["market_time_utc"].dt.strftime("%Y-%m-%dT%H:%M:%SZ")
    for c in PRICE_COLS:
        p_out[c] = p_out[c].round(4)
    p_out["recent_5d_return"] = p_out["recent_5d_return"].round(2)
    p_out[["symbol", "seq", "market_time_utc", "open", "high", "low", "close",
           "volume", "recent_20d_high", "recent_20d_low", "recent_5d_return"]] \
        .to_csv(f"{OUT_DIR}/price_candles.csv", index=False)

    # ── 검증 ────────────────────────────────────────────────
    print("\n" + "=" * 64)
    print("검증")
    print("=" * 64)
    n_sym = m1["symbol"].nunique()
    n_seq = len(common)
    print(f"종목 수                  : {n_sym}" + (f"  (제외: {skipped})" if skipped else ""))
    print(f"공통 봉 수 (seq 개수)    : {n_seq}")
    print(f"price_candles 행 수      : {len(m1)}  (= {n_sym} × {n_seq})")
    print(f"daily_candles 행 수      : {len(d_out)}")
    print(f"seq 범위                 : {int(m1['seq'].min())} ~ {int(m1['seq'].max())}")
    print(f"종목별 seq 최댓값 동일   : {m1.groupby('symbol')['seq'].max().nunique() == 1}")
    print(f"거래일 수                : {m1['trade_date'].nunique()}")

    null_high = int(p_out["recent_20d_high"].isna().sum())
    print(f"recent_20d_high NULL     : {null_high} 행")
    if null_high:
        ok = m1.loc[m1["recent_20d_high"].notna(), "seq"]
        if len(ok):
            print(f"  → market_clock.start_seq 를 {int(ok.min())} 이상으로 설정할 것")
        else:
            print("  → 전 구간 NULL. 일봉 기간(DAILY_PERIOD)을 늘려야 한다")

    print(f"close <= 0               : {int((p_out['close'] <= 0).sum())} 행")

    # 재생 시간 안내
    print("\n재생 소요 시간 (1틱 = 1봉)")
    for ms in (1000, 500, 250, 100):
        total = n_seq * ms / 1000
        print(f"  tick {ms:>4}ms → {int(total // 60):>3}분 {int(total % 60):>2}초")

    # ── shift(1) 적용 여부 자동 검증 ────────────────────────
    # 눈으로 보는 방식(당일 high 가 20일 최고가보다 큰 날이 있는지)은 하락장에서
    # 한 건도 안 나올 수 있어 신뢰할 수 없다. 값을 직접 재계산해 대조한다.
    print("\nshift(1) 검증")
    ok_all = True
    for sym, g in daily[daily["symbol"].isin(kept)].groupby("symbol"):
        g = g.reset_index(drop=True)
        expect = g["high"].shift(1).rolling(WINDOW_HIGH_LOW,
                                            min_periods=WINDOW_HIGH_LOW).max()
        wrong = g["high"].rolling(WINDOW_HIGH_LOW, min_periods=WINDOW_HIGH_LOW).max()
        matches_expect = g["recent_20d_high"].round(6).equals(expect.round(6))
        same_as_wrong = g["recent_20d_high"].round(6).equals(wrong.round(6))
        if not matches_expect or same_as_wrong:
            ok_all = False
            print(f"  ✗ {sym}: shift(1) 미적용 의심 "
                  f"(기대값 일치={matches_expect}, 미적용값과 동일={same_as_wrong})")
    if ok_all:
        i = WINDOW_HIGH_LOW
        first = sorted(kept)[0]
        g = daily[daily["symbol"] == first].reset_index(drop=True)
        manual = g["high"].iloc[i - WINDOW_HIGH_LOW:i].max()
        stored = g["recent_20d_high"].iloc[i]
        print(f"  ✓ 전 종목 정상")
        print(f"    표본 {first} {g['trade_date'].iloc[i]} : "
              f"직전 20일 최고가 수동계산={manual:.4f} / 저장값={stored:.4f}")

    print("\n지표 샘플")
    first = TICKERS[0][0] if TICKERS[0][0] in kept else sorted(kept)[0]
    s = d_out[d_out["symbol"] == first].dropna(subset=["recent_20d_high"]).head(5)
    print(s[["trade_date", "high", "recent_20d_high", "recent_20d_low",
             "recent_5d_return"]].to_string(index=False))

    print(f"\n산출물 → {OUT_DIR}/")
    print("※ CSV 는 Git 에 커밋하지 말 것 (야후 데이터 재배포 이슈). "
          "공유 드라이브에 올리고 이 스크립트만 커밋한다.")


if __name__ == "__main__":
    main()
