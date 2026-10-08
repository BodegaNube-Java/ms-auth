package cl.duoc.ms_auth.exception;

public class EmailYaRegistradoException extends RuntimeException {
    public EmailYaRegistradoException(String email) {
        super("Intento de registro con email existente: " + email);
    }
}