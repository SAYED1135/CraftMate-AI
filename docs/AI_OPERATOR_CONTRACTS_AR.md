# CraftMate — عقود شات السيرفر والمهام والأدوات

**الإصدار:** 0.1 — عقد تصميم قبل تنفيذ P1/P2  
**التاريخ:** 2026-10-17  
**النطاق:** شات سيرفر موثق، أدوات Gemini، وقناة مهام البلجن. هذا العقد لا يفعّل أي تنفيذ بحد ذاته.

## 1. مبادئ ملزمة

- الشات يعمل فقط بعد جلسة مستخدم سحابية، وكل سياق/مهمة مرتبطة بمالك وسيرفر محددين.
- Gemini لا يتلقى توكن البلجن، مفتاح Gemini، مفتاح خدمة، باسورد، أو سجلًا خامًا كاملًا.
- نصوص السجل والمحادثة بيانات غير موثوقة؛ لا تُعامل كتعليمات نظام.
- القراءة لا تعني التنفيذ. أي أداة كتابة تتحول إلى اقتراح مهمة يحتاج موافقة بشرية مرتبطة ببصمة الخطة.
- البلجن ينفذ فقط نوع مهمة معروفًا ومخططًا مُتحققًا منه محليًا، ويرجع نتيجة منظمة؛ لا توجد أداة Console/Shell حرة.

## 2. سياق الشات المسموح

```json
{
  "server": {
    "id": "uuid",
    "name": "string",
    "status": "ONLINE|STALE|OFFLINE|HIBERNATING|UNLINKED",
    "status_source": "heartbeat_age|explicit_shutdown|none",
    "host_provider": "string",
    "server_software": "string",
    "minecraft_version": "string",
    "connector_version": "string",
    "last_heartbeat_at": "iso-8601|null",
    "players": 0,
    "max_players": 0,
    "ram_used_mb": 0,
    "ram_max_mb": 0,
    "java_version": "string",
    "plugins": ["string"]
  },
  "latest_reports": [
    {
      "id": "uuid",
      "created_at": "iso-8601",
      "summary": "string",
      "findings": [{"text": "string", "severity": "low|med|high"}],
      "scrubbed_excerpt_preview": "string<=2500"
    }
  ],
  "recent_task_results": [
    {"task_id": "uuid", "type": "string", "status": "string", "finished_at": "iso-8601"}
  ]
}
```

كل حقل يجب أن يحمل مصدره وحداثته ضمنيًا من وقت النبضة/التقرير؛ لا تتحول لقطة قديمة إلى حقيقة حية.

## 3. سجل الأدوات الأول

| الأداة | النوع | مدخلات مسموحة | موافقة |
|---|---|---|---|
| `server.get_status` | قراءة | لا شيء | لا |
| `server.get_environment` | قراءة | لا شيء | لا |
| `server.get_recent_logs` | قراءة/تشخيص | عدد أسطر محدود | لا |
| `server.get_latest_reports` | قراءة | حد تقارير | لا |
| `config.read_allowlisted` | قراءة | ملف ومفتاح من قائمة | لا |
| `connector.refresh_environment` | مهمة آمنة | لا شيء | لا، غير مدمرة |
| `connector.request_diagnosis` | مهمة آمنة | سبب اختياري | لا، غير مدمرة |
| `config.set_server_properties` | كتابة | مفتاح مسموح وقيمة ضمن نطاق | نعم |
| `backup.restore_config` | كتابة مؤثرة | مرجع نسخة مملوكة | نعم قوية |
| `plugin.install_trusted` | لاحقًا مؤثرة | مصدر/منتج/إصدار متوافق | نعم قوية |

## 4. عقد المهمة

```json
{
  "id": "uuid",
  "server_id": "uuid",
  "owner_id": "uuid",
  "conversation_id": "uuid|null",
  "type": "connector.request_diagnosis",
  "payload": {},
  "risk": "read|safe|write|high",
  "status": "PROPOSED",
  "proposal_hash": "sha256",
  "idempotency_key": "string",
  "backup_ref": null,
  "restart_required": false,
  "expires_at": "iso-8601",
  "created_at": "iso-8601",
  "claimed_at": null,
  "finished_at": null,
  "result": null,
  "error_code": null
}
```

انتقالات الحالة المسموحة:

```text
PROPOSED → AWAITING_APPROVAL → QUEUED → CLAIMED → RUNNING → VERIFYING
VERIFYING → SUCCEEDED | FAILED | ROLLED_BACK
QUEUED → CANCELLED | EXPIRED
```

- `proposal_hash` يحسب فوق النوع والحمولة والسيرفر. أي تعديل بعد الموافقة يغيّر البصمة ويعيد الحالة إلى `AWAITING_APPROVAL`.
- البلجن لا يستلم إلا `QUEUED` غير منتهية، ويستطيع تنفيذ مفتاح عدم التكرار مرة واحدة.
- نتيجة النجاح لا تُقبل بلا تحقق مناسب لنوع المهمة؛ تغيير إعداد يتطلب قراءة لاحقة.

## 5. عقد نتيجة البلجن

```json
{
  "task_id": "uuid",
  "status": "SUCCEEDED|FAILED|ROLLED_BACK",
  "summary": "safe human summary",
  "evidence": {
    "before": {},
    "after": {},
    "backup_ref": "string|null",
    "restart_required": false
  },
  "error_code": "CM-TASK-000|null",
  "finished_at": "iso-8601"
}
```

لا يرسل البلجن أسرار ملفات أو محتوى إعداد كاملًا في النتيجة؛ يرسل فقط القيم اللازمة للتحقق بعد تنقيتها.

## 6. واجهات برمجية مقترحة

### اللوحة/الشات

- `POST /v1/ai/server-chat` — سؤال قراءة على سيرفر مملوك، بلا مهام كتابة في P1.
- `POST /v1/tasks/propose` — إنشاء اقتراح من أداة كتابة.
- `POST /v1/tasks/:id/approve` — موافقة على بصمة اقتراح محددة.
- `POST /v1/tasks/:id/cancel` — إلغاء قبل الاستلام.
- `GET /v1/tasks?server_id=...` — سجل مهام المالك.

### البلجن

- `POST /v1/plugin/tasks/poll` — استلام مهمة واحدة أو لا شيء.
- `POST /v1/plugin/tasks/:id/result` — إرسال النتيجة والتحقق.

كل مسارات البلجن تستخدم توكن السيرفر الحالي و`server_id`، ولا تعتمد على جلسة متصفح.

## 7. أكواد دعم الذكاء الحالية

| الرمز | المعنى الآمن للمستخدم |
|---|---|
| `CM-AI-001` | خدمة الذكاء غير مفعلة |
| `CM-AI-002` | خدمة الذكاء غير متاحة مؤقتًا لسبب عام/شبكي |
| `CM-AI-010` | إعداد اعتماد خدمة الذكاء يحتاج مراجعة CraftMate |
| `CM-AI-011` | نموذج الذكاء غير متاح حاليًا |
| `CM-AI-012` | حد استخدام خدمة الذكاء مؤقتًا |
| `CM-AI-401` | جلسة الدخول غير صالحة |
| `CM-AI-404` | التقرير/السيرفر غير موجود في حساب المستخدم |
| `CM-AI-422` | سؤال غير صالح |
| `CM-AI-429` | حد استخدام CraftMate للمستخدم |

لا يظهر اسم مزود داخلي أو رسالة خطأ خام في تجربة المستخدم العادية.

## 8. بوابات قبل الكود التنفيذي

- P1 لا يبدأ إلا بعد مراجعة هذا العقد واختبار ملكية/تنقية لمسار التقرير الحالي.
- P2 لا يبدأ إلا بعد نجاح شات القراءة على سيرفر المالك.
- P3 لا يبدأ إلا بعد نجاح مهمة غير مدمرة على Paper قابل للإتلاف مع منع تكرار وانتهاء.
- أي أداة جديدة تحتاج: مخطط مدخلات، تصنيف خطر، اختبار تحقق، خطة تراجع، ورمز خطأ قبل إضافتها للسجل.
