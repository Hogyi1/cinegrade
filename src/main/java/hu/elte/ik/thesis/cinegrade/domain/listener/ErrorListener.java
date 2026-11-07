package hu.elte.ik.thesis.cinegrade.domain.listener;

import hu.elte.ik.thesis.cinegrade.domain.error.ErrorHandler;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;

public interface ErrorListener {
    void onError(CineGradeException exception, ErrorHandler.ErrorDisplayMode displayMode);
}
