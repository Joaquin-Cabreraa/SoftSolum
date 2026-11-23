# API pública de UMnatural

API REST para que otras plataformas consulten la oferta de UMnatural.

- **Base:** `http://localhost:8080/api` (en producción, el dominio donde se publique la aplicación).
- **Formato:** todas las respuestas son JSON (UTF-8).
- **Autenticación:** no requiere. Es de solo lectura (todas las operaciones son `GET`) y devuelve únicamente datos públicos.
- **Código:** `controllers/ApiPublicaController.java` (endpoints), `services/ApiPublicaService.java` (lógica y conversión a DTO), carpeta `dto/` (lo que se devuelve).

Solo se muestran emprendimientos **aceptados** por la administración y actividades **publicadas** (de un emprendimiento aceptado y que no estén pausadas, eliminadas ni dadas de baja por el administrador). Las búsquedas por texto no distinguen mayúsculas ni tildes.

## Resumen

| # | Operación | Método y ruta |
| --- | --- | --- |
| 1 | Listado de todos los emprendimientos | `GET /api/emprendimientos` |
| 2 | Actividades de un día por departamento o localidad | `GET /api/actividades` |
| 3 | Top 3 actividades mejor puntuadas del mes por departamento | `GET /api/actividades/top` |
| 4 | Verificar emprendimiento | `GET /api/emprendimientos/{id}/verificar` |
| 5 | Buscar actividades por palabra clave | `GET /api/actividades/buscar` |

---

## 1. Listado de emprendimientos

`GET /api/emprendimientos`

Devuelve los emprendimientos aceptados, ordenados por nombre. No incluye RUT, documento ni datos personales del prestador.

**Ejemplo:** `GET /api/emprendimientos`

```json
[
  {
    "id": 3,
    "nombre": "Caballos del Este",
    "telefono": "099123456",
    "email": "contacto@caballosdeleste.uy",
    "ubicacion": "La Paloma, Rocha",
    "descripcion": "Cabalgatas por la costa rochense."
  }
]
```

---

## 2. Actividades de un día

`GET /api/actividades?fecha={fecha}&departamento={departamento}&localidad={localidad}`

| Parámetro | Obligatorio | Formato | Descripción |
| --- | --- | --- | --- |
| `fecha` | sí | `AAAA-MM-DD` | Día consultado |
| `departamento` | uno de los dos | texto | Ej.: `Rocha` |
| `localidad` | uno de los dos | texto | Ej.: `La Paloma` |

Hay que indicar el departamento, la localidad o ambos (si vienen los dos, tienen que coincidir los dos). Devuelve las actividades que tienen al menos un horario ese día (sin contar los cancelados ni los eliminados), con esos horarios, ordenadas por la hora del primero.

**Ejemplo:** `GET /api/actividades?fecha=2026-12-05&departamento=Rocha`

```json
[
  {
    "id": 12,
    "titulo": "Cabalgata al atardecer",
    "descripcion": "Recorrido de dos horas por la playa.",
    "departamento": "Rocha",
    "localidad": "La Paloma",
    "precio": 1500,
    "emprendimientoId": 3,
    "emprendimiento": "Caballos del Este",
    "promedio": 4.5,
    "cantidadValoraciones": 8,
    "horarios": [
      { "fecha": "2026-12-05", "horaInicio": "18:00:00", "horaFin": "20:00:00", "cuposDisponibles": 4 }
    ]
  }
]
```

`promedio` es `null` si la actividad todavía no tiene valoraciones.

---

## 3. Top 3 del mes

`GET /api/actividades/top?departamento={departamento}&mes={mes}`

| Parámetro | Obligatorio | Formato | Descripción |
| --- | --- | --- | --- |
| `departamento` | sí | texto | Ej.: `Rocha` |
| `mes` | no | `AAAA-MM` | Si no se indica, se usa el mes actual |

Las tres actividades publicadas del departamento con mejor promedio de valoraciones **hechas en ese mes** (no cuentan las reseñas ocultadas por el administrador). Desempate: más valoraciones en el mes y, después, la actividad más antigua. Una actividad sin valoraciones en el mes no entra. `promedio` y `cantidadValoraciones` son los del mes, y `horarios` viene vacío porque es un ranking.

**Ejemplo:** `GET /api/actividades/top?departamento=Rocha&mes=2026-11`

```json
[
  {
    "id": 12,
    "titulo": "Cabalgata al atardecer",
    "descripcion": "Recorrido de dos horas por la playa.",
    "departamento": "Rocha",
    "localidad": "La Paloma",
    "precio": 1500,
    "emprendimientoId": 3,
    "emprendimiento": "Caballos del Este",
    "promedio": 4.8,
    "cantidadValoraciones": 5,
    "horarios": []
  }
]
```

---

## 4. Verificar emprendimiento

`GET /api/emprendimientos/{id}/verificar`

Indica si existe un emprendimiento con ese id y si está verificado (aceptado por la administración). El nombre solo se muestra si está verificado. Que no exista no es un error: se responde `existe: false`.

**Ejemplo:** `GET /api/emprendimientos/3/verificar`

```json
{ "id": 3, "existe": true, "verificado": true, "nombre": "Caballos del Este" }
```

**Ejemplo con un id inexistente:** `GET /api/emprendimientos/999/verificar`

```json
{ "id": 999, "existe": false, "verificado": false, "nombre": null }
```

---

## 5. Buscar actividades

`GET /api/actividades/buscar?q={palabra}`

| Parámetro | Obligatorio | Descripción |
| --- | --- | --- |
| `q` | sí | Palabra clave |

Busca entre las actividades publicadas en el título, la descripción, el emprendimiento, el departamento, la localidad y las categorías. Cada actividad trae sus próximos horarios con lugar disponible. Mismo formato que la operación 2.

**Ejemplo:** `GET /api/actividades/buscar?q=cabalgata`

---

## Errores

| Código | Cuándo | Cuerpo |
| --- | --- | --- |
| `200` | Consulta correcta (una lista vacía también es una respuesta correcta) | El JSON de la operación |
| `400` | Falta un parámetro obligatorio, tiene un formato inválido, o no se indicó departamento ni localidad | `{ "error": "Indique un departamento o una localidad" }` |
| `500` | Error inesperado del servidor | Error estándar de Spring Boot |

Mensajes de error posibles: `Falta la fecha (formato AAAA-MM-DD)`, `Indique un departamento o una localidad`, `Indique un departamento`, `Indique una palabra clave`. Si el parámetro directamente no se envía o tiene un formato inválido (por ejemplo `fecha=05/12/2026`), Spring responde `400` con su cuerpo de error estándar.

## Cómo probarla

Con la aplicación corriendo, abrir en el navegador (o con `curl`):

```
http://localhost:8080/api/emprendimientos
http://localhost:8080/api/actividades?fecha=2026-12-05&departamento=Rocha
http://localhost:8080/api/actividades/top?departamento=Rocha
http://localhost:8080/api/emprendimientos/1/verificar
http://localhost:8080/api/actividades/buscar?q=cabalgata
```

Las pruebas unitarias de esta API están en `src/test/java/.../services/ApiPublicaServiceTest.java`.
