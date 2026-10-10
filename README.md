# ms-auth

Microservicio de **autenticación** de la plataforma **BodegaNube** (gestión de pedidos
serverless en AWS). Parte de la Evaluación Parcial N°2 de *Java: Diseño y Construcción
de Soluciones nativas en Nube* (JVY0101).

## Responsabilidad

- **Registrar** comercios (rol `COMERCIO`).
- **Autenticar** usuarios con email y clave, y entregar un **JWT**.
- **Validar un token** y devolver quién es el usuario y qué rol tiene (`/auth/me`).

El API Gateway usa este servicio para decidir quién puede llamar al resto de los
microservicios. Los demás servicios no guardan usuarios ni claves.

## Tecnologías

| Tecnología | Versión |
|---|---|
| Java | 17 |
| Spring Boot | 4.x (Web, Data JPA, Security, Validation, Actuator) |
| Hibernate / JPA | incluido en Spring Boot |
| JWT | jjwt 0.12.6 (HS256) |
| PostgreSQL | 16 (en Docker) |
| Maven | wrapper incluido (`mvnw`) |
| Lombok | sí |

## Requisitos previos

- JDK 17 o superior (`java -version`)
- Docker Desktop (`docker --version`)
- Git
- Postman (o similar) para probar los endpoints

No necesitas instalar Maven ni PostgreSQL: se usa el wrapper `mvnw` y un contenedor.

## Levantar el proyecto desde cero

Los comandos son para **PowerShell (Windows)**.

### 1. Clonar el repositorio

```powershell
git clone <URL-DEL-REPOSITORIO>
cd ms-auth
```

### 2. Crear el archivo `.env`

```powershell
Copy-Item .env.example .env
```

Contenido para desarrollo local:

```
DB_USER=postgres
DB_PASSWORD=postgres
```

> El `.env` **no se sube a Git** (está en `.gitignore`). Solo se versiona `.env.example`.

### 3. Levantar la base de datos (Docker)

```powershell
docker compose up -d
docker compose ps
```

Debe verse el contenedor `auth-db` en estado `healthy`, con el puerto
`0.0.0.0:5434->5432/tcp`. Esto crea automáticamente la base `auth_db`.

### 4. Ejecutar el microservicio

```powershell
.\mvnw clean spring-boot:run
```

Queda escuchando en **http://localhost:8081**. Hibernate crea la tabla `usuarios`
al arrancar (`ddl-auto=update`).

> Al arrancar aparece `Using generated security password`. Es un aviso de Spring Security
> y no afecta: la autenticación real la hace este servicio con JWT.

### 5. Verificar

```powershell
docker exec -it auth-db psql -U postgres -d auth_db -c "\dt"
```

Debe listarse la tabla `usuarios`. También:

```
GET http://localhost:8081/actuator/health   ->   {"status":"UP"}
```

### 6. Crear un usuario de prueba

Hay dos formas.

**A) Registrando un comercio por la API (recomendado):**

```http
POST http://localhost:8081/auth/registro
Content-Type: application/json

{ "email": "tienda@ejemplo.cl", "password": "clave12345" }
```

**B) Insertando un operario por SQL.** Los operarios no se pueden registrar por la API
(el rol no lo elige el cliente). Entra a la consola de Postgres:

```powershell
docker exec -it auth-db psql -U postgres -d auth_db
```

Pega este comando **en una sola línea**. La clave del usuario es `password123`:

```sql
INSERT INTO usuarios (id, email, password_hash, rol, comercio_id, activo, creado_en, actualizado_en) VALUES (gen_random_uuid(), 'operario@bodeganube.cl', '$2a$10$6UVVhfgi09tDjgMrKTuwnuMjWcsv/QxzQaiStUrHbkw9Oe/OssyHS', 'OPERARIO', NULL, true, now(), now());
```

Debe responder `INSERT 0 1`. Salir con `\q`.

## Configuración

Archivo `src/main/resources/application.properties`:

| Propiedad | Valor | Descripción |
|---|---|---|
| `server.port` | `8081` | Puerto del servicio |
| `spring.datasource.url` | `jdbc:postgresql://127.0.0.1:5434/auth_db` | Conexión a la BD |
| `spring.datasource.username` | `${DB_USER:postgres}` | Usuario (variable de entorno) |
| `spring.datasource.password` | `${DB_PASSWORD:postgres}` | Clave (variable de entorno) |
| `spring.jpa.hibernate.ddl-auto` | `update` | Crea/actualiza tablas |
| `app.jwt.secret` | `${JWT_SECRET:...}` | Secreto para firmar el JWT (mínimo 32 caracteres) |
| `app.jwt.expiracion-ms` | `28800000` | Duración del token: 8 horas |

La URL usa `127.0.0.1` y no `localhost` a propósito: en Windows, `localhost` puede
resolverse a IPv6 (`::1`) y llegar a otro proceso (por ejemplo el relay de WSL).

Para usar otras credenciales o un secreto propio, defínelos en la misma terminal donde
ejecutas la app:

```powershell
$env:DB_USER="postgres"
$env:DB_PASSWORD="tu-clave"
$env:JWT_SECRET="un-secreto-largo-de-al-menos-32-caracteres"
.\mvnw spring-boot:run
```

> El secreto por defecto es solo para desarrollo. En un despliegue real debe venir de una
> variable de entorno y nunca del repositorio.

## Arquitectura (CSR)

```
controller/   Recibe HTTP y delega. Sin lógica de negocio.
service/      Reglas de negocio: login, registro y validación de token.
repository/   Acceso a datos con Spring Data JPA.
model/        Entidad Usuario y enum Rol.
dto/          Objetos de entrada y salida de la API (records).
security/     JwtProvider: genera y valida tokens.
config/       SecurityConfig: BCrypt y reglas de acceso.
exception/    Excepciones de negocio y @RestControllerAdvice.
```

### Modelo de datos

Tabla `usuarios`:

| Columna | Tipo | Notas |
|---|---|---|
| `id` | UUID | Clave primaria |
| `email` | texto | Único |
| `password_hash` | texto | Hash BCrypt, nunca la clave |
| `rol` | texto | `COMERCIO` u `OPERARIO` |
| `comercio_id` | UUID | Nulo para operarios |
| `activo` | booleano | |
| `creado_en`, `actualizado_en` | fecha y hora | |

### Decisiones de diseño

- **JWT sin estado:** el servicio no guarda sesiones. El token trae `email`, `rol` y
  `comercioId`, y expira a las 8 horas.
- **Claves con BCrypt:** se guarda solo el hash, con sal aleatoria por usuario.
- **Sin enumeración de usuarios:** si el email no existe, la clave es incorrecta o el
  usuario está inactivo, la respuesta pública es siempre la misma
  (`Credenciales invalidas`). El motivo real queda solo en el log del servidor.
- **El rol no lo elige el cliente:** el registro público siempre crea `COMERCIO`;
  los operarios se crean por SQL.
- **DTOs de salida:** `passwordHash` nunca se expone en la API.

## Endpoints

Base URL: `http://localhost:8081`

| Método | Ruta | Descripción | Éxito |
|---|---|---|---|
| `POST` | `/auth/registro` | Registra un comercio | `201` |
| `POST` | `/auth/login` | Autentica y entrega el JWT | `200` |
| `GET` | `/auth/me` | Valida el token y devuelve el usuario | `200` |

### Ejemplos

**Registro**

```http
POST /auth/registro
Content-Type: application/json

{ "email": "tienda@ejemplo.cl", "password": "clave12345" }
```

Respuesta `201` (sin `passwordHash`):

```json
{ "id": "...", "email": "tienda@ejemplo.cl", "rol": "COMERCIO", "comercioId": "..." }
```

**Login**

```http
POST /auth/login
Content-Type: application/json

{ "email": "operario@bodeganube.cl", "password": "password123" }
```

Respuesta `200`:

```json
{ "token": "eyJ...", "tipo": "Bearer", "expiraEnSegundos": 28800 }
```

**Usuario actual**

```http
GET /auth/me
Authorization: Bearer <TOKEN>
```

### Códigos de respuesta

| Código | Cuándo |
|---|---|
| `200` / `201` | Operación exitosa |
| `400` | Validación fallida (email inválido, clave de menos de 8 caracteres, campos vacíos) |
| `401` | Credenciales inválidas o token inválido o vencido: `"Credenciales invalidas"` |
| `409` | Registro con un email que ya existe |
| `500` | Fallo técnico no controlado |

## Probar con Postman

1. `POST /auth/registro` con un comercio nuevo → `201`.
2. `POST /auth/login` con ese comercio → `200` y copia el `token`.
3. `GET /auth/me` con el header `Authorization: Bearer <token>` → `200` con email y rol.
4. Casos de error:
   - Login con clave incorrecta → `401` `"Credenciales invalidas"`.
   - Login con un email que no existe → `401` con **el mismo** mensaje.
   - Registro con un email repetido → `409`.
   - Registro con clave de 5 caracteres → `400`.
   - `/auth/me` sin header o con un token alterado → `401`.

Verificación directa en la BD (se ve el hash BCrypt, no la clave):

```powershell
docker exec -it auth-db psql -U postgres -d auth_db -c "SELECT email, rol, activo, left(password_hash, 7) AS hash FROM usuarios;"
```

## Maven: compilar, probar y empaquetar

```powershell
.\mvnw clean            # limpia target/
.\mvnw test             # ejecuta los tests (requiere la BD levantada)
.\mvnw clean package    # compila, prueba y genera el .jar en target/
```

Ejecutar el artefacto generado:

```powershell
java -jar target\ms-auth-0.0.1-SNAPSHOT.jar
```

El nombre exacto del `.jar` depende del `artifactId` y la `version` del `pom.xml`.

### Dependencias que no vienen de Spring Initializr

JWT se agrega a mano en el `pom.xml`:

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

## Flujo de trabajo en Git

| Rama | Uso |
|---|---|
| `main` | Versión estable y entregable |
| `develop` | Integración del trabajo en curso |
| `feature/*` | Una rama por funcionalidad (por ejemplo `feature/auth-registro`) |

Los commits siguen el formato `tipo(alcance): descripción`, por ejemplo
`feat(auth): agrega endpoint de registro de comercios`.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| `la autentificación password falló` (en español) | La app llega a otro Postgres (Windows o WSL). Usa `127.0.0.1` y el puerto `5434`. Revisa con `netstat -ano \| findstr :5434` |
| Falla la clave aunque coincide con `.env` | El volumen guarda la clave de la primera vez: `docker compose down -v` y `docker compose up -d` |
| `export` no funciona | Es de Linux. En PowerShell: `$env:DB_PASSWORD="clave"` |
| Login da `401` con la clave correcta | El hash del usuario no corresponde a esa clave. Usa el hash de este documento o registra el usuario por la API |
| `syntax error at or near "="` al pegar el SQL | Se pegó en varias líneas y se juntaron las palabras. Pegar en una sola línea |
| `Using generated security password` | Aviso inofensivo de Spring Security. Se puede quitar con un bean `UserDetailsService` vacío |
| `mvn package` falla en los tests | La BD no está levantada: `docker compose up -d` |
| Puerto 8081 ocupado | Cambia `server.port` o cierra el proceso que lo usa |

## Comandos útiles de Docker

| Qué quieres | Comando |
|---|---|
| Apagar (conserva datos) | `docker compose down` |
| Encender | `docker compose up -d` |
| Ver logs de la BD | `docker compose logs -f` |
| Borrar todo y partir de cero | `docker compose down -v` |
| Entrar a la BD | `docker exec -it auth-db psql -U postgres -d auth_db` |

## Estructura del repositorio

```
ms-auth/
├── src/main/java/cl/duoc/ms_auth/   controller, service, repository, model, dto, security, config, exception
├── src/main/resources/              application.properties
├── src/test/java/...                tests
├── docker-compose.yml               base de datos PostgreSQL
├── .env.example                     variables de entorno de ejemplo
├── pom.xml
└── README.md
```