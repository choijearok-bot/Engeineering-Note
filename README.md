# Engineering Note v0.1

Galaxy Tab + S Pen 기반 엔지니어링 노트/도면 검토 앱의 1차 본체입니다.

## 현재 포함된 기능
- 사용자가 직접 작업폴더 선택 (Android Storage Access Framework)
- 프로젝트/카테고리 폴더 자유 생성
- PDF/JPG/PNG 등 이미지 파일 삽입 및 실제 선택한 폴더에 저장
- PDF 페이지 렌더링 및 페이지 이동
- S Pen 필기 / 형광펜
- Cloud / Arrow / Box 정형 도형
- 키보드 Text/Comment 입력
- S Pen 버튼 또는 Eraser로 획 삭제
- Undo / Redo
- `정리` 버튼: 마지막 자유 펜 표시를 형태에 따라 Cloud/Arrow/Box로 정형화
- 모든 마크업 변경 즉시 자동저장
- 문서를 다시 열면 페이지별 마크업 자동 복원
- 원본 PDF/이미지는 수정하지 않고 Annotation 데이터를 별도 저장

## 중요한 설계 원칙
1. 저장 버튼이 필요하지 않음: 변경 행동 단위로 자동저장.
2. 원본 문서는 보존.
3. 도형 좌표는 정규화 좌표로 저장해서 화면 크기가 바뀌어도 위치 유지.
4. 프로젝트/카테고리명은 앱이 강제하지 않고 사용자가 직접 생성.
5. S Pen 필기와 손가락 입력을 구분해서 실수로 손가락 잉크가 생기지 않게 구성.

## 다음 구현 순서
- 핀치 줌/팬 + S Pen 필기 동시 사용
- Comment Object (C-001 자동 번호, Category, Status, 상세 내용)
- Comment List 패널 + 코멘트 클릭 시 페이지/위치 이동
- 펜 글씨 OCR / 도형 인식 후보 선택 UI
- 한글 메모 -> 영문 Engineering Comment 변환
- 코멘트가 있는 페이지만 자동 추출
- Comment Summary + Marked-up Drawing PDF Export
- 사진 코멘트 / 현장 Punch 기능
- Revision A/B 비교 및 Open/Closed 추적
- 프로젝트 ZIP Backup/Restore

## Android Studio
- compileSdk / targetSdk: 37
- AGP: 9.4.0
- Kotlin: 2.4.20
- minSdk: 26

Android Studio에서 폴더를 열고 SDK 37을 설치한 뒤 Sync/Run 하세요.
