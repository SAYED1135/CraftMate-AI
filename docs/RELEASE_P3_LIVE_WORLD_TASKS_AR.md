# CraftMate — P3 Slice 1: Live World Tasks — Build `2026.10.17-ux7`

**التاريخ:** 2026-10-17

## الهدف
أول مهام كتابة حقيقية عبر البلجن بعد نجاح أساس التحكم: تعديلات حية داخل عالم Minecraft قابلة للعكس والتحقق، بدون أوامر كونسول حرة وبدون Shell.

## ما تم
- Connector `0.6.0`:
  - `SET_DIFFICULTY` (PEACEFUL/EASY/NORMAL/HARD)
  - `SET_TIME` (DAY/NOON/NIGHT/MIDNIGHT أو ticks من 0 إلى 23999)
  - `SET_WEATHER` (CLEAR/RAIN/THUNDER)
  - `SET_DEFAULT_GAMEMODE` (SURVIVAL/CREATIVE/ADVENTURE/SPECTATOR)
  - `BROADCAST_MESSAGE` (1–200 حرف، أكواد الألوان ممنوعة)
  - كل مهمة تسجل القيمة السابقة، تطبق، ثم تتحقق بإعادة القراءة وترجع `verified` و`reversible`.
  - مفتاح إيقاف محلي: `control.allow-live-write-tasks` (افتراضي true لأن المهام لا تصل إلا أثناء جلسة تحكم مفعّلة من الموقع).
  - تعديلات الملفات ما زالت منفصلة تحت `security.read-only-mode` — الشريحة الجاية.
- Worker `0.7.0`: سجل مهام مركزي `PLUGIN_TASK_DEFINITIONS` بقيم risk حقيقية (`read`/`write`) وتحقق كامل من كل payload قبل الإنشاء (`CM-TASK-422` عند أي قيمة غير صالحة).
- الموقع `ux7`: أزرار إجراءات سريعة داخل الشات (نهار/ليل/طقس صافٍ/مطر/سلمي/عادي/وضع افتراضي: بقاء) + إصلاح عرض نتيجتي السجلات (`scrubbed_log`) والبيئة (حقول الـ snapshot الحقيقية).

## الاختبارات
- Worker JS syntax OK + ‏10 mock tests ناجحة (منع بدون تحكم، إنشاء read/write، رفض preset/difficulty غير صالحة، رفض بث طويل، تجريد أكواد الألوان).
- بلجن: بناء عبر GitHub Actions (JDK 21).
- نشر وWorker حي يُتحقق منه عبر `/health` = `0.7.0`.

## Acceptance المتبقي (مالك)
1. تشغيل migration `006` في Supabase إن لم تُشغّل بعد.
2. تثبيت Connector `0.6.0` وإعادة تشغيل السيرفر.
3. تفعيل التحكم من الشات وتجربة زر «🌅 نهار» و«🕊️ سلمي» ومقارنة النتيجة داخل اللعبة.

## الخطوة التالية
P3 Slice 2: تعديل ملفات الإعدادات المسموحة (allowlist) مع backup + verify + rollback عبر قناة المهام نفسها، ثم قوالب كونسول جاهزة مُتحقق منها.
