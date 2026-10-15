# ⚡ CraftMate AI

**مساعد ذكي لتشخيص وإدارة سيرفرات Minecraft (Paper / Purpur) مبني على السياق والقدرات — بتكلفة تشغيل مجانية ($0 Stack).**

[![GitHub Pages](https://img.shields.io/badge/GitHub%20Pages-Live%20Dashboard-10b981?style=for-the-badge&logo=github)](https://esaye2312-art.github.io/CraftMate-AI/)
[![Plugin Build](https://img.shields.io/github/actions/workflow/status/esaye2312-art/CraftMate-AI/build-plugin.yml?style=for-the-badge&label=CraftMate%20Connector%20JAR)](https://github.com/esaye2312-art/CraftMate-AI/actions/workflows/build-plugin.yml)
[![PRD Version](https://img.shields.io/badge/PRD-v0.2-3b82f6?style=for-the-badge)](./docs/CraftMate_AI_PRD_v0.2.md)
[![Supabase](https://img.shields.io/badge/Supabase-fucsztovkkilyboinhrv-3ecf8e?style=for-the-badge&logo=supabase)](https://fucsztovkkilyboinhrv.supabase.co)

---

## 🎯 الاستضافات المدعومة رسمياً في النسخة الأولية (MVP)

بدلاً من افتراض أن جميع الاستضافات متطابقة، يعتمد **CraftMate AI** على ملفات تعريف قدرات وسياسات مخصصة (`data/hosts-profiles.json`) لأربع استضافات أساسية (بالاستناد إلى مرجعية [Free-Minecraft-Hosts](https://github.com/Myuui/Free-Minecraft-Hosts)):

| الاستضافة | الرام المجانية | تثبيت بلجن CraftMate | دعم Bedrock (Geyser) | القواعد الخاصة في Policy Engine |
| :--- | :--- | :--- | :--- | :--- |
| **[play.hosting](https://play.hosting)** | `3 GB` | رفع `.jar` مباشر عبر File Manager | منفذ إضافي مجاني (`19132 UDP`) أو Crossplay مدمج | دعم كامل (Tier 1) |
| **[falixnodes.net](https://falixnodes.net)** | `2.5 - 4.5 GB` | عبر متجر اللوحة (**Modrinth / Hangar**) للخطة المجانية | حتى 5 منافذ إضافية من `Network -> Ports` | منع افتراض رفع `.jar` خارجي في المجاني + دعم وضع السبات |
| **[mcserverhost.com](https://mcserverhost.com)** | `4 GB` | رفع `.jar` عبر File Manager أو SFTP | رفع Geyser + Floodgate وضبط `config.yml` | دعم كامل + إرشادات SFTP |
| **[minefort.com](https://minefort.com)** | `1 GB` | رفع `.jar` عبر `Files -> plugins` أو `ftp.minefort.com` | يمر عبر البروكسي المركزي `play.minefort.com` | **حظر تعطيل `bungeecord: true`** في `spigot.yml` + تحسين رام 1GB |

---

## 🔌 بلجن `CraftMate Connector` (Paper / Purpur 1.20.4 – 1.21.x)

يقع الكود المصدري الكامل للبلجن في مجلد [`plugin/`](./plugin) ويُبنى تلقائياً عبر **GitHub Actions** إلى ملف `CraftMate-Connector-0.2.0.jar`.

### الأوامر الإدارية داخل السيرفر (`craftmate.admin`)
* `/craftmate status` — عرض حالة الاتصال، ملف الاستضافة المكتشف (`play_hosting` / `falixnodes` / `mcserverhost` / `minefort`)، استهلاك الرام، وحالة الحماية.
* `/craftmate link <CM-XXXX-XXXX>` — ربط السيرفر بلوحة التحكم باستخدام رمز ربط مؤقت أحادي الاستخدام.
* `/craftmate sync` — إرسال نبضة اتصال فورية (`heartbeat`) وتحديث مصفوفة القدرات.
* `/craftmate diagnose` — تنقيح ملف `logs/latest.log` محلياً (إخفاء عناوين IP اللاعبين وكلمات مرور RCON/MySQL) وضغط الأخطاء المتكررة ورفع لقطة التشخيص.
* `/craftmate disconnect` — إلغاء ربط السيرفر وحذف رمز الاتصال محلياً.

---

## 🏗️ هيكل المستودع (Repository Structure)

```text
CraftMate-AI/
├── index.html                                  # لوحة التحكم التفاعلية (Doctor MVP + Host Wizard + Policy Engine)
├── data/
│   └── hosts-profiles.json                     # قواعد وقدرات الاستضافات الأربع المدعومة وتصنيف FMHL
├── docs/
│   └── CraftMate_AI_PRD_v0.2.md                # وثيقة متطلبات المنتج المحدثة (الإصدار 0.2)
├── plugin/                                     # الكود المصدري لبلجن CraftMate Connector (Paper/Purpur Java)
│   ├── build.gradle.kts                        # إعدادات بناء Gradle (Paper API + Gson)
│   ├── settings.gradle.kts
│   └── src/main/
│       ├── resources/
│       │   ├── plugin.yml                      # تعريف البلجن والأوامر والصلاحيات
│       │   └── config.yml                      # إعدادات الاستضافة والـ Polling والتنقيح الأمني
│       └── java/ai/craftmate/connector/
│           ├── CraftMateConnectorPlugin.java   # نقطة الدخول الرئيسية وإشعار السبات (HIBERNATING)
│           ├── EnvironmentDetector.java        # اكتشاف نوع الاستضافة والرام والبلجنز وGeyser/Floodgate
│           ├── LogScrubber.java                # تنقيح عناوين IP والأسرار وضغط الأخطاء المتكررة محلياً
│           ├── AllowlistTaskExecutor.java      # محرك تنفيذ مقيد بقائمة سماح + حماية بروكسي Minefort
│           ├── ConnectorClient.java            # عميل اتصال HTTPS الصادر غير المتزامن (Async Polling)
│           └── CraftMateCommand.java           # معالج أوامر /craftmate داخل السيرفر
├── supabase/
│   ├── config.toml                             # إعدادات مشروع Supabase (fucsztovkkilyboinhrv)
│   └── migrations/
│       └── 001_craftmate_schema.sql            # مخطط قاعدة البيانات + سياسات الأمان (RLS)
└── .github/
    └── workflows/
        ├── deploy-pages.yml                    # النشر التلقائي المجاني للوحة التحكم على GitHub Pages
        └── build-plugin.yml                    # بناء ملف CraftMate-Connector.jar تلقائياً عبر JDK 21
```

---

## 🔗 ربط قاعدة بيانات Supabase (`fucsztovkkilyboinhrv`)

1. افتح لوحة تحكم مشروعك في Supabase: `https://supabase.com/dashboard/project/fucsztovkkilyboinhrv/sql/new`
2. انسخ محتوى ملف [`supabase/migrations/001_craftmate_schema.sql`](./supabase/migrations/001_craftmate_schema.sql) وشغّله (Run) لإنشاء الجداول وتفعيل سياسات العزل (RLS).
3. من قسم **Project Settings -> API**، انسخ مفتاح `anon public` وضعه داخل إعدادات لوحة التحكم لتفعيل الحفظ السحابي الفوري.

## 🚀 المرحلة B — الحسابات ولوحة التحكم (`app.html`)

تطبيق الحسابات والرحلة الحقيقية متاح الآن في [`app.html`](./app.html) ومرتبط من الموقع الرئيسي:

- **تسجيل ودخول فوريين** باسم مستخدم وكلمة سر مشفرة (SHA-256 + Salt) وجلسة 7 أيام، مع **حساب تجريبي بنقرة واحدة**.
- **وضع Supabase سحابي جاهز**: الدخول عبر Supabase Auth يُفعَّل بلصق مفتاح `anon public` العام (آمن مع RLS) من الإعدادات، أو بوضعه مرة واحدة في `SUPABASE_ANON_KEY_DEFAULT` داخل `app.html` للنشر العام — المشروع: `fucsztovkkilyboinhrv`.
- **Onboarding كامل**: اختيار الاستضافة (Play Hosting / FalixNodes / MCServerHost / Minefort) → بيانات السيرفر → طريقة الربط (بلجن / رفع سجل / لاحقاً).
- **أكواد ربط حقيقية** `CM-XXXX-XXXX` صالحة 15 دقيقة ولمرة واحدة، مع أمر `/craftmate link` ومعاينة اتصال تجريبية داخل اللوحة.
- **لوحة سيرفرات** بحالة حية/سبات/غير مرتبط، ومزامنة نبضات، وفصل وحذف — و**حدود الخطة المجانية مفروضة** (سيرفر واحد، 10 تشخيصات/شهر، احتفاظ 7 أيام).
- **تشخيصات محفوظة** في حساب كل مستخدم معزولة عنه غيره، وتصدير بيانات وحذف حساب كامل.

### تفعيل قاعدة البيانات السحابية
1. افتح Supabase SQL Editor لمشروعك `fucsztovkkilyboinhrv`.
2. شغّل [`supabase/migrations/001_craftmate_schema.sql`](./supabase/migrations/001_craftmate_schema.sql) ثم [`supabase/migrations/002_phase_b_commercial.sql`](./supabase/migrations/002_phase_b_commercial.sql).
3. من **Project Settings → API** انسخ `anon public` والصقه في إعدادات `app.html` للاختبار، أو في ثابت `SUPABASE_ANON_KEY_DEFAULT` للنشر العام.

## 🛡️ المرحلة C — عمق القيمة والإدارة

اكتملت داخل [`app.html`](./app.html):

- **مركز تنبيهات**: ربط السيرفرات، السبات النظيف، التشخيصات عالية الخطورة وحدود الخطة — مقروء/غير مقروء، مع إعدادات تفعيل وDiscord Webhook اختياري.
- **إعدادات سيرفر آمنة قابلة للتراجع**: تعديل مسافات الرؤية وأقصى اللاعبين والصعوبة مع نسخة `.bak` وسجل تغييرات وزر تراجع لكل تطبيق.
- **مجتمع ومنشورات**: منشورات إدارة ومستخدمين وإعجاب وتعليقات وحذف، خلف مفتاح ميزة من الإدارة.
- **إشعارات مطوّر فورية** تظهر أعلى اللوحة وتحترم إغلاق كل مستخدم.
- **دعم متكامل**: بحث فوري في قاعدة معرفة، وتذاكر دعم بحالات (open / in-progress / closed).
- **لوحة إدارة** (للحساب التجريبي وحسابات الإدارة): إحصائيات، حدود واستحقاقات تُطبق فوراً، مفاتيح ميزات، إدارة مستخدمين وخططهم، إشعارات، قوائم انتظار وتذاكر — وترحيلة سحابية [`003_phase_c_platform.sql`](./supabase/migrations/003_phase_c_platform.sql).

> **تنبيه مفاتيح Supabase:** لا تضع مفتاح `service_role` في الموقع أو GitHub أبداً — ده مفتاح سري بيتجاوز RLS. المفتاح الوحيد المسموح في الواجهة هو `anon public` المحمي بسياسات RLS.
