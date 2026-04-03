# Rule System Adduct Pattern – Backend

This repository contains a lightweight Spring Boot backend that exposes:

- A feature annotation endpoint that transforms one or more LC-MS features
	(m/z, intensity, retention time) into annotated features using the
	adduct catalog, matching adduct masses within a tolerance.
- A rule_puntuation endpoint skeleton that receives annotated features and
	returns them with scores (implementation placeholder).

---

## Overview

The service is organized following a feature-based architecture similar to
the CEU Mass Mediator backend. Each feature isolates its controller, service,
DTOs, and domain objects. Shared models and adduct utilities live under
`shared/`.

---

## Running the Project Locally

### Requirements

- Java 21
- Maven

### Steps

1. Clone the repository.
2. Run the application:

```bash
mvn spring-boot:run
```

---

## Structure Overview

```
ceu.biolab.cmm
│
├── featureAnnotation/
│   ├── controller/
│   ├── service/
│   └── dto/
│
├── rulePuntuation/
│   ├── controller/
│   ├── service/
│   └── dto/
│
├── shared/
│   ├── domain/
│   └── service/
│
└── Application.java
```

---

## Main Endpoints

All endpoints live under the `/api` prefix.

| Endpoint | Description |
| --- | --- |
| `POST /api/annotate-feature` | Annotate one or more LC-MS features using adducts. |
| `POST /api/rule-puntuation` | Score pre-annotated features (skeleton). |

---


## Resources

- Adduct catalogs live under `src/main/resources/adducts` and are loaded at
	startup for annotation.