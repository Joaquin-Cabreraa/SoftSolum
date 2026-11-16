package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.EmprendimientoRepository;
import uy.edu.um.proytic2026.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

// "Mis datos": cada usuario modifica sus propios datos y su contraseña.
// - Turista: nombre, apellido, documento, fecha de nacimiento, correo y celular.
// - Prestador: sus datos de contacto y los de su emprendimiento.
//   El RUT (empresa) y la cédula (particular) NO se cambian: son lo que verificó el
//   administrador al aceptar el emprendimiento. Si están mal, se pide al administrador.
@Service
public class PerfilService {

    private static final String CELULAR = "09[1-9][0-9]{6}";
    private static final String TELEFONO = "09[1-9][0-9]{6}|[24][0-9]{7}";   // celular o fijo
    private static final String EMAIL = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EmprendimientoRepository emprendimientoRepository;
    @Autowired private uy.edu.um.proytic2026.repository.TuristaRepository turistaRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional(readOnly = true)
    public Usuario usuario(Long id) {
        return id == null ? null : usuarioRepository.findById(id).orElse(null);
    }

    // ---------- Turista ----------

    // Devuelve el mensaje de error, o null si se guardó
    @Transactional
    public String actualizarTurista(Long turistaId, String nombre, String apellido, String documento,
                                    String fechaNacimiento, String email, String telefono) {
        if (!(usuario(turistaId) instanceof Turista turista)) {
            return "No encontramos tu cuenta.";
        }
        if (vacio(nombre) || vacio(apellido) || vacio(documento) || vacio(fechaNacimiento)
                || vacio(email) || vacio(telefono)) {
            return "Completá todos los campos.";
        }
        String doc = Documentos.limpiar(documento);
        String errorDocumento = Documentos.validar(documento);
        if (errorDocumento != null) {
            return errorDocumento;
        }
        if (turistaRepository.existsByDocumentoIgnoreCaseAndIdNot(doc, turistaId)) {
            return "Ya existe otra cuenta registrada con ese documento.";
        }
        LocalDate nacimiento = fecha(fechaNacimiento);
        if (nacimiento == null || nacimiento.isAfter(LocalDate.now())) {
            return "La fecha de nacimiento no es válida.";
        }
        String error = validarContacto(turista, email, telefono, CELULAR);
        if (error != null) return error;

        turista.setName(nombre.trim());
        turista.setLastName(apellido.trim());
        turista.setDocumento(doc);
        turista.setFechaNacimiento(nacimiento);
        cambiarContacto(turista, email, telefono);
        usuarioRepository.save(turista);
        return null;
    }

    // ---------- Prestador ----------

    // Datos de la persona o empresa. En una empresa el "nombre" es la razón social.
    @Transactional
    public String actualizarPrestador(Long prestadorId, String nombre, String apellido, String email, String telefono) {
        if (!(usuario(prestadorId) instanceof Prestador prestador)) {
            return "No encontramos tu cuenta.";
        }
        boolean esParticular = prestador instanceof Particular;
        if (vacio(nombre) || (esParticular && vacio(apellido)) || vacio(email) || vacio(telefono)) {
            return "Completá todos los campos.";
        }
        String error = validarContacto(prestador, email, telefono, TELEFONO);
        if (error != null) return error;

        prestador.setName(nombre.trim());
        if (esParticular) prestador.setLastName(apellido.trim());
        cambiarContacto(prestador, email, telefono);
        usuarioRepository.save(prestador);
        return null;
    }

    @Transactional
    public String actualizarEmprendimiento(Long prestadorId, String nombre, String ubicacion, String email,
                                           String telefono, String descripcion) {
        if (!(usuario(prestadorId) instanceof Prestador prestador) || prestador.getEmprendimiento() == null) {
            return "No tenés un emprendimiento asociado.";
        }
        if (vacio(nombre) || vacio(ubicacion) || vacio(email)) {
            return "El nombre, la dirección y el correo del emprendimiento son obligatorios.";
        }
        if (!email.trim().matches(EMAIL)) {
            return "El correo del emprendimiento no tiene un formato válido.";
        }
        String tel = vacio(telefono) ? null : telefono.replaceAll("[\\s-]", "");
        if (tel != null && !tel.matches(TELEFONO)) {
            return "El teléfono del emprendimiento no es válido (celular 09XXXXXXX o fijo de 8 dígitos).";
        }
        if (descripcion != null && descripcion.length() > 255) {
            return "La descripción puede tener hasta 255 caracteres.";
        }

        Emprendimiento emp = prestador.getEmprendimiento();
        emp.setNombre(nombre.trim());
        emp.setUbicacion(ubicacion.trim());
        emp.setEmail(email.trim().toLowerCase());
        emp.setTelefono(tel);
        emp.setDescripcion(vacio(descripcion) ? null : descripcion.trim());
        emprendimientoRepository.save(emp);
        return null;
    }

    // ---------- Contraseña (todos) ----------

    @Transactional
    public String cambiarContrasena(Long usuarioId, String actual, String nueva, String confirmacion) {
        Usuario usuario = usuario(usuarioId);
        if (usuario == null) return "No encontramos tu cuenta.";
        if (vacio(actual) || !passwordEncoder.matches(actual, usuario.getPasswordHash())) {
            return "La contraseña actual no es correcta.";
        }
        if (nueva == null || nueva.length() < 8) {
            return "La nueva contraseña debe tener al menos 8 caracteres.";
        }
        if (!nueva.equals(confirmacion)) {
            return "Las contraseñas nuevas no coinciden.";
        }
        usuario.setPasswordHash(passwordEncoder.encode(nueva));
        usuarioRepository.save(usuario);
        return null;
    }

    // ---------- Ayuda ----------

    private String validarContacto(Usuario usuario, String email, String telefono, String formatoTelefono) {
        String correo = email.trim().toLowerCase();
        if (!correo.matches(EMAIL)) {
            return "El correo electrónico no tiene un formato válido.";
        }
        // Si cambió el correo, que no lo use otra cuenta (el correo es el usuario para entrar)
        boolean cambio = !correo.equalsIgnoreCase(usuario.getUserEmail());
        if (cambio && usuarioRepository.existsByUserEmailIgnoreCase(correo)) {
            return "Ya existe otra cuenta con ese correo electrónico.";
        }
        if (!telefono.replaceAll("[\\s-]", "").matches(formatoTelefono)) {
            return "Ingresá un teléfono válido, por ejemplo 099123456.";
        }
        return null;
    }

    private void cambiarContacto(Usuario usuario, String email, String telefono) {
        String correo = email.trim().toLowerCase();
        usuario.setUserEmail(correo);
        usuario.setUserName(correo);   // el usuario para entrar es el correo
        usuario.setTelefono(telefono.replaceAll("[\\s-]", ""));
    }

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private LocalDate fecha(String texto) {
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
