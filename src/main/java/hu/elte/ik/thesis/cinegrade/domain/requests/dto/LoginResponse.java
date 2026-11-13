package hu.elte.ik.thesis.cinegrade.domain.requests.dto;

public record LoginResponse(String jwt, String refreshToken) {
}
