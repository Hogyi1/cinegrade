// [CONTAINS AI-GENERATED CODE: download, startDownload, statusOf, report, constants]
package hu.elte.ik.thesis.cinegrade.infra.social;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import hu.elte.ik.thesis.cinegrade.app.managers.presets.PresetManager;
import hu.elte.ik.thesis.cinegrade.app.managers.social.CineGradeApi;
import hu.elte.ik.thesis.cinegrade.app.managers.social.ShareManager;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.SocialException;
import hu.elte.ik.thesis.cinegrade.domain.requests.DownloadRequest;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.database.LastPathService;
import javafx.application.Platform;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import static hu.elte.ik.thesis.cinegrade.javafx.ui.catalog.SocialsController.SOCIALS_URL;

/**
 * Set as {@code window.cinegradeDesktop} on our pages. JS calls arrive on the FX thread.
 * The contract with the page is in {@code frontend/src/app/shared/desktop.ts}.
 */
public class DesktopBridge {

    private static final Logger logger = LogManager.getLogger(DesktopBridge.class);
    private static final Gson GSON = new Gson();

    // [AI-GENERATED] The only two kinds; anything else is refused, since kind ends up inside report()'s script
    private static final String LUT = "lut";
    private static final String CGEDIT = "cgedit";
    private static final FileChooser.ExtensionFilter LUT_FILES = new FileChooser.ExtensionFilter("LUT files", "*.cube");
    private static final FileChooser.ExtensionFilter CGEDIT_FILES =
            new FileChooser.ExtensionFilter("CineGrade edits", "*.cgedit");
    // [AI-GENERATED] The JSON shape of DesktopShareDraft / DesktopShareFile in frontend/src/app/shared/desktop.ts
    private static final String OCTET_STREAM = "application/octet-stream";
    private final WebView webView;
    private final LastPathService lastPathService;
    private final CineGradeApi api;
    private final ExecutorService ioPool;
    private final PresetManager presetManager;
    private final ShareManager shareManager;

    public DesktopBridge(WebView webView, CineGradeApi api, ExecutorService ioPool, PresetManager presetManager, ShareManager shareManager) {
        this.webView = webView;
        this.api = api;
        this.ioPool = ioPool;
        this.presetManager = presetManager;
        this.shareManager = shareManager;
        this.lastPathService = new LastPathService();
    }

    // [AI-GENERATED METHOD]

    // [AI-GENERATED METHOD]
    private static String statusOf(SocialException ex) {
        if (ex.isNotLoggedIn()) {
            return "signin";
        }
        return ex.getErrorCode() == ErrorCode.SOCIAL_OFFLINE ? "offline" : "failed";
    }

    // [AI-GENERATED METHOD]
    private static ShareFile shareFile(Path path, String name, String type) throws IOException {
        return new ShareFile(name, type, Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
    }

    // [AI-GENERATED METHOD]
    // "Teal & Orange #2" -> "teal-orange-2"; the extension is what the upload form checks, the rest is cosmetic
    private static String fileBase(String title) {
        String base = title == null ? "" : Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return base.isEmpty() ? "cinegrade" : base;
    }

    /**
     * The files the preset menu's Share exported, as JSON for the upload page (DesktopShareDraft in desktop.ts).
     * A String on purpose: a Gson JsonObject would reach JS as an unreadable Java handle. Null when there is no
     * finished draft or a file can't be read; the page then says so.
     */
    public String shareDraft() {
        ShareManager.ShareDraft draft = shareManager.getDraft();
        if (draft == null) {
            return null;
        }
        String base = fileBase(draft.title());
        try {
            ShareDraftJson json = new ShareDraftJson(
                    draft.title(),
                    draft.description(),
                    shareFile(draft.before(), "before.jpg", "image/jpeg"),
                    shareFile(draft.after(), "after.jpg", "image/jpeg"),
                    draft.lut() == null ? null : shareFile(draft.lut(), base + ".cube", OCTET_STREAM),
                    draft.cgedit() == null ? null : shareFile(draft.cgedit(), base + ".cgedit", OCTET_STREAM));
            return GSON.toJson(json);   // null fields are left out, which the page expects
        } catch (IOException ex) {
            logger.warn("Couldn't read the share draft's files", ex);
            return null;
        }
    }

    // [AI-GENERATED METHOD]
    /**
     * Publishes the current share draft: the page sends title, description and tags as JSON
     * (DesktopPublishRequest in desktop.ts), the files come from the draft. Java sends the request because the
     * WebView can't upload a FormData with files made in JavaScript. Answers with one 'publish' cg-desktop event.
     */
    public void publish(String json) {
        PublishRequest request;
        try {
            request = GSON.fromJson(json, PublishRequest.class);
        } catch (JsonParseException ex) {
            logger.warn("Ignoring a publish request that isn't valid JSON");
            reportPublish("failed", 0);
            return;
        }
        ShareManager.ShareDraft draft = shareManager.getDraft();
        if (request == null || request.title() == null || request.title().isBlank() || draft == null) {
            reportPublish("failed", 0);
            return;
        }
        ioPool.submit(() -> {
            try {
                int postId = api.createPost(draft, request.title().trim(), request.description(),
                        request.tags() == null ? List.of() : request.tags());
                reportPublish("done", postId);
            } catch (SocialException ex) {
                reportPublish(statusOf(ex), 0);
                if (!ex.isNotLoggedIn() && ex.getErrorCode() != ErrorCode.SOCIAL_OFFLINE) {
                    ErrorHandler.getInstance().handle(ex);
                }
            } catch (RuntimeException ex) {
                reportPublish("failed", 0);
                ErrorHandler.getInstance().handle(ex);
            }
        });
    }

    // [AI-GENERATED METHOD] status: done (with the new post's id) | signin | offline | failed
    private void reportPublish(String status, int postId) {
        Platform.runLater(() -> webView.getEngine().executeScript(
                "window.dispatchEvent(new CustomEvent('cg-desktop', {detail: {type: 'publish', status: '"
                        + status + "', postId: " + postId + "}}))"));
    }

    // [AI-GENERATED] Matches DesktopPublishRequest in frontend/src/app/shared/desktop.ts
    private record PublishRequest(String title, String description, List<String> tags) {
    }

    public void download(String json) {
        DownloadRequest request;
        try {
            request = GSON.fromJson(json, DownloadRequest.class);
        } catch (JsonParseException ex) {
            logger.warn("Ignoring a download request that isn't valid JSON: {}", json);
            return;
        }
        if (request == null || !(LUT.equals(request.kind()) || CGEDIT.equals(request.kind()))) {
            logger.warn("Ignoring a download request with an unknown kind: {}", json);
            return;
        }

        boolean lut = LUT.equals(request.kind());
        FileChooser chooser = new FileChooser();
        chooser.setTitle(lut ? "Save LUT" : "Save CineGrade edit");
        chooser.getExtensionFilters().add(lut ? LUT_FILES : CGEDIT_FILES);
        chooser.setInitialFileName(request.fileName());
        lastPathService.getDownloadLastFolder()
                .filter(Files::isDirectory)
                .ifPresent(folder -> chooser.setInitialDirectory(folder.toFile()));

        Window owner = webView.getScene() == null ? null : webView.getScene().getWindow();
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            report(request, "cancelled");
            return;
        }
        startDownload(request, file.toPath());
    }

    private void startDownload(DownloadRequest request, Path target) {
        ioPool.submit(() -> {
            try {
                api.download(request.postId(), request.kind(), target);
                lastPathService.setDownloadLastFolder(target.getParent());
                if (CGEDIT.equals(request.kind())) {
                    // Also straight into the preset panel, in the category written in the file
                    presetManager.importFile(target);
                }
                report(request, "done");
            } catch (SocialException ex) {
                report(request, statusOf(ex));
                if (!ex.isNotLoggedIn() && ex.getErrorCode() != ErrorCode.SOCIAL_OFFLINE) {
                    ErrorHandler.getInstance().handle(ex);
                }
            } catch (RuntimeException ex) {
                report(request, "failed");
                ErrorHandler.getInstance().handle(ex);
            }
        });
    }

    public void retry() {
        webView.getEngine().load(SOCIALS_URL);
    }

    public String version() {
        return "0";
    }

    // [AI-GENERATED METHOD] status: done | cancelled | signin | offline | failed
    private void report(DownloadRequest request, String status) {
        Platform.runLater(() -> webView.getEngine().executeScript(
                "window.dispatchEvent(new CustomEvent('cg-desktop', {detail: {type: 'download', postId: "
                        + request.postId() + ", kind: '" + request.kind() + "', status: '" + status + "'}}))"));
    }

    private record ShareFile(String name, String type, String base64) {
    }

    private record ShareDraftJson(String title, String description, ShareFile before, ShareFile after,
                                  ShareFile lut, ShareFile cgedit) {
    }
}
