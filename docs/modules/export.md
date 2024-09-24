# Aam Digital - Export API

## Overview

The export module is responsible for handling various template-based export operations.
This module primarily focuses on generating PDFs for entities within the Aam Digital system.

### Dependencies

This service is using an external template engine for handle placeholder replacement in files and render PDF files.  
See https://carbone.io for more information and the specification [carboneio-api-spec.yaml](../api-specs/carboneio-api-spec.yaml)

## Controllers

### TemplateExportController

REST controller responsible for handling export operations related to templates. It provides endpoints for creating new templates, fetching existing templates, and rendering templates.

#### Specification

[export-api-v1.yaml](../api-specs/export-api-v1.yaml)

## Setup

Configure a compatible render api in the environment. You can use the default, aam-internal implementation,
but make sure, that the authentication is configured:

```
aam-render-api-client-configuration:
  base-path: https://pdf.aam-digital.dev
    auth-config:
      client-id: <needs-environment-configuration>
      client-secret: <needs-environment-configuration>
      token-endpoint: <needs-environment-configuration>
      grant-type: <needs-environment-configuration>
      scope: <needs-environment-configuration>
```

