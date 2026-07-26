# راهنمای استقرار Chat2DB (نسخه فارسی، تحت وب)

این سند نحوه اجرای Chat2DB Community را به‌صورت یک سرویس تحت وب با Docker توضیح می‌دهد.
رابط کاربری این نسخه فارسی است و زبان پیش‌فرض روی فارسی تنظیم شده است.

---

## پیش‌نیازها

فقط دو چیز روی سرور لازم است:

- **Docker** نسخه ۲۰.۱۰ یا بالاتر
- **Docker Compose v2** (دستور `docker compose`)

نیازی به نصب Java، Node.js، Maven یا Yarn روی سرور نیست — همه‌چیز داخل خود Docker ساخته می‌شود.

منابع پیشنهادی سرور:

| منبع | حداقل | پیشنهادی |
|---|---|---|
| CPU | ۲ هسته | ۴ هسته |
| RAM | ۲ گیگابایت | ۴ گیگابایت |
| فضای دیسک | ۵ گیگابایت | ۱۰ گیگابایت |

> نکته: مرحله‌ی **ساخت** ایمیج سنگین‌تر از اجرای آن است (کامپایل فرانت‌اند و بک‌اند).
> اگر سرور شما کم‌منابع است، ایمیج را روی یک ماشین قوی‌تر بسازید و طبق بخش
> [انتقال ایمیج به سرورهای دیگر](#انتقال-ایمیج-به-سرورهای-دیگر) منتقل کنید.

---

## راه‌اندازی سریع

```bash
git clone https://github.com/techwikicto-cyber/Chat2DB.git
cd Chat2DB
docker compose -f docker/docker-compose.yml up -d --build
```

اولین ساخت بسته به سرعت شبکه و سرور حدود **۱۰ تا ۲۵ دقیقه** طول می‌کشد.
پس از آن، برنامه روی این نشانی در دسترس است:

```
http://127.0.0.1:10825
```

بررسی وضعیت:

```bash
docker compose -f docker/docker-compose.yml ps
docker compose -f docker/docker-compose.yml logs -f
```

---

## اگر ساخت به‌دلیل مخزن npm شکست خورد

این پروژه به‌صورت پیش‌فرض از مخزن `registry.npmmirror.com` (آینه‌ی چینی npm) استفاده می‌کند.
اگر شبکه‌ی شما به این آدرس دسترسی ندارد، ساخت در مرحله‌ی `yarn install` متوقف می‌شود.

راه‌حل — مخزن رسمی npm را جایگزین کنید:

```bash
docker build \
  --build-arg NPM_REGISTRY=https://registry.npmjs.org/ \
  -f docker/Dockerfile \
  -t chat2db-fa:latest \
  .
```

یا در فایل `docker/docker-compose.yml` این خط را از حالت کامنت خارج کنید:

```yaml
        NPM_REGISTRY: https://registry.npmjs.org/
```

این کار هیچ فایلی را در مخزن تغییر نمی‌دهد و فقط روی همان ساخت اثر می‌گذارد.

---

## ⚠️ امنیت — این بخش را حتماً بخوانید

**Chat2DB Community هیچ سیستم ورود، حساب کاربری یا احراز هویتی ندارد.**
این محصول برای اجرای محلی و تک‌کاربره طراحی شده است.

یعنی: **هر کسی که به پورت این سرویس دسترسی داشته باشد، به همه‌ی اتصال‌های
پایگاه‌داده‌ی ذخیره‌شده و داده‌های آن‌ها دسترسی کامل دارد.**

به همین دلیل، پیکربندی پیش‌فرض فقط روی `127.0.0.1` گوش می‌دهد و از بیرون سرور
قابل دسترسی نیست.

### اگر می‌خواهید از راه دور به آن دسترسی داشته باشید

**هرگز** فقط `CHAT2DB_BIND_ADDRESS=0.0.0.0` را تنظیم نکنید و آن را روی اینترنت باز نگذارید.
یکی از این سه روش را انتخاب کنید:

**۱. تونل SSH (ساده‌ترین و امن‌ترین برای استفاده‌ی شخصی)**

سرویس روی سرور همچنان فقط روی `127.0.0.1` می‌ماند و شما از سیستم خودتان تونل می‌زنید:

```bash
ssh -L 10825:127.0.0.1:10825 user@your-server
```

سپس در مرورگرِ سیستم خودتان `http://127.0.0.1:10825` را باز کنید.

**۲. پروکسی معکوس با TLS و احراز هویت**

سرویس را پشت Nginx / Caddy / Traefik قرار دهید که هم HTTPS را مدیریت کند و هم
کاربر را احراز هویت کند (مثلاً Basic Auth، OAuth2 Proxy یا Authelia).
نمونه‌ی Nginx با Basic Auth:

```nginx
server {
    listen 443 ssl;
    server_name db.example.com;

    ssl_certificate     /etc/letsencrypt/live/db.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/db.example.com/privkey.pem;

    location / {
        auth_basic           "Chat2DB";
        auth_basic_user_file /etc/nginx/.htpasswd;

        proxy_pass http://127.0.0.1:10825;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # پاسخ‌های جریانی هوش مصنوعی نباید بافر شوند
        proxy_buffering off;
        proxy_read_timeout 3600s;
    }
}
```

ساخت فایل رمز عبور:

```bash
sudo htpasswd -c /etc/nginx/.htpasswd myuser
```

**۳. شبکه‌ی خصوصی / VPN**

سرویس را فقط روی یک رابط شبکه‌ی داخلی یا VPN (مثلاً WireGuard یا Tailscale) منتشر کنید:

```bash
CHAT2DB_BIND_ADDRESS=10.8.0.1 docker compose -f docker/docker-compose.yml up -d
```

---

## کلید رمزنگاری

Chat2DB رمز عبور اتصال‌های پایگاه‌داده و کلیدهای API مدل‌های هوش مصنوعی را با
**AES-256-GCM** رمزنگاری می‌کند و برای این کار به یک کلید نیاز دارد.

در این نسخه، اگر کلیدی تعیین نکنید، هنگام اولین اجرا به‌طور خودکار یک کلید امن
ساخته و در این مسیر داخل والیوم داده ذخیره می‌شود:

```
/root/.chat2db-community/config/encryption.key
```

> ⚠️ **هشدار مهم:** اگر این کلید را از دست بدهید، هیچ‌کدام از رمزهای عبور و
> کلیدهای API ذخیره‌شده دیگر قابل رمزگشایی نیستند. حتماً از والیوم داده
> پشتیبان بگیرید (بخش [پشتیبان‌گیری](#پشتیبانگیری-و-بازیابی)).

### تعیین کلید به‌صورت دستی

اگر می‌خواهید کلید را خودتان مدیریت کنید (مثلاً برای اجرای چند نمونه با داده‌ی مشترک):

```bash
# ساخت یک کلید ۳۲ بایتی با کدگذاری Base64
openssl rand -base64 32
```

سپس آن را در فایل `.env` کنار `docker-compose.yml` قرار دهید:

```env
CHAT2DB_COMMUNITY_ENCRYPTION_KEY=<کلیدی که ساختید>
```

---

## تنظیمات قابل شخصی‌سازی

این متغیرها را می‌توانید در فایل `.env` یا هنگام اجرای دستور تنظیم کنید:

| متغیر | پیش‌فرض | توضیح |
|---|---|---|
| `CHAT2DB_PORT` | `10825` | پورتی که روی سرور منتشر می‌شود |
| `CHAT2DB_BIND_ADDRESS` | `127.0.0.1` | آدرسی که سرویس روی آن گوش می‌دهد |
| `CHAT2DB_JAVA_OPTS` | `-Xmx2g` | تنظیمات JVM (مثلاً سقف حافظه) |
| `CHAT2DB_COMMUNITY_ENCRYPTION_KEY` | (خودکار) | کلید رمزنگاری |
| `CHAT2DB_IMAGE_TAG` | `latest` | برچسب ایمیج |
| `CHAT2DB_APP_VERSION` | `5.3.0` | نسخه‌ی نمایش‌داده‌شده در برنامه |

نمونه‌ی فایل `.env`:

```env
CHAT2DB_PORT=8080
CHAT2DB_BIND_ADDRESS=127.0.0.1
CHAT2DB_JAVA_OPTS=-Xmx4g
```

---

## انتقال ایمیج به سرورهای دیگر

نیازی نیست روی هر سرور ایمیج را از نو بسازید. یک بار بسازید و منتقل کنید.

**روش ۱ — از طریق فایل (بدون نیاز به رجیستری):**

```bash
# روی ماشین سازنده
docker save chat2db-fa:latest | gzip > chat2db-fa.tar.gz
scp chat2db-fa.tar.gz user@target-server:/tmp/

# روی سرور مقصد
gunzip -c /tmp/chat2db-fa.tar.gz | docker load
```

**روش ۲ — از طریق رجیستری خصوصی:**

```bash
docker tag chat2db-fa:latest registry.example.com/chat2db-fa:5.3.0
docker push registry.example.com/chat2db-fa:5.3.0

# روی سرور مقصد
docker pull registry.example.com/chat2db-fa:5.3.0
```

سپس روی سرور مقصد، در `docker-compose.yml` بخش `build` را حذف یا کامنت کنید تا
از ایمیج آماده استفاده شود.

---

## پشتیبان‌گیری و بازیابی

همه‌ی داده‌ها (اتصال‌ها، تاریخچه‌ی کوئری‌ها، کلید رمزنگاری) در والیوم
`chat2db-community-data` قرار دارند.

**پشتیبان‌گیری:**

```bash
docker run --rm \
  -v chat2db-community-data:/data:ro \
  -v "$(pwd)":/backup \
  alpine tar czf /backup/chat2db-backup-$(date +%F).tar.gz -C /data .
```

**بازیابی:**

```bash
docker compose -f docker/docker-compose.yml down

docker run --rm \
  -v chat2db-community-data:/data \
  -v "$(pwd)":/backup \
  alpine sh -c "rm -rf /data/* && tar xzf /backup/chat2db-backup-YYYY-MM-DD.tar.gz -C /data"

docker compose -f docker/docker-compose.yml up -d
```

---

## به‌روزرسانی

```bash
cd Chat2DB
git pull
docker compose -f docker/docker-compose.yml up -d --build
```

والیوم داده دست‌نخورده می‌ماند، بنابراین اتصال‌ها و تاریخچه حفظ می‌شوند.
(با این حال، پیش از هر به‌روزرسانی پشتیبان بگیرید.)

---

## عیب‌یابی

**کانتینر بالا نمی‌آید یا مدام ری‌استارت می‌شود**

```bash
docker compose -f docker/docker-compose.yml logs --tail=100
```

**خطای مربوط به کلید رمزنگاری**

اگر پیام خطایی درباره‌ی `encryption key` دیدید، یعنی متغیر
`CHAT2DB_COMMUNITY_ENCRYPTION_KEY_FILE` به فایلی اشاره می‌کند که وجود ندارد.
یا فایل را در آن مسیر mount کنید، یا متغیر را خالی بگذارید تا کلید به‌طور خودکار ساخته شود.

**ساخت در مرحله‌ی `yarn install` گیر می‌کند**

بخش [اگر ساخت به‌دلیل مخزن npm شکست خورد](#اگر-ساخت-بهدلیل-مخزن-npm-شکست-خورد) را ببینید.

**اتصال به پایگاه‌داده‌ای روی خود سرور میزبان برقرار نمی‌شود**

از داخل کانتینر، `localhost` به خود کانتینر اشاره می‌کند نه به سرور.
به‌جای آن از `host.docker.internal` استفاده کنید و این بخش را به سرویس اضافه کنید:

```yaml
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

**کمبود حافظه هنگام ساخت**

مقدار حافظه‌ی در دسترس Docker را افزایش دهید، یا ایمیج را روی ماشین دیگری بسازید و
طبق بخش انتقال ایمیج منتقل کنید.

---

## زبان رابط کاربری

زبان پیش‌فرض **فارسی** است. برای تغییر زبان:

**تنظیمات ← پایه ← زبان**

گزینه‌های موجود: فارسی و English.

چیدمان صفحه در هر دو زبان چپ‌به‌راست (LTR) است؛ این کار عمدی است، چون ویرایشگر SQL،
جدول نتایج و درخت اشیاء پایگاه‌داده ذاتاً محتوای چپ‌به‌راست دارند.

---

## ⚠️ نکته‌ی مجوز (License)

نسخه‌های ۵.۳.۰ به بعد Chat2DB تحت مجوز **source-available** منتشر شده‌اند (نه Apache 2.0 خالص).

بر اساس متن مجوز:

- ✅ استفاده‌ی شخصی، استفاده‌ی داخلی سازمانی و خودمیزبانی آموزشی/غیرانتفاعی **آزاد** است —
  یعنی اجرای آن روی سرورهای خودتان برای استفاده‌ی خودتان یا سازمانتان مجاز است.
- ❌ ارائه‌ی آن به‌عنوان محصول یا سرویس به **اشخاص ثالث مستقل** (SaaS، میزبانی مدیریت‌شده،
  white-label، OEM یا توزیع نسخه‌ی باینری/Docker به بیرون) بدون مجوز کتبی از
  Chat2DB (OtterMind) **مجاز نیست** — چه رایگان و چه پولی.

متن کامل را در فایل [LICENSE](../LICENSE) بخوانید. این خلاصه مشاوره‌ی حقوقی نیست؛
پیش از هر تصمیم تجاری با یک وکیل مشورت کنید.
