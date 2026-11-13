package hu.elte.ik.thesis.cinegrade.domain.requests.dto;

public record UserProfileResponse(String username, String email, Long avatarFileId) {
}
