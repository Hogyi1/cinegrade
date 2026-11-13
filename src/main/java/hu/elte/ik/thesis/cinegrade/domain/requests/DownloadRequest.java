package hu.elte.ik.thesis.cinegrade.domain.requests;

public record DownloadRequest(int postId, String kind, String fileName) {
}
