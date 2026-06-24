# OpenAPI contract reconciliation — backlog

Status of bringing each module under strict contract enforcement
(`-Dcontract.strict.modules`). See the PR for the mechanism.

| Module | Spec valid & reconciled | e2e coverage | Strict now? |
|---|---|---|---|
| reporting | ✅ | ✅ full | ✅ **strict** |
| export | ✅ (rewritten to match code) | ⚠️ render-batch untested | ❌ report-only |
| notification | ✅ (was invalid — now loads) | ⚠️ device-test untested | ❌ report-only |
| third-party-authentication | ✅ (was invalid — now loads) | ❌ no e2e tests at all | ❌ report-only |
| skill | — (intentionally skipped, module abandoned) | — | — |

The three remaining modules now have **valid specs reconciled to the current code**, so
they run cleanly in report-only mode. To flip each to strict, the items below need a
decision and/or a new e2e scenario. Each is independent.

---

## export

**Done:** rewrote the spec to match the controller — fixed the `GET` path
(`/render/{id}` → `/template/{id}`), added the missing `POST /render-batch/{id}`,
added `reportName` to the render request, added `401`/`404` responses, and documented
the binary response content-types. Report-only drift is down to one harness issue (below).

**To enable strict:**

1. **Write an e2e scenario for `POST /v1/export/render-batch/{templateId}`** — it is
   implemented and documented but never exercised, so the coverage gate fails. The
   request body is a free-form `JsonNode` (batch data); the response is a ZIP or
   combined document stream. Needs a fixture and an understanding of the batch modes.
2. **HARNESS — multipart request bodies.** `POST /template` is `multipart/form-data`;
   the e2e `exchangeMultipart` helper does not forward the body to the validator, so it
   reports a false "request body is required but none found". Before export strict,
   either forward the multipart body to the validator or relax
   `validation.request.body.missing` for multipart operations (in `OpenApiContractValidators`).
3. **DECISION — dynamic binary content-types.** `GET /template/{id}` and
   `POST /render/{id}` return the document's own media type, which varies with the
   uploaded template / `convertTo` (`application/pdf`,
   `…wordprocessingml.document`, …). The spec currently lists pdf + docx + octet-stream.
   Decide: (a) enumerate every supported output format, or (b) relax response
   content-type validation for these operations (a `LevelResolver` ignore), or
   (c) accept the enumerated list as "good enough". A streaming download uses an
   octet-stream `Accept` header (see the `download` step added for reporting).

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
