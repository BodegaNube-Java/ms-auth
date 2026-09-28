package cl.duoc.ms_auth.dto;

public record LoginResponse(
        String token,
        String tipo,
        long expiraEnSegundos
) {
    public static LoginResponse bearer(String token, long expiraEnSegundos) {
        return new LoginResponse(token, "Bearer", expiraEnSegundos);
    }
}