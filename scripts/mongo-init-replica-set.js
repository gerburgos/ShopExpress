// Lo ejecuta el healthcheck de MongoDB en docker-compose.yml, cada pocos segundos.
// La primera vez inicializa el replica set; después solo comprueba que el nodo ya sea primario.
//
// No va en /docker-entrypoint-initdb.d: esos scripts corren contra un mongod temporal que la imagen
// levanta sin --replSet, así que ahí rs.initiate() fallaría.
try {
  rs.status();
} catch (e) {
  // NotYetInitialized: el replica set todavía no existe. Cualquier otro error es un problema real.
  if (e.codeName !== "NotYetInitialized") {
    throw e;
  }
  // El host es el nombre del servicio de Compose: es la dirección que usan los otros contenedores.
  rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "mongodb:27017" }] });
}

// El chequeo pasa recién cuando el nodo es primario: antes no acepta escrituras ni transacciones.
if (!db.hello().isWritablePrimary) {
  quit(1);
}
