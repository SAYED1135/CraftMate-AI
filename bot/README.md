# CraftMate Platform Connection Runner (بوت اتصال المنصة)

مشغّل اتصال المنصة: بياخد مهام من خدمة CraftMate (طلبات تحقق + مهام سيرفر معتمدة)
وبيدخل سيرفرات Minecraft بحساب اتصال المنصة (Mineflayer, Offline Mode) وينفّذ
قوالب الأوامر المعتمدة فقط ويرجع نتائج موثقة.

- التشغيل: GitHub Actions → workflow `bot-runner.yml` (manual, يدوي)
- السر المشترك مع الخدمة: `BOT_RUNNER_SECRET` (GitHub Secret) =
  سر الـ Worker `BOT_RUNNER_SECRET` (Cloudflare) — نفس القيمة بالضبط.
- تشغيل محلي للاختبار: `CM_DRY_RUN=1 CM_TEST_HOST=127.0.0.1:25599 node test-exec.mjs`
- مفيش تنفيذ نصوص حرة: بس قوالب `TEMPLATES` في `runner.mjs`.
- حدود: Offline Mode يعني هوية الاسم فقط — التفاصيل في docs/AI_REAL_ASSISTANT_REPLAN_AR.md.
