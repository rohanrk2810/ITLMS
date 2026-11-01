# Documentation index

Start with [../README.md](../README.md) for what the system is and how to run it, and [../AGENTS.md](../AGENTS.md) if you are an AI agent or new to the codebase.

## Core

| Doc | Covers |
|---|---|
| [01-architecture.md](01-architecture.md) | Services, calls, events, trade-offs |
| [19-system-design-hld-lld.md](19-system-design-hld-lld.md) | High-level and low-level design with diagrams |
| [03-erd.md](03-erd.md) | Database schema per service, plus later migrations |
| [04-api-reference.md](04-api-reference.md) | Every endpoint and who may call it |
| [26-security.md](26-security.md) | Security rules and checklist |
| [27-design-system.md](27-design-system.md) | UI tokens, components, patterns |
| [28-prd.md](28-prd.md) | Product requirements |
| [29-code-style.md](29-code-style.md) | Code, test, commit and doc conventions |
| [06-test-plan.md](06-test-plan.md) | Testing approach and coverage |
| [25-load-testing.md](25-load-testing.md) | k6 load tests and findings |

## Operating

| Doc | Covers |
|---|---|
| [05-local-setup.md](05-local-setup.md) | Run it on one machine, troubleshooting |
| [07-deployment-guide.md](07-deployment-guide.md) | Production additions, updating a server by hand |
| [02-documentation-review.md](02-documentation-review.md) | Departures from the original PDF, open items, limitations |

## Features

| Doc | Feature |
|---|---|
| [08](08-code-execution.md), [16](16-code-analysis.md), [17](17-language-choice.md) | Practice editor, code analysis, language choice |
| [09](09-test-portal.md), [10](10-student-progress-report.md), [18](18-microphone-option.md) | Test portal, progress report, microphone option |
| [11](11-live-recording-and-content-protection.md), [15](15-live-class-monitoring.md), [23](23-live-class-subtitles-and-ending.md) | Live recording, monitoring, subtitles and ending a class |
| [20](20-class-delivery-options.md), [21](21-video-lesson-monitoring.md) | Class delivery options, video-lesson monitoring |
| [12](12-single-active-login.md), [13](13-branding.md), [22](22-human-check-captcha.md) | Single login, branding, human check |
| [14](14-certificate-workflow.md), [24](24-new-students-and-fees.md), [30](30-id-cards.md) | Certificates, new students and fees, ID cards |

[diagrams/](diagrams/README.md) holds 40 diagrams (architecture, one ER diagram per service, flows, state machines, security, frontend) as Mermaid source and SVG. `api/` is a placeholder for an OpenAPI export and is empty today.

## Keeping docs true

A change to behaviour changes its doc in the same commit. Numbers (tests, endpoints, tables) are regenerated from a real run and dated. Departures from the PDF go in 02.
