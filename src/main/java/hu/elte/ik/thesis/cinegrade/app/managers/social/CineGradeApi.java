package hu.elte.ik.thesis.cinegrade.app.managers.social;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.SocialException;
import hu.elte.ik.thesis.cinegrade.domain.requests.dto.AuthenticationResponse;
import hu.elte.ik.thesis.cinegrade.domain.requests.dto.UserProfileResponse;
import hu.elte.ik.thesis.cinegrade.infra.config.AppConfig;
import hu.elte.ik.thesis.cinegrade.infra.utils.file.FileUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The desktop's own calls to the web platform. The JWT lives only here, in memory; the refresh token lives
 * in the {@link PersistentCookieStore}. Every method blocks, so call them from a worker thread, never the FX thread.
 */
public class CineGradeApi implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(CineGradeApi.class);
    private static final Gson GSON = new Gson();

    private final String api = AppConfig.INSTANCE.getAppApi();
    private final PersistentCookieStore cookieStore;
    private final CloseableHttpClient client;
    private volatile String jwt;

    public CineGradeApi(PersistentCookieStore cookieStore) {
        this.cookieStore = cookieStore;
        // The cookie store is the only cookie jar; Apache's own would keep a second copy of refresh-token
        this.client = HttpClients.custom()
                .disableCookieManagement()
                .setUserAgent("CineGradeDesktop/1.0")
                .build();
    }

    // [AI-GENERATED METHOD]

    /**
     * Swaps the refresh token for a new JWT. A 401 means the session is dead (revoked, expired, password
     * changed): the stored session is cleared, which fires the logged-out event, and the call throws.
     *
     * @throws SocialException {@code SOCIAL_NOT_LOGGED_IN}, {@code SOCIAL_OFFLINE}, {@code SOCIAL_REQUEST_FAILED}
     *                         or {@code SOCIAL_BAD_RESPONSE}
     */
    public synchronized void refresh() {
        String refreshToken = cookieStore.getRefreshToken()
                .orElseThrow(() -> new SocialException(ErrorCode.SOCIAL_NOT_LOGGED_IN));

        HttpPost post = new HttpPost(api + "/auth/refresh");
        post.setHeader(HttpHeaders.COOKIE, PersistentCookieStore.REFRESH_TOKEN + "=" + refreshToken);

        try {
            jwt = client.execute(post, response -> {
                int status = response.getCode();
                if (status == 401) {
                    return null;
                }
                if (status != 200) {
                    throw new SocialException(ErrorCode.SOCIAL_REQUEST_FAILED, status, "/auth/refresh");
                }
                String body = response.getEntity() == null ? ""
                        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                AuthenticationResponse parsed = GSON.fromJson(body, AuthenticationResponse.class);
                if (parsed == null || parsed.getToken() == null || parsed.getToken().isBlank()) {
                    throw new SocialException(ErrorCode.SOCIAL_BAD_RESPONSE, "/auth/refresh");
                }
                return parsed.getToken();
            });
        } catch (IOException ex) {
            throw new SocialException(ErrorCode.SOCIAL_OFFLINE, ex, ex.getMessage());
        } catch (JsonParseException ex) {
            throw new SocialException(ErrorCode.SOCIAL_BAD_RESPONSE, ex, "/auth/refresh");
        }

        if (jwt == null) {
            logger.info("Refresh token rejected, clearing the stored session");
            cookieStore.removeAll();
            throw new SocialException(ErrorCode.SOCIAL_NOT_LOGGED_IN);
        }
    }

    public <T> T send(Supplier<ClassicHttpRequest> request, HttpClientResponseHandler<T> handler) {
        ClassicHttpRequest first = request.get();
        String path = first.getRequestUri();
        logger.info("Sending request to {}", path);
        try {
            if (jwt == null) {
                refresh();
            }
            T result = attempt(first, handler);
            if (result != null) {
                return result;
            }
            refresh();
            result = attempt(request.get(), handler);
            if (result != null) {
                return result;
            }
            throw new SocialException(ErrorCode.SOCIAL_NOT_LOGGED_IN);
        } catch (IOException ex) {
            throw new SocialException(ErrorCode.SOCIAL_OFFLINE, ex, ex.getMessage());
        } catch (JsonParseException ex) {
            throw new SocialException(ErrorCode.SOCIAL_BAD_RESPONSE, ex, path);
        }
    }

    private <T> T attempt(ClassicHttpRequest httpRequest, HttpClientResponseHandler<T> handler) throws IOException {
        httpRequest.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        return client.execute(httpRequest, response -> {
            int status = response.getCode();
            if (status == 401) {
                logger.debug("Request failed with 401, refreshing token");
                return null;
            }
            if (status < 200 || status >= 300) {
                throw new SocialException(ErrorCode.SOCIAL_REQUEST_FAILED, status, httpRequest.getRequestUri());
            }
            logger.debug("Request succeeded with status: {}", status);
            return handler.handleResponse(response);
        });
    }

    public UserProfileResponse me() {
        return send(() -> new HttpGet(api + "/users/me"), (response) -> {
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            UserProfileResponse profile = GSON.fromJson(body, UserProfileResponse.class);
            if (profile == null) {
                throw new SocialException(ErrorCode.SOCIAL_BAD_RESPONSE, "/users/me");
            }
            return profile;
        });
    }

    /**
     * Revokes the session on the server, then always signs out locally, even offline or when nobody was signed
     * in. Clearing the store fires the logged-out event.
     */
    public void logout() {
        Optional<String> refreshToken = cookieStore.getRefreshToken();
        try {
            if (refreshToken.isPresent()) {
                logger.info("Logging out user");
                // /auth/logout reads the cookie, not the JWT, so this doesn't go through send()
                HttpPost post = new HttpPost(api + "/auth/logout");
                post.setHeader(HttpHeaders.COOKIE, PersistentCookieStore.REFRESH_TOKEN + "=" + refreshToken.get());
                client.execute(post, response -> Boolean.TRUE);
            }
        } catch (IOException ex) {
            logger.warn("Couldn't revoke the session on the server, signing out locally only: {}", ex.getMessage());
        } finally {
            jwt = null;
            cookieStore.removeAll();
        }
    }

    public String downloadAvatar(long fileId, Path target) {
        if (fileId > 0) {
            logger.info("Downloading avatar for file ID: {}", fileId);
            if (!Files.exists(target)) {
                send(() -> new HttpGet(api + "/files/" + fileId), res -> {
                    try (InputStream in = res.getEntity().getContent()) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return target;
                });
            }
            return target.toString();
        }
        return "";
    }

    @Override
    public void close() throws IOException {
        jwt = null;
        client.close();
    }

    // [AI-GENERATED METHOD]
    /**
     * Downloads a post's LUT or .cgedit to {@code target}, overwriting it (the save dialog already asked).
     * The bytes go to {@code <target>.part} first and are moved over the target only once complete, so a failed
     * download never leaves a broken file or destroys the one that was there.
     *
     * @param kind {@code "lut"} or {@code "cgedit"}
     */
    public Path download(int postId, String kind, Path target) {
        logger.info("Downloading {} for post {}", kind, postId);
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try {
            return send(() -> new HttpGet(api + "/posts/" + postId + "/download/" + kind), res -> {
                try (InputStream in = res.getEntity().getContent()) {
                    Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                return target;
            });
        } finally {
            try {
                Files.deleteIfExists(part);   // only still there when something failed
            } catch (IOException ex) {
                logger.warn("Couldn't delete the unfinished download {}", part, ex);
            }
        }
    }

    // [AI-GENERATED METHOD]
    /**
     * POST /posts as multipart, the same fields as CreatePostRequest.java: title, description, tags (repeated),
     * before, after, lut, cgedit. Sent from Java because JavaFX's WebView can't send a FormData with files
     * made in JavaScript (the request never leaves the app).
     *
     * @return the new post's id
     */
    public int createPost(ShareManager.ShareDraft draft, String title, String description, List<String> tags) {
        ContentType text = ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8);   // accents in titles
        return send(() -> {
            MultipartEntityBuilder body = MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.EXTENDED)           // UTF-8 file names
                    .addTextBody("title", title, text)
                    .addBinaryBody("before", draft.before().toFile(), ContentType.IMAGE_JPEG, "before.jpg")
                    .addBinaryBody("after", draft.after().toFile(), ContentType.IMAGE_JPEG, "after.jpg");
            if (description != null && !description.isBlank()) {
                body.addTextBody("description", description, text);
            }
            for (String tag : tags) {
                body.addTextBody("tags", tag, text);
            }
            if (draft.lut() != null) {
                body.addBinaryBody("lut", draft.lut().toFile(), ContentType.APPLICATION_OCTET_STREAM, "preset.cube");
            }
            if (draft.cgedit() != null) {
                body.addBinaryBody("cgedit", draft.cgedit().toFile(), ContentType.APPLICATION_OCTET_STREAM, "preset.cgedit");
            }
            HttpPost post = new HttpPost(api + "/posts");
            post.setEntity(body.build());   // file parts can be sent twice, so send()'s retry after a 401 works
            return post;
        }, res -> {
            CreatedPost created = GSON.fromJson(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8), CreatedPost.class);
            if (created == null || created.id() == null) {
                throw new SocialException(ErrorCode.SOCIAL_BAD_RESPONSE, "/posts");
            }
            return created.id();
        });
    }

    // [AI-GENERATED] Only the id of the PostDetailResponse is needed, Gson skips the rest
    private record CreatedPost(Integer id) {
    }
}
