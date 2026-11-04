#!/usr/bin/env bash
# Carga de lecturas repetidas sobre un mismo producto, para comparar la latencia con y sin caché (CA-11).
#
# Uso, desde la raíz del repositorio y con el sistema levantado:
#   scripts/carga-lecturas.sh                                  # con caché
#   SPRING_CACHE_TYPE=none docker compose up -d catalog-service
#   scripts/carga-lecturas.sh                                  # sin caché
#   docker compose up -d catalog-service                       # vuelve a la caché
#
# Necesita curl y ab (Apache Bench). Se usa ab y no autocannon porque, en el entorno de desarrollo, autocannon 7 y 8
# informaban unos 40 ms de latencia donde ab y un cliente secuencial medían menos de 1 ms. Ese valor tampoco cerraba
# con su propio caudal: 10 conexiones a 11.500 solicitudes por segundo dan 0,9 ms por solicitud.
#
# Variables opcionales: CATALOG_URL (http://localhost:8082), CONCURRENCY (10), REQUESTS por ronda (50000), ROUNDS (3)
# y WARMUP, en segundos (15).
set -euo pipefail

url=${CATALOG_URL:-http://localhost:8082}
concurrency=${CONCURRENCY:-10}
requests=${REQUESTS:-50000}
rounds=${ROUNDS:-3}
warmup=${WARMUP:-15}

command -v ab > /dev/null || { echo "Falta ab (Apache Bench)." >&2; exit 1; }

# Un producto nuevo en cada corrida, para no depender de datos anteriores. El SKU lleva la hora para no repetirse.
response=$(curl -fsS -X POST "$url/api/products" -H 'Content-Type: application/json' \
	-d '{"sku":"CARGA-'"$(date +%s%N)"'","name":"Producto de la prueba de carga","price":100,"stock":1000}')
product_id=$(printf '%s' "$response" | sed -E 's/.*"id":"([^"]+)".*/\1/')
target="$url/api/products/$product_id"

# Carga previa que se descarta. Recién reiniciado, el servicio responde más lento hasta que la JVM optimiza el código:
# con un calentamiento de 5000 solicitudes, la primera corrida salía entre 2 y 3 veces más lenta que las siguientes.
# La corrida sin caché siempre empieza con el servicio recién reiniciado, así que sin esto saldría peor por un motivo
# ajeno a la caché. El -n va después del -t para que el límite sea el tiempo y no las 50000 solicitudes que -t fija.
echo "Calentando: $warmup s sobre $product_id"
ab -k -q -c "$concurrency" -t "$warmup" -n 100000000 "$target" > /dev/null 2>&1

# Varias rondas porque una sola varía bastante de una corrida a otra; se informa la mediana del tiempo medio.
echo "Midiendo: $rounds rondas de $requests lecturas, $concurrency concurrentes"
means=()
for round in $(seq 1 "$rounds"); do
	# Con set -e, una ronda fallida cortaría el script antes de mostrarla: el estado se guarda y se revisa después.
	status=0
	summary=$(ab -k -q -c "$concurrency" -n "$requests" "$target" | awk -v round="$round" '
		/^Failed requests:/ { failed = $3 }
		/^Non-2xx responses:/ { non2xx = $3 }
		/^Requests per second:/ { rps = $4 }
		/^Time per request:.*\(mean\)$/ { mean = $4 }
		/^  50%/ { p50 = $2 }
		/^  99%/ { p99 = $2 }
		END {
			if (non2xx == "") non2xx = 0
			printf "%s ronda %d: %8.0f por segundo, tiempo medio %s ms, el 50%% en %s ms o menos, el 99%% en %s ms", mean, round, rps, mean, p50, p99
			if (failed != 0 || non2xx != 0) printf " (fallidas: %s, sin 2xx: %s)", failed, non2xx
			printf "\n"
			if (failed != 0 || non2xx != 0) exit 1
		}') || status=$?
	echo "  ${summary#* }"
	[ "$status" -eq 0 ] || exit 1
	means+=("${summary%% *}")
done
median=$(printf '%s\n' "${means[@]}" | sort -g | awk '{ v[NR] = $1 } END { print (NR % 2 ? v[(NR + 1) / 2] : (v[NR / 2] + v[NR / 2 + 1]) / 2) }')
echo "Mediana del tiempo medio: $median ms"
