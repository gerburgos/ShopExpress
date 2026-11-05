#!/usr/bin/env bash
# La misma compra, con la misma clave, varias veces en paralelo y después una vez más (CA-05). Tiene que quedar un
# solo pedido, una sola reserva y un solo cobro. Las repeticiones que llegan mientras la original sigue en curso
# reciben 409; las que llegan después, y la última, la misma respuesta que la original (RN-03, D-06).
#
# Uso, desde la raíz del repositorio y con el sistema levantado:
#   scripts/idempotencia-pedidos.sh
#
# Variables opcionales: USERS_URL (http://localhost:8081), CATALOG_URL (http://localhost:8082),
# ORDERS_URL (http://localhost:8083) y PARALLEL, las solicitudes simultáneas (10).
set -euo pipefail

users=${USERS_URL:-http://localhost:8081}
catalog=${CATALOG_URL:-http://localhost:8082}
orders=${ORDERS_URL:-http://localhost:8083}
parallel=${PARALLEL:-10}

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
run=$(date +%s%N)
id() { sed -E 's/.*"id":"([^"]+)".*/\1/'; }

user_id=$(curl -fsS -X POST "$users/api/users" -H 'Content-Type: application/json' \
	-d '{"documentType":"PASSPORT","documentNumber":"IDEM'"$run"'","firstName":"Prueba","lastName":"Idempotencia","email":"idem.'"$run"'@example.com"}' | id)
product_id=$(curl -fsS -X POST "$catalog/api/products" -H 'Content-Type: application/json' \
	-d '{"sku":"IDEM-'"$run"'","name":"Producto de la prueba de idempotencia","price":10,"stock":5}' | id)
key="idempotencia-$run"
body='{"userId":"'"$user_id"'","items":[{"productId":"'"$product_id"'","quantity":1}],"payment":{"method":"CREDIT_CARD"}}'

buy() {
	curl -s -o "$work/body-$1" -w '%{http_code}' -X POST "$orders/api/orders" -H 'Content-Type: application/json' \
		-H "Idempotency-Key: $key" -d "$body" > "$work/code-$1"
}

echo "Misma compra, misma clave, $parallel veces a la vez"
for n in $(seq 1 "$parallel"); do buy "$n" & done
wait
echo "Y una vez más, después de que terminaron"
buy last

created=0; in_progress=0; others=0
for n in $(seq 1 "$parallel"); do
	case $(cat "$work/code-$n") in
		201) created=$((created + 1)); cp "$work/body-$n" "$work/original" ;;
		409) in_progress=$((in_progress + 1)) ;;
		*) others=$((others + 1)) ;;
	esac
done
echo "En paralelo: $created con el pedido (201), $in_progress en curso (409), $others con otra respuesta"

ok=true
# Todas las respuestas 201 tienen que ser el mismo pedido, byte por byte.
for n in $(seq 1 "$parallel") last; do
	if [ "$(cat "$work/code-$n")" = 201 ] && ! cmp -s "$work/body-$n" "$work/original"; then
		echo "La respuesta $n es un 201 con otro cuerpo"; ok=false
	fi
done
[ "$created" -ge 1 ] || { echo "Ninguna solicitud creó el pedido"; ok=false; }
[ "$others" -eq 0 ] || { echo "Hubo respuestas distintas de 201 y 409"; ok=false; }
[ "$(cat "$work/code-last")" = 201 ] && cmp -s "$work/body-last" "$work/original" \
	&& echo "La última recibió la misma respuesta que la original" \
	|| { echo "La última no recibió la misma respuesta que la original"; ok=false; }

# Una sola reserva: el stock bajó una unidad. Es la primera consulta del producto, así que se lee de MongoDB.
stock=$(curl -fsS "$catalog/api/products/$product_id" | sed -E 's/.*"stock":([0-9-]+).*/\1/')
echo "Stock: 5 al empezar, $stock al terminar (esperado: 4)"
[ "$stock" -eq 4 ] || ok=false
# Un solo cobro: una autorización y una captura en el historial del pago.
operations=$(grep -o '"operation":"[A-Z]*"' "$work/original" | paste -sd' ')
echo "Historial del pago: $operations"
[ "$operations" = '"operation":"AUTHORIZE" "operation":"CAPTURE"' ] || ok=false

if $ok; then echo "OK"; else echo "NO COINCIDE"; trap - EXIT; echo "Respuestas en $work"; exit 1; fi
