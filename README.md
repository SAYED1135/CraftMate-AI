# ⚡ CraftMate AI

**مساعد ذكي لتشخيص وإدارة سيرفرات Minecraft (Paper / Purpur) مبني على السياق والقدرات — بتكلفة تشغيل مجانية ($0 Stack).**

[![GitHub Pages](https://img.shields.io/badge/GitHub%20Pages-Live%20Dashboard-10b981?style=for-the-badge&logo=github)](https://esaye2312-art.github.io/CraftMate-AI/)
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

## 🏗️ هيكل المستودع (Repository Structure)

```text
CraftMate-AI/
├── index.html                                  # لوحة التحكم التفاعلية (Doctor MVP + Host Wizard + Policy Engine)
├── data/
│   └── hosts-profiles.json                     # قواعد وقدرات الاستضافات الأربع المدعومة وتصنيف FMHL
├── docs/
│   └── CraftMate_AI_PRD_v0.2.md                # وثيقة متطلبات المنتج المحدثة (الإصدار 0.2)
├── supabase/
│   ├── config.toml                             # إعدادات مشروع Supabase (fucsztovkkilyboinhrv)
│   └── migrations/
│       └── 001_craftmate_schema.sql            # مخطط قاعدة البيانات + سياسات الأمان (RLS)
└── .github/
    └── workflows/
        └── deploy-pages.yml                    # النشر التلقائي المجاني على GitHub Pages
```

---

## 🔗 ربط قاعدة بيانات Supabase (`fucsztovkkilyboinhrv`)

1. افتح لوحة تحكم مشروعك في Supabase: `https://supabase.com/dashboard/project/fucsztovkkilyboinhrv/sql/new`
2. انسخ محتوى ملف [`supabase/migrations/001_craftmate_schema.sql`](./supabase/migrations/001_craftmate_schema.sql) وشغّله (Run) لإنشاء الجداول وتفعيل سياسات العزل (RLS).
3. من قسم **Project Settings -> API**، انسخ مفتاح `anon public` وضعه داخل إعدادات لوحة التحكم لتفعيل الحفظ السحابي الفوري.
