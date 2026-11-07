package hu.elte.ik.thesis.cinegrade.domain.listener;

public interface HistoryListener {
    void onHistory(String history, boolean isUndo);
}
