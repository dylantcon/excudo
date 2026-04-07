package com.excudo.console;

import com.excudo.core.commands.CommandSessionManager;
import com.excudo.core.orchestration.SessionManager;
import com.excudo.core.orchestration.PresentationMetadata;

/**
 * Adapts SessionManager.ManagedSession to the CommandSessionManager.SessionInfo interface.
 * Bridges the orchestration layer's session representation to the command layer's contract.
 */
public class SessionInfoAdapter implements CommandSessionManager.SessionInfo {

    private final String sessionId;
    private final String filename;
    private final boolean active;
    private final long creationTime;
    private final int slideCount;
    private final String title;

    public SessionInfoAdapter(SessionManager.ManagedSession session) {
        this.sessionId = session.getSessionId();
        this.filename = session.hasOriginalFile() ? session.getOriginalFile().getName() : null;
        this.active = true;
        this.creationTime = session.getLastAccessed();

        int slides = 0;
        String sessionTitle = null;
        try {
            PresentationMetadata metadata = session.getOrchestrator().getPresentationMetadata();
            if (metadata != null) {
                slides = metadata.getSlideCount();
                sessionTitle = metadata.getTitle();
            }
        } catch (Exception e) {
            // Metadata unavailable -- leave defaults
        }
        this.slideCount = slides;
        this.title = sessionTitle;
    }

    @Override public String getSessionId() { return sessionId; }
    @Override public String getFilename() { return filename; }
    @Override public boolean isActive() { return active; }
    @Override public long getCreationTime() { return creationTime; }
    @Override public int getSlideCount() { return slideCount; }
    @Override public String getTitle() { return title; }
}
