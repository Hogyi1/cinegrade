package hu.elte.ik.thesis.cinegrade.app.managers.social;

import java.net.HttpCookie;

public interface CookieListener {
    void onCookieChanged(HttpCookie cookie);
}
