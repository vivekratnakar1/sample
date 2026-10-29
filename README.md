
# Spring Boot Rate Limiter (Redis)

A learning project demonstrating a fixed-window API rate limiter using Spring Boot, Java 21, Maven, and Redis.

## What it does

- Allows up to 5 requests per client IP in a 60-second window.
- Applies the limit to paths starting with `/api/`.
- Returns HTTP `429 Too Many Requests` after the limit is exceeded.
- Sends `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, and `Retry-After` headers.
- Uses a Redis Lua script so incrementing the counter and setting its expiry are atomic.

## Architecture

```text
Postman / Client
      |
      v
RateLimitFilter
      |
      v
RateLimiterService
      |
      v
Redis (counter + TTL)
      |
      +-- within limit --> ApiController --> HTTP 200
      |
      +-- over limit ----> HTTP 429
```

## Requirements

- JDK 21
- Maven 3.9+ (or use the Maven integration in your IDE)
- Docker Desktop, or a separately installed Redis server
- Eclipse IDE (Spring Tools is helpful)
- Postman or curl

## Run Redis

From the project root:

```bash
docker compose up -d
docker compose ps
```

If Docker is unavailable, start a local Redis server and ensure it listens on `localhost:6379`.

## Run the application

In Eclipse:
1. Import the folder as **Existing Maven Project**.
2. Wait for Maven dependencies to finish downloading.
3. Confirm the project JRE is Java 21.
4. Run `RateLimiterApplication.java` as **Java Application** or **Spring Boot App**.

Or from a terminal in the project root:

```bash
mvn spring-boot:run
```

## Test with curl

First five requests from the same client IP should normally return HTTP 200:

```bash
curl -i http://localhost:8080/api/hello
```

Repeat the command 6 times quickly. Once the request count exceeds 5 during the same 60-second window, the response should be HTTP 429.

Test a second API path; it shares the same IP-based limit:

```bash
curl -i http://localhost:8080/api/products
```

Health endpoint is not covered by the `/api/` limiter:

```bash
curl -i http://localhost:8080/health
```

Wait for the window to expire (up to 60 seconds from the first request) and try again.

## Postman proof of concept (POC)

1. Create a GET request to `http://localhost:8080/api/hello`.
2. Click **Send** five times quickly. Inspect the response status and rate-limit headers.
3. Click **Send** a sixth time before the window expires.
4. Expected result: `429 Too Many Requests`, with a JSON error message and a `Retry-After` header.
5. Repeat with `/api/products`; the limit is shared because both paths use the same client-IP key.
6. Call `/health`; it should return `200` without consuming the `/api/` quota.

Expected allowed response (example):

```json
{
  "message": "Hello! Your request was allowed.",
  "timestamp": "2026-10-21T10:00:00Z"
}
```

Expected blocked response:

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please retry after the window resets."
}
```

Header values depend on when the request is made.

## Configuration

Edit `src/main/resources/application.properties`:

```properties
app.rate-limit.limit=5
app.rate-limit.window-seconds=60
app.rate-limit.path-prefix=/api/
```

For quick local testing, you can temporarily use `limit=3` and `window-seconds=30`.

## Code walkthrough (Hinglish)

1. **RateLimiterApplication**: Spring Boot application ka entry point hai.
2. **RateLimitProperties**: `application.properties` se limit, window aur path prefix read karta hai.
3. **RateLimiterService**: Redis mein client ka request counter increment karta hai. First request par expiry set hoti hai.
4. **Lua script**: Increment aur expiry ko Redis ke andar ek atomic operation banati hai. Concurrent requests mein counter/TTL race ka risk kam hota hai.
5. **RateLimitFilter**: Controller se pehle run hota hai. Allowed request ko aage bhejta hai; limit cross hone par 429 return karta hai.
6. **ApiController**: Demo endpoints provide karta hai.
7. **docker-compose.yml**: Local Redis server run karne ka simple setup deta hai.

## Important production considerations

- This is a fixed-window teaching example. Traffic can burst around window boundaries; sliding-window or token-bucket algorithms may suit some systems better.
- The sample uses `request.getRemoteAddr()`. Behind a reverse proxy, that may be the proxy address. Only trust forwarded IP headers when the proxy is configured and trusted.
- If Redis is unavailable, this implementation does not silently allow requests; the request may fail with a server error. Define and test your desired fail-open/fail-closed policy for production.
- Add authentication-aware keys when limits should apply per user/API key instead of per IP.
- Consider Redis authentication, TLS, monitoring, metrics, integration tests, and load tests before production use.
- Rate limiting is not a replacement for authentication, authorization, input validation, or abuse detection.
