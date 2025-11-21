# 스쿠리 마인크래프트 채팅 플러그인

마인크래프트 서버와 모바일 앱 간의 실시간 채팅 및 서버 상태 동기화를 제공하는 Paper 플러그인입니다.

## 주요 기능

### 📱 실시간 채팅 연동
- 마인크래프트 서버와 모바일 앱 간 양방향 채팅
- 앱에서 보낸 메시지가 서버에 실시간으로 표시
- 서버 채팅이 앱으로 실시간 전송

### 💀 사망 메시지 처리
- 플레이어 사망 메시지를 한국어로 번역하여 앱에 전송
- 엔티티(주민, 길들여진 동물 등) 사망 메시지 포맷팅
- 다양한 사망 원인에 대한 상세한 메시지 제공

### ⚙️ 서버 상태 동기화
- 서버 온라인/오프라인 상태 실시간 업데이트
- 접속 중인 플레이어 목록 및 인원수 동기화
- 10초마다 자동 하트비트 전송

### 🔒 화이트리스트 관리
- Firebase를 통한 화이트리스트 실시간 동기화
- 앱에서 화이트리스트 추가/제거 시 서버에 즉시 반영
- 화이트리스트 활성화/비활성화 제어
- Java Edition (JE) 플레이어: 화이트리스트 미등록 시 접속 차단
- Bedrock Edition (BE) 플레이어: 계정 미등록 시 접속은 허용하되 모든 행동 제한 (이동, 블록 설치/파괴, 채팅, 상호작용 등 불가)

## 요구사항

- **Minecraft 버전**: 1.21
- **서버**: Paper 또는 그 기반 서버
- **Java 버전**: 21 이상
- **Firebase**: Firebase Realtime Database 설정 필요

## 설치 방법

1. **프로젝트 클론**
   ```bash
   git clone https://github.com/jisung-louis/skuri-mc-chat-plugin.git
   cd skuri-mc-chat-plugin
   ```

2. **빌드**
   ```bash
   ./gradlew build
   ```
   빌드된 JAR 파일은 `build/libs/` 디렉토리에 생성됩니다. 
   `build` 태스크는 자동으로 `shadowJar`를 실행하여 모든 의존성을 포함한 fat JAR를 생성합니다.

3. **플러그인 설치**
   - 빌드된 JAR 파일을 서버의 `plugins` 디렉토리에 복사
   - 서버 재시작

4. **Firebase 설정**
   - Firebase 콘솔에서 서비스 계정 키를 다운로드
   - `serviceAccount.json` 파일을 플러그인 데이터 폴더에 배치
   - `config.yml`에서 Firebase Database URL 설정

## 설정

### config.yml

```yaml
firebase:
  databaseUrl: "https://YOUR_PROJECT_ID.firebaseio.com/"
```

### serviceAccount.json

Firebase 콘솔에서 다운로드한 서비스 계정 키 파일을 플러그인 데이터 폴더에 배치해야 합니다.

## 빌드

```bash
./gradlew build
```

또는 Shadow JAR만 빌드하려면:

```bash
./gradlew shadowJar
```

빌드된 JAR 파일은 `build/libs/` 디렉토리에 생성되며, 모든 의존성을 포함한 fat JAR로 생성됩니다.

## 개발 환경

- **Gradle**: 프로젝트 빌드 시스템
- **Java 21**: 최소 요구 버전
- **Paper API**: 1.21.10-R0.1-SNAPSHOT

## 프로젝트 구조

```
src/main/java/com/jisung/skurimcchat/
├── Skurimcchat.java              # 메인 플러그인 클래스
├── DeathMessageMapper.java       # 사망 메시지 번역 유틸리티
└── EntityDeathMessageFormatter.java  # 엔티티 사망 메시지 포맷터
```

## 라이선스

이 프로젝트는 개인 프로젝트입니다.
