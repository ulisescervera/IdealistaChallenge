# ADR 0004 — El sheet de filtros solo filtra sobre datos que el listado ya tiene

- **Estado:** aceptada
- **Fecha:** 2026-09-13
- **Contexto del enunciado:** *"En el listado/mapa añade un botón arriba a la derecha, que abra
  un sheet donde se puedan establecer filtros para todas las propiedades de un inmueble"* —
  seguido de una lista exhaustiva de campos: tipo de vivienda, compra/alquiler, precio, tamaño,
  habitaciones, baños, certificado energético, estado, ascensor y garaje.

## Decisión

El sheet de filtros (`FiltersSheetFragment`, modelo `PropertyFilters`) muestra los diez campos
que pide el enunciado, sin excepción. Pero solo ocho de ellos cambian de verdad lo que se ve en el
listado: tipo, operación, precio, tamaño, habitaciones, baños, ascensor y garaje. Los otros dos —
**certificado energético** y **estado** — se recogen en la interfaz y no filtran nada.

## Razonamiento

`PropertyFilters.matches(property: Property)` opera sobre `Property`, el modelo del listado. Ese
modelo es exactamente lo que la Regla 2 de este documento exige que sea: lo que la base de datos
ya tiene cacheado, disponible antes de que la red responda. Y `Property` no lleva ni
`energyCertification` ni `status` — esos dos campos existen únicamente en `PropertyDetail`, que se
descarga por inmueble, uno a uno, la primera vez que se abre su ficha.

Para que el sheet filtrara de verdad por esos dos campos, el listado tendría que conocerlos para
*cada* inmueble visible antes de poder decidir qué fila mostrar. Eso significa una de dos cosas,
ambas peores que el problema que resuelven:

1. **Descargar el detalle de todos los inmuebles del feed** antes de aplicar cualquier filtro —
   exactamente el comportamiento "listo antes de que la red responda" que el repositorio
   offline-first existe para evitar, multiplicado por N inmuebles en cada apertura del listado.
2. **Cambiar el endpoint de listado** para que devuelva esos dos campos por adelantado — no es una
   opción: el payload lo define el desafío de Idealista, no esta app.

Ninguna de las dos entra en el alcance de "añadir un sheet de filtros".

## Por qué las casillas siguen ahí, en vez de desaparecer

El enunciado las pide explícitamente, con las mismas opciones exactas (A+, A, B, C, D, E, F para
el certificado; nuevo, buen estado, para reformar para el estado). Quitarlas habría sido más
"honesto" en un sentido estricto, pero también habría sido ignorar una petición explícita sin
decirlo. La alternativa elegida es la contraria: dejarlas, y documentar con toda claridad —en el
comentario de clase de `PropertyFilters`, en el layout XML y aquí— que hoy no tienen efecto. Un
comentario que dice "esto no hace nada, y por qué" es mejor que un campo que calla su propia
limitación.

## Consecuencias

**A favor**

- El listado sigue renderizando desde caché sin ningún fetch adicional al abrir el sheet o al
  aplicar un filtro: la promesa del repositorio offline-first no se rompe para dar cabida a estos
  dos campos.
- Los ocho campos restantes son completamente funcionales y cubren la mayoría de los casos de uso
  reales de un filtro de listado (tipo, operación, precio, tamaño, habitaciones, baños, ascensor,
  garaje).
- La limitación está documentada en tres sitios (clase, layout, este ADR), así que ningún futuro
  cambio la redescubre por sorpresa.

**En contra, y asumido**

- Un usuario puede marcar "certificado A" y "para reformar" y ver inmuebles que no cumplen ninguno
  de los dos, sin que la UI se lo impida activamente. Se acepta porque el comentario en el propio
  layout XML explica el porqué, y porque impedirlo (deshabilitando las casillas) sería más
  confuso que dejarlas visibles y documentar el límite.

## Alternativas descartadas

**Quitar los dos campos del sheet.** Habría sido ignorar una parte explícita del enunciado sin
decir nada al respecto.

**Descargar el detalle de cada inmueble visible para poder filtrar por estos dos campos.** Rompe
el offline-first del repositorio y multiplica las llamadas de red por el tamaño del feed, por dos
campos que un desafío de alcance limitado no necesita resolver hoy.

**Deshabilitar (grisar) esas casillas en vez de dejarlas activas.** Más "correcto" en apariencia,
pero peor en la práctica: un control que no se puede tocar sin explicación visible es más
confuso que uno que se puede marcar y que, documentado, no hace nada todavía.
