# 📘 الملف الشامل لمشروع CraftMate AI — المرجع الكامل
> آخر تحديث: 2026-10-17 · الحالة الحية: Worker ‏0.13.0 · الموقع ux20 · ‏HEAD: ‏7f1d76a
> هذا الملف هو نقطة الحقيقة الواحدة للمشروع: الهوية، الروابط، المعمارية، المفاتيح (بالأسماء والأماكن فقط — بلا أي قيم سرية)، القرارات، والتشغيل.

---

## 1) الهوية والرؤية
- **المنتج:** CraftMate AI — منصة ويب عربية تساعد صاحب سيرفر Minecraft على إدارة سيرفره عبر **شات ذكاء اصطناعي**: تشخيص وحل مشاكل، تجهيز وبناء، وإدارة يومية.
- **الشكل المعتمد للمنتج:** ربط موثق للسيرفر ← شات سيرفر شبه كامل الشاشة (بأسلوب محادثات حديث). لا لوحة تحكم بديلة عن الاستضافة.
- **سياسة التنفيذ:** بعد الربط وتفعيل التحكم، التنفيذ فوري (`full_instant`) لكن فقط عبر **قوالب أوامر معتمدة ومحددة** — ممنوع التنفيذ الحر وممنوع وصول النموذج لباسوردات أو شيل.
- **المنصات المستهدفة بالدعم العميق:** ‏play.hosting · falixnodes.net · mcserverhost.com · minefort.com — والمنتج يعمل مع أي استضافة حسب قدراتها.
- **النموذج التجاري:** مجاني حاليًا بالكامل ($0) باستخدام GitHub + Cloudflare + Supabase بخططهم المجانية. خطة مجانية: سيرفر واحد + 10 تشخيصات/شهر.

## 2) الروابط الرسمية
| العنصر | الرابط |
|---|---|
| المستودع (GitHub) | https://github.com/SAYED1135/CraftMate-AI |
| الموقع التعريفي | https://sayed1135.github.io/CraftMate-AI/ |
| تطبيق الويب (اللوحة والشات) | https://sayed1135.github.io/CraftMate-AI/app.html |
| خدمة الـ API (Worker) | https://craftmate-connector-api.craftmate-api-sayed.workers.dev |
| فحص صحة الخدمة | ‎/health‎ على نفس النطاق (حاليًا: version 0.13.0) |
| مشروع Supabase | ref: ‎fucsztovkkilyboinhrv‎ |
| لوحة play.hosting (هدف الربط الأول) | https://panel.play.hosting |

## 3) المعمارية الحالية
```
متصفح المستخدم (app.html على GitHub Pages)
   │  HTTPS + جلسة سحابية
   ▼
Cloudflare Worker (worker/src/index.js) — العقل: حسابات/ربط/مهام/شات AI
   │  Service Role عبر REST
   ▼
Supabase (Postgres + Auth) — المستخدمون/السيرفرات/التقارير/المهام
   ▲
   │  قناة محمية بسر مشترك (x-runner-secret)
Panel Runner (bot/runner.mjs على GitHub Actions — workflow: bot-runner.yml)
   │  HTTPS
   ▼
لوحات الاستضافات (حساب المنصة Subuser) — تحقق الظهور ثم تنفيذ عبر الكونسول/API
نموذج AI: Gemini (مفتاحه سر في Cloudflare) — يخطط فقط لقوالب معتمدة، والنتيجة دائمًا من المنفذ.
```

## 4) طريقة الربط المعتمدة الآن (قرار المالك — Subuser v3)
1. زر «اربط سيرفرك» ← لصق **رابط الاستضافة** (كشف تلقائي للاستضافة من الرابط).
2. المستخدم **ينشئ بنفسه حسابًا مخصصًا** على الاستضافة (زر يفتح صفحة التسجيل) — مش حسابه الرئيسي.
3. يدخل **إيميل الحساب وباسورده** (+ مفتاح API اختياريًا) — تُحفظ مشفرة AES-GCM.
4. يختار **اسم الحساب**؛ المنصة تسجل دخول وتقرأ/تعتمد **الاسم الحقيقي** وترجعه له.
5. حالة **«⏳ بانتظار ظهور السيرفر على الحساب»**: المستخدم يضيف الحساب **Subuser** على سيرفره بصلاحيات (عرض كونسول + إرسال أوامر + بدء/إعادة تشغيل).
6. المنصة تفحص دوريًا: ظهر السيرفر؟ **← تحويل لشات الـ AI.** ما ظهرش؟ تبقى منتظرة وتعيد الفحص.
7. بديل للوحات المحمية من البوتات (مثل play.hosting خلف Cloudflare): **Client API Key** من حساب المنصة داخل اللوحة.

### ما تم إلغاؤه نهائيًا بقرار المالك
- **البلجن (Plugin):** محذوف من شجرة المشروع وخطوط النشر (محفوظ فقط في سجل Git والوسم v0.6.2).
- **مسار البوت داخل اللعبة (Minecraft Bot):** كان مرحلة وسطى؛ الكود موجود في ‎bot/‎ لكن دوره الآن هو **مشغّل اللوحات (Panel Runner)**، وليس مسار الربط الأساسي.
- **AFK Bot:** محظور نهائيًا كمنتج أو ميزة.

## 5) المفاتيح والأسرار — بالأسماء والأماكن فقط
> ⚠️ قاعدة ذهبية: **لا توجد أي قيمة سرية داخل هذا الملف أو المستودع أبدًا.** أي مفتاح ظهر في محادثة أو لقطة يُعتبر مكشوفًا ويُلغى ويُعاد إنشاؤه.

| السر | مكانه | وظيفته | من يعرفه |
|---|---|---|---|
| `SUPABASE_SERVICE_ROLE_KEY` | Cloudflare Worker Secret | وصول الخدمة لقاعدة البيانات | المالك فقط (لوحة Cloudflare) |
| `GEMINI_API_KEY` | Cloudflare Worker Secret | نموذج الـ AI (Gemini) | المالك فقط |
| `BOT_RUNNER_SECRET` | Cloudflare Worker Secret **و** GitHub Repo Secret (نفس القيمة بالضبط) | مصادقة مشغّل اللوحات مع الخدمة | المالك فقط |
| `BOT_CREDENTIALS_KEY` | Cloudflare Worker Secret | مفتاح AES-GCM لتشفير بيانات حسابات الاتصال (Base64 لـ 32 بايت) | المالك فقط — توليده: ‎[Convert]::ToBase64String((1..32 \| % { Get-Random -Maximum 256 }))‎ من PowerShell |
| مفتاح Supabase العام (anon/publishable) | داخل كود الواجهة (آمن علنًا بتصميمه) | وصول المتصفح المقيد بسياسات RLS | علني |
| Deploy Key للمستودع | GitHub Deploy Key + مفتاح SSH خارج المستودع | دفع الكود | لا يُطبع ولا يُلصق أبدًا |

**بيانات حساب المنصة على الاستضافة** (إيميل/باسورد/API Key التي يدخلها المستخدم): تُنقل عبر HTTPS، تُحفظ **مشفرة فقط** (‎bot_password_enc / bot_api_key_enc‎)، لا تظهر في الواجهة بعد الحفظ، وتُفك حصريًا داخل قناة المشغّل المحمية. ممنوع منعًا باتًا إدخال بيانات **الحساب الرئيسي** للمستخدم في الاستضافة.

## 6) قاعدة البيانات (Supabase) — الهجرات بالترتيب
| # | الملف | المحتوى باختصار |
|---|---|---|
| 001 | 001_craftmate_schema.sql | الجداول الأساسية: servers/users/reports/plans |
| 002 | 002_phase_b_commercial.sql | العنوان وطريقة الربط وقيودها الأصلية |
| 003 | 003_phase_c_platform.sql | أسس المنصة السحابية |
| 004 | 004_connector_api_v03.sql | رموز الربط ونبض المكوّن (أرشيف البلجن) |
| 005 | 005_connector_token_rls_explicit.sql | سياسات RLS صريحة للرموز |
| 006 | 006_server_tasks_control.sql | مهام التحكم والقوالب (‎server_tasks‎) |
| 007 | 007_bot_connection_account.sql | أعمدة حساب الاتصال: ‎bot_username/bot_verify_status/...‎ |
| 008 | 008_connection_account_credentials.sql | ‎bot_email + bot_password_enc‎ (مشفر فقط) |
| 009 | 009_fix_link_constraints.sql | إصلاح قيود ‎link_method/bot_verify_status‎ (سبب خطأ 400 الشهير) |
| 010 | 010_panel_api_key.sql | ‎bot_api_key_enc‎ لمفتاح API اللوحة |

**حالات الربط الرسمية لعمود ‎bot_verify_status‎:**
‎none ← waiting_server «بانتظار ظهور السيرفر على الحساب» ← check_requested ← checking ← verified / failed‎

## 7) واجهات الخدمة (Worker API)
- حسابات: ‎POST /v1/auth/register‎ (تسجيل سحابي مؤكد — بلا حسابات محلية)
- ربط اللوحات: ‎/v1/bot/identity‎ (حفظ بيانات الحساب والاسم) · ‎/v1/bot/status‎ · ‎/v1/panel/request-check‎
- قناة المشغّل (سر مشترك): ‎/v1/runner/jobs/next‎ · ‎/v1/runner/jobs/panel-result‎ · ‎/v1/runner/jobs/verify-result‎ · ‎/v1/runner/jobs/task-result‎
- تحكم ومهام: ‎/v1/control/set‎ · ‎/v1/control/status‎ · ‎/v1/tasks/create‎ · ‎/v1/tasks/list‎
- شات وتقارير: ‎/v1/ai/server-chat‎ · ‎/v1/ai/report-chat‎ · ‎/v1/diagnose‎
- (أرشيف البلجن): ‎/v1/claim /heartbeat /revoke /shutdown‎ ومهام البلجن — تاريخية فقط.

## 8) قوالب الأوامر المعتمدة (التنفيذ المسموح فقط)
قراءة: ‎GET_ENVIRONMENT_SNAPSHOT‎ · ‎GET_RECENT_LOGS‎
كتابة: ‎SET_TIME‎ (توقيت/طقوس جاهزة أو ticks 0–23999) · ‎SET_WEATHER‎ · ‎SET_DIFFICULTY‎ · ‎SET_DEFAULT_GAMEMODE‎ · ‎BROADCAST_MESSAGE‎ (1–200 حرف)
- الشات يكتب أمرًا طبيعيًا أو بصيغة كونسول فيُلتقط حتميًا لقالب معتمد، والنتيجة دائمًا من المنفذ الفعلي لا من النموذج.

## 9) أكواد الدعم (تظهر للمستخدم ودية وبلا مصطلحات داخلية)
- ذكاء: ‎CM-AI-001/002/010/011/012/401/404/422/429‎
- ربط لوحات: ‎CM-BOT-401‎ (دخول) · ‎404‎ · ‎422‎ (بيانات ناقصة) · ‎503‎ (أعمدة DB ناقصة) · ‎504‎ (مفتاح التشفير ناقص) · ‎CM-BOT-VFY‎ (فشل تحقق) · ‎CM-BOT-SLOW‎ (بطء)
- تحكم/مهام/سيرفر: ‎CM-CTRL-*‎ · ‎CM-TASK-*‎ · ‎CM-SERVER-401‎ (مع رقم HTTP/Kود القيد عند الحاجة للتشخيص)

## 10) الإصدارات والحالة الحية (2026-10-17)
- Worker: **0.13.0** حي ومفحوص عبر ‎/health‎ (`configured + gemini_configured: true`)
- الموقع: Build **2026.10.17-ux20**
- آخر ‏commit على ‏main: ‎7f1d76a‎
- البلجن المؤرشف: v0.6.2 → ‎37acb37‎ (خارج الشجرة الآن)
- اختبارات آلية: **29 فحص Mock ناجح** (‎worker/tests/mock-harness.mjs‎) + اختبار لوحة وهمية (‎bot/test-panel.mjs‎) لحالتي ظهور السيرفر وعدمه.

## 11) ملفات المشروع المهمة
| المسار | الدور |
|---|---|
| ‎index.html‎ | الموقع التعريفي العام |
| ‎app.html‎ | التطبيق الكامل: حسابات/لوحة/معالج ربط/شات |
| ‎worker/src/index.js‎ | خدمة Cloudflare Worker (كل الـ API) |
| ‎bot/runner.mjs‎ | مشغّل اللوحات (GitHub Actions): دخول/تحقق/تنفيذ |
| ‎bot/test-*.mjs‎ | اختبارات المشغّل المحلية |
| ‎worker/tests/mock-harness.mjs‎ | اختبارات الخدمة (يجب تشغيلها قبل أي دفع) |
| ‎supabase/migrations/‎ | هجرات قاعدة البيانات 001–010 بالترتيب |
| ‎.github/workflows/deploy-worker.yml‎ | نشر الخدمة + بوابة تحقق من النسخة |
| ‎.github/workflows/deploy-pages.yml‎ | نشر الموقع |
| ‎.github/workflows/bot-runner.yml‎ | تشغيل مشغّل اللوحات يدويًا (حتى ساعتين) |
| ‎docs/‎ | الخطط وملاحظات الإصدار والبحث (انظر §13) |

## 12) دليل التشغيل (Runbook)
**إعداد مرة واحدة (المالك):**
1. Supabase → SQL Editor: تنفيذ الهجرات بالترتيب حتى 010.
2. Cloudflare → Worker → Settings → Variables and Secrets: الأسرار الأربعة في §5.
3. GitHub → Settings → Secrets → Actions: ‎BOT_RUNNER_SECRET‎ بنفس قيمة Cloudflare.

**قبل أي تجربة ربط:** GitHub → Actions → **Bot Runner (platform connection)** → Run workflow (يبقى صاحيًا حتى ساعتين ويعالج مهام التحقق).

**لو التحقق علق:** اطلب التحقق من الموقع من جديد (يعيد جدولة المهمة) ← تأكد أن المشغّل شغال ← رسالة ‎PANEL_BOT_CHALLENGE‎ تعني أن اللوحة منعت الدخول الآلي: استخدم مفتاح الـ API.

**إلغاء وصول المنصة فوريًا:** حذف الـ Subuser من لوحة الاستضافة، أو حذف حساب المنصة نفسه.

## 13) قرارات موثقة (سجل مختصر — 2026-10-17)
1. المنتج = ربط موثق + شات AI (لا لوحة بديلة).
2. التنفيذ فوري بقوالب معتمدة فقط.
3. إلغاء البلجن من المنتج ثم **نهائيًا من المشروع**.
4. اعتماد فكرة المالك النهائية: **Subuser** — حساب منصة داخل الاستضافة بصلاحيات من المالك، والتحقق بظهور السيرفر.
5. المستخدم ينشئ حساب المنصة بنفسه (بيانات مخصصة، لا رئيسية) — حلًا لحمايات Cloudflare/CAPTCHA.
6. خطط مفصلة: ‎docs/SUBUSER_PANEL_LINK_PLAN_AR.md‎ · ‎docs/PHASE0_PLAY_HOSTING_SPIKE_AR.md‎ · ‎docs/CONNECTION_ACCOUNT_FLOW_PLAN_AR.md‎

## 14) حقائق بحث مؤكدة
- FalixNodes: Sub-Users موثقة رسميًا بصلاحيات Console/Send Commands وأدوار جاهزة.
- play.hosting: ‏"Invite subusers" معلنة؛ اللوحة خلف **Cloudflare Bot Challenge** (فحص عملي: حتى بصمة متصفح من بيئة سحابية تُرفض) — لذلك مفتاح الـ API هو المسار.
- لوحات Pterodactyl: صلاحيات ‎websocket.connect/control.console‎ وClient API بمفاتيح ‎ptlc_‎ وأمر ‎POST /api/client/servers/{id}/command‎.
- سيرفرات Online Mode تحتاج حساب Minecraft حقيقيًا — حد معلن لا يُخفى.

## 15) الحالة الراهنة والخطوات التالية
- ✅ مبني ومنشور: معالج v3 المبسط (ux20) — رابط فقط ← إيميل/باسورد (+ API Key) ← اسم ← انتظار الظهور ← تحقق.
- ⏳ **الاختبار الحاسم الجاري:** أول ربط حقيقي كامل على play.hosting بمفتاح API (إنشاء المفتاح من حساب المنصة ← إدخاله في المعالج ← Subuser ← فتح الشات).
- بعد نجاحه: **Phase 2** — تنفيذ القوالب السبع عبر كونسول اللوحة ونتائج موثقة في الشات.
- بعده: FalixNodes ثم Pterodactyl العام، ثم MCServerHost/Minefort حسب الفحص.
- بنود قبول مفتوحة أقدم: سؤال التقرير الحقيقي للمالك، ظهور التقرير في اللوحة بعد تحديث قسري، دورة تسجيل/خروج/دخول جديدة كاملة.
