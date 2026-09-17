import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const read = async path => readFile(new URL('../'+path, import.meta.url),'utf8');
test('Android limits media access to picker and call permissions', async () => {
  const manifest=await read('app/src/main/AndroidManifest.xml');
  assert.ok(manifest.includes('android.permission.INTERNET'));
  assert.doesNotMatch(manifest,/android.permission.(READ_MEDIA|READ_EXTERNAL_STORAGE)/);
  assert.ok(manifest.includes('android.permission.CAMERA'));
  assert.ok(manifest.includes('android.permission.RECORD_AUDIO'));
  assert.ok(manifest.includes('android:allowBackup="false"'));
  assert.ok(manifest.includes('android:usesCleartextTraffic="false"'));
});
test('secret storage uses Android Keystore authenticated encryption', async () => {
  const code=await read('app/src/main/java/com/webgenius/spark/Platform.kt');
  assert.ok(code.includes('AndroidKeyStore')); assert.ok(code.includes('AES/GCM/NoPadding'));
});
test('private media does not use public URLs or persistent caches', async () => {
  const api=await read('core/src/main/kotlin/com/webgenius/spark/core/SparkApi.kt');
  const ui=await read('app/src/main/java/com/webgenius/spark/ui/SparkApp.kt');
  assert.ok(api.includes('/storage/v1/object/authenticated/'));
  assert.doesNotMatch(api,/object\/public/);
  assert.ok(ui.includes('.diskCachePolicy(CachePolicy.DISABLED)'));
  assert.ok(ui.includes('.memoryCachePolicy(CachePolicy.DISABLED)'));
});
test('account deletion checks identity and password before privileged cleanup', async () => {
  const code=await read('supabase/functions/delete-account/index.ts');
  assert.ok(code.indexOf('/auth/v1/user') < code.indexOf('deleting_at:'));
  assert.ok(code.indexOf('verified.user?.id !== user.id') < code.indexOf('deleting_at:'));
  assert.ok(code.indexOf('deleting_at:') < code.indexOf('auth/v1/admin/users/'));
  assert.doesNotMatch(code,/console\.(log|error)/);
});
test('password recovery uses PKCE, not incoming bearer-token fragments', async () => {
  const api=await read('core/src/main/kotlin/com/webgenius/spark/core/SparkApi.kt');
  const activity=await read('app/src/main/java/com/webgenius/spark/MainActivity.kt');
  assert.ok(api.includes('Pkce.challenge(verifier)')); assert.ok(api.includes('"grant_type" to "pkce"'));
  assert.doesNotMatch(activity,/getQueryParameter\("access_token"\)/);
});
