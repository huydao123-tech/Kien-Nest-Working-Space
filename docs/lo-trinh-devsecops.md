# Lộ trình học DevSecOps — áp dụng vào dự án Co-Working Space

Dành cho người làm backend, **chưa biết gì về DevOps / bảo mật hạ tầng**. Học theo đúng thứ tự từ Giai đoạn 0 đến 6; giai đoạn 7 chỉ học khi thật sự cần.

Mỗi giai đoạn có 6 phần:
1. **Mục tiêu** — học xong làm được gì.
2. **Kiến thức** — danh sách cụ thể cần học, đánh dấu `[x]` khi xong.
3. **Bảo mật** — phần "Sec" trong DevSecOps của giai đoạn đó.
4. **Bài tập luyện** — làm trên máy hoặc server thử, chưa đụng tới project.
5. **Áp dụng vào project** — việc cụ thể làm trên dự án này.
6. **Tự kiểm tra** — trả lời được hết thì sang giai đoạn sau.

Cách học mỗi giai đoạn: **đọc → làm bài tập → áp dụng vào project → tự kiểm tra**. Không đọc lý thuyết quá 2 ngày mà không gõ lệnh.

---

## Mục lục

| # | Giai đoạn | Thời gian gợi ý |
| :---: | :--- | :--- |
| 0 | [Nền tảng: Linux, Bash, Mạng, Git](#giai-đoạn-0--nền-tảng-linux-bash-mạng-git) | 3–4 tuần |
| 1 | [Docker](#giai-đoạn-1--docker) | 2–3 tuần |
| 2 | [CI và quét bảo mật tự động](#giai-đoạn-2--ci-và-quét-bảo-mật-tự-động) | 2–3 tuần |
| 3 | [Quản lý cấu hình và secret](#giai-đoạn-3--quản-lý-cấu-hình-và-secret) | 1 tuần |
| 4 | [Server, CD và hạ tầng mạng](#giai-đoạn-4--server-cd-và-hạ-tầng-mạng) | 3–4 tuần |
| 5 | [Bảo mật ứng dụng](#giai-đoạn-5--bảo-mật-ứng-dụng) | 3–4 tuần |
| 6 | [Observability: giám sát và cảnh báo](#giai-đoạn-6--observability-giám-sát-và-cảnh-báo) | 2–3 tuần |
| 7 | [Nâng cao (tùy chọn): Kubernetes, Helm, Terraform](#giai-đoạn-7--nâng-cao-tùy-chọn) | khi cần |

Tổng cộng khoảng **4–5 tháng** nếu học buổi tối và cuối tuần.

---

## DevSecOps là gì (đọc trước khi bắt đầu)

- **DevOps:** quy trình đưa code từ máy lập trình viên tới người dùng một cách **tự động, lặp lại được, nhanh**: build → test → đóng gói → triển khai → vận hành → giám sát.
- **DevSecOps:** DevOps **cộng bảo mật ở mọi bước**, thay vì đợi làm xong mới nhờ người khác kiểm tra. Khẩu hiệu là **"shift left"**: phát hiện lỗi bảo mật càng sớm (càng về bên trái của quy trình) thì sửa càng rẻ.

```
 Code ──▶ Build ──▶ Test ──▶ Đóng gói ──▶ Deploy ──▶ Vận hành ──▶ Giám sát
  │         │         │          │            │           │            │
 không    quét      quét       quét image   secret      firewall,    log bảo mật,
 lộ mật   thư viện  lỗi bảo    Docker       an toàn     HTTPS,       cảnh báo
 khẩu     có lỗ hổng mật code                           vá lỗi OS    bất thường
 (GĐ 2,3) (GĐ 2)    (GĐ 2,5)   (GĐ 1,2)     (GĐ 3,4)    (GĐ 4)       (GĐ 6)
```

---

## Giai đoạn 0 — Nền tảng: Linux, Bash, Mạng, Git

**Mục tiêu:** tự tin làm việc trên dòng lệnh Linux, hiểu một request đi qua mạng thế nào, làm việc nhóm bằng Git đúng cách. Mọi giai đoạn sau đều đứng trên nền này.

> Bạn dùng Windows: cài **WSL2 + Ubuntu** (Microsoft Store → "Ubuntu") để có Linux thật ngay trên máy, không cần mua server.

### 0.1. Linux

**Kiến thức**
- [ ] Cấu trúc thư mục: `/` `/home` `/etc` (cấu hình) `/var` (log, dữ liệu) `/opt` `/tmp` `/usr/bin`
- [ ] Di chuyển, xem file: `pwd` `ls -la` `cd` `cat` `less` `head` `tail -f` `tree`
- [ ] Thao tác file: `cp` `mv` `rm -r` `mkdir -p` `touch` `ln -s`
- [ ] Tìm kiếm: `grep -rn` `find` `which`
- [ ] Trình soạn thảo trên terminal: `nano` (dễ), `vim` (tối thiểu: `i`, `Esc`, `:wq`, `:q!`)
- [ ] Pipe và chuyển hướng: `|` `>` `>>` `2>&1`; kết hợp `cat log | grep ERROR | wc -l`
- [ ] Người dùng & nhóm: `whoami` `id` `sudo` `adduser` `usermod -aG`
- [ ] **Quyền file**: `rwx`, ký hiệu số (`755`, `644`, `600`), `chmod` `chown`
- [ ] Tiến trình: `ps aux` `top` / `htop` `kill` `kill -9`, foreground / background
- [ ] Dịch vụ với **systemd**: `systemctl status/start/stop/restart/enable`, xem log bằng `journalctl -u <tên> -f`
- [ ] Tài nguyên: `df -h` (ổ đĩa) `du -sh` (thư mục) `free -h` (RAM) `uptime`
- [ ] Cài phần mềm: `apt update` `apt install` `apt upgrade`
- [ ] Biến môi trường: `export`, `env`, `echo $PATH`, file `~/.bashrc`
- [ ] Nén / giải nén: `tar -czf` `tar -xzf` `unzip`
- [ ] Tải dữ liệu, gọi API: `curl` (`-X`, `-H`, `-d`, `-v`, `-o`), `wget`

**Bảo mật**
- [ ] Nguyên tắc **quyền tối thiểu** (least privilege): không làm mọi thứ bằng `root`; dùng `sudo` khi cần
- [ ] Vì sao file chứa khóa / mật khẩu phải là quyền `600`
- [ ] Đọc log đăng nhập: `/var/log/auth.log`, lệnh `last`

**Bài tập luyện**
1. Tạo user `deploy`, cho vào nhóm `sudo`, đăng nhập bằng user đó.
2. Tạo thư mục `/opt/demo`, chủ sở hữu là `deploy`, quyền `750`. Thử đọc bằng một user khác và quan sát bị từ chối.
3. Cài `nginx`, dùng `systemctl` để bật / tắt, xem log bằng `journalctl`.
4. Đếm số dòng chứa `ERROR` trong một file log bằng một dòng lệnh.

### 0.2. Bash script

**Kiến thức**
- [ ] Biến, nháy đơn / nháy kép, `$1 $2 $@`
- [ ] `if`, `for`, `while`, `case`; so sánh chuỗi và số
- [ ] Hàm, mã thoát (exit code) `$?`, `&&` và `||`
- [ ] `set -euo pipefail`: dừng script ngay khi có lỗi
- [ ] Đọc file cấu hình, xử lý tham số dòng lệnh

**Bảo mật**
- [ ] Không ghi mật khẩu cứng trong script; đọc từ biến môi trường
- [ ] Luôn đặt biến trong nháy kép `"$VAR"` để tránh lỗi khi có khoảng trắng / ký tự lạ

**Bài tập luyện**
- Viết `backup.sh`: nén một thư mục thành `backup-YYYYMMDD.tar.gz`, chỉ giữ 7 bản gần nhất, in thông báo lỗi và thoát mã `1` nếu thư mục không tồn tại.

### 0.3. Mạng

**Kiến thức**
- [ ] Địa chỉ IP, IP public / private, `localhost` (`127.0.0.1`) khác `0.0.0.0` thế nào
- [ ] **Port**: một ứng dụng "lắng nghe" một port; xem bằng `ss -tlnp`
- [ ] TCP và UDP khác nhau ra sao (mức khái niệm)
- [ ] **DNS**: tên miền → IP; bản ghi `A`, `CNAME`, `TXT`; lệnh `nslookup`, `dig`
- [ ] **HTTP**: method (`GET/POST/PUT/DELETE`), status code (`2xx/3xx/4xx/5xx`), header, body; xem bằng `curl -v`
- [ ] **HTTPS / TLS**: chứng chỉ, CA, vì sao trình duyệt tin một website
- [ ] **Reverse proxy**: một máy chủ đứng trước ứng dụng, nhận request rồi chuyển vào trong (nginx sẽ làm việc này)
- [ ] **Firewall**: chặn / mở port; NAT ở mức khái niệm

**Bảo mật**
- [ ] Vì sao database **không bao giờ** được mở port ra Internet
- [ ] HTTP thường bị nghe lén thế nào, HTTPS chống lại ra sao
- [ ] Bề mặt tấn công (attack surface): càng ít port mở càng ít chỗ bị tấn công

**Bài tập luyện**
1. Chạy project (`mvn spring-boot:run`), dùng `ss -tlnp` (hoặc `netstat -ano` trên Windows) tìm port 8080 và 5432.
2. `curl -v http://localhost:8080/api/resources`: chỉ ra dòng request, các header, status code trong kết quả.
3. `nslookup github.com`: GitHub đang trỏ về IP nào?

### 0.4. Git làm việc nhóm

**Kiến thức**
- [ ] `branch`, `checkout -b`, `merge`, `rebase` (khi nào dùng cái nào)
- [ ] Giải quyết **conflict**
- [ ] **Pull Request** trên GitHub: tạo, review, comment, merge
- [ ] `.gitignore`; `git log --oneline --graph`; `git revert` khác `git reset` ra sao
- [ ] Tag và đánh phiên bản theo **SemVer** (`v1.2.3`)
- [ ] **Branch protection**: cấm push thẳng vào `main`, bắt buộc qua PR

**Bảo mật**
- [ ] **Không bao giờ commit secret.** Lỡ commit thì: coi như secret đã lộ → **đổi ngay**, rồi mới xóa khỏi lịch sử (`git filter-repo`)
- [ ] Bật xác thực 2 lớp (2FA) cho tài khoản GitHub
- [ ] Ký commit (signed commits): biết là gì, vì sao dùng

**Áp dụng vào project**
- [ ] Đẩy project lên một repo GitHub (để **private**)
- [ ] Bật branch protection cho `main`
- [ ] Từ nay mọi thay đổi đi theo: tạo branch `feature/...` → commit → mở PR → merge

**Tự kiểm tra**
- Một file có quyền `640`: chủ, nhóm, người khác được làm gì?
- Gõ `https://example.com` vào trình duyệt: những bước nào xảy ra trước khi trang hiện ra (DNS, TCP, TLS, HTTP)?
- Lỡ commit mật khẩu database lên GitHub: làm gì đầu tiên?

**Tài liệu:** *The Linux Command Line* (William Shotts, đọc miễn phí tại linuxcommand.org) · MDN *An overview of HTTP* · *Pro Git* chương 2–3 (git-scm.com/book)

---

## Giai đoạn 1 — Docker

**Mục tiêu:** đóng gói backend thành một **image** chạy được ở mọi nơi; một lệnh dựng cả hệ thống (app + database).

**Kiến thức**
- [ ] Container khác máy ảo (VM) thế nào; image, layer, container, registry
- [ ] Lệnh cơ bản: `docker run` (`-d`, `-p`, `-e`, `--name`, `--rm`) `ps` `logs -f` `exec -it` `stop` `rm` `images` `rmi` `pull` `push`
- [ ] **Dockerfile**: `FROM` `WORKDIR` `COPY` `RUN` `ENV` `ARG` `EXPOSE` `USER` `ENTRYPOINT` / `CMD` `HEALTHCHECK`
- [ ] **Layer cache**: vì sao `COPY pom.xml` rồi tải thư viện **trước** khi `COPY src`
- [ ] **Multi-stage build**: stage 1 dùng Maven + JDK để build ra file `.jar`, stage 2 chỉ có JRE để chạy → image nhỏ hơn nhiều
- [ ] `.dockerignore`
- [ ] Lưu dữ liệu: **volume** khác **bind mount** thế nào; dữ liệu PostgreSQL phải nằm trong volume
- [ ] **Network**: các container trong cùng network gọi nhau bằng **tên service** (ví dụ `postgres`, không phải `localhost`)
- [ ] **Docker Compose**: `services`, `image` / `build`, `ports`, `environment`, `env_file`, `volumes`, `depends_on` + `condition: service_healthy`, `healthcheck`, `restart`
- [ ] Lệnh compose: `up -d` `down` `down -v` `logs -f` `ps` `exec`
- [ ] Giới hạn tài nguyên: `mem_limit`, `cpus`

**Bảo mật**
- [ ] **Image gốc nhỏ và chính thức**: `eclipse-temurin:21-jre` thay vì image to đủ thứ
- [ ] **Ghim phiên bản** image (`postgres:17.2`, không dùng `latest`)
- [ ] **Không chạy bằng root** trong container: tạo user riêng + `USER app`
- [ ] **Không đưa secret vào image** (không `ENV DB_PASSWORD=...`, không `COPY .env`); secret truyền lúc chạy
- [ ] Không mở port database ra máy host khi không cần
- [ ] Hiểu vì sao **không được** mount `/var/run/docker.sock` vào container
- [ ] Quét lỗ hổng image bằng **Trivy**: `trivy image <tên-image>`

**Bài tập luyện**
1. Chạy `nginx` bằng Docker, map port 8081, sửa trang chủ bằng bind mount.
2. Chạy `postgres` có volume, tạo bảng, xóa container rồi tạo lại: dữ liệu vẫn còn không?
3. Viết Dockerfile cho một app "Hello world" bất kỳ, so sánh kích thước image dùng 1 stage và multi-stage.

**Áp dụng vào project**
- [ ] `backend/Dockerfile` multi-stage: stage build (Maven + JDK 21) → stage chạy (JRE 21), chạy bằng user không phải root
- [ ] `backend/.dockerignore` (bỏ `target/`, `.idea/`, `.git/`…)
- [ ] `docker-compose.yml` gồm:
  - `postgres`: volume lưu dữ liệu; mount `backend/db/schema.sql` vào `/docker-entrypoint-initdb.d/` để **tự tạo bảng lần đầu**; healthcheck
  - `app`: build từ Dockerfile; `DB_URL=jdbc:postgresql://postgres:5432/working-space`; chờ postgres healthy mới chạy
- [ ] Chạy `trivy image` cho image backend, đọc báo cáo, sửa các lỗi mức HIGH / CRITICAL nếu có

**Tự kiểm tra**
- Vì sao trong `docker-compose.yml` app kết nối tới `postgres:5432` mà không phải `localhost:5432`?
- `docker compose down -v` khác `down` ở chỗ nào? Lệnh nào làm mất dữ liệu?
- Image backend của bạn nặng bao nhiêu MB? Vì sao multi-stage nhẹ hơn?

**Tài liệu:** docs.docker.com → *Get started* và *Dockerfile best practices* · *Docker Security Cheat Sheet* (cheatsheetseries.owasp.org)

---

## Giai đoạn 2 — CI và quét bảo mật tự động

**Mục tiêu:** mỗi Pull Request **tự động** build, chạy test và quét bảo mật. Có lỗi thì PR không được merge.

### 2.1. CI với GitHub Actions

**Kiến thức**
- [ ] CI (Continuous Integration) là gì, vì sao chạy trên mỗi PR
- [ ] Cú pháp YAML cơ bản (thụt lề, list, map)
- [ ] Khái niệm: **workflow** → **job** → **step**; **runner**; **trigger** (`on: push`, `pull_request`, `schedule`, `workflow_dispatch`)
- [ ] Action có sẵn: `actions/checkout`, `actions/setup-java` (kèm cache Maven)
- [ ] **Artifact** (lưu báo cáo test, file `.jar`), **cache**
- [ ] Job chạy song song / tuần tự (`needs`), `matrix`
- [ ] **Secrets** và biến của repository
- [ ] **Required status checks**: bắt PR phải xanh mới merge được

### 2.2. Quét bảo mật trong pipeline

| Loại quét | Viết tắt | Tìm ra cái gì | Công cụ |
| :--- | :--- | :--- | :--- |
| Lộ secret | — | Mật khẩu, token, khóa bị commit vào code | **Gitleaks**, GitHub Secret Scanning |
| Phân tích mã nguồn | **SAST** | Lỗi bảo mật trong code bạn viết: SQL injection, dùng hàm mã hóa yếu… | **CodeQL**, Semgrep |
| Thư viện bên thứ ba | **SCA** | Thư viện trong `pom.xml` có lỗ hổng đã công bố | **Dependabot**, OWASP Dependency-Check |
| Image Docker | — | Lỗ hổng trong hệ điều hành / gói cài sẵn của image | **Trivy** |
| Dockerfile / cấu hình | — | Viết Dockerfile sai thói quen an toàn | **Hadolint**, Checkov |
| Danh sách thành phần | **SBOM** | "Hóa đơn nguyên liệu": phần mềm gồm những thư viện nào, phiên bản nào | CycloneDX, Syft |

**Kiến thức**
- [ ] **CVE** là gì; điểm **CVSS** và các mức LOW / MEDIUM / HIGH / CRITICAL
- [ ] Đặt **ngưỡng chặn** (quality gate): ví dụ có lỗ hổng CRITICAL thì pipeline đỏ
- [ ] Xử lý **báo động giả** (false positive): đánh dấu bỏ qua có ghi lý do, không tắt cả công cụ
- [ ] **Bảo mật chính pipeline** (supply chain):
  - [ ] Ghim action theo mã commit SHA thay vì `@v4`
  - [ ] Giới hạn quyền token: `permissions: contents: read`
  - [ ] Không in secret ra log
  - [ ] Cẩn thận với PR từ người ngoài (`pull_request_target`)

**Bài tập luyện**
1. Tạo repo thử, viết workflow in `Hello` mỗi khi push.
2. Cố tình commit một chuỗi giống AWS key giả vào repo thử, xem Gitleaks bắt được không.

**Áp dụng vào project**
- [ ] `.github/workflows/ci.yml`: checkout → setup Java 21 (cache Maven) → `mvn -B verify` → lưu báo cáo test làm artifact
- [ ] `.github/workflows/security.yml`:
  - [ ] Gitleaks quét toàn bộ lịch sử
  - [ ] CodeQL cho Java
  - [ ] Build image Docker → Trivy quét, CRITICAL thì đỏ
  - [ ] Hadolint kiểm tra `Dockerfile`
- [ ] `.github/dependabot.yml`: tự mở PR cập nhật thư viện Maven, GitHub Actions, image Docker
- [ ] Bật branch protection: bắt buộc `ci` và `security` xanh mới merge được

**Tự kiểm tra**
- SAST khác SCA thế nào? Mỗi loại có bắt được lỗ hổng trong `spring-boot-starter-web` phiên bản cũ không?
- Trivy báo một lỗ hổng HIGH trong image gốc, nhưng chưa có bản vá. Bạn xử lý thế nào?
- Vì sao nên ghim action theo SHA?

**Tài liệu:** docs.github.com → *GitHub Actions: Quickstart*, *Security hardening for GitHub Actions* · OWASP *DevSecOps Guideline*

---

## Giai đoạn 3 — Quản lý cấu hình và secret

**Mục tiêu:** cùng một image chạy được ở mọi môi trường (máy dev, test, production), chỉ khác cấu hình. Không còn mật khẩu nằm trong code.

**Kiến thức**
- [ ] **12-Factor App**, đặc biệt mục *III. Config* (cấu hình nằm ở biến môi trường)
- [ ] Phân biệt **cấu hình** (URL, số phút giữ chỗ) và **secret** (mật khẩu, khóa ký)
- [ ] **Spring profiles** (`dev`, `prod`); thứ tự ưu tiên cấu hình trong Spring Boot (biến môi trường đè lên `application.yml`)
- [ ] File `.env` cho máy dev + `.env.example` để commit (chỉ có tên biến, không có giá trị)
- [ ] **GitHub Secrets** và **Environments** (`staging`, `production`, có thể yêu cầu duyệt trước khi deploy)
- [ ] Biết tới các **secret manager** (HashiCorp Vault, AWS Secrets Manager…): chưa cần dùng, chỉ cần biết chúng giải quyết việc gì
- [ ] **Xoay vòng secret** (rotation): đổi định kỳ và đổi ngay khi nghi bị lộ
- [ ] Băm (hash) khác mã hóa (encrypt) thế nào; mật khẩu người dùng phải **băm** (BCrypt), không mã hóa

**Bảo mật**
- [ ] Mỗi môi trường có secret **riêng**; không dùng chung mật khẩu dev và production
- [ ] User database của ứng dụng chỉ có quyền tối thiểu (project đã làm: user `cowork` không xóa được bảng)
- [ ] Tắt mọi thứ chỉ dành cho dev khi lên production

**Áp dụng vào project**
- [ ] Tạo `application-prod.yml`: **không có giá trị mặc định** cho `DB_PASSWORD`, `PAYMENT_WEBHOOK_SECRET` (thiếu là app không khởi động)
- [ ] Profile `prod` bắt buộc: `payment.sandbox.enabled: false`, `app.seed-demo-data: false`
- [ ] Thêm `.env` vào `.gitignore`, tạo `.env.example`
- [ ] `docker-compose.yml` đọc secret từ `.env`
- [ ] Chạy Gitleaks trên toàn bộ lịch sử git để chắc chưa từng lộ secret thật

**Tự kiểm tra**
- Nếu quên tắt `payment.sandbox.enabled` trên production, kẻ xấu làm được gì?
- Mật khẩu database production nên được lưu ở những đâu, và **không** được nằm ở đâu?

**Tài liệu:** 12factor.net · *Secrets Management Cheat Sheet* (OWASP) · Spring Boot docs → *Externalized Configuration*

---

## Giai đoạn 4 — Server, CD và hạ tầng mạng

**Mục tiêu:** merge vào `main` → vài phút sau phiên bản mới **tự động** chạy trên server thật, có tên miền và HTTPS, server được bảo vệ.

### 4.1. Chuẩn bị và bảo vệ server (VPS)

**Kiến thức**
- [ ] Thuê VPS Linux (Ubuntu LTS, 1–2 GB RAM là đủ để học)
- [ ] Đăng nhập SSH, tạo **khóa SSH** (`ssh-keygen -t ed25519`), `ssh-copy-id`, file `~/.ssh/config`
- [ ] Tạo user thường có `sudo`, không làm việc bằng `root`

**Bảo mật (hardening)**
- [ ] `/etc/ssh/sshd_config`: `PasswordAuthentication no`, `PermitRootLogin no`
- [ ] Firewall **ufw**: chỉ mở `22`, `80`, `443`
- [ ] **fail2ban**: tự chặn IP dò mật khẩu SSH
- [ ] **unattended-upgrades**: tự cài bản vá bảo mật cho hệ điều hành
- [ ] Docker tự mở port vượt qua ufw: hiểu vấn đề này và cách tránh (chỉ bind `127.0.0.1` cho các service nội bộ)

### 4.2. nginx, tên miền, HTTPS

**Kiến thức**
- [ ] nginx làm **reverse proxy**: `server` block, `location`, `proxy_pass`, header `X-Forwarded-For` / `X-Forwarded-Proto`
- [ ] Mua tên miền; tạo bản ghi DNS `A` trỏ về IP của VPS
- [ ] **Let's Encrypt** với `certbot`: lấy chứng chỉ, tự gia hạn, chuyển hướng HTTP → HTTPS
- [ ] Log của nginx: `access.log`, `error.log`

**Bảo mật**
- [ ] Chỉ bật TLS 1.2 và 1.3
- [ ] Header bảo mật: `Strict-Transport-Security` (HSTS), `X-Content-Type-Options`, `X-Frame-Options`, `Content-Security-Policy` (cơ bản)
- [ ] Giới hạn tần suất request bằng `limit_req` (chống spam, dò mật khẩu)
- [ ] Giới hạn kích thước body (`client_max_body_size`)
- [ ] Ẩn phiên bản nginx (`server_tokens off`)

### 4.3. CD (Continuous Deployment)

**Kiến thức**
- [ ] Container registry: **GitHub Container Registry (GHCR)**; đặt tag image theo mã commit (`:sha-abc123`) và theo phiên bản (`:v1.0.0`)
- [ ] Pipeline deploy: build image → push GHCR → SSH vào server → `docker compose pull && docker compose up -d`
- [ ] **GitHub Environments** cho `production`, bắt buộc người duyệt trước khi deploy
- [ ] **Rollback**: quay lại tag image trước đó
- [ ] **Sao lưu database**: `pg_dump` hằng ngày bằng cron, lưu ra ngoài server, **thử khôi phục** ít nhất một lần
- [ ] Biết tới deploy không gián đoạn (zero-downtime): chưa cần làm, chỉ cần hiểu ý tưởng

**Áp dụng vào project**
- [ ] VPS đã hardening đủ các mục 4.1
- [ ] `docker-compose.prod.yml` trên server: `app` + `postgres` + `nginx`; **chỉ nginx mở port ra ngoài**
- [ ] Tên miền dạng `api.<ten-mien-cua-ban>` có HTTPS
- [ ] `.github/workflows/deploy.yml`: chạy khi merge vào `main` và CI xanh
- [ ] Script sao lưu `pg_dump` + cron, giữ 7 bản

**Tự kiểm tra**
- Từ máy bạn thử kết nối thẳng `<IP-VPS>:5432` và `<IP-VPS>:8080`: cả hai phải **bị chặn**. Vì sao?
- Bản deploy mới bị lỗi: mất bao lâu để quay về bản cũ, và bằng lệnh nào?
- Kiểm tra trang của bạn trên ssllabs.com: được hạng mấy?

**Tài liệu:** DigitalOcean Community Tutorials (*Initial Server Setup with Ubuntu*, *How To Secure Nginx with Let's Encrypt*) · nginx.org/en/docs · Mozilla *SSL Configuration Generator*

---

## Giai đoạn 5 — Bảo mật ứng dụng

**Mục tiêu:** ứng dụng tự chống được các kiểu tấn công phổ biến; biết cách tìm lỗ hổng trong chính code của mình.

### 5.1. OWASP Top 10 (2021), soi vào project

| Mã | Lỗ hổng | Ở project này | Tình trạng |
| :--- | :--- | :--- | :--- |
| A01 | **Broken Access Control** — truy cập thứ không được phép | **Không có đăng nhập**: ai cũng gọi được API bảo trì chỗ ngồi; ai biết `id` đơn là xem được đơn của người khác, kể cả mã QR | ❌ Nghiêm trọng nhất |
| A02 | Cryptographic Failures — mã hóa yếu / không mã hóa | Chưa có HTTPS (sửa ở GĐ 4); chưa có mật khẩu người dùng để băm | 🟡 |
| A03 | Injection — SQL injection… | Truy vấn dùng JPA có tham số → an toàn | ✅ |
| A04 | Insecure Design — lỗi thiết kế | Đã có chống đặt trùng, giữ chỗ có hạn | ✅ phần lớn |
| A05 | Security Misconfiguration — cấu hình sai | Endpoint sandbox thanh toán, H2 console nếu bật nhầm profile | 🟡 sửa ở GĐ 3 |
| A06 | Vulnerable Components — thư viện có lỗ hổng | Chưa quét | 🟡 sửa ở GĐ 2 |
| A07 | Authentication Failures — đăng nhập yếu | Chưa có đăng nhập; OTP (UC01) cần chống dò | ❌ |
| A08 | Software & Data Integrity — dữ liệu bị giả mạo | Webhook thanh toán đã kiểm tra chữ ký HMAC | ✅ |
| A09 | Logging & Monitoring Failures | Chưa ghi log bảo mật, chưa dùng bảng `audit_log` | ❌ sửa ở GĐ 6 |
| A10 | SSRF — server bị lừa gọi tới địa chỉ nội bộ | Chưa có chức năng gọi URL do người dùng nhập | ✅ |

### 5.2. Kiến thức

**Xác thực và phân quyền**
- [ ] **Authentication** (bạn là ai?) khác **Authorization** (bạn được làm gì?)
- [ ] **Spring Security**: filter chain, `SecurityFilterChain`, `UserDetailsService`
- [ ] Băm mật khẩu bằng **BCrypt**
- [ ] **JWT**: cấu trúc (header.payload.signature), ký, hạn dùng, refresh token; những lỗi hay gặp (không kiểm tra hạn, dùng thuật toán `none`, đưa thông tin nhạy cảm vào payload)
- [ ] Phân quyền theo vai trò: `CUSTOMER`, `RECEPTIONIST`, `MANAGER`, `ADMIN` (khớp bảng `staff.role`); `@PreAuthorize`
- [ ] **IDOR**: kiểm tra *đơn này có thuộc về người đang gọi không*, không chỉ kiểm tra *đã đăng nhập chưa*
- [ ] CORS (khi có frontend); CSRF (khi nào cần, khi nào API không cần)

**Phòng thủ khác**
- [ ] Kiểm tra dữ liệu đầu vào (project đã dùng `@Valid`)
- [ ] Không trả stack trace / thông tin nội bộ trong lỗi (project đã dùng `ProblemDetail`)
- [ ] Giới hạn tần suất gọi (rate limit) theo IP / tài khoản: thư viện **Bucket4j**; quan trọng cho OTP và đăng nhập
- [ ] Ghi `audit_log` cho thao tác nhạy cảm: đổi giá, miễn phụ phí, đền bù

**Kiểm thử bảo mật**
- [ ] **Threat modeling** với **STRIDE** (Spoofing, Tampering, Repudiation, Information disclosure, Denial of service, Elevation of privilege): áp dụng cho luồng thanh toán
- [ ] **DAST**: quét ứng dụng đang chạy bằng **OWASP ZAP** (baseline scan)
- [ ] Viết **test bảo mật**: gọi API không token → 401; khách A xem đơn của khách B → 403

**Áp dụng vào project**
- [ ] Thêm Spring Security + JWT; đăng nhập nhân viên bằng bảng `staff` (UC23)
- [ ] Khách hàng: đăng nhập bằng OTP qua SĐT (UC01), có rate limit
- [ ] Phân quyền từng API:
  - Khách: chỉ xem / hủy / gia hạn **đơn của mình**
  - Lễ tân: check-in / check-out hộ, thu tiền, bảo trì chỗ ngồi
  - Quản lý: cấu hình, báo cáo
- [ ] Endpoint sandbox chỉ tồn tại ở profile dev
- [ ] Ghi `audit_log`
- [ ] Thêm job OWASP ZAP baseline vào pipeline, chạy trên môi trường staging
- [ ] Bộ integration test cho 401 / 403

**Tự kiểm tra**
- Viết một kịch bản tấn công IDOR trên API hiện tại, rồi viết test chứng minh đã sửa xong.
- JWT bị lộ thì kẻ xấu dùng được bao lâu? Làm sao rút ngắn thời gian đó?
- Áp STRIDE cho webhook thanh toán: mỗi chữ cái, project đã chống bằng cách nào?

**Tài liệu:** owasp.org/Top10 · OWASP *Cheat Sheet Series* (Authentication, JWT, Authorization, REST Security) · Spring Security Reference → *Servlet Applications* · OWASP *ZAP Getting Started*

---

## Giai đoạn 6 — Observability: giám sát và cảnh báo

**Mục tiêu:** biết hệ thống đang **khỏe hay ốm** mà không cần ai báo; có lỗi hoặc dấu hiệu bị tấn công thì nhận cảnh báo ngay.

**Kiến thức**
- [ ] Ba trụ cột: **metrics** (con số theo thời gian), **logs** (sự kiện chi tiết), **traces** (đường đi của một request qua nhiều thành phần)
- [ ] **Spring Boot Actuator**: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`
- [ ] **Micrometer**: counter, gauge, timer; tự tạo metric nghiệp vụ
- [ ] **Prometheus**: scrape, khái niệm time series, **PromQL** cơ bản (`rate`, `sum by`, `histogram_quantile`)
- [ ] **Grafana**: datasource, dashboard, panel, biến
- [ ] **Cảnh báo**: alert rule, gửi tin qua Telegram / email; tránh "bão cảnh báo"
- [ ] **Log có cấu trúc** (JSON); log level; **correlation id** (MDC) để lần theo một request
- [ ] **Loki** + Promtail để gom log; truy vấn bằng LogQL
- [ ] **OpenTelemetry** và tracing ở mức khái niệm
- [ ] **SLI / SLO**: đo theo yêu cầu phi chức năng của SRS. Ví dụ NFR-05: uptime ≥ 99.5%; NFR-01: tìm chỗ trống p95 ≤ 2 giây

**Bảo mật**
- [ ] Endpoint actuator **không được mở công khai**: chỉ cho mạng nội bộ hoặc yêu cầu xác thực
- [ ] **Không ghi dữ liệu nhạy cảm vào log**: mật khẩu, token, mã QR, số điện thoại đầy đủ (project đã che SĐT trong log thông báo)
- [ ] Giám sát tín hiệu tấn công: số lỗi 401 / 403 tăng đột biến, webhook sai chữ ký, nhiều OTP sai từ một IP
- [ ] Giữ log đủ lâu để điều tra sự cố (SRS NFR-10: audit log ≥ 2 năm)

**Áp dụng vào project**
- [ ] Thêm `spring-boot-starter-actuator` + `micrometer-registry-prometheus`
- [ ] Metric nghiệp vụ: số đơn tạo mới, số đơn hết hạn giữ chỗ, số thanh toán thất bại, số lần chặn đặt trùng
- [ ] `docker-compose.monitoring.yml`: Prometheus + Grafana + Loki
- [ ] Dashboard: request / giây, tỷ lệ lỗi, thời gian phản hồi p95, metric nghiệp vụ ở trên
- [ ] Cảnh báo: app không phản hồi > 2 phút; tỷ lệ lỗi 5xx > 5%; webhook sai chữ ký > 10 lần / 5 phút
- [ ] Log dạng JSON có correlation id

**Tự kiểm tra**
- Viết câu PromQL tính tỷ lệ request lỗi 5xx trong 5 phút gần nhất.
- Khách báo "bấm thanh toán xong mà đơn không xác nhận": dùng log và correlation id lần ra nguyên nhân thế nào?
- Cảnh báo nào cho bạn biết có người đang dò mã OTP?

**Tài liệu:** prometheus.io/docs → *Getting started* · grafana.com/docs → *Getting started* · Spring Boot docs → *Production-ready Features* · Google *SRE Book* chương 4 (SLO)

---

## Giai đoạn 7 — Nâng cao (tùy chọn)

Chỉ học khi **thật sự có nhu cầu**: nhiều server, nhiều service, hoặc đi làm ở công ty dùng cloud. Với project cỡ này, Giai đoạn 4 là đủ để chạy thật.

| Chủ đề | Học gì | Khi nào cần |
| :--- | :--- | :--- |
| **Kubernetes** | Pod, Deployment, Service, Ingress, ConfigMap, Secret, liveness / readiness probe, resource limit, namespace, RBAC, NetworkPolicy | Chạy nhiều bản sao app trên nhiều máy, tự co giãn |
| **Helm** | Chart, values, template, release, upgrade / rollback | Đã dùng Kubernetes và muốn đóng gói cấu hình |
| **Terraform** (IaC) | Provider, resource, state, `plan` / `apply`, module, remote state; quét bằng Checkov / tfsec | Tạo hạ tầng cloud (server, database, mạng) bằng code |
| **Terragrunt** | Dùng lại cấu hình Terraform cho nhiều môi trường | Nhiều môi trường, nhiều tài khoản cloud |
| **Policy as Code** | OPA / Kyverno | Bắt buộc mọi deployment tuân theo luật bảo mật |

---

## Thói quen nên giữ trong suốt quá trình học

- Mỗi giai đoạn làm trên **một branch riêng**, mở PR, tự review lại trước khi merge.
- Ghi chú lại **lỗi đã gặp và cách sửa** vào một file riêng (ví dụ `docs/nhat-ky-hoc.md`). Đây là phần học được nhiều nhất.
- Mỗi khi thêm công cụ mới, tự hỏi: **"Cái này có làm lộ gì không? Ai truy cập được nó?"**. Đó chính là tư duy DevSecOps.
