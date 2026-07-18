# Order Service

Creates and tracks orders for authenticated users.

Part of a five-service stack — see [`k8s`](https://github.com/timode-6/k8s) for how it's deployed alongside [`api-gateway`](https://github.com/timode-6/api-gateway), [`auth-service`](https://github.com/timode-6/auth-service), [`user-service`](https://github.com/timode-6/user-service), and [`payment-service`](https://github.com/timode-6/payment-service).

## Responsibilities

- Reads `userId` straight from the JWT — it doesn't take part in registration and doesn't need to know Auth or User Service exist.
- On order creation, saves the order locally, publishes an `order.created` event to Kafka once the transaction commits, and returns `201 Created` right away. Payment Service picks the event up asynchronously.

## Stack

- Java 21, Spring Boot
- Spring Data JPA + Liquibase
- PostgreSQL
- Kafka producer

## Running locally

```bash
./gradlew bootRun
```

or:

```bash
docker compose up --build
```

## Notes

- Order creation still makes one synchronous call to User Service. Under heavy concurrent load this is the bottleneck — a load test showed p95 latency climbing past 25s as the thread pool starved, while a chaos test that killed Payment Service entirely for 20s had *zero* impact on order creation (Kafka just buffered the backlog). Moving the User Service lookup off the hot path is the natural next step.