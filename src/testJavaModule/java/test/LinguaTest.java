/*
 * Copyright © 2018-today Peter M. Stahl pemistahl@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.junit.jupiter.api.Test;

import static com.github.pemistahl.lingua.api.Language.ENGLISH;
import static com.github.pemistahl.lingua.api.Language.FRENCH;
import static com.github.pemistahl.lingua.api.Language.SPANISH;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests basic Lingua functionality. The main purpose of this test is to verify that the
 * packages can be accessed from a different module and that Lingua specifies all required
 * modules and can be used successfully.
 */
class LinguaTest {
    @Test
    void testDetector() {
        LanguageDetector detector = LanguageDetectorBuilder.fromLanguages(ENGLISH, FRENCH, SPANISH).build();
        Language detectedLanguage = detector.detectLanguageOf("languages are awesome");
        assertEquals(ENGLISH, detectedLanguage);
    }
}
