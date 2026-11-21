# Waylink link protocol (v1)

How the phone app and Waylink for desktop (`/web`) share data through the relay (`/relay`) without the relay being able to read it.

The phone is the only source of truth. It sends a locked copy of its data; the web sends back small locked changes ("ops") that the phone applies.

## 1. Pairing (QR code)

1. The browser makes a P-256 key pair, a 16-byte **pairing id** and a 16-byte **QR secret**, and shows:

   ```
   waylink://link?v=1&p=<pairing id>&s=<QR secret>&k=<browser public key>&r=<relay host>
   ```

   All values are base64url without padding. The public key is the 65-byte uncompressed point.

2. The phone scans it, makes its own P-256 key pair and derives the link (below). It posts once:

   `POST https://<relay host>/v1/pair/<pairing id>` with `{"pub": "<phone public key>", "box": "<hello>"}`

   `hello` = seal(kind `hello`, `{"device": "Pixel 8", "platform": "android", "at": <ms>}`), base64url.

3. The browser polls `GET /v1/pair/<pairing id>` (204 until the phone answers, then 200 once, then gone). It derives the same link and opens `hello`. If it can't, the code was tampered with and it shows a new one. A code lasts 10 minutes.

4. The phone uploads its first vault straight away.

## 2. Deriving the link

```
bits  = ECDH(own private key, other public key)                       32 bytes
okm   = HKDF-SHA256(ikm = bits, salt = QR secret, info = "waylink-link-v1", length 64)
key   = okm[0..32)    AES-256-GCM key
token = okm[32..64)   bearer token, sent as base64url
linkId = base64url(SHA-256(token))[0..32)
```

The QR secret never reaches the relay, so the relay can't swap in its own key.

## 3. Sealing

`seal(kind, json)` = 12 random IV bytes, then AES-256-GCM ciphertext and 16-byte tag of the UTF-8 JSON, with additional data `"<linkId>:<kind>"`. Kinds: `hello`, `vault`, `op`.

## 4. Relay calls (all with `Authorization: Bearer <token>`)

| Call | Who | What |
|---|---|---|
| `PUT /v1/link/<linkId>/vault` | phone | body = seal(`vault`, vault). Up to 4 MB. Returns `{ver}` |
| `GET /v1/link/<linkId>/vault` | web | 204 if none yet; 304 with `If-None-Match`; else the sealed bytes and `ETag` |
| `POST /v1/link/<linkId>/ops` | web | body = seal(`op`, op). Returns `{seq}` |
| `GET /v1/link/<linkId>/ops?after=<seq>` | phone | `{ops: [{seq, data (base64url sealed op), at}], seq}` |
| `DELETE /v1/link/<linkId>/ops?upto=<seq>` | phone | drop ops it has applied |
| `DELETE /v1/link/<linkId>` | either | unlink: wipes everything; later calls get 410 |

401 means the token is wrong; 410 means the link is gone (unlinked, or unused for 120 days). Either side treats both as "unlinked".

## 5. Vault

```json
{ "format": "waylink-sync", "v": 1, "state": { ...the app state... }, "ackSeq": 12, "device": "Pixel 8", "at": 1763700000000 }
```

`state` is the same JSON as a backup's `data`, with these left out: `profile.blood`, `profile.medical`, `profile.iceName`, `profile.icePhone`, `profile.phone`, `profile.email`, `profile.licenceNo`, `profile.photo`, `alerts`, `glove`, `places`, and each vehicle's `bike` (pairing) and `parking`. `ackSeq` is the last op the phone has applied.

## 6. Ops

The phone pulls ops when the app opens, comes to the foreground, and every few minutes while open. It applies them in `seq` order, saves, uploads a new vault with the new `ackSeq`, then deletes them from the relay. The web shows its own unapplied ops on top of the last vault until `ackSeq` passes them.

```
{ id, at, op: "upsert", vid, coll, item }     coll: fuel | records | service | checklist | trips; replace by item.id or add
{ id, at, op: "delete", vid, coll, itemId }
{ id, at, op: "patch", vid, patch }           vehicle fields: nickname, plate, colour, model, fuelPrice
{ id, at, op: "patch", patch }                (no vid) profile fields: name, city, country
{ id, at, op: "odo", vid, km }                set the odometer
{ id, at, op: "batch", ops: [...] }           up to 50 of the above, in order
```

Anything unknown is skipped, so an older phone never breaks on a newer web app. Reference implementation: `web/js/ops.js`.

## 7. When the phone uploads

After any change to its data (a few seconds after the last one), after applying ops, and when the app opens. Each linked computer has its own link, so the phone uploads once per link.
