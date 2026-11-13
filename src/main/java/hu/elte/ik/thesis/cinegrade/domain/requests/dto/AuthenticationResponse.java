package hu.elte.ik.thesis.cinegrade.domain.requests.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor // Jackson 3 (Spring Boot 4) üres konstruktorral hozza létre a JSON-ból
@AllArgsConstructor
public class AuthenticationResponse {
	private String token;
}
