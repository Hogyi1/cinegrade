package hu.elte.ik.thesis.cinegrade.domain.requests.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationRequest {
    private String login;
    private String password;
}
