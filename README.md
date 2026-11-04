# 구역별 정원 관수 스케줄러

최준서 · 224110 · 소프트웨어 시스템 설계

﻿정원 관리 담당자 혼자서 넓은 공간의 다양한 구역에 식물을 키운다.  각 식물이 심어진 구역 별로 물을 주는 주기와 얼마나 물을 주는지도 각각 다르다. 이러한 정원 관리 담당자를 겨냥해 만든 구역별 관수 일정 프로그램이다



## 실행

Java 17 이상이 필요하다. Windows에서는 압축을 푼 뒤 `run.bat`을 실행한다. 터미널에서는 다음 명령을 사용한다.

```sh
java -jar dist/ssd_ip_cp4_2026f_224110_app.jar
```

macOS/Linux에서는 `sh run.sh`로 실행할 수 있다. 데이터는 사용자 홈의 `.garden-irrigation/irrigation.dat`에 자동 저장된다. 테스트용 파일을 따로 쓰려면 다음처럼 실행한다.

```sh
java -jar dist/ssd_ip_cp4_2026f_224110_app.jar --data ./practice.dat
```



## 파일 구성

| 경로 | 내용 |
| --- | --- |
| `src/irrigation/` | 프로그램 클래스 5개 |
| `dist/ssd_ip_cp4_2026f_224110_app.jar` | 실행 프로그램 |
| `docs/code-guide.md` | 보고서와 코드 연결, 읽는 순서 |
| `docs/api/index.html` | 브라우저로 여는 Javadoc API 문서 |
| `docs/verification.md` | 확인한 항목과 테스트 방법 |
| `docs/beta-calendar.png` | 테스트 자료를 넣어 실행한 달력 화면 |
| `test/irrigation/` | 검증용 코드. 실행 JAR에는 포함되지 않음 |


