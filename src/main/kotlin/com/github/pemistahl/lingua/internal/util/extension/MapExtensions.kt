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

package com.github.pemistahl.lingua.internal.util.extension

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.*

internal fun <T> Object2IntOpenHashMap<T>.incrementCounter(key: T) {
    this.addTo(key, 1)
}

internal fun <E : Enum<E>> setOfEnum(first: E, vararg others: E): Set<E> {
    return if (others.isNotEmpty()) EnumSet.of(first, *others) else Collections.singleton(first)

}
