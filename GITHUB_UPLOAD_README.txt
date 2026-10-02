Engineering Note v0.2 - GitHub 업데이트 방법

권장: PATCH ZIP의 내용 전체를 기존 GitHub 저장소 루트에 업로드하여 동일 경로 파일을 교체/추가한 뒤 Commit changes 하세요.

핵심 교체 파일:
- build.gradle.kts
- app/build.gradle.kts
- app/src/main/java/com/kcc/engineeringnote/MainActivity.kt
- app/src/main/java/com/kcc/engineeringnote/DrawingOverlayView.kt
- app/src/main/java/com/kcc/engineeringnote/MarkupModels.kt
- app/src/main/java/com/kcc/engineeringnote/AnnotationStore.kt

추가/갱신:
- .github/workflows/main.yml
- README.md
- CHANGELOG_v0.2.txt

커밋 후 Actions > Android APK Build 가 자동 실행됩니다.
성공 시 Artifacts > EngineeringNote-v0.2-debug-apk 에서 APK를 받을 수 있습니다.
