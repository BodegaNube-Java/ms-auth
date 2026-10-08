package cl.duoc.ms_auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Email String email,
        // BCrypt solo procesa los primeros 72 bytes, por eso el maximo
        @NotBlank @Size(min = 8, max = 72) String password
) {}