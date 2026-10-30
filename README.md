# ShopExpress

Sistema de microservicios para comercio electrónico: Tarea 2 de NoSQL 2026.

Tiene tres servicios Spring Boot. MongoDB es la fuente de verdad de los tres, y Redis es la caché de lectura de Usuarios y Catálogo. La especificación completa, con los requisitos, las decisiones de diseño y los diagramas, está en [`docs/`](docs/).

> Este README se completa a medida que avanzan los incrementos: la justificación del diseño, los diagramas y las pruebas se agregan cuando estén implementados.

## Requisitos

- **Docker con Docker Compose.** No hace falta Java ni Maven: cada servicio se compila dentro de Docker.
- **JDK 25**, solo para desarrollar o correr los tests fuera de Docker.
- **Node.js**, solo para correr la colección de Postman desde la terminal con Newman.

## Levantar el sistema

```bash
docker compose up --build
```

El primer arranque tarda unos minutos, porque baja las imágenes y las dependencias de Maven.

| Componente | Puerto | Para comprobar que está vivo |
| --- | --- | --- |
| `users-service` (Usuarios) | 8081 | `curl http://localhost:8081/actuator/health` |
| `catalog-service` (Catálogo e Inventario) | 8082 | `curl http://localhost:8082/actuator/health` |
| `orders-service` (Pedidos y Pagos) | 8083 | `curl http://localhost:8083/actuator/health` |
| MongoDB 8.0, replica set `rs0` de un nodo | 27017 | `docker compose exec mongodb mongosh --quiet --eval "rs.status().ok"` |
| Redis 8.10, sin persistencia | 6379 | `docker compose exec redis redis-cli ping` |

Cada servicio responde `"status":"UP"` solo si llega a sus bases: los tres a MongoDB, y Usuarios y Catálogo, además, a Redis.

## Probar la API

Con el sistema levantado, desde la raíz del repositorio:

```bash
npx newman run postman/ShopExpress.postman_collection.json -e postman/ShopExpress.postman_environment.json
```

Cada solicitud verifica su resultado, y Newman termina con un resumen de las verificaciones que fallaron. La colección crea datos nuevos en cada corrida, así que se puede correr varias veces seguidas.

Para usarla desde Postman, se importan los dos archivos de `postman/` y se elige el entorno **ShopExpress local**. Las solicitudes se corren en orden, porque algunas usan el id que guardó una anterior.

## Detener

```bash
docker compose down      # conserva los datos de MongoDB
docker compose down -v   # borra también los datos, para empezar de cero
```

## Desarrollar un servicio fuera de Docker

Se levantan solo las bases con Compose y el servicio se corre desde la máquina:

```bash
docker compose up -d mongodb redis
cd users-service
./mvnw spring-boot:run
```

Si el sistema completo ya está levantado, el contenedor del servicio ocupa su puerto y el servicio local no puede arrancar. Antes de correrlo, detené ese contenedor, por ejemplo con `docker compose stop users-service`.

## Conectarse a MongoDB desde la máquina

Con Compass o `mongosh`, usar `mongodb://localhost:27017/?directConnection=true`.

`directConnection=true` es necesario porque el replica set anuncia a su miembro como `mongodb:27017`, un nombre que solo existe dentro de la red de Docker. Sin esa opción, el cliente intentaría conectarse a ese nombre y fallaría.

## Estructura

```text
ShopExpress/
├── README.md
├── docker-compose.yml
├── docs/              especificación de requerimientos y diagramas
├── users-service/     Servicio de Usuarios
├── catalog-service/   Servicio de Catálogo e Inventario
├── orders-service/    Servicio de Pedidos y Pagos
├── postman/           colección y entorno de Postman
└── scripts/           inicialización del replica set
```
