# ADR 0005: No service registry, no config server

**Status:** accepted

## Context
Spring Cloud offers Eureka (discovery) and Config Server, but the target platform (Docker Compose now, Kubernetes
later) already provides DNS-based discovery and environment/ConfigMap/Secret based configuration.

## Decision
Services are addressed by DNS name (`order-service:8082`) and configured through environment variables with local
defaults in `application.yml`. We use Spring Cloud only for the gateway.

## Consequences
- One less component to run and secure; the same configuration model works on Kubernetes.
- Client-side load balancing and dynamic route changes would need the platform (Kubernetes Services, a service mesh)
  or an added registry.
