# OpenAPI contract reconciliation — backlog

Status of bringing each module under strict contract enforcement
(`-Dcontract.strict.modules`). See the PR for the mechanism.

| Module | Spec valid & reconciled | e2e coverage | Strict now? |
|---|---|---|---|
| reporting | ✅ | ✅ full | ✅ **strict** |
| export | ✅ (rewritten to match code) | ✅ (render-batch success unverifiable — engine lacks batch) | ✅ **strict** |
| notification | ✅ (was invalid — now loads) | ⚠️ device-test untested | ❌ report-only |
| third-party-authentication | ✅ (was invalid — now loads) | ❌ no e2e tests at all | ❌ report-only |
| skill | — (intentionally skipped, module abandoned) | — | — |

The three remaining modules now have **valid specs reconciled to the current code**, so
they run cleanly in report-only mode. To flip each to strict, the items below need a
decision and/or a new e2e scenario. Each is independent.

---

## export — ✅ now strict

**Done:** rewrote the spec to match the controller — fixed the `GET` path
(`/render/{id}` → `/template/{id}`), added the missing `POST /render-batch/{id}`
(now with the `mode` query param, a `data`-array request body, and `400`/`422`/`500`
responses), added `reportName` to the render request, added `401`/`404` responses, and
documented the binary response content-types. Added e2e scenarios for `render-batch`
(a `422` engine-rejection path and a `404` not-found path). Fixed the multipart-body
harness false-positive (see resolved item below) and **flipped export into the strict
set** (`contract.strict.modules` default is now `reporting,export`).

**Resolved on the way to strict:**

- **HARNESS — multipart request bodies (FIXED).** `POST /template` is
  `multipart/form-data`; `exchangeMultipart` did not forward the body to the validator,
  so it raised a false `validation.request.body.missing`. Fixed by threading a request
  content-type through `OpenApiContractValidators.validate` and having `exchangeMultipart`
  forward a URL-encoded *representation* of the form parts (`template=<filename>`) — the
  validator parses form bodies with its URL-encoded parser, so that is enough for it to
  see the documented `template` part. Keep the representation in sync if more form fields
  are ever sent.

**Remaining caveats (do NOT block strict, but track):**

1. **render-batch SUCCESS (200 + content-type) is UNVERIFIED at integration level.**
   The endpoint is exercised only on its **error** paths (`422` engine-rejection, `404`
   not-found) — which is what satisfies the strict **coverage** gate. The **success**
   path (a real ZIP / combined PDF) **cannot be exercised** in the e2e harness: the test
   `carbone/carbone-ee` container refuses batch rendering. Setting `nbReportMaxPerBatch > 0`
   (as the prod/dev docs require, see `docs/developer/carbone.config.json`) clears the
   first check (`Batch processing deactivated`), but the engine then rejects with
   `Cannot use batch processing in buffer mode`. **Root cause not fully isolated** — the
   full Carbone EE config schema (dumped from the binary) has no render-mode/buffer knob,
   and the prod dev-docs config sets only `nbReportMaxPerBatch` (no license), so the
   "buffer mode" barrier was not resolvable from config alone in the test container.
   Consequences:
     - The spec's `200` content-type list (`application/zip` / `pdf` / `octet-stream`)
       is a **documented guess**, flagged `UNVERIFIED` in `export-api-v1.yaml`. Confirm
       the real `Content-Type` against a live batch render before trusting it.
     - Happy-path batch logic (zip assembly, entry-name decoding, combined mode) is
       covered **only** by the unit test `DefaultRenderTemplateBatchUseCaseTest` (mocked
       Carbone), which does **not** assert the real engine's response content-type.
     - **Strict green ≠ batch-success verified.** The coverage gate is satisfied by the
       `422`/`404` paths alone. To truly verify, run against a Carbone instance where
       batch works (and read the actual `Content-Type`), or determine what the test
       container needs to enable file-mode batch rendering.
2. **DECISION — dynamic binary content-types.** `GET /template/{id}` and
   `POST /render/{id}` return the document's own media type, which varies with the
   uploaded template / `convertTo` (`application/pdf`,
   `…wordprocessingml.document`, …). The spec currently lists pdf + docx + octet-stream,
   which covers everything the current scenarios render — so it produces **no mismatch
   today**. If a scenario ever renders another format, either enumerate it, or relax
   response content-type validation for these operations (a `LevelResolver` ignore). A
   streaming download uses an octet-stream `Accept` header (see the `download` step).

---

## notification

**Done:** the spec was **invalid and failed to load** — `DeviceRegistration` used
property-level `required: true/false` (not valid OpenAPI). Fixed to an object-level
`required: [deviceToken]`, so the spec now loads. Also documented the previously-missing
`401` on `POST /device` and the JSON error bodies (`Error`) on the `400`/`403`
responses. After this, the **exercised** paths are clean; only the device-test gap below
remains.

**To enable strict:**

1. **Write an e2e scenario for `POST /v1/notification/message/device-test`** — it is
   implemented and documented but never exercised (the only remaining coverage gap).
2. **CONTRADICTION — `TestMessageResponse` schema.** The spec documents
   `{ receiverIds: string[] }`, but the controller returns
   `{ outcome: CreateNotificationData }`. The spec is wrong; fix it to the real shape
   when adding the device-test scenario (left as-is for now since it is untested).

---

## third-party-authentication

**Done:** the spec was **invalid and failed to load** — every schema used
property-level `required: true/false`, and `Error.errorCode` had the same malformed
`items` block seen elsewhere. Fixed to object-level `required` arrays and a plain
`errorCode` string, so the spec now loads.

**To enable strict (largest effort):**

1. **Write e2e scenarios — this module has _no_ e2e tests at all.** All three
   operations (`POST /session`, `GET /session/{id}`, `GET /session/{id}/redirect`)
   are uncovered. This requires understanding the external-auth flow (a trusted client
   creating sessions for externally-authenticated users) and likely new fixtures/realm
   setup. Until then conformance is never checked and coverage cannot pass.
2. **VERIFY — `format: uuid`** kept on `userId`/`sessionId`; confirm these are bare
   UUIDs in reality (unlike reporting's prefixed entity-reference ids).
3. **NOTE** — this spec is `openapi: 3.1.0` (the others are `3.0.3`); confirm the
   validator handles 3.1 once the module is exercised.

---

## Cross-cutting decision

**`additionalProperties` policy (applies to every module).** The contract is currently
strict on undocumented response fields — a response with an extra field the schema
doesn't list fails. That forced documenting every field for reporting. If that is too
strict for hand-maintained specs, set `validation.response.body.schema.additionalProperties`
to `IGNORE` in the validator's `LevelResolver` (in `OpenApiContractValidators`) so the
contract guarantees documented fields are present and correctly typed without failing
on extras. This would materially reduce the reconciliation effort for the remaining
modules. Decide before reconciling more modules.
