package com.github.pemistahl.lingua.app.multilanguage

import com.github.pemistahl.lingua.api.LanguageDetector

internal interface DetectionProgressListener {
    fun detectionStarted() {
        // Do nothing by default
    }

    fun detectionFinished(sections: List<LanguageDetector.LanguageSection>)
}
