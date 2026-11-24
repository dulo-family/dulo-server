package com.dulo.adapter.identity.signin;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionIdListener;
import jakarta.servlet.http.HttpSessionListener;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The open browser sessions by id, so a back-channel logout, which comes from the identity provider
 * and not from the browser, can end one. Registered with the servlet container as a listener.
 */
final class BrowserSessions implements HttpSessionListener, HttpSessionIdListener {

    private final Map<String, HttpSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void sessionCreated(final HttpSessionEvent event) {
        this.sessions.put(event.getSession().getId(), event.getSession());
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent event) {
        this.sessions.remove(event.getSession().getId());
    }

    @Override
    public void sessionIdChanged(final HttpSessionEvent event, final String oldSessionId) {
        this.sessions.remove(oldSessionId);
        this.sessions.put(event.getSession().getId(), event.getSession());
    }

    /** Ends the session with the given id, if it's still open. */
    void invalidate(final String sessionId) {
        Optional.ofNullable(this.sessions.remove(sessionId))
                .ifPresent(session -> {
                    try {
                        session.invalidate();
                    } catch (final IllegalStateException _) {
                        // It ended in the meantime.
                    }
                });
    }
}
