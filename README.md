# 구역별 정원 관수 스케줄러

최준서 · 224110 · 소프트웨어 시스템 설계

정원 관리 담당자 혼자서 넓은 공간의 다양한 구역에 식물을 키운다.  각 식물이 심어진 구역 별로 물을 주는 주기와 얼마나 물을 주는지도 각각 다르다. 이러한 정원 관리 담당자를 겨냥해 만든 구역별 관수 일정 프로그램이다



## 완료 기록

완료할 때 당시 구역명, 설명, 관수 주기를 함께 저장한다. 완료 기록에서 행을 선택하고 **선택 기록 상세·특이사항**을 누르면 설명을 확인하고 작업 메모를 남길 수 있다. 삭제한 구역의 기록은 조회만 할 수 있다.

예전 저장 파일도 열 수 있다. 예전 기록에 저장되지 않은 설명과 주기는 빈 정보로 표시한다. 예전 형식에서 처음 저장할 때는 같은 폴더에 원본을 `.v1.bak` 파일로 남긴다.

## 실행

GitHub에서 받은 소스는 JDK 17 이상으로 먼저 빌드한다. Windows는 `build.bat`, macOS/Linux는 `sh build.sh`를 실행한다. 그다음 Windows에서는 `run.bat`을 실행하거나 아래 명령으로 연다.

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
| `docs/api/index.html` | 브라우저로 여는 Javadoc API 문서 |
| `test/irrigation/` | 검증용 코드. 실행 JAR에는 포함되지 않음 |


