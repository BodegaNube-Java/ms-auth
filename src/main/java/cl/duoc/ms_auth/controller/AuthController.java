package cl.duoc.ms_auth.controller;

import cl.duoc.ms_auth.dto.LoginRequest;
import cl.duoc.ms_auth.dto.LoginResponse;
import cl.duoc.ms_auth.dto.RegistroRequest;
import cl.duoc.ms_auth.dto.UsuarioResponse;
import cl.duoc.ms_auth.exception.CredencialesInvalidasException;
import cl.duoc.ms_auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> obtenerUsuarioActual(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {

        String token = extraerToken(authorizationHeader);
        return ResponseEntity.ok(authService.obtenerUsuarioActual(token));
    }

    private String extraerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(PREFIJO_BEARER)) {
            throw CredencialesInvalidasException.tokenInvalido("Header Authorization mal formado");
        }
        return authorizationHeader.substring(PREFIJO_BEARER.length());
    }

    @PostMapping("/registro")
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(request));
    }
}