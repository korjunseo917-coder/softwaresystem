# 구역별 정원 관수 스케줄러

최준서 · 224110 · 소프트웨어 시스템 설계

보고서의 5개 클래스 구조를 유지한 Java Swing 프로그램이다. 반복 간격은 일 단위 정수로 입력하며, 0이면 시작일에 일정 하나만 만든다.

## 실행

Java 17 이상이 필요하다. Windows에서는 압축을 푼 뒤 `run.bat`을 실행한다. 터미널에서는 다음 명령을 사용한다.

```sh
java -jar dist/ssd_ip_cp4_2026f_224110_app.jar
```

macOS/Linux에서는 `sh run.sh`로 실행할 수 있다. 데이터는 사용자 홈의 `.garden-irrigation/irrigation.dat`에 자동 저장된다. 테스트용 파일을 따로 쓰려면 다음처럼 실행한다.

```sh
java -jar dist/ssd_ip_cp4_2026f_224110_app.jar --data ./practice.dat
```

이전 버전의 저장 형식을 유지했다. 다만 반복 간격 0으로 저장한 자료는 0을 지원하는 이번 버전에서 사용해야 한다.

## 소스 수정 후 빌드

JDK 17 이상이 필요하다. Windows는 `build.bat`, macOS/Linux는 `sh build.sh`를 실행한다. 실행 JAR와 Javadoc이 함께 갱신된다.

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

먼저 `docs/code-guide.md`를 읽고 `IrrigationService.registerZone()`부터 따라가면 된다. 전체 화면 배치 코드를 한 번에 이해할 필요는 없다.

이 ZIP은 개발용 프로젝트 묶음이다. 최종 제출 때는 보고서 PDF와 GitHub URL을 준비하고, 교수님이 지정한 소스 ZIP·JAR·API ZIP 구성으로 다시 묶어야 한다.
