package uy.edu.um.proytic2026.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.proytic2026.dto.ActividadPublicaDto;
import uy.edu.um.proytic2026.dto.EmprendimientoPublicoDto;
import uy.edu.um.proytic2026.dto.VerificacionDto;
import uy.edu.um.proytic2026.services.ApiPublicaService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

// API publica de UMnatural: la usan otras plataformas, sin login (no esta bajo /panel ni /admin).
// Es un @RestController: lo que devuelve cada metodo se convierte a JSON.
@RestController
@RequestMapping("/api")
public class ApiPublicaController {

    @Autowired
    private ApiPublicaService apiPublicaService;

    // 1) GET /api/emprendimientos
    @GetMapping("/emprendimientos")
    public List<EmprendimientoPublicoDto> listarEmprendimientos() {
        return apiPublicaService.listarEmprendimientos();
    }

    // 2) GET /api/actividades?fecha=2026-12-01&departamento=Rocha&localidad=La Paloma
    // La fecha es obligatoria (si falta o tiene mal el formato, Spring responde 400).
    // Hay que indicar departamento o localidad (o ambos).
    @GetMapping("/actividades")
    public List<ActividadPublicaDto> actividadesDelDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) String departamento,
            @RequestParam(required = false) String localidad) {
        return apiPublicaService.actividadesDelDia(fecha, departamento, localidad);
    }

    // 3) GET /api/actividades/top?departamento=Rocha&mes=2026-11
    // El departamento es obligatorio. El mes es opcional (formato AAAA-MM); si falta, se usa el mes actual.
    @GetMapping("/actividades/top")
    public List<ActividadPublicaDto> top3DelMes(
            @RequestParam String departamento,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return apiPublicaService.top3DelMes(departamento, mes);
    }

    // 4) GET /api/emprendimientos/5/verificar
    // Responde si el emprendimiento existe y si esta verificado (aceptado). Si no existe, existe=false.
    @GetMapping("/emprendimientos/{id}/verificar")
    public VerificacionDto verificarEmprendimiento(@PathVariable Long id) {
        return apiPublicaService.verificarEmprendimiento(id);
    }

    // 5) GET /api/actividades/buscar?q=cabalgata
    // La palabra clave es obligatoria. Devuelve las actividades publicadas que la contienen.
    @GetMapping("/actividades/buscar")
    public List<ActividadPublicaDto> buscarActividades(@RequestParam String q) {
        return apiPublicaService.buscarActividades(q);
    }

    // Si el service rechaza el pedido (por ejemplo, no se indico departamento ni localidad),
    // se responde 400 con un JSON que explica el motivo, en vez de un error 500.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> pedidoInvalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
