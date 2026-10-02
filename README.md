# Engineering Note v0.2

Galaxy Tab + Samsung S Pen 기반 엔지니어링 노트/도면 리뷰 앱의 개발 버전입니다.

## v0.2 반영 기능
- 사용자가 직접 작업공간 선택, 실제 Galaxy Tab 폴더 사용
- 프로젝트/카테고리 폴더 자유 생성 및 하위 폴더 탐색
- PDF / 이미지 다중 삽입
- S Pen 자유 필기, 형광펜, 지우개, 압력 기반 선굵기
- S Pen 버튼을 누른 상태에서 순간 지우개
- Revision Cloud, Arrow, Box, Text
- 대충 그린 펜 선을 Cloud/Arrow/Box로 정리하는 기본 인식
- C-001 형식 Engineering Comment 자동 번호
- Comment Category 및 OPEN/PENDING/CLOSED 상태
- 오른쪽 Comment List에서 해당 페이지로 이동
- Comment 항목 길게 누르면 상태 순환
- 페이지 이동/점프
- 읽기 모드, 전체화면
- 펜 색상/굵기
- Undo / Redo
- 행동 단위 즉시 자동저장 + 5초 안전 자동저장
- 원본 PDF/이미지와 Annotation 데이터를 분리 저장

## 다음 단계 예정
Goodnotes/Samsung Notes 계열 기능을 계속 확장합니다.
- 올가미 선택/이동/크기조절/복사붙여넣기
- 핀치 줌 및 손가락 팬, S Pen 필기 분리 고도화
- 텍스트 상세 서식(굵게/기울임/밑줄/취소선/정렬/목록)
- 페이지 썸네일 관리/복제/순서 변경
- Blank/Grid/Engineering/Meeting 템플릿
- 사진 촬영/Drag & Drop/Scrap
- 손글씨 OCR, 한글→Engineering English
- Comment page only / Review Package PDF / Excel Comment List Export
- Revision 비교
- Engineering Element Library
- Audio recording + note replay
- Whiteboard/Infinite Canvas
- 클라우드 백업/동기화

## Build
Android Gradle Plugin 9.4.0 / Gradle 9.6.0 / compileSdk 37
