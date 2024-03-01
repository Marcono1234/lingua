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

module com.github.pemistahl.lingua {
    exports com.github.pemistahl.lingua.api;

    // 'transitive' because Lingua API might expose Kotlin stdlib types
    // Note: This causes a spurious warning about "automatic module", even though it has an explicit
    // module descriptor, see https://bugs.openjdk.org/browse/JDK-8235229
    requires transitive kotlin.stdlib;
    requires it.unimi.dsi.fastutil;

    // Optional; used by multi-language detection GUI
    requires static java.desktop;
}
