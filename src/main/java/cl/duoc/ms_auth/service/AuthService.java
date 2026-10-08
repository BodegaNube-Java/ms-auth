package cl.duoc.ms_auth.service;

import cl.duoc.ms_auth.dto.LoginRequest;
import cl.duoc.ms_auth.dto.LoginResponse;
import cl.duoc.ms_auth.dto.RegistroRequest;
import cl.duoc.ms_auth.dto.UsuarioResponse;
import cl.duoc.ms_auth.exception.CredencialesInvalidasException;
import cl.duoc.ms_auth.exception.EmailYaRegistradoException;
import cl.duoc.ms_auth.model.Rol;
import cl.duoc.ms_auth.model.Usuario;
import cl.duoc.ms_auth.repository.UsuarioRepository;
import cl.duoc.ms_auth.security.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> CredencialesInvalidasException.emailNoRegistrado(request.email()));

        if (Boolean.FALSE.equals(usuario.getActivo())) {
            throw CredencialesInvalidasException.usuarioInactivo(usuario.getId());
        }

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw CredencialesInvalidasException.passwordIncorrecta(usuario.getId());
        }

        String token = jwtProvider.generarToken(usuario);
        return LoginResponse.bearer(token, jwtProvider.getExpiracionSegundos());
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtenerUsuarioActual(String token) {
        Claims claims;
        try {
            claims = jwtProvider.validarToken(token);
        } catch (Exception ex) {
            throw CredencialesInvalidasException.tokenInvalido(ex.getMessage());
        }

        UUID usuarioId = UUID.fromString(claims.getSubject());

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> CredencialesInvalidasException.usuarioNoExiste(usuarioId));

        return UsuarioResponse.desde(usuario);
    }

    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        String email = request.email().trim().toLowerCase();

        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailYaRegistradoException(email);
        }

        Usuario usuario = Usuario.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(Rol.COMERCIO) // nunca viene del cliente
                .comercioId(UUID.randomUUID()) // lo asigna el servidor
                .activo(true)
                .build();

        try {
            return UsuarioResponse.desde(usuarioRepository.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException e) {
            // dos registros simultaneos con el mismo email: gana la restriccion UNIQUE
            throw new EmailYaRegistradoException(email);
        }
    }
}
