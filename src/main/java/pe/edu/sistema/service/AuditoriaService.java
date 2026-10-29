package pe.edu.sistema.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import pe.edu.sistema.model.RegistroAuditoria;
import pe.edu.sistema.model.Usuario;
import pe.edu.sistema.util.ConexionBD;

/**
 * Servicio: auditoría de acciones del personal y de los accesos (dbo.auditoria o memoria).
 * Registrar nunca lanza excepciones: si falla la base de datos, guarda el registro en memoria.
 */
public final class AuditoriaService {

    /** Registros que se conservan en memoria (los más antiguos se descartan). */
    public static final int MAX_MEMORIA = 1000;

    // Longitudes de las columnas de dbo.auditoria
    private static final int MAX_USUARIO = 40;
    private static final int MAX_ROL = 30;
    private static final int MAX_ACCION = 40;
    private static final int MAX_ENTIDAD = 80;
    private static final int MAX_DETALLE = 400;
    private static final int MAX_IP = 45;

    /** Años que admite el filtro por fecha: fuera de este rango el límite no restringe o no puede coincidir. */
    private static final int ANIO_MINIMO = 1900;
    private static final int ANIO_MAXIMO = 9000;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm");

    private static final String SQL_REGISTRAR =
            "INSERT INTO dbo.auditoria (username, rol, accion, entidad, detalle, ip) VALUES (?, ?, ?, ?, ?, ?)";

    private static final AuditoriaService INSTANCIA = new AuditoriaService();

    /** Registro en memoria junto con su fecha exacta (para filtrar por rango). */
    private record EntradaMemoria(RegistroAuditoria registro, LocalDateTime fecha) {
    }

    private final ArrayDeque<EntradaMemoria> memoria = new ArrayDeque<>();
    private int ultimoIdMemoria;

    private AuditoriaService() {
    }

    public static AuditoriaService getInstancia() {
        return INSTANCIA;
    }

    /** Registra una acción de un usuario con sesión (acción: constantes de {@link RegistroAuditoria}). */
    public void registrar(Usuario actor, String accion, String entidad, String detalle, String ip) {
        registrar(actor == null ? null : actor.getUsername(), actor == null ? null : actor.getRol(),
                accion, entidad, detalle, ip);
    }

    /** Variante para accesos sin usuario autenticado (p. ej. intento fallido con el nombre escrito). */
    public void registrar(String username, String rol, String accion, String entidad, String detalle, String ip) {
        try {
            String u = recortar(valorOpcional(username, "anónimo"), MAX_USUARIO);
            String r = recortar(valorOpcional(rol, null), MAX_ROL);
            String a = recortar(valorOpcional(accion, "Sin acción"), MAX_ACCION);
            String e = recortar(valorOpcional(entidad, null), MAX_ENTIDAD);
            String d = recortar(valorOpcional(detalle, null), MAX_DETALLE);
            String i = recortar(valorOpcional(ip, null), MAX_IP);
            if (ConexionBD.estaDisponible() && registrarEnBD(u, r, a, e, d, i)) {
                return;
            }
            registrarEnMemoria(u, r, a, e, d, i);
        } catch (RuntimeException ex) {
            // Auditar nunca debe interrumpir la operación que se está auditando
        }
    }

    /**
     * Registros del más reciente al más antiguo. Filtros opcionales (null o vacío = sin filtro):
     * usuario exacto sin distinguir mayúsculas, acción exacta y rango de fechas inclusivo.
     *
     * @throws IllegalStateException si SQL Server está activo pero la consulta falla (no se mezcla con la memoria)
     */
    public List<RegistroAuditoria> listar(String usuario, String accion, LocalDate desde, LocalDate hasta, int limite) {
        List<RegistroAuditoria> resultado = new ArrayList<>();
        if (limite < 1) {
            return resultado;
        }
        // Fechas extremas: un límite fuera de rango deja de filtrar, o no puede coincidir con ningún registro
        if ((desde != null && desde.getYear() > ANIO_MAXIMO) || (hasta != null && hasta.getYear() < ANIO_MINIMO)) {
            return resultado;
        }
        if (desde != null && desde.getYear() < ANIO_MINIMO) {
            desde = null;
        }
        if (hasta != null && hasta.getYear() > ANIO_MAXIMO) {
            hasta = null;
        }
        String u = usuario == null ? "" : usuario.trim();
        String a = accion == null ? "" : accion.trim();
        if (ConexionBD.estaDisponible()) {
            List<RegistroAuditoria> deBD = listarDeBD(u, a, desde, hasta, limite);
            if (deBD != null) {
                return deBD;
            }
            if (ConexionBD.estaDisponible()) {
                throw new IllegalStateException("No se pudo consultar la auditoría en BD_NEXORA.");
            }
        }
        synchronized (memoria) {
            // Se recorre del final (más reciente) al principio
            Iterator<EntradaMemoria> it = memoria.descendingIterator();
            while (it.hasNext() && resultado.size() < limite) {
                EntradaMemoria entrada = it.next();
                RegistroAuditoria r = entrada.registro();
                if (!u.isEmpty() && !u.equalsIgnoreCase(r.getUsuario())) {
                    continue;
                }
                if (!a.isEmpty() && !a.equals(r.getAccion())) {
                    continue;
                }
                LocalDate dia = entrada.fecha().toLocalDate();
                if ((desde != null && dia.isBefore(desde)) || (hasta != null && dia.isAfter(hasta))) {
                    continue;
                }
                resultado.add(copiar(r));
            }
        }
        return resultado;
    }

    // ---------------------------------------------------------------- persistencia

    /** @return true si el registro quedó en la base de datos */
    private boolean registrarEnBD(String username, String rol, String accion, String entidad, String detalle, String ip) {
        try (Connection cn = ConexionBD.obtenerConexion(); PreparedStatement ps = cn.prepareStatement(SQL_REGISTRAR)) {
            ps.setString(1, username);
            ps.setString(2, rol);
            ps.setString(3, accion);
            ps.setString(4, entidad);
            ps.setString(5, detalle);
            ps.setString(6, ip);
            ps.executeUpdate();
            return true;
        } catch (SQLException ex) {
            ConexionBD.probarConexion();
            return false;
        }
    }

    private void registrarEnMemoria(String username, String rol, String accion, String entidad, String detalle,
            String ip) {
        synchronized (memoria) {
            LocalDateTime ahora = LocalDateTime.now();
            RegistroAuditoria r = new RegistroAuditoria();
            r.setIdAuditoria(++ultimoIdMemoria);
            r.setUsuario(username);
            r.setRol(rol);
            r.setAccion(accion);
            r.setEntidad(entidad);
            r.setDetalle(detalle);
            r.setIp(ip);
            r.setFecha(ahora.format(FORMATO_FECHA));
            memoria.addLast(new EntradaMemoria(r, ahora));
            while (memoria.size() > MAX_MEMORIA) {
                memoria.removeFirst();
            }
        }
    }

    /** @return los registros, o null si la consulta falló (se usa entonces la memoria) */
    private List<RegistroAuditoria> listarDeBD(String usuario, String accion, LocalDate desde, LocalDate hasta,
            int limite) {
        StringBuilder sql = new StringBuilder(
                "SELECT TOP (?) id_auditoria, username, rol, accion, entidad, detalle, ip, fecha "
                + "FROM dbo.auditoria WHERE 1 = 1 ");
        if (!usuario.isEmpty()) {
            sql.append("AND LOWER(username) = LOWER(?) ");
        }
        if (!accion.isEmpty()) {
            sql.append("AND accion = ? ");
        }
        if (desde != null) {
            sql.append("AND fecha >= ? ");
        }
        if (hasta != null) {
            sql.append("AND fecha < ? ");
        }
        sql.append("ORDER BY fecha DESC, id_auditoria DESC");
        try (Connection cn = ConexionBD.obtenerConexion(); PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            int n = 1;
            ps.setInt(n++, limite);
            if (!usuario.isEmpty()) {
                ps.setString(n++, usuario);
            }
            if (!accion.isEmpty()) {
                ps.setString(n++, accion);
            }
            if (desde != null) {
                ps.setTimestamp(n++, Timestamp.valueOf(desde.atStartOfDay()));
            }
            if (hasta != null) {
                ps.setTimestamp(n++, Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            }
            List<RegistroAuditoria> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RegistroAuditoria r = new RegistroAuditoria();
                    r.setIdAuditoria(rs.getInt("id_auditoria"));
                    r.setUsuario(rs.getString("username"));
                    r.setRol(rs.getString("rol"));
                    r.setAccion(rs.getString("accion"));
                    r.setEntidad(rs.getString("entidad"));
                    r.setDetalle(rs.getString("detalle"));
                    r.setIp(rs.getString("ip"));
                    r.setFecha(rs.getTimestamp("fecha").toLocalDateTime().format(FORMATO_FECHA));
                    lista.add(r);
                }
            }
            return lista;
        } catch (SQLException ex) {
            ConexionBD.probarConexion();
            return null;
        }
    }

    // ---------------------------------------------------------------- auxiliares

    private static RegistroAuditoria copiar(RegistroAuditoria origen) {
        RegistroAuditoria r = new RegistroAuditoria();
        r.setIdAuditoria(origen.getIdAuditoria());
        r.setUsuario(origen.getUsuario());
        r.setRol(origen.getRol());
        r.setAccion(origen.getAccion());
        r.setEntidad(origen.getEntidad());
        r.setDetalle(origen.getDetalle());
        r.setIp(origen.getIp());
        r.setFecha(origen.getFecha());
        return r;
    }

    /** Texto sin espacios sobrantes; si queda vacío, el valor alterno (que puede ser null). */
    private static String valorOpcional(String texto, String alterno) {
        if (texto == null || texto.isBlank()) {
            return alterno;
        }
        return texto.trim();
    }

    private static String recortar(String texto, int maximo) {
        return texto != null && texto.length() > maximo ? texto.substring(0, maximo) : texto;
    }
}
