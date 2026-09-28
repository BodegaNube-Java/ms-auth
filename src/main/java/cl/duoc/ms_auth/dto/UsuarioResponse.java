package cl.duoc.ms_auth.dto;

import cl.duoc.ms_auth.model.Rol;
import cl.duoc.ms_auth.model.Usuario;

import java.util.UUID;

//sin pass hash
public record UsuarioResponse(
        UUID id,
        String email,
        Rol rol,
        UUID comercioId
) {
    public static UsuarioResponse desde(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.getComercioId()
        );
    }
}