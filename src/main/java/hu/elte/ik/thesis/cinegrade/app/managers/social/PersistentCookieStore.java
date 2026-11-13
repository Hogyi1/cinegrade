// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.app.managers.social;

import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.domain.token.RefreshToken;
import hu.elte.ik.thesis.cinegrade.infra.database.dao.token.RefreshTokenDao;

import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The cookie jar shared by the Socials WebView (installed as {@code CookieHandler.getDefault()}) and the
 * desktop's own API calls. Cookies live in the JDK's in-memory store, which does the domain, path and expiry
 * matching; only the {@code refresh-token} cookie is also written to the app database, so a login made in the
 * WebView survives a restart.
 */
public class PersistentCookieStore implements CookieStore {

    public static final String REFRESH_TOKEN = "refresh-token";
    // Same lifetime as RefreshCookieFactory on the backend (sliding, renewed on every refresh)
    private static final Duration REFRESH_MAX_AGE = Duration.ofDays(14);

    private final CookieStore delegate = new CookieManager().getCookieStore();
    private final URI api;
    private final RefreshTokenDao dao;
    private final List<CookieListener> cookieListeners = new CopyOnWriteArrayList<>();
    // guarded by this
    private RefreshToken current;

    public PersistentCookieStore(URI api, RefreshTokenDao dao) throws SQLException {
        this.api = api;
        this.dao = dao;
        current = dao.load().orElse(null);
        if (current != null && current.getRefreshToken() != null) {
            delegate.add(api, cookie(current.getRefreshToken()));
        }
    }

    public void registerListener(CookieListener listener) {
        cookieListeners.add(listener);
    }

    /** For listeners that live shorter than the app, like a view's controller. */
    public void removeListener(CookieListener listener) {
        cookieListeners.remove(listener);
    }

    /**
     * Runs on the caller's thread, which is the FX thread for logins made in the WebView, so listeners
     * must move network calls to a worker thread themselves. {@code cookie == null} means logged out.
     */
    public void notifyCookieChanged(HttpCookie cookie) {
        cookieListeners.forEach(listener -> listener.onCookieChanged(cookie));
    }

    @Override
    public void add(URI uri, HttpCookie cookie) {
        delegate.add(uri, cookie);
        if (!REFRESH_TOKEN.equals(cookie.getName()) || !isApi(uri)) {
            return;
        }

        String raw = (cookie.hasExpired() || cookie.getValue() == null || cookie.getValue().isEmpty()) ? null : cookie.getValue();

        boolean changed;
        synchronized (this) {
            changed = !Objects.equals(raw, current == null ? null : current.getRefreshToken());
            try {
                if (changed && raw == null) {
                    dao.deleteToken();
                    current = null;
                } else if (changed) {
                    current = dao.save(raw).orElse(null);
                } else if (current != null && current.getId() != 0) {
                    Instant now = Instant.now();
                    dao.updateLastUsedAt(current.getId(), now);
                    current.setLastUsedAt(now);
                }
            } catch (SQLException ex) {
                current = raw == null ? null : RefreshToken.builder().refreshToken(raw).build();
                ErrorHandler.getInstance().handle(ex);
            }
        }

        if (changed) {
            notifyCookieChanged(raw == null ? null : cookie);
        }
    }

    private boolean isApi(URI uri) {
        return uri != null && api.getHost().equalsIgnoreCase(uri.getHost());
    }

    @Override
    public List<HttpCookie> get(URI uri) {
        return delegate.get(uri);
    }

    @Override
    public List<HttpCookie> getCookies() {
        return delegate.getCookies();
    }

    @Override
    public List<URI> getURIs() {
        return delegate.getURIs();
    }

    @Override
    public boolean remove(URI uri, HttpCookie cookie) {
        return delegate.remove(uri, cookie);
    }

    @Override
    public boolean removeAll() {
        boolean removed = delegate.removeAll();
        boolean hadSession;
        synchronized (this) {
            hadSession = current != null;
            if (hadSession) {
                current = null;
                try {
                    dao.deleteToken();
                } catch (SQLException ex) {
                    ErrorHandler.getInstance().handle(ex);
                }
            }
        }
        // Outside the lock, and only when someone was logged in, like add()
        if (hadSession) {
            notifyCookieChanged(null);
        }
        return removed;
    }

    // The cookie as the backend sets it, rebuilt from the stored value
    // The cookie as the backend sets it (RefreshCookieFactory), rebuilt from the stored value
    private HttpCookie cookie(String raw) {
        HttpCookie c = new HttpCookie(REFRESH_TOKEN, raw);
        c.setVersion(0);                          // plain "refresh-token=…" header; version 1 adds $Version junk
        c.setDomain(api.getHost());
        c.setPath(api.getPath() + "/auth");       // /api/v1/auth
        c.setSecure("https".equals(api.getScheme()));
        c.setHttpOnly(true);
        c.setMaxAge(REFRESH_MAX_AGE.toSeconds());
        return c;
    }

    /** The raw refresh token for the {@code Cookie} header of the desktop's own calls; empty when logged out. */
    public synchronized Optional<String> getRefreshToken() {
        return Optional.ofNullable(current).map(RefreshToken::getRefreshToken);
    }

    /** The {@code refresh_tokens} row the {@code user_session} row has to point at; empty when logged out or not saved. */
    public synchronized OptionalInt getRefreshTokenId() {
        return current == null || current.getId() == 0 ? OptionalInt.empty() : OptionalInt.of(current.getId());
    }
}
