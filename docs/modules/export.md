# Aam Digital - Export API

## Overview

The export module is responsible for handling various template-based export operations. This module primarily focuses on generating PDFs for entities within the AAM system.

### Dependencies

This service is using an external template engine for handle placeholder replacement in files and render PDF files.  
See https://carbone.io for more information and the specification [carboneio-api-spec.yaml](../api-specs/carboneio-api-spec.yaml)

## Controllers

### TemplateExportController

REST controller responsible for handling export operations related to templates. It provides endpoints for creating new templates, fetching existing templates, and rendering templates.

#### Specification

[export-api-v1.yaml](../api-specs/export-api-v1.yaml)
