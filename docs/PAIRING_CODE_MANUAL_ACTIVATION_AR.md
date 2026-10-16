# تفعيل كود الربط يدوياً عند ظهور `INVALID_CODE`

هذه طريقة طوارئ لاختبار بلجن CraftMate عندما يعرض الموقع كوداً لكنه لا يكون محفوظاً بعد في Supabase. الاستخدام الطبيعي بعد إصلاح لوحة التحكم لا يحتاج هذه الخطوات.

## 1. اعرض سيرفراتك السحابية

نفّذ في Supabase SQL Editor:

```sql
select id, name, status, created_at
from public.servers
order by created_at desc
limit 5;
```

انسخ `id` الخاص بسيرفر ماينكرافت الذي تريد ربطه. إذا لم يظهر أي سيرفر، فالسيرفر الموجود في اللوحة محلي فقط ويجب إنشاؤه/مزامنته سحابياً أولاً.

## 2. ولّد كوداً جديداً من اللوحة

انسخ الكود كما هو تماماً، ثم نفّذ الاستعلام التالي بعد استبدال القيمتين:

```sql
insert into public.connector_tokens (
  server_id,
  pairing_code,
  pairing_expires_at,
  is_revoked,
  token_hash
)
values (
  'PUT-SERVER-UUID-HERE',
  'CM-XXXX-XXXX',
  now() + interval '15 minutes',
  false,
  null
)
on conflict (pairing_code) do update
set
  server_id = excluded.server_id,
  pairing_expires_at = excluded.pairing_expires_at,
  is_revoked = false,
  token_hash = null;
```

## 3. تحقق قبل تشغيل أمر ماينكرافت

```sql
select server_id, pairing_code, pairing_expires_at, is_revoked, token_hash
from public.connector_tokens
where pairing_code = 'CM-XXXX-XXXX';
```

يجب أن ترى صفاً واحداً، و`is_revoked = false`، و`token_hash` فارغاً، ووقت انتهاء في المستقبل.

## 4. اربط فوراً

```text
/craftmate link CM-XXXX-XXXX
```

لا تشارك كود الربط بعد استخدامه. إذا مرت 15 دقيقة، ولّد كوداً جديداً وكرر الإدراج بنفس الطريقة.
