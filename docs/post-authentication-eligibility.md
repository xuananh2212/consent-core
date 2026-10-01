# Post-authentication eligibility during Consent Preparation

## Canonical ownership

After the PSU is authenticated, Flow Manager reaches `SUBJECT_AUTHENTICATED`, moves to `CONSENT_PREPARING` and calls only:

```text
POST /internal/v1/consents/{consentId}/prepare-authorization
```

Flow Manager does not know whether a business eligibility check exists and does not call Customer/Core Banking/service-status APIs itself.

Consent Core determines the effective preparation requirements from configuration using:

```text
tenantId + consentType + optional clientId + optional requiredScopes
```

`requiredScopes` uses ALL semantics: every configured scope must exist in the effective OAuth scope set supplied by the trusted Flow Manager/AS context.

## Runtime sequence

```text
PSU authentication success
        |
        v
Flow Manager: SUBJECT_AUTHENTICATED -> CONSENT_PREPARING
        |
        | prepare-authorization(clientId, scopes, subjectRef)
        v
Consent Core / PrepareAuthorizationUseCase
        |
        | 1. load consent; cross-check client/subject
        | 2. resolve effective POLICY requirements
        | 3. Data Enrichment -> backend -> normalized POLICY facts
        | 4. Policy/Eligibility evaluate
        |
        +---- DENY ----> preparationStatus=DENIED
        |                consent remains REGISTERED
        |                presentationReady=false
        |                Flow Manager stops before consent page
        |
        `---- ALLOW ----> resolve CONTEXT/SELECTION
                         -> PSU selection if needed
                         -> final revision/contentHash
                         -> RequestAuthorization
                         -> AWAITING_AUTHORIZATION
```

## Reference eligibility configuration

The reference implementation keeps eligibility evaluation deliberately narrow. A POLICY requirement becomes a boolean gate when its `configuration` contains:

```json
{
  "eligibility": {
    "field": "eligible",
    "reasonCode": "PSU_SERVICE_NOT_REGISTERED",
    "messageKey": "consent.eligibility.service_not_registered",
    "retryable": false
  }
}
```

The provider/mapping adapter normalizes the bank-specific backend response to:

```json
{
  "eligible": false
}
```

This is not a general expression engine. Complex bank-specific logic should be normalized in a provider/mapping/approved extension into the same canonical business outcome.

## Structured denial

Example response:

```json
{
  "preparationStatus": "DENIED",
  "authorizationContext": {
    "status": "REGISTERED",
    "presentationReady": false
  },
  "reasonCode": "PSU_SERVICE_NOT_REGISTERED",
  "messageKey": "consent.eligibility.service_not_registered",
  "retryable": false,
  "correlationId": "..."
}
```

A preparation DENY is not a PSU reject decision and therefore does not move the consent to `REJECTED`.

## Out of scope

This implementation does not re-check dynamic eligibility every time a Client App invokes a protected resource API. Runtime enforcement/revalidation will be designed separately when required.
