# Levantar las BD con Docker (ms-auth y ms-inventario)

Cada microservicio tiene su propia BD en su propio contenedor (Database per Service).
Todo funciona en PowerShell (Windows).

| Servicio | Contenedor | BD | Puerto en tu PC |
|---|---|---|---|
| ms-auth | `auth-db` | `auth_db` | **5434** |
| ms-inventario | `inventario-db` | `inventario_db` | **5435** |

> Los puertos 5432/5433 se evitan a propósito: en Windows suele haber otro Postgres
> (o un relay de WSL) escuchando ahí y la app se conecta al equivocado.

---

## 1. Archivos (en la raíz de cada microservicio)

### `docker-compose.yml` (ms-auth)

```yaml
services:
  auth-db:
    image: postgres:16-alpine
    container_name: auth-db
    restart: unless-stopped
    environment:
      POSTGRES_DB: auth_db
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5434:5432"
    volumes:
      - auth-pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d auth_db"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  auth-pgdata:
```

### `docker-compose.yml` (ms-inventario)

Igual, cambiando nombres, BD y puerto:

```yaml
services:
  inventario-db:
    image: postgres:16-alpine
    container_name: inventario-db
    restart: unless-stopped
    environment:
      POSTGRES_DB: inventario_db
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5435:5432"
    volumes:
      - inventario-pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d inventario_db"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  inventario-pgdata:
```

### `.env` (en cada carpeta, **no se sube a git**)

```
DB_USER=postgres
DB_PASSWORD=postgres
```

Agrega `.env` al `.gitignore`. En el repo sube un `.env.example` con valores de ejemplo.

### `application.properties`

Usar **`127.0.0.1`** y no `localhost` (evita que Windows resuelva a IPv6 `::1`
y llegue a otro proceso).

```properties
# ms-auth
spring.datasource.url=jdbc:postgresql://127.0.0.1:5434/auth_db

# ms-inventario
spring.datasource.url=jdbc:postgresql://127.0.0.1:5435/inventario_db
```

El usuario y la clave siguen como `${DB_USER:postgres}` / `${DB_PASSWORD:postgres}`.
Si el `.env` usa `postgres/postgres`, no hay que exportar nada.

---

## 2. Levantar

```powershell
docker compose up -d
docker compose ps
```

`ps` debe mostrar `healthy` y el puerto correcto (`0.0.0.0:5434->5432` o `5435->5432`).

## 3. Arrancar el microservicio

```powershell
.\mvnw clean spring-boot:run
```

Hibernate crea las tablas solo (`ddl-auto=update`).

## 4. Usuario de prueba (solo ms-auth)

```powershell
docker exec -it auth-db psql -U postgres -d auth_db
```

Pega **en una sola línea** (las palabras no deben quedar pegadas):

```sql
INSERT INTO usuarios (id, email, password_hash, rol, comercio_id, activo, creado_en, actualizado_en) VALUES (gen_random_uuid(), 'operario@bodeganube.cl', '$2a$10$6UVVhfgi09tDjgMrKTuwnuMjWcsv/QxzQaiStUrHbkw9Oe/OssyHS', 'OPERARIO', NULL, true, now(), now());
```

Clave de ese usuario: `password123` (hash verificado). Salir con `\q`.

Prueba en Postman:

- `POST http://localhost:8081/auth/login` con `{"email":"operario@bodeganube.cl","password":"password123"}`
- `GET http://localhost:8081/auth/me` con header `Authorization: Bearer <token>`

---

## 5. Comandos del día a día

| Qué quieres | Comando |
|---|---|
| Apagar (conserva datos) | `docker compose down` |
| Encender | `docker compose up -d` |
| Ver logs de la BD | `docker compose logs -f` |
| Borrar todo y partir de cero | `docker compose down -v` |
| Entrar a la BD | `docker exec -it auth-db psql -U postgres -d auth_db` |

---

## 6. Errores comunes y solución

| Síntoma | Causa | Solución |
|---|---|---|
| `la autentificación password falló` (en español) | La app llega a OTRO Postgres (Windows o WSL), no al contenedor | Usar `127.0.0.1` y puertos 5434/5435. Revisar con `netstat -ano \| findstr :5434` que solo aparezca Docker |
| Falla la clave aunque coincida con el `.env` | El volumen guarda la clave de la primera vez | `docker compose down -v` y `docker compose up -d` |
| `export` no funciona | `export` es de Linux; en PowerShell no existe | `$env:DB_PASSWORD="clave"` en la misma terminal, o variables en IntelliJ (*Edit Configurations*) |
| `syntax error at or near "="` al pegar SQL | Se pegó en varias líneas y se juntaron palabras (`usuariosSET`) | Pegar el SQL en una sola línea, con espacios |
| Login da 401 con la clave correcta | Hash BCrypt que no corresponde a esa clave | Usar el hash de este documento o generar uno y verificarlo |
| Cambié el puerto y sigue el error | Config sin guardar o compilación vieja | Guardar, `.\mvnw clean` y reiniciar. Revisar variables antiguas en IntelliJ |
| Aparece `Using generated security password` | Spring Security crea un usuario por defecto | Es inofensivo. Para quitarlo, bean `UserDetailsService` vacío en `SecurityConfig` |
| ms-inventario responde 401 en todo | Tiene Spring Security activo | `SecurityConfig` con `permitAll` (la auth la hace el API Gateway) |
| El init de la BD no se re-ejecuta | Solo corre con volumen vacío | `docker compose down -v` |

---

## 7. Seguridad (recordatorio)

- Nunca subir el `.env` al repo.
- La respuesta 401 del login debe ser siempre `"Credenciales invalidas"`;
  el motivo real va solo al log del servidor.
- Estos valores `postgres/postgres` son **solo para desarrollo local**.