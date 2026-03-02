# JavaPerf Bench Guide

이 프로젝트는 Seqlock 구현 비교와 타이머/기본 동기화 벤치를 포함합니다.

## 공통 준비
```bash
./gradlew jmhJar
```

## 1) `SeqlockRwGroupBenchmark` (JMH, RW 동시)
- 설명: writer 1개 + reader 1개 그룹으로 `offheap / heap-naive / heap-aligned / object-array` 테이블을 비교합니다.
- 특징: `measureAge` 파라미터로 메시지 age 측정 on/off 가능.
- 실행:
```bash
java -Dseqlock.writer.cpu=2 -Dseqlock.reader.cpu=3 \
  -jar build/libs/JavaPerf-1.0-SNAPSHOT-jmh.jar '.*SeqlockRwGroupBenchmark.*_rw.*' \
  -bm avgt -tu ns -p measureAge=false -wi 5 -i 10 -f 1
```

## 2) `SeqlockWriterBenchmark` (JMH, Writer 전용)
- 설명: 각 테이블의 write 경로만 단독 측정합니다.
- 실행:
```bash
java -Dseqlock.writer.cpu=2 \
  -jar build/libs/JavaPerf-1.0-SNAPSHOT-jmh.jar '.*SeqlockWriterBenchmark.*'
```

## 3) `SeqlockReaderBenchmark` (JMH, Reader 전용)
- 설명: 각 테이블의 read/tryLoad 경로만 단독 측정합니다.
- 실행:
```bash
java -Dseqlock.reader.cpu=3 \
  -jar build/libs/JavaPerf-1.0-SNAPSHOT-jmh.jar '.*SeqlockReaderBenchmark.*'
```

## 4) `PingPongControlBaselineBenchmark` (JMH, 2코어 baseline)
- 설명: `AtomicBoolean` ping-pong으로 2스레드 기본 왕복 비용을 측정합니다.
- 실행:
```bash
java -Dpingpong.ping.cpu=2 -Dpingpong.pong.cpu=3 \
  -jar build/libs/JavaPerf-1.0-SNAPSHOT-jmh.jar '.*PingPongControlBaselineBenchmark.*' \
  -bm avgt -tu ns
```

## 5) `TimersBenchmark` (JMH, timer 호출 비용/해상도)
- 설명: `System.nanoTime()`/`currentTimeMillis()`의 latency, granularity를 측정합니다.
- 실행:
```bash
java -jar build/libs/JavaPerf-1.0-SNAPSHOT-jmh.jar '.*TimersBenchmark.*'
```

## 6) `SeqlockTableDiffRunner` (비JMH, 2스레드 직접 실행)
- 설명: writer가 `long0`에 publish 시각을 기록하고, reader가 `tryLoad` 성공한 업데이트에 대해서만 `diff(now - publish)`를 집계합니다.
- 출력: 테이블별 `avg / p50 / p99 / p99.9 / samples`.
- 실행:
```bash
./gradlew runSeqlockDiff \
  -Dseqlock.writer.cpu=2 \
  -Dseqlock.reader.cpu=3 \
  -Dseqlock.diff.warmupSec=3 \
  -Dseqlock.diff.measureSec=10 \
  -Dseqlock.diff.numSlots=256 \
  -Dseqlock.pin.log=true
```

## 자주 쓰는 옵션
- `-Dseqlock.pin.log=true`: pin 결과 로그 출력
- `-p measureAge=true|false`: `SeqlockRwGroupBenchmark`, `SeqlockReaderBenchmark`에서 age 측정 토글
- `-bm avgt -tu ns`: 평균 시간(ns/op)으로 출력
