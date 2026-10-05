package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.*;
import uy.edu.um.proytic2026.repository.*;
// Los ".*" traen todas las clases de esa carpeta de una vez.

@Service
public class PrestadorService {

    @Autowired
    private UsuarioRepository usuarioRepository;
    // Para chequear que el email no esté usado por NINGÚN usuario.

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private ParticularRepository particularRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    // El mismo encriptador de contraseñas que en UsuarioService.

    // ---------- Registro de EMPRESA ----------
    public boolean altaEmpresa(String razonSocial, String rut, String email, String telefono,
                               String password, String nombreEmprendimiento, String direccion) {

        if (vacio(razonSocial) || vacio(rut) || vacio(email) || vacio(password) || vacio(nombreEmprendimiento)) {
            return false;
        }
        // Si falta algún dato obligatorio, no se registra.

        if (usuarioRepository.existsByUserEmail(email)) {
            return false;
        }
        // Si el email ya lo tiene otro usuario, no se registra.

        if (empresaRepository.existsByRut(rut)) {
            return false;
        }
        // Si ya hay una empresa con ese RUT, no se registra (RNE #10).

        Empresa empresa = new Empresa();
        // Crea una empresa vacía (usa el constructor vacío).

        empresa.setName(razonSocial);
        empresa.setRut(rut);
        empresa.setUserName(email);       // usamos el email como nombre de usuario
        empresa.setUserEmail(email);
        empresa.setTelefono(telefono);
        empresa.setPasswordHash(passwordEncoder.encode(password));
        // Le carga los datos, con la contraseña encriptada.

        empresa.setEmprendimiento(crearEmprendimiento(nombreEmprendimiento, direccion));
        // Le asocia su emprendimiento nuevo (en estado PENDIENTE).

        empresaRepository.save(empresa);
        // Guarda la empresa. Por el "cascade", también guarda el emprendimiento.

        return true;
    }

    // ---------- Registro de PARTICULAR ----------
    public boolean altaParticular(String nombre, String apellido, String documento, String email,
                                  String telefono, String password, String nombreEmprendimiento, String direccion) {

        if (vacio(nombre) || vacio(apellido) || vacio(documento) || vacio(email)
                || vacio(password) || vacio(nombreEmprendimiento)) {
            return false;
        }

        if (usuarioRepository.existsByUserEmail(email)) {
            return false;
        }

        if (particularRepository.existsByDocumento(documento)) {
            return false;
        }
        // Si ya hay un prestador con esa CI, no se registra (RNE #13).

        Particular particular = new Particular();
        particular.setName(nombre);
        particular.setLastName(apellido);
        particular.setDocumento(documento);
        particular.setUserName(email);
        particular.setUserEmail(email);
        particular.setTelefono(telefono);
        particular.setPasswordHash(passwordEncoder.encode(password));
        particular.setEmprendimiento(crearEmprendimiento(nombreEmprendimiento, direccion));

        particularRepository.save(particular);
        return true;
    }

    // ---------- Métodos de ayuda (private = solo se usan acá adentro) ----------

    private Emprendimiento crearEmprendimiento(String nombre, String direccion) {
        Emprendimiento emp = new Emprendimiento();
        emp.setNombre(nombre);
        emp.setDireccion(direccion);
        emp.setEstado(EstadoEmprendimiento.PENDIENTE);
        // Todo emprendimiento nuevo arranca PENDIENTE hasta que el admin lo acepte (RNE #8).
        return emp;
    }

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
        // true si el texto no vino (null) o vino vacío/con espacios.
    }
}