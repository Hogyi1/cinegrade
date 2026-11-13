package hu.elte.ik.thesis.cinegrade.app.managers.user;

import hu.elte.ik.thesis.cinegrade.app.managers.social.CineGradeApi;
import hu.elte.ik.thesis.cinegrade.app.managers.social.CookieListener;
import hu.elte.ik.thesis.cinegrade.app.managers.social.PersistentCookieStore;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.domain.requests.dto.UserProfileResponse;
import hu.elte.ik.thesis.cinegrade.domain.user.User;
import hu.elte.ik.thesis.cinegrade.infra.config.AppConfig;
import hu.elte.ik.thesis.cinegrade.infra.database.TransactionRunner;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.database.UserService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class UserManager implements CookieListener {

    private static final Logger logger = LogManager.getLogger(UserManager.class);
    private final UserService userService;
    private final ObjectProperty<User> activeUser = new SimpleObjectProperty<>();
    private final CineGradeApi api;
    private final String apiBaseUrl = AppConfig.INSTANCE.getAppApi();
    private final ExecutorService ioPool;
    private final PersistentCookieStore cookieStore;

    public UserManager(PersistentCookieStore cookieStore, CineGradeApi api, ExecutorService ioPool) {
        this.userService = new UserService();
        this.api = api;
        this.ioPool = ioPool;
        this.cookieStore = cookieStore;
        refreshUser();
        if (cookieStore.getRefreshTokenId().isPresent()) {
            loadProfile();
        }
    }

    public void refreshUser() {
        Optional<User> userOpt = userService.getActiveUser();
        activeUser.set(userOpt.orElse(null));
        logger.debug("User set to {}", activeUser.get());
    }

    public boolean isLoggedIn() {
        return activeUser.get() != null;
    }

    public String getUsername() {
        User user = activeUser.get();
        String userName = user != null ? user.getUsername() : "";
        logger.debug("Username: {}", userName);
        return userName;
    }

    // [AI-GENERATED METHOD]
    /**
     * The downloaded avatar of the signed-in user, or null (not signed in, no avatar, or the file is gone);
     * the caller shows the default picture then. Read from the user in memory: the database row is written
     * after activeUser changes, so a lookup there would still see the previous user.
     */
    public Path getAvatarPath() {
        User user = activeUser.get();
        if (user == null || user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            return null;
        }
        Path avatar = Path.of(user.getAvatarUrl());
        return Files.isRegularFile(avatar) ? avatar : null;
    }

    public ReadOnlyObjectProperty<User> currentUserProperty() {
        return activeUser;
    }

    public void logout() {
        ioPool.submit(api::logout);
        activeUser.set(null);
    }

    public void changeUser(User user) {
        if (user != null) {
            activeUser.set(user);
            userService.insertUser(user);
        } else {
            throw new CineGradeException(ErrorCode.UNKNOWN_ERROR, "Cannot change to a null user.");
        }
    }

    @Override
    public void onCookieChanged(HttpCookie cookie) {
        if (cookie == null) {
            activeUser.set(null);
        }
        ioPool.submit(this::loadProfile);
    }

    private void loadProfile() {
        UserProfileResponse response = api.me();
        int tokenId = cookieStore.getRefreshTokenId().orElseThrow(() -> new CineGradeException(ErrorCode.SOCIAL_NOT_LOGGED_IN));
        Long fileId = response.avatarFileId();
        Path target = Path.of(AppConfig.INSTANCE.getTempDirectory(), "avatar-" + fileId + ".jpg");
        String avatarUrl = api.downloadAvatar(fileId, target);
        User u = new User(0, response.username(), response.email(), avatarUrl, tokenId, null, null);
        Platform.runLater(() -> changeUser(u));
    }
}
