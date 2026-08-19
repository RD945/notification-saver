# npoint encryption

Notification Saver writes **sealed ciphertext** to [npoint.io](https://www.npoint.io/). Telegram messages are **not** encrypted (the bot chat is already private). This file is the full wire format so another app can decrypt the bin.

Implementation: [`SealedBoxCrypto.kt`](app/src/main/java/com/notificationsaver/app/data/crypto/SealedBoxCrypto.kt) via [lazysodium-android](https://github.com/terl/lazysodium-android) (libsodium). Spec: [libsodium sealed boxes](https://doc.libsodium.org/public-key_cryptography/sealed_boxes).

---

## What you copy in the app

Home → **npoint bin**.

| Field in the app | What it is | Who uses it |
| --- | --- | --- |
| **API URL** | `https://api.npoint.io/{id}` | Phone posts here. Anyone can GET the ciphertext. |
| **Bearer token** | npoint *account* API key | Leave **blank**. Only for owned/premium bins. **Not** an encryption key. |
| **Encode key** | Curve25519 **public** key (32 bytes) | This app seals with it. Also stored in the JSON as `encodeKey`. Safe to share with the bin. |
| **Decode key** | Curve25519 **secret** key (32 bytes) | Other apps open the boxes. **Never** uploaded. Treat like a password. |

Fields start empty. Tap **Generate keys**, or paste a pair you already have, then **Save**. After keys are saved they lock; **Edit keys** unlocks them to paste a different pair. **Generate keys** again (with confirmation if a pair already exists) fills a new pair — old items will not open. **Clear bin** POSTs `{ "items": [] }` and wipes the local buffer.

---

## Algorithm

| | |
| --- | --- |
| Primitive | libsodium `crypto_box_seal` / `crypto_box_seal_open` |
| KEM | X25519 (Curve25519) |
| Symmetric | XSalsa20-Poly1305 |
| Key sizes | public 32 bytes, secret 32 bytes |
| Overhead | `crypto_box_SEALBYTES` = 48 (32-byte ephemeral public key + 16-byte MAC) |
| Encoding | UTF-8 plaintext; keys and `box` are **URL-safe Base64 with padding** (`Base64.URL_SAFE \| NO_WRAP` on Android) |

Sealed box is anonymous: each item uses a fresh ephemeral sender keypair. Decrypt needs only the **decode key**. The encode key is included in the document so a consumer can check it matches `scalarmult_base(decode_key)`.

This is standard NaCl/libsodium. It is not readable without the decode key. It is **not** uncrackable if that key leaks.

---

## Document on npoint (GET)

`GET https://api.npoint.io/{id}` returns one JSON object. The app **POSTs the whole document** each time (last 50 items). It does not GET-then-patch, because npoint caches GET.

```json
{
  "v": 1,
  "alg": "crypto_box_seal",
  "encodeKey": "pLRuiSTf33pb2TQf5rVoivnDFax-s5pwCcPXOFT9EA0=",
  "items": [
    {
      "ts": 1787081548733,
      "box": "RtR_hxJVtnEC_neNeYGoR_7Lw-8c1KY-cpiSv94vmCFtvWSWppzCW1w5eE26PaX655lJjnvzb2XeocPNujyQz2HM-nnhSSVpmBVHX2tcQdb0rQ8Lt6P-AcAWWUt33lzk0VESZ4r8CcsQHZ3WYW_rpm-48UBOg0c_J2Kc6OGsPudZ8MTBdQTx6ZrCMH3LiDWHXYvuqcCjgOdE6T1gD7mUs0mMjK3wyPta3YRuHfc-YBZ_7cXerT31G8k3zUlEltvRSLdh1wZIT91_r5Gulvr6Np0-2UTDh-2aAuRLyWquH_1E4Uf2Fg=="
    }
  ]
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `v` | number | Document version. Currently `1`. |
| `alg` | string | Always `crypto_box_seal`. |
| `encodeKey` | string | URL-safe Base64 public key used to seal. |
| `items` | array | Oldest → newest. Capped at **50**. |
| `items[].ts` | number | Notification `postedAt` (Unix ms). |
| `items[].box` | string | URL-safe Base64 sealed plaintext. |

`encodeKey` is public. The decode key is **not** in this JSON.

---

## Plaintext inside each `box`

UTF-8 JSON, then sealed:

```json
{
  "packageName": "com.whatsapp",
  "appName": "WhatsApp",
  "title": "...",
  "text": "...",
  "otp": "482914",
  "postedAt": 1710000000000
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `packageName` | string | Android package. |
| `appName` | string | Launcher label. |
| `title` | string | Notification title. |
| `text` | string | Notification body. |
| `otp` | string or `null` | Extracted one-time code, or `null`. |
| `postedAt` | number | Unix milliseconds. |

A **Test connection** item looks like:

```json
{
  "packageName": "com.notificationsaver.app.debug",
  "appName": "Notification Saver",
  "title": "Connection test",
  "text": "npoint forwarding is working.",
  "otp": null,
  "postedAt": 1787081548732
}
```

---

## Decrypt (Python / PyNaCl)

```python
import base64, json, urllib.request
from nacl.public import PrivateKey, SealedBox
from nacl.bindings import crypto_scalarmult_base

BIN_URL = "https://api.npoint.io/YOUR_BIN_ID"
DECODE_KEY = "paste-decode-key-from-the-app"

def b64(value: str) -> bytes:
    pad = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + pad)

document = json.loads(urllib.request.urlopen(BIN_URL).read().decode("utf-8"))
assert document.get("alg") == "crypto_box_seal"

sk_bytes = b64(DECODE_KEY)
derived = crypto_scalarmult_base(sk_bytes)
if derived != b64(document["encodeKey"]):
    raise SystemExit("decode key does not match encodeKey on the bin (wrong key or keys were reset)")

box = SealedBox(PrivateKey(sk_bytes))
for item in document.get("items", []):
    plain = json.loads(box.decrypt(b64(item["box"])))
    print(item["ts"], plain)
```

Install: `pip install pynacl`

---

## Decrypt (JavaScript / libsodium-wrappers)

```javascript
import _sodium from "libsodium-wrappers";

function b64(value) {
  const pad = "=".repeat((4 - (value.length % 4)) % 4);
  return _sodium.from_base64(value + pad, _sodium.base64_variants.URLSAFE);
}

await _sodium.ready;
const sodium = _sodium;

const document = await (await fetch("https://api.npoint.io/YOUR_BIN_ID")).json();
const sk = b64("paste-decode-key-from-the-app");
const pk = b64(document.encodeKey);

for (const item of document.items) {
  const opened = sodium.crypto_box_seal_open(b64(item.box), pk, sk);
  console.log(item.ts, JSON.parse(new TextDecoder().decode(opened)));
}
```

`crypto_box_seal_open` needs **both** the public (encode) and secret (decode) keys. The public key is already on the bin as `encodeKey`.

---

## Check that a decode key belongs to a bin

Decode key → public key must equal `encodeKey`:

```
encodeKey  ==  Base64URL( X25519_public_from_private( decodeKey ) )
```

If **Generate keys** replaced an existing pair, old `items` will fail to open even if `encodeKey` is the new public key (those boxes were sealed to the old public key). Clear the bin after generating if other apps should not keep unreadable ciphertext.

---

## What this does not protect

- The **decode key**, if copied into chat, screenshots, or another device.
- The **phone** itself (it holds plaintext notifications and both keys).
- **Telegram** (plaintext HTML to your chat).
- **npoint availability**, size limits, or rate limits (about 100 req/min per IP, 600 per bin).
- Items after you **generate new keys** without clearing the bin.

Create the bin on npoint.io **while logged out** so POST works without a bearer token. If POST returns 401, the bin is owned — make a new unowned bin.
