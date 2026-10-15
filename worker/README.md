# CraftMate Connector API — Cloudflare Worker (v0.3)

الوسيط الآمن بين بلجن ماينكرافت وقاعدة Supabase. البلجن لا يعرف أي مفتاح قاعدة بيانات: يعرف فقط كود ربط لمرة واحدة، ثم رمز سيرفر طويل العمر خاص به.

## لماذا Worker أصلاً؟

ربط البلجن بقاعدة البيانات يحتاج صلاحية تتجاوز RLS (العثور على كود الربط وتفعيله). هذه الصلاحية لا يجوز أن تعيش في:

- ❌ كود الموقع العام
- ❌ إعدادات البلجن على سيرفر اللاعب
- ❌ مستودع GitHub

لذلك تعيش فقط في **Cloudflare Workers Secrets** على حساب مالك المشروع.

## خطوات النشر (مرة واحدة — حساب Cloudflare مجاني)

```bash
cd worker
npx wrangler login
npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY
# الصق هنا المفتاح الجديد بعد تدويره فقط — لا يظهر ولا يُحفظ في أي ملف
npx wrangler deploy
```

بعد النشر ستحصل على رابط مثل:

```text
https://craftmate-connector-api.<your-subdomain>.workers.dev
```

## بعد النشر: 3 أماكن تُحدَّث بالرابط

1. **بلجن السيرفرات**: `plugins/CraftMateConnector/config.yml` → `backend.api-url`
2. **قاعدة البيانات**: شغّل `supabase/migrations/004_connector_api_v03.sql` في SQL Editor
3. **التحقق**:

```bash
curl https://craftmate-connector-api.<your-subdomain>.workers.dev/health
# {"ok":true,"service":"craftmate-connector-api","version":"0.3.0","configured":true}
```

## الـ API

| المسار | الوظيفة | المصادقة |
|---|---|---|
| `POST /v1/claim` | استبدال كود `CM-XXXX-XXXX` لمرة واحدة برمز سيرفر طويل العمر | الكود نفسه |
| `POST /v1/heartbeat` | نبضة حالة وبيئة كل 60 ثانية | رمز السيرفر (Bearer) |
| `POST /v1/diagnose` | رفع لقطة سجل منقّاة وتصنيفها وحفظ تقرير وتنبيه | رمز السيرفر |
| `POST /v1/shutdown` | حالة سبات نظيفة `HIBERNATING` عند إغلاق سيرفر فارغ | رمز السيرفر |
| `POST /v1/revoke` | إبطال رمز السيرفر وفصله | رمز السيرفر |
| `GET /health` | فحص جاهزية الخدمة | — |

تم اختبار دورة كاملة آلياً (مطالبة → رفض إعادة استخدام الكود → نبضة → تشخيص → سبات) قبل نشر v0.3.
