package cl.duoc.ms_auth.exception;

import java.util.UUID;

public class CredencialesInvalidasException extends RuntimeException {

    private CredencialesInvalidasException(String motivoInterno) {
        super(motivoInterno);
    }

    public static CredencialesInvalidasException emailNoRegistrado(String email) {
        return new CredencialesInvalidasException("Email no registrado: " + email);
    }

    public static CredencialesInvalidasException usuarioInactivo(UUID usuarioId) {
        return new CredencialesInvalidasException("Usuario inactivo: " + usuarioId);
    }

    public static CredencialesInvalidasException passwordIncorrecta(UUID usuarioId) {
        return new CredencialesInvalidasException("Password incorrecta: " + usuarioId);
    }

    
    public static CredencialesInvalidasException tokenInvalido(String detalle) {
        return new CredencialesInvalidasException("Token invalido: " + detalle);
    }

    public static CredencialesInvalidasException usuarioNoExiste(UUID usuarioId) {
        return new CredencialesInvalidasException("Usuario del token no existe: " + usuarioId);
    }
}
