# Análisis técnico — Pinatech (Computer Store)

**Stack:** Angular 21 · Spring Boot 3.5 · PostgreSQL 17 · Java 21 · Docker Compose
**Alcance revisado:** backend, frontend, infraestructura, CI, con foco en seguridad y buenas prácticas.

## Resumen ejecutivo

El proyecto está bastante mejor construido de lo que sugiere el README ("Etapa 1"): ya hay autenticación JWT completa, catálogo, inventario con locking, órdenes con recálculo de precio server-side, y tickets de servicio técnico. Las decisiones de seguridad de base están bien tomadas (passwords con BCrypt, refresh tokens hasheados y rotados, cookies HttpOnly+SameSite, RFC 9457 para errores, usuario no-root en Docker). Los problemas más importantes no son de diseño sino de **completitud**: falta casi toda la cobertura de tests, hay inconsistencias de formato que dificultan el mantenimiento, y hay un par de vacíos de sesión/autorización que conviene cerrar antes de sumar más funcionalidades.

---

## 🔴 Prioridad alta

### 1. Cobertura de tests casi nula para la lógica de negocio crítica
Solo existen `HealthControllerTest` y `InventoryTest` en el backend, y `app.spec.ts` (boilerplate por defecto) en el frontend. No hay tests para:
- Auth (registro, login, refresh, rotación de refresh token, expiración)
- Creación de órdenes (reserva de stock, recálculo de total, condiciones de carrera)
- Transiciones de estado de órdenes (`transitionTo`, cancelación y liberación de stock)
- Guards de Angular (`authGuard`, `adminGuard`, `technicalGuard`)

Esto es justamente el código con más riesgo (dinero, stock, seguridad). El CI corre `mvnw test`, así que hoy pasa "en verde" sin validar casi nada.
**Sugerencia:** priorizar tests de `AuthService`, `OrderController`/`AdminOrderController` (incluyendo condición de carrera con `findByProductIdForUpdate`) y de los guards de Angular.

### 2. No hay renovación automática de sesión (access token de 15 min)
`restoreSession()` solo se llama una vez, en el bootstrap de la app (`provideAppInitializer`). El access token expira a los 15 minutos (`access-expiration-ms: 900000`) y no existe un interceptor que capture un `401` y dispare `/api/auth/refresh` antes de reintentar la request original.
**Efecto real:** un usuario logueado que use la app por más de 15 minutos empieza a recibir 401 en llamadas autenticadas (crear orden, ver "Mis pedidos", tickets) sin que la UI se lo explique ni lo re-loguee solo.
**Sugerencia:** interceptor que, ante 401, llame a `refresh`, reintente la request original una vez, y si el refresh también falla, redirija a `/login`.

### 3. `GET /api/inventory/{productId}` accesible para cualquier usuario autenticado
En `SecurityConfiguration` solo quedan públicos `products`, `categories` y `brands`. Todo lo demás requiere *estar autenticado*, pero no necesariamente ser admin. `InventoryController.get()` no tiene `@PreAuthorize`, así que cualquier customer logueado puede consultar el stock exacto de cualquier producto por ID, iterando IDs. Es información interna de negocio (nivel de stock exacto) expuesta sin necesidad.
**Sugerencia:** `@PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")` en ese endpoint, o exponer solo un booleano `inStock` en un endpoint público separado si el frontend lo necesita para mostrar disponibilidad.

### 4. Sin rate limiting en `/api/auth/login` (ni en `refresh`)
No hay ninguna protección contra fuerza bruta a nivel aplicación (ni Bucket4j, ni rate limiting en gateway/proxy, ni bloqueo tras N intentos). El logging de intentos fallidos (`AuthService.invalidCredentials`) es correcto pero solo es observabilidad, no mitigación.
**Sugerencia:** rate limit por IP+email en `/api/auth/login` (aunque sea simple, ej. Bucket4j o un `ConcurrentHashMap` con ventana deslizante para el MVP), antes de exponer esto fuera de local.

---

## 🟡 Prioridad media

### 5. Inconsistencia de formato de código en el backend
Archivos como `SecurityConfiguration`, `AuthService`, `AuthController`, `JwtService` están bien formateados (multilínea, legible). Pero `CatalogAdminController`, `InventoryController`, `OrderController`, `AdminOrderController` y `TechnicalServiceTicketController` están literalmente comprimidos en 1–9 líneas por archivo (todo el cuerpo del método en una sola línea). Es difícil de leer, revisar en PR, y de debuggear con breakpoints.
**Sugerencia:** correr un formatter (ej. `spotless` con `google-java-format` o el de IntelliJ) sobre todo el módulo y agregarlo como check de CI para que no se repita.

### 6. CI incompleto
- El job de frontend corre `npm install && npm run build` pero **no ejecuta tests** (`npm test`).
- No hay linting (ni `ng lint`/ESLint en frontend, ni Checkstyle/Spotless en backend).
- `setup-node` usa `node-version: '22'`, pero el README pide **Node 24.15.0** — inconsistencia entre lo documentado y lo que valida el pipeline.
- No hay análisis de dependencias vulnerables (`npm audit`, `mvnw dependency-check`, o Dependabot/Renovate).
**Sugerencia:** agregar `npm run test -- --watch=false`, alinear la versión de Node, y sumar un job de auditoría de dependencias.

### 7. Sin paginación acotada en `GET /api/products`
`Pageable` viene con `@PageableDefault(size = 12)`, pero nada impide que el cliente pida `?size=100000`. Es un vector de DoS liviano (consulta pesada + payload grande) y de scraping masivo del catálogo.
**Sugerencia:** capar el tamaño máximo de página (interceptando el `Pageable` o validando manualmente antes de llamar al repositorio).

### 8. Interceptor de auth manda credenciales a cualquier request
`authInterceptor` agrega `Authorization` y `withCredentials: true` a **toda** petición HTTP saliente del front, sin filtrar por dominio. Hoy no es explotable porque todo apunta a la misma API, pero si en el futuro se agrega alguna llamada a un servicio de terceros (mapas, analytics, pasarela de pago, etc.), el JWT y las cookies viajarían también hacia ahí.
**Sugerencia:** condicionar el interceptor a `request.url.startsWith(environment.apiBaseUrl)`.

### 9. README desactualizado respecto al código real
El README dice "La Etapa 1 esta implementada" y lista un roadmap con auth/catálogo/órdenes como pendientes (Etapas 2–5), pero todo eso ya está en el código. Esto puede confundir a quien se sume al equipo o revise el proyecto (como pasó acá, con las capturas de otra demo). También falta la sección de "Screenshots" (`TODO`) y "Team".
**Sugerencia:** actualizar el estado real del roadmap; ayuda mucho para onboarding y para que el README siga siendo confiable como fuente de verdad.

---

## 🟢 Cosas bien resueltas (para no tocar)

- **Passwords y tokens:** BCrypt para passwords, refresh tokens generados con `SecureRandom` de 64 bytes, almacenados como hash SHA-256 (nunca en texto plano), con rotación en cada refresh y revocación explícita en logout.
- **Cookies de refresh:** `HttpOnly`, `SameSite=Strict`, `Secure` parametrizable por entorno, `path` acotado a `/api/auth`.
- **Access token en memoria (signal), no en localStorage:** reduce superficie de robo por XSS. Buena decisión, poco común ver esto bien hecho en proyectos MVP.
- **Precio recalculado siempre server-side** al crear una orden, con lock pesimista (`findByProductIdForUpdate`) para evitar sobreventa en condiciones de carrera — exactamente lo que promete el README.
- **Manejo de errores RFC 9457** (`ProblemDetail`), sin fugas de stack trace, con logging server-side de errores inesperados.
- **Seed de usuarios de desarrollo** correctamente acotado con `@Profile("dev")`.
- **Dockerfile multi-stage con usuario no-root** (`spring:spring`) — buena práctica de seguridad de contenedores.
- **Lazy loading de rutas** en Angular vía `loadComponent`, y guards separados por rol (`authGuard`, `adminGuard`, `technicalGuard`).
- **`.gitignore`** cubre correctamente `.env`, `target/`, `node_modules/`, etc. — no hay secretos versionados.

---

## Próximos pasos sugeridos (orden recomendado)

1. Agregar tests de `AuthService` y del flujo de órdenes (son los de mayor riesgo).
2. Cerrar el gap de renovación de sesión (interceptor de refresh-and-retry).
3. Restringir `GET /api/inventory/{id}` a roles internos.
4. Sumar rate limiting básico a `/api/auth/login`.
5. Formatear de forma consistente los controllers "comprimidos".
6. Completar el CI (tests de frontend, lint, alineación de versión de Node, audit de dependencias).
7. Actualizar el README al estado real del proyecto.
