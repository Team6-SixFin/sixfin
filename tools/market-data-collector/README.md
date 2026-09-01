# Market Data Collector

Yahoo Finance 의 과거 시세를 수집해 CSV 로 다운로드하는 **개발 도구**다.

> **런타임에는 사용하지 않는다.** 개발 준비 단계에서 1회만 실행하고, 이후 서비스는 DB 에 적재된
> 데이터만 재생한다. 외부 API 의존을 이 스크립트 하나에 격리해 부하 테스트가 야후 응답 속도에
> 좌우되지 않도록 하기 위함이다.

- 위치: `tools/market-data-collector/`
- Gradle 모듈이 아니다. `settings.gradle` 에 추가하지 않는다.

---

## 1. 사전 준비

**Python 3.11 이상** (개발·검증 환경: `3.11.15`)

```bash
python3 --version      # macOS / Linux
py --version           # Windows
```

3.11 미만이면 [python.org](https://www.python.org/downloads/) 에서 설치한다.
`pandas 3.x` 가 3.10 이하를 지원하지 않으므로 반드시 3.11 이상이어야 한다.

---

## 2. 가상환경 생성 및 의존성 설치

프로젝트 루트가 아니라 **이 디렉터리에서** 실행한다.

### macOS / Linux

```bash
cd tools/market-data-collector

python3 -m venv .venv
source .venv/bin/activate

pip install --upgrade pip
pip install -r requirements.txt
```

### Windows (PowerShell)

```powershell
cd tools\market-data-collector

py -m venv .venv
.\.venv\Scripts\Activate.ps1

pip install --upgrade pip
pip install -r requirements.txt
```

### 설치 확인

```bash
python -c "import yfinance, pandas; print(yfinance.__version__, pandas.__version__)"
# 1.6.0 3.0.2
```

### 가상환경 종료

```bash
deactivate
```

`.venv/` 는 `.gitignore` 에 포함되어 있으므로 커밋되지 않는다.

---

## 3. 실행

### 3-1. 가용성 점검 (최초 1회, 약 1분)

```bash
python check_yahoo.py
```

**`[AAPL] 1분봉 7일`** 이 성공하는지만 확인한다.

| 결과 | 조치 |
|---|---|
| 1분봉 7일 성공 | 계획대로 진행 |
| 1분봉 30일이 비어 있음 | 정상. 7일씩 페이징으로 받으므로 문제없다 |
| 1분봉이 전부 실패 | 5분봉(`60d`)으로 전환 검토 |
| 전부 실패 | 일봉 리플레이로 전환. **팀에 즉시 공유** |
| `005930.KS` 분봉 실패 | 예상된 결과. 미국 종목만 사용한다 |

### 3-2. 수집 (약 5~6분)

```bash
python collect.py
```

- 20종목 × (일봉 1회 + 1분봉 5회) = 약 120회 호출
- 요청 간 2초 대기 (429 방지)
- 원본은 `out/_raw/` 에 청크 단위로 캐시되므로, 중간에 실패해도 재실행 시 성공분은 다시 받지 않는다

---

## 4. 산출물

`out/` 에 생성된다.

| 파일 | 내용 | 예상 크기 |
|---|---|---|
| `stocks.csv` | 종목 마스터 | 1 KB |
| `daily_candles.csv` | 일봉 + 진단 지표 | 500 KB |
| `price_candles.csv` | 1분봉 + 지표 + 전 종목 공통 `seq` (재생 원본) | 약 15 MB |

`price_candles.csv` 는 20종목 × 390봉 × 약 21거래일 ≈ **16만 행**이다.

---

## 5. 실행 후 반드시 확인할 것

스크립트가 마지막에 출력하는 검증 결과에서 아래 세 줄을 본다.

```
종목별 seq 최댓값 동일   : True          ← False 면 공통 시간축이 깨진 것
shift(1) 검증            : ✓ 전 종목 정상  ← ✗ 면 진단 지표가 잘못 계산된 것
recent_20d_high NULL     : N 행
  → market_clock.start_seq 를 XXXX 이상으로 설정할 것
```

- **`shift(1) 검증`이 실패하면 절대 진행하지 말 것.** 당일 고가가 20일 최고가에 포함되어
  "고점 추격 매수" 진단이 항상 참이 된다. 진단 규칙 자체가 무의미해진다.
- `recent_20d_high NULL` 은 앞 20거래일 구간이라 정상이다. 출력에 안내된 `start_seq` 값을
  `market_clock` 초기 데이터에 반영한다.

재생 소요 시간도 함께 출력된다. `tick_interval_ms` 기본값은 **250 정도**를 권장한다.
1000 으로 두면 전체 재생에 2시간이 넘어 개발 중 확인이 어렵다.

---

## 6. 팀 공유 규칙

**CSV 를 Git 에 커밋하지 않는다.** Yahoo 데이터 재배포에 해당한다.
`.gitignore` 에 `out/` 이 포함되어 있으니 실수로 올라가지 않지만, 강제 추가하지 말 것.

- CSV 는 **팀 공유 드라이브**에 업로드한다
- 업로드 시 **생성 일시와 `seq` 범위, `start_seq` 값**을 함께 적는다
- 스크립트와 이 README 만 커밋한다

종목·기간은 `collect.py` 상단 상수로 고정되어 있으므로, 같은 설정으로 실행하면 같은 데이터가 나온다.

---

## 7. 설정 변경

`collect.py` 상단 상수만 수정한다.

| 상수 | 기본값 | 설명 |
|---|---|---|
| `TICKERS` | 20종목 | 수집 대상. 늘리면 호출 수와 수집 시간이 비례해 증가 |
| `INTRADAY_INTERVAL` | `"1m"` | 분봉 단위 |
| `INTRADAY_DAYS` | `30` | 야후 1분봉 전체 조회 한도 |
| `CHUNK_DAYS` | `7` | 야후 1분봉 1회 요청 한도 |
| `DAILY_PERIOD` | `"1y"` | 일봉 기간 (20일 지표 계산용) |
| `SLEEP_SEC` | `2.0` | 요청 간 대기. 429 가 잦으면 늘린다 |

> 종목을 추가하면 전 종목 공통 시간축이 바뀌어 **`seq` 가 전부 재계산된다.**
> 기존 주문·체결에 기록된 `candle_seq` 가 다른 시각을 가리키게 되므로,
> MVP 시연 이후에는 종목 구성을 바꾸지 않는 것을 권장한다.

---

## 8. DB 적재

`trading-service` 를 `init` 프로필로 기동하면 CSV 를 읽어 적재한다.

```bash
java -jar trading-service.jar \
  --spring.profiles.active=init \
  --market-data.csv-dir=/absolute/path/to/tools/market-data-collector/out
```

CSV 를 `src/main/resources` 에 넣지 않는다. jar 크기가 커지고 Git 에도 올라간다.
경로는 설정으로 주입한다.

```yaml
# trading-service/src/main/resources/application-init.yml
market-data:
  csv-dir: ${MARKET_DATA_DIR:../tools/market-data-collector/out}
```

---

## 9. 트러블슈팅

| 증상 | 원인 | 해결 |
|---|---|---|
| `YFRateLimitError` / 빈 응답 반복 | 429 (호출 과다) | `SLEEP_SEC` 를 3~5 로 올린다. 캐시 덕분에 재실행 시 성공분은 유지된다 |
| 특정 종목만 계속 실패 | 티커 오타·상장폐지 | `TICKERS` 에서 제거한다. 스크립트가 자동으로 제외하고 진행한다 |
| `공통 시간축이 비어 있습니다` | 종목별 거래일이 전혀 겹치지 않음 | 종목 수를 줄이거나 `out/_raw/` 를 지우고 다시 받는다 |
| `recent_20d_high` 전 구간 NULL | 일봉 기간 부족 | `DAILY_PERIOD` 를 `"2y"` 로 늘린다 |
| `ModuleNotFoundError` | 가상환경 미활성화 | `source .venv/bin/activate` 후 재실행 |
| pandas 버전 충돌 | 전역 패키지와 섞임 | `.venv` 를 지우고 2번부터 다시 수행 |

원본 데이터를 새로 받으려면 캐시를 지운다.

```bash
rm -rf out/_raw
```
