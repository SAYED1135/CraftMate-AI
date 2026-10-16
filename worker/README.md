# CraftMate Connector & Cloud Auth API — Cloudflare Worker (v0.4.0)

الوسيط الآمن بين بلجن ماينكرافت وقاعدة Supabase. البلجن لا يعرف أي مفتاح قاعدة بيانات: يعرف فقط كود ربط لمرة واحدة، ثم رمز سيرفر طويل العمر خاص به.

## الحالة الحالية

- **Worker URL:** https://craftmate-connector-api.craftmate-api-sayed.workers.dev
- **Health check:** https://craftmate-connector-api.craftmate-api-sayed.workers.dev/health
- النتيجة المتوقعة:

```json
{"ok":true,"service":"craftmate-connector-api","version":"0.4.0","auth_registration":true,"configured":true}
```

تم إثبات `configured:true` بعد حفظ `SUPABASE_SERVICE_ROLE_KEY` كـ Cloudflare Worker Secret بالاسم الصحيح. بعد نشر v0.4.0 يجب أن يظهر أيضًا `auth_registration:true`.

## لماذا Worker أصلاً؟

ربط البلجن بقاعدة البيانات يحتاج صلاحية تتجاوز RLS (العثور على كود الربط وتفعيله). هذه الصلاحية لا يجوز أن تعيش في:

- ❌ كود الموقع العام
- ❌ إعدادات البلجن على سيرفر اللاعب
- ❌ مستودع GitHub

لذلك تعيش فقط في **Cloudflare Workers Secrets** على حساب مالك المشروع.

## نشر أو إعادة نشر الـ Worker

```bash
cd worker
npx wrangler login
npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY
# الصق هنا المفتاح الجديد بعد تدويره فقط، بعد ظهور Enter a secret value
# لا تكتب المفتاح بعد اسم الأمر نفسه
npx wrangler deploy
```

الأمر الصحيح هو:

```text
npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY
```

ثم لصق القيمة السرية بعد طلبها. وضع المفتاح مكان `SUPABASE_SERVICE_ROLE_KEY` ينشئ Secret باسم خاطئ ولا يجعل الـ Worker جاهزاً.

## إعداد بلجن السيرفرات

ابتداءً من **CraftMate-Connector v0.4.0**، يهاجر البلجن تلقائياً أي `config.yml` قديم يحتوي على `YOUR-SUBDOMAIN` إلى عنوان الإنتاج أعلاه عند أول تشغيل. كما يمكن ضبطه يدوياً في:

```text
plugins/CraftMateConnector/config.yml
```

```yaml
backend:
  api-url: "https://craftmate-connector-api.craftmate-api-sayed.workers.dev"
```

لا تضف `/` في نهاية الرابط، ولا تضف `/v1/claim`، ولا تضع رابط Supabase أو أي مفتاح هنا.

## قاعدة البيانات

شغّل الملف التالي مرة واحدة في Supabase SQL Editor بعد الملفات 001–003:

```text
supabase/migrations/004_connector_api_v03.sql
```

## أداة الإصلاح لـ Windows

الملف [`fix-connector.ps1`](./fix-connector.ps1) يحذف أي Secret خاطئ الاسم، ويطلب مفتاح Supabase الجديد في إدخال مخفي، ويحفظه بالاسم الصحيح، ثم يعيد النشر ويتحقق من `/health`. لا يعرض أسماء الأسرار الخاطئة ولا يكتب المفتاح إلى القرص.

لنشر نقطة التسجيل السحابي فقط، عندما يكون الـ Secret موجودًا أصلًا، استخدم [`deploy-cloud-auth.ps1`](./deploy-cloud-auth.ps1): يتحقق من اسم الـ Secret من دون قراءة قيمته، ثم ينشر وينتظر `auth_registration:true`.

## الـ API

| المسار | الوظيفة | المصادقة |
|---|---|---|
| `POST /v1/auth/register` | إنشاء حساب Supabase مؤكد من خادم موثوق من دون إرسال بريد تأكيد | تحقق خادمي + Honeypot + حد محاولات |
| `POST /v1/claim` | استبدال كود `CM-XXXX-XXXX` لمرة واحدة برمز سيرفر طويل العمر | الكود نفسه |
| `POST /v1/heartbeat` | نبضة حالة وبيئة كل 60 ثانية | رمز السيرفر (Bearer) |
| `POST /v1/diagnose` | رفع لقطة سجل منقّاة وتصنيفها وحفظ تقرير وتنبيه | رمز السيرفر |
| `POST /v1/shutdown` | حالة سبات نظيفة `HIBERNATING` عند إغلاق سيرفر فارغ | رمز السيرفر |
| `POST /v1/revoke` | إبطال رمز السيرفر وفصله | رمز السيرفر |
| `GET /health` | فحص جاهزية الخدمة | — |

## أخطاء الربط الشائعة

| الخطأ | المعنى | الحل |
|---|---|---|
| `Connector API URL is not configured` | إصدار بلجن قديم أو إعداد ما زال يحتوي قيمة مؤقتة | حدّث إلى v0.4.0 أو عدّل `backend.api-url` يدوياً وأعد التشغيل |
| `404 INVALID_CODE` | الكود غير محفوظ سحابياً أو غير صحيح | سجّل دخولك بالوضع السحابي وولّد كوداً جديداً |
| `409 CODE_ALREADY_USED` | الكود استُخدم من قبل | ولّد كوداً جديداً |
| `410 CODE_EXPIRED` | مرّت أكثر من 15 دقيقة على الكود | ولّد كوداً جديداً واربط فوراً |
| `500 INTERNAL_ERROR` | مشكلة قاعدة بيانات أو صلاحيات الـ Worker | تأكد من Migration 004 ومن صلاحية مفتاح الـ Worker السري |

تم اختبار دورة الربط آليًا بمحاكاة، وتمت إضافة اختبارات Mock لنقطة التسجيل السحابي في v0.4.0 (إنشاء صالح/تكرار/كلمة سر ضعيفة/Honeypot/Health).
