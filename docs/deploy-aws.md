# AWS EC2 배포 가이드 (담당자 체험용)

- 작성자: JinsuBae2
- 최종 수정일: 2026-10-21
- 대상: 기업 담당자가 URL로 접속해 시스템을 써 볼 수 있게 서버 한 대에 올린다.
- 방식: **AWS 웹 콘솔에서만** 진행한다. 내 PC에 따로 설치할 것은 없다. 명령은 AWS 페이지 안의 브라우저 터미널(EC2 Instance Connect)에 붙여 넣는다.

## 0. 전체 구성

```
담당자 브라우저 ── https://3-35-10-20.sslip.io
                      │ (80·443만 외부에 열림)
             ┌────────▼────────┐  EC2 한 대 (Docker Compose)
             │ web (Caddy)     │  화면 파일 서빙 + HTTPS 자동 발급
             │   └ /api/* ─────┼─▶ backend (Spring Boot)
             │                 │      ├─▶ 첨부파일 (file-data 볼륨)
             │                 │      ├─▶ postgres (DB)
             │                 │      └─▶ mailpit (알림 메일 보관, 외부 비공개)
             └─────────────────┘
```

| 저장소 파일 | 역할 |
|---|---|
| `docker-compose.prod.yml` | 서버 구성 전체 (DB·백엔드·메일함·웹서버) |
| `backend/Dockerfile` | 백엔드 빌드 |
| `frontend/Dockerfile` | 화면 빌드 + Caddy 웹서버 |
| `deploy/Caddyfile` | HTTPS·API 전달 설정 |
| `deploy/.env.prod.example` | 서버 비밀번호·주소 설정 예시 |

- 체험용이므로 `dev` 프로파일로 실행한다. 가상 샘플 데이터가 자동으로 들어가고, 관리자 화면의 시연 도구(기준일 변경·만료 점검 즉시 실행)를 쓸 수 있다.
- **실제 계약·개인정보는 입력하지 않도록 담당자에게 안내한다.** 보안 점검을 거친 운영 시스템이 아니다.

## 1. 리전 바꾸기

AWS 콘솔(https://console.aws.amazon.com) 로그인 → 오른쪽 위 리전을 **아시아 태평양(서울) ap-northeast-2**로 바꾼다.
이후 모든 작업은 서울 리전에서 한다.

## 2. 인스턴스(서버) 만들기

### 2-0. 먼저: 접속용 IAM 역할 만들기 (1분)

브라우저 터미널이 안 될 때 Session Manager로 접속할 수 있게 권한을 미리 만들어 둔다.
(학교망 등에서 SSH 22번이 막혀도 Session Manager는 HTTPS로 접속한다)

검색창에 `IAM` → 왼쪽 **역할** → **역할 생성** → 신뢰할 수 있는 엔터티 **AWS 서비스**, 사용 사례 **EC2** → 다음 →
권한 정책 `AmazonSSMManagedInstanceCore` 체크 → 다음 → 역할 이름 `supplier-demo-ssm` → **역할 생성**

### 2-1. 인스턴스 시작

검색창에 `EC2` → **EC2** → 왼쪽 메뉴 **인스턴스** → 오른쪽 위 **인스턴스 시작**

| 화면 항목 | 선택·입력 값 |
|---|---|
| 이름 및 태그 → 이름 | `supplier-demo` |
| 애플리케이션 및 OS 이미지 | **Ubuntu** → **Ubuntu Server 24.04 LTS** (64비트 x86) |
| 인스턴스 유형 | **t3.small** (메모리 2GB. t3.micro는 메모리가 부족해 멈출 수 있음) |
| 키 페어(로그인) | **새 키 페어 생성** → 이름 `supplier-demo` → 생성 (`.pem` 파일이 내려받아짐. 비상용으로 보관). 이미 있으면 기존 `supplier-demo` 선택 |
| 네트워크 설정 | (다시 만드는 경우: **기존 보안 그룹 선택** → `launch-wizard-1`)<br>**SSH 트래픽 허용** 체크 → 위치: **위치 무관 0.0.0.0/0**<br>**인터넷에서 HTTPS 트래픽 허용** 체크<br>**인터넷에서 HTTP 트래픽 허용** 체크 |
| 스토리지 구성 | **20** GiB, gp3 |

> SSH를 "내 IP"로 제한하면 브라우저 터미널(EC2 Instance Connect)이 접속하지 못한다. AWS 서비스가 대신 접속하기 때문이다. 키·임시 키로만 들어갈 수 있으므로 체험 기간에는 "위치 무관"으로 둔다.

### 2-2. 고급 세부 정보 (IAM 역할, 서버 준비 자동화)

같은 화면 맨 아래 **고급 세부 정보**를 펼친다.

- **IAM 인스턴스 프로파일**: `supplier-demo-ssm` 선택
- 맨 아래 **사용자 데이터** 칸에 아래를 그대로 붙여 넣는다. **빠뜨리면 Docker가 설치되지 않는다.**
서버가 처음 켜질 때 스왑 메모리 설정과 Docker 설치를 자동으로 한다.

```bash
#!/bin/bash
# 메모리 부족에 대비한 스왑 2GB
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
# Docker 설치, ubuntu 사용자가 sudo 없이 docker를 쓰도록 권한 추가
curl -fsSL https://get.docker.com | sh
usermod -aG docker ubuntu
```

오른쪽 **요약** → **인스턴스 시작**. "인스턴스를 시작했습니다"가 나오면 완료.

## 3. 고정 IP 붙이기 (탄력적 IP)

서버를 껐다 켜도 주소가 바뀌지 않게 한다. (인스턴스를 다시 만든 경우: 할당은 건너뛰고 기존 주소를 새 인스턴스에 **연결**만 한다)

1. 왼쪽 메뉴 **네트워크 및 보안 → 탄력적 IP** → **탄력적 IP 주소 할당** → 그대로 **할당**
2. 방금 만든 주소 선택 → **작업 → 탄력적 IP 주소 연결**
3. 인스턴스: `supplier-demo` 선택 → **연결**

이 주소(예: `3.35.10.20`)가 서버 주소다. 메모해 둔다.

## 4. 브라우저 터미널 열기

왼쪽 메뉴 **인스턴스** → `supplier-demo` 체크 → 위쪽 **연결** 버튼 → **EC2 인스턴스 연결** 탭 → 사용자 이름 `ubuntu` 확인 → **연결**

새 탭에 검은 터미널 화면이 열린다. 아래 명령들은 여기에 붙여 넣는다. (붙여넣기: Ctrl+Shift+V 또는 마우스 오른쪽 클릭)

> **"인스턴스에 연결하지 못했습니다"가 나오면**: 같은 **연결** 화면의 **Session Manager** 탭 → **연결**. 터미널이 열리면 먼저 `sudo su - ubuntu`를 실행한다. (2-0의 IAM 역할이 붙어 있어야 하고, 인스턴스 시작 후 3~5분 걸린다)

상태 검사가 통과되고 3~5분 더 지나야 2-2의 Docker 설치가 끝난다. 설치가 끝났는지 확인한다.

```bash
docker --version
```

`Docker version ...`이 나오면 준비 완료. `command not found`면 1~2분 뒤 다시 실행한다.
`permission denied`가 나오면 터미널 탭을 닫고 4장을 다시 해 새로 접속한다.

## 5. 코드 받기

저장소가 공개(public)이므로 토큰 없이 바로 받는다. 서버에는 항상 배포 브랜치인 `main`을 받는다.

```bash
git clone -b main https://github.com/ync-capstone-desgin/hkagri-supplier.git
cd hkagri-supplier
```

> 저장소를 다시 비공개로 돌렸다면 `Username`·`Password`를 묻는다. 이때 Password에는 GitHub 비밀번호가 아니라 **토큰**을 넣는다.
> (GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens → 이 저장소만, Contents: Read-only)

## 6. 서버 설정 파일 만들기

아래를 **통째로** 붙여 넣는다. 서버의 공인 IP로 접속 주소를 만들고, DB 비밀번호와 체험 계정 비밀번호를 임의로 생성한다.

```bash
cd ~/hkagri-supplier
IP=$(curl -s https://checkip.amazonaws.com)
cat > .env.prod <<EOF
# 접속 주소 (공인 IP 기반 무료 도메인)
SITE_ADDRESS=${IP//./-}.sslip.io
# DB 접속 정보 (외부에 공개되지 않음)
DB_NAME=supplier
DB_USER=supplier
DB_PASSWORD=$(openssl rand -hex 24)
# 체험 계정(admin·manager1·manager2·viewer) 공통 비밀번호
SEED_PASSWORD=$(openssl rand -base64 9 | tr -d '/+=')
EOF
chmod 600 .env.prod
cat .env.prod
```

출력에서 두 가지를 확인한다.

- `SITE_ADDRESS`가 탄력적 IP와 같은지 (`3.35.10.20` → `3-35-10-20.sslip.io`). 다르면 3장의 탄력적 IP 연결 후 다시 실행한다.
- `SEED_PASSWORD`를 메모한다. 담당자에게 알려줄 로그인 비밀번호다.

기억하기 쉬운 비밀번호를 쓰려면 (**7장 실행 전에만** 적용됨 — DB가 처음 만들어질 때 들어간다):

```bash
sed -i 's/^SEED_PASSWORD=.*/SEED_PASSWORD=원하는비밀번호/' .env.prod && cat .env.prod
```

> 도메인이 있으면 도메인의 A 레코드를 고정 IP로 연결하고 `SITE_ADDRESS`에 도메인을 쓴다.

## 7. 실행

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

처음에는 빌드 때문에 5~10분 걸린다. 끝나면 `Started`가 4줄 나온다. 백엔드가 다 켜졌는지 확인한다.

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f backend
```

`Started SupplierPocApplication`이 보이면 준비 완료. **Ctrl+C**로 로그 보기를 끝낸다(서버는 계속 돌아간다).

브라우저에서 `https://3-35-10-20.sslip.io`로 접속해 로그인되는지 확인한다.
브라우저 터미널 탭은 닫아도 서버는 계속 켜져 있다.

## 8. 담당자에게 전달할 것

- 주소: `https://3-35-10-20.sslip.io`
- 계정: `manager1` (담당자 화면), 필요하면 `admin` (알림 설정·시연 도구)
- 비밀번호: `.env.prod`의 `SEED_PASSWORD` (로그인 화면의 "README 참고" 문구는 무시해도 된다고 안내)
- 화면의 업체·계약·인증은 모두 가상 데이터이며, 실제 계약·개인정보는 넣지 말 것
- 알림 메일은 실제로 발송되지 않는다. 알림은 화면 오른쪽 위 🔔와 알림함에서 확인한다.

## 9. 운영

4장처럼 브라우저 터미널을 열고 `cd hkagri-supplier`로 이동한 뒤 실행한다.

| 하고 싶은 일 | 명령 |
|---|---|
| 새 코드 반영 | `git pull && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build` |
| 로그 보기 | `docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f backend` |
| 샘플 데이터 처음 상태로 | `docker compose -f docker-compose.prod.yml --env-file .env.prod down -v` 후 7장 실행 명령 (**담당자가 입력한 데이터와 올린 첨부파일도 모두 지워짐**) |
| 첨부파일 용량 보기 | `docker compose -f docker-compose.prod.yml --env-file .env.prod exec backend du -sh /app/data/files` |

- 서버를 잠깐 멈추려면 콘솔에서 인스턴스 선택 → **인스턴스 상태 → 인스턴스 중지**. 다시 **인스턴스 시작**하면 앱도 자동으로 다시 켜진다(고정 IP라 주소 그대로).
- 서버를 재시작하면 관리자가 옮겨 둔 "시연 기준일"은 실제 날짜로 돌아간다.
- 첨부파일은 `file-data` 볼륨에 저장된다. 새 코드 반영(`up -d --build`)으로는 지워지지 않고, `down -v`를 하면 DB와 함께 지워진다.
- 첨부파일 크기 한도(기본 20MB)는 `.env.prod`에 `FILE_MAX_SIZE=50MB`, `FILE_MAX_REQUEST_SIZE=51MB`처럼 적고 새 코드 반영 명령을 다시 실행하면 바뀐다. (요청 전체 한도는 파일 한도보다 1MB쯤 크게)

### 체험이 끝나면 (요금 방지)

1. **인스턴스** → `supplier-demo` → **인스턴스 상태 → 인스턴스 종료(삭제)**
2. **탄력적 IP** → 주소 선택 → **작업 → 탄력적 IP 주소 연결 해제** → 다시 **작업 → 탄력적 IP 주소 릴리스**
   (쓰지 않는 탄력적 IP에도 요금이 붙는다)

## 10. 문제 해결

| 증상 | 확인할 것 |
|---|---|
| 브라우저 터미널 "연결 실패" | 보안 그룹의 SSH(22) 위치가 "위치 무관 0.0.0.0/0"인지. 그래도 안 되면 4장의 Session Manager로 접속 |
| Session Manager 탭에서 연결 버튼이 비활성 | 인스턴스 → **작업 → 보안 → IAM 역할 수정**으로 `supplier-demo-ssm`을 붙이고 3~5분 기다린다 |
| `docker: command not found` | 인스턴스 생성 직후면 몇 분 기다린다. 계속 안 되면 2-2 사용자 데이터를 빠뜨린 것 → `curl -fsSL https://get.docker.com \| sudo sh && sudo usermod -aG docker ubuntu` 실행 후 터미널 재접속 |
| 사이트가 안 열림 | 보안 그룹에 80·443이 열려 있는지, `docker compose -f docker-compose.prod.yml --env-file .env.prod ps`에서 `web`이 실행 중인지 |
| 인증서 오류 (HTTPS 경고) | `SITE_ADDRESS`가 고정 IP와 맞는지, `... logs web`에서 인증서 오류 확인. sslip.io 발급이 계속 실패하면 도메인을 사서 연결한다 |
| 로그인 후 바로 튕김, 502 오류 | 백엔드가 아직 기동 중이거나 실패. `... logs backend` 확인 |
| 빌드 중 터미널이 멈춤 | 인스턴스 유형이 t3.small 이상인지, 스왑이 켜졌는지 (`free -h`의 Swap 줄이 2.0Gi) |
