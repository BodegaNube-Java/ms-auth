# ms-auth

Microservicio de autenticación y usuarios de BodegaNube.

## Tecnologías

- Java 21
- Spring Boot
- Spring Data JPA
- Spring Security
- PostgreSQL

## Responsabilidad

- Autenticar usuarios y emitir el token (JWT) usado por Amazon API Gateway para autorizar al resto de los microservicios.
- Administrar los roles del sistema: `OPERARIO` y `COMERCIO`.

## Endpoints

```http
POST /auth/login
GET  /auth/usuarios/me
```

## Reglas de negocio

- Solo usuarios activos pueden autenticarse.
- Cada usuario tiene un único rol: `OPERARIO` o `COMERCIO`.
- Un usuario `COMERCIO` queda asociado a un identificador de comercio, usado por `ms-ordenes` para filtrar sus propias órdenes.

## Roles

```text
OPERARIO
COMERCIO
```

## Base de datos

PostgreSQL (`ms_auth`).

## Variables de entorno

```env
DB_URL=
DB_USERNAME=
DB_PASSWORD=
JWT_SECRET=
```

## Ejecución local

```bash
./mvnw spring-boot:run
```

## Build

```bash
./mvnw clean package
```
