#!/usr/bin/env bash
# Reservas y reposiciones simultáneas sobre un mismo producto (CA-17). El stock final tiene que ser el inicial, más lo
# repuesto, menos lo reservado, y nunca negativo (RN-01, RN-12).
#
# Uso, desde la raíz del repositorio y con el sistema levantado:
#   scripts/concurrencia-stock.sh
#
# Variables opcionales: CATALOG_URL (http://localhost:8082), INITIAL_STOCK (50), RESERVATIONS (200), RESTOCKS (100)
# y PARALLEL, las operaciones que van a la vez (20). Cada reserva y cada reposición es de una unidad. Con los valores
# por defecto se piden 50 unidades más de las que hay, así que algunas reservas tienen que quedarse sin stock.
set -euo pipefail

url=${CATALOG_URL:-http://localhost:8082}
initial=${INITIAL_STOCK:-50}
reservations=${RESERVATIONS:-200}
restocks=${RESTOCKS:-100}
parallel=${PARALLEL:-20}

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
run=$(date +%s%N)

response=$(curl -fsS -X POST "$url/api/products" -H 'Content-Type: application/json' \
	-d '{"sku":"CONCURRENCIA-'"$run"'","name":"Producto de la prueba de concurrencia","price":1,"stock":'"$initial"'}')
product_id=$(printf '%s' "$response" | sed -E 's/.*"id":"([^"]+)".*/\1/')

# Una operación por línea. Se mezclan al azar para que las reservas y las reposiciones se intercalen.
{
	for i in $(seq 1 "$reservations"); do echo "reserve $i"; done
	for i in $(seq 1 "$restocks"); do echo "restock $i"; done
} | shuf > "$work/operations"

# Un 503 significa que la transacción chocó en todos sus intentos: se repite con el mismo orderId, como haría Pedidos
# (D-17). Sin esto, esa reserva quedaría sin contar, y no se sabría si el stock cierra.
operation() {
	local kind=$1 n=$2 path body code
	if [ "$kind" = reserve ]; then
		path=/internal/reservations
		body='{"orderId":"ord_concurrencia-'"$run"'-'"$n"'","items":[{"productId":"'"$product_id"'","quantity":1}]}'
	else
		path=/api/products/$product_id/restock
		body='{"quantity":1}'
	fi
	for attempt in 1 2 3 4 5; do
		code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$url$path" -H 'Content-Type: application/json' -d "$body")
		[ "$code" != 503 ] && break
		sleep "0.$((attempt * 2))"
	done
	echo "$kind $code"
}
export -f operation
export url product_id run

echo "Producto $product_id, con stock inicial $initial"
echo "$reservations reservas y $restocks reposiciones de una unidad, $parallel a la vez"
xargs -P "$parallel" -L 1 bash -c 'operation "$0" "$1"' < "$work/operations" > "$work/results"

count() { grep -c "^$1 $2$" "$work/results" || true; }
reserved=$(count reserve 200)
out_of_stock=$(count reserve 409)
# Un 503 que sigue después de los reintentos es una respuesta válida: la reserva no se hizo y Pedidos la cancelaría.
# No se cuenta como reservada. Si se hubiera aplicado igual, el stock final no cerraría y el script fallaría.
conflicts=$(count reserve 503)
restocked=$(count restock 200)
others=$(grep -cvE '^(reserve (200|409|503)|restock 200)$' "$work/results" || true)

# La consulta lee de MongoDB y no de la caché: nadie consultó este producto antes, y la última reposición invalidó
# la entrada.
final=$(curl -fsS "$url/api/products/$product_id" | sed -E 's/.*"stock":([0-9-]+).*/\1/')
expected=$((initial + restocked - reserved))

echo "Reservas: $reserved aceptadas, $out_of_stock sin stock y $conflicts que siguieron chocando después de 5 intentos"
echo "Reposiciones: $restocked aceptadas"
[ "$others" -eq 0 ] || echo "Otras respuestas: $others (ver $work/results)"
echo "Stock final: $final. Esperado: $initial + $restocked - $reserved = $expected"
if [ "$final" -eq "$expected" ] && [ "$final" -ge 0 ] && [ "$others" -eq 0 ]; then
	echo "OK"
else
	trap - EXIT
	echo "NO COINCIDE"
	exit 1
fi
