# Performance optimizations

The following lists considered and implemented performance optimizations for the language detection process, in-memory
models and model files, compared to the original Lingua implementation. Some of these optimizations might also
positively affect frequency lookup time (and therefore language detection speed) or reduce the amount of memory
'garbage' produced during lookup, while others might negatively affect the lookup to gain improvements in other areas.

:warning: The performance differences for these optimizations have not been properly tested. Some of these optimizations
might be "premature", and not actually necessary. Additionally, optimizations in one area might negatively affect
performance in another area. For example a more compact in-memory model might increase lookup time. In general these
optimizations also make the code more complex, which in turn makes it more difficult to maintain.

## Language detection

Optimizations implemented or considered for the process of language detection.

### Implemented

#### Word splitting with custom word list class

As part of language detection Lingua first splits up the input text in words. The original Lingua implementation
does this by simply creating substrings from the input text. Depending on the length of the input text this can
create quite a lot of temporary substring objects. To avoid this a custom `WordList` class can be used which for
each word just stores the offset from the last word and the length of the word. That class can then provide a
custom iteration function which provides a single `CharSequence` instance which is reused for each word. This way
nearly all allocations for word splitting can be avoided.

#### Primitive ngram encoding with inline class

During language detection the text is split in ngrams. To avoid creating a large number of `String` objects,
the ngram can be encoded as primitive and a [Kotlin inline class](https://kotlinlang.org/docs/inline-classes.html)
can be used to provide abstraction over it and provide convenience functions. A `Long` can be used to encode all
uni-, bi- and trigrams. For quadri- and fivegrams it would still be necessary to use a `String` object. However,
Lingua only [uses trigrams for larger texts](https://github.com/pemistahl/lingua/blob/4ca58ead2d2ce6bad6d85574b7efd5b3ca29aa4b/src/main/kotlin/com/github/pemistahl/lingua/api/LanguageDetector.kt#L147)
so only short texts would be affected, where the effect of using `String` objects might not be that big.

#### Object ngram encoding with reused instance

For ngrams which cannot be [encoded as primitive type](#primitive-ngram-encoding-with-inline-class) a `String`
or `CharSequence` has to be used. To avoid creating large amounts of temporary substrings at runtime when
sub-grams (ngrams of shorter length) are derived, a single object storing the ngram in a `Char` dropped is used.
`array[0]` encodes the length of the ngram, `array[1]`, `array[2]`, ... `array[5]` encode the characters of the
ngram. When a sub-ngram is then derived, its encoded length in `array[0]` is simply decremented.

## In-memory model

Optimizations implemented or considered for the in-memory model. This refers to the runtime representation of ngram
frequencies for a language stored in memory (RAM).

Some of these optimizations also apply to the model file if the in-memory model is written in the same format to
the file.

### Implemented

#### Primitive ngram encoding

Since ngrams are grouped by their size (unigram, bigram, trigram, quadrigram, fivegram) they have a known fixed
size. This can be used to encode them using JVM primitives, e.g. a bigram could be encoded as an `Int` consisting
of the 16 bit of the first char and the 16 bit of the second char. Performing operations on primitives is most
likely faster and more efficient than comparing `String` objects, especially for some of the other techniques
mentioned here, e.g. usage of a [sorted array map structure](#sorted-array-map-structure).

##### Compressed primitive encoding

Since Java 9 `String` stores the characters in a byte array, which allows it to require less memory if all
characters can be encoded with ISO-8859-1/Latin-1, see [JEP 254: Compact Strings](https://openjdk.java.net/jeps/254).
Therefore, encoding a `String` as primitive by merely combining its `Char` values can be inefficient memory-wise. Instead,
based on the `Char` values different encoding formats can be used:

- unigram: `Byte` or `Char`
- bigram: `Short` or `Int`
- trigram: `Int` or `Long`
- quadrigram: `Int` or `Long`
- fivegram: `Int`, `Long` or `String`

Except for fivegrams it is guaranteed that all other ngrams can be encoded as primitives. And if combined
with the other approaches it is possible to reduce the number of fivegrams which need to be encoded as `String`
to a small number.

In theory for some ngrams smaller primitive types might work as well, e.g. when using the offset based encoding
described below it would be reasonable to encode a trigram and quadrigram as `Short`. However, during testing
the memory reduction by this was only very small. The reason for this might be that some other optimizations,
such as a [trie structure for keys](#trie-like-data-structure-for-int-to-float-map), or
[shared frequency values](#shared-frequency-values) work better for an `Int` key map.

##### Offset based encoding 

Often languages use mostly (or only) characters from specific scripts. For example the Greek script starts at `U+0370`.
This can be leveraged to encode offsets instead of the absolute `Char` value, which allows using small primitive types
for encoding.

One possible implementation for this is to store per language a sorted `Char` array containing all chars used by that
language. Binary search can then be used to determine during frequency lookup if a char exists in the models for
a language, and obtain the relative offset. The relative offset can either be the index in the char array, or
alternatively an additional `Short` array of the same size containing the relative offsets for the respective chars
can be used. That additional array has the advantage that offsets can be assigned arbitrarily instead of based
on the char index in the sorted array. This allows assigning smaller indices (starting at 0) for commonly used chars,
which makes it more likely that the offset for all chars of an ngram can be encoded in a small primitive type.

This approach can be cleaner than a global [letter index map](#letter-index-map), but might be slightly slower
during lookup due to the additional binary search per char. On the other hand, lookup can fail fast if one of
the chars if not used by the language (and therefore that ngram does not exist either), without having to
perform a lookup on the frequency map.

Note: The original approach of using a `Char` array was dropped and replaced with a fastutil `Char2Short...HashMap`.
The memory overhead of this really small (< 1 MB), but it makes language detection faster.

#### Sorted array map structure

The most compact way to store [primitive encoded ngrams](#primitive-ngram-encoding) is to use a sorted array map
structure, where one sorted primitive array represents the ngram keys, and another array (for example an
`Int` array, see [frequency encoding](#frequency-encoding)) contains the frequency value for each ngram.
This implementation is based on fvasco's proof of concept described in [this comment](https://github.com/pemistahl/lingua/issues/101#issuecomment-1086563136).

In theory this can make lookup slower because a binary search has to be performed, compared to O(1) for
a hash map in the best case. However, it is likely that the loops in `Arrays.binarySearch` are handled
efficiently by the JVM (or the JIT compiler); though the actual performance difference has to be tested, also
taking [trimmed hash maps with high load factor](#trimmed-fastutil-hash-maps) into account, which will
make collisions in the hash map more likely.

#### Shared frequency values

Often multiple ngrams share the same frequency (see also [model files grouping by frequency](#ngrams-grouped-by-frequency)),
it can therefore be wasteful to repeat the value multiple times for different ngrams. When using a
[sorted array map](#sorted-array-map-structure) as data structure (and possibly for other data
structures as well), an indirect value lookup can be used to share frequency values between ngrams.

For example, if frequencies are stored with 32 bit, a separate value index `UShort` (= 16 bit) array
can be added. For an ngram based on its index in the key array the corresponding value from the
value index array is obtained. That value index is then used to obtain the actual value from the
values array. This way more than one ngram can refer to the same value by using the same value index.
Only 65536 (= number of values representable by a `UShort`) value indices can be used, however since
multiple ngrams can share one value index this means that a large number of ngrams can share frequencies.
After all value indices are assigned, subsequent ngrams directly access the frequency from the
values array by subtracting from their key index the number of ngrams which use shared frequencies
and adding 65536 (number of shared values).

If too few ngrams share the same frequency for it to cause any memory reduction, the value index
array is kept empty and all ngrams directly access values from the values array. This means the
memory overhead for this approach is in the worst case (when no frequencies are shared) an empty
`UShort` array.

This implementation makes mostly sense for maps with `Int` or `Long` key (maybe also for maps with `Short` key);
for all other maps with smaller key type it is unlikely that enough shared frequencies exist that
an indirect lookup would be more efficient.

The disadvantage of this approach is that lookup might become slightly slower, and that the
creation and handling of the value index array adds additional complexity.

#### Trie-like data structure for `Int`-to-`Float` map

Ngrams often share common prefixes, whether encoded as `Char` array or with a [primitive encoding](#primitive-ngram-encoding).
This can be leveraged to reduce memory usage by encoding ngrams in a trie-like structure where common prefixes
are not repeated. When primitive encoding is used, the effectiveness depends on how the primitive encoding is
performed, and how likely common prefixes are.

Most likely it only makes sense to use shared prefixes for the first few bytes and store the remainder
as separate primitive (e.g. as `Short`). Otherwise the management data overhead for the trie in the lower layers will
most likely negate any memory reductions. Additionally, when using multidimensional arrays the overhead for the nested
arrays has to be considered. Even if the theoretical (or model file size) decreases, due to the overhead for every
array instance, the runtime memory usage might still be the same or even increase.

Currently, only for `Int`-to-`Float` maps a trie-like structure is implemented, see also the section
[trie-like data structure for other maps](#trie-like-data-structure-for-other-maps).

The implementation has separate layers for the first and the second byte of the `Int`, the remainder is stored
as `Short`. The mapping from the trie-like separated key to the frequency value is implemented by storing the
remainder `Short` of the key in a single array which has the size of the complete count of ngrams. After determining
the first byte and second byte indices using binary search, it has to be determined in which range of the remainder
array the remainder should be searched (remainders are only sorted in the range for the ngram they belong to). The
start index can be estimated based on the first byte and second byte indices, combined with a separate array storing
the absolute start index in the remainder array for each first byte. An additional _search data array_ is used to encode
the actual range data (start and length) in the remainder array. The search data consist of an offset (positive or negative)
from the estimated index as well as the length. The remainder of the key is then searched in that range of the remainder
array. The obtained global index is then used to look up the frequency value, as usual. This also allows to still
support [shared frequency values](#shared-frequency-values).

If at any step of the lookup (first byte, second byte or remainder search) no index was found, it means that
the ngram is not contained in the model.

#### Conditional quadrigram and fivegram model loading

Lingua's original implementation only [considers the trigram models for long texts](https://github.com/pemistahl/lingua/blob/4ca58ead2d2ce6bad6d85574b7efd5b3ca29aa4b/src/main/kotlin/com/github/pemistahl/lingua/api/LanguageDetector.kt#L147).
If a user knows in advance that all (or the vast majority) of the text snippets provided to Lingua will
have a certain minimum length, e.g. in cleaned up form >= 120, then it would be reasonable to not load
all models. Most importantly, quadri- and fivegram models, which take up the majority of the memory,
can be omitted.

A new method can be added to `LanguageDetectorBuilder` which ensures that quadri- and fivegram models
will never be loaded. This includes:
- not loading them when `withPreloadedLanguageModels()` is used
- making sure that `LanguageDetector` in no situation tries to access them, even when shorter
  texts are provided for detection

While this might (severely) decrease accuracy for short texts, it has no effect on longer texts because
it is identical to Lingua's current behavior for these longer texts.

This was also [implemented in Lingua version 1.2.0](https://github.com/pemistahl/lingua/commit/a845fe49fd54c43e3138419c08e535d3fc14c136).

### Not implemented

#### Trimmed fastutil hash maps

When using fastutil's `OpenHashMap` implementations, memory usage can be reduced by increasing the load factor,
at the cost of higher lookup and construction costs, and by calling `trim()` to make the map as small as possible.

This was implemented originally, but was dropped when a [sorted array map structure](#sorted-array-map-structure)
was used instead to store the frequencies. The disadvantage of using fastutil collections is also, that there
is currently no way to efficiently reconstruct them from binary data. They only support Java serialization, which
is inefficient due to overhead for class structure representation and dangerous due to being able to load
arbitrary classes. Though, these map types at least allow specifying an expected map size for their constructor.

#### Trie-like data structure for other maps

Ngrams often share common prefixes, whether encoded as `Char` array or with a [primitive encoding](#primitive-ngram-encoding).
This can be leveraged to reduce memory usage by encoding ngrams in a trie-like structure where common prefixes
are not repeated. When primitive encoding is used, the effectiveness depends on how the primitive encoding is
performed, and how likely common prefixes are.

Most likely it only makes sense to use shared prefixes for the first few bytes and store the remainder
as separate primitive (e.g. as `Short`). Otherwise the management data overhead for the trie in the lower layers will
most likely negate any memory reductions. Additionally, when using multidimensional arrays the overhead for the nested
arrays has to be considered. Even if the theoretical (or model file size) decreases, due to the overhead for every
array instance, the runtime memory usage might still be the same or even increase.

Currently, only a trie-like encoding is implemented for [`Int`-to-`Float` maps](#trie-like-data-structure-for-int-to-float-map)
because those take up the majority of memory. Other map types either contain too few entries at the moment to be
worth the additional complexity and memory overhead to implement a trie, or they cannot be split very well into
smaller primitive types. For example, encoding the first two bytes of a 64-bit `Long` would have a 48-bit remainder.
The remainder would therefore have to be split again, possibly introducing another trie layer, or having two separate arrays
to store the parts of the remainder. And then it would be necessary to map to the frequency value in some way.
Because `Long`-to-`Float` maps currently don't have that many entries, it is most likely not worth it to implement this
yet.

For `Short`-to-`Float` maps it might be reasonable to implement them as trie, but it appears for them the overhead
of mapping to the corresponding frequency value negates most of the saved memory, see also
[compressed primitive encoding](#compressed-primitive-encoding).

#### Letter index map

As part of pre-processing Lingua converts text to lowercase and removes punctuation. Therefore, only a subset of all
Unicode characters can occur in language models. To leverage this, a global letter index map could be created which
maps a `Char` to its letter index (assigned in increasing order). Non-letters and uppercase letters would have no index. Then for each
language, or even for each ngram type, a base index could be encoded, which is the lowest letter index of the used
chars of a language. Combined with [primitive encoding](#primitive-ngram-encoding) this allows encoding ngrams as
smaller primitive types. Additionally, because the code points used by a language might be spread across separate
Unicode blocks, only the first char of an ngram can be encoded relative to the based index, and for all other chars
the offset difference (positive or negative) to that first char can be encoded.

However, because the Unicode data included in a Java runtime differs between versions, it is not possible to create
the letter index map dynamically when models are loaded. Instead, it has to be created during model creation and be
persisted as file to make sure the same result is obtained for all Java versions. Additionally, when creating the
letter index map certain special letters have to be considered, which are neither uppercase nor lowercase, but have
a lowercase variant (e.g. `U+01C5`), or which are considered uppercase but have no lowercase variant (e.g. `U+03D2`).

This was originally implemented, but dropped in favor of [offset based encoding](#offset-based-encoding), since
that groups the used letters more cleanly per language and simplifies encoding, such as not having to use a base
index and a difference to that base index for subsequent chars. Additionally, it is not dependent on the Unicode data
of the JDK used to construct the language models.

#### Frequency encoding

Ngram frequencies expressed as floating point value are in the range 0.0 < _f_ < 1.0. Therefore, encoding them
as regular `Float` or `Double` is wasteful because for example their sign bit and some bits of their exponent
will never be used. A custom encoding which maps that value range from 0.0 to 1.0 to bytes can therefore represent
the same frequency, but requires fewer bytes. 32-bit, compared to the 64-bit `Double` values used by the original
Lingua implementation, can still yield good accuracy (since version 1.2.0 Lingua also uses [`Float` to store the
frequencies](https://github.com/pemistahl/lingua/commit/7633ea97dc0d39eee589427ce594394890f1502f)). Note that
Lingua's original JSON models store the frequency as fraction and are therefore lossless, but even when reducing
the fraction (which is already done for the JSON models), for large models it might not be possible to store the
integer values of the numerator and the denominator both combined in 32 bits.

It might even be possible to increase accuracy by reducing the range 0.0 to 1.0 further because it is unlikely
that any ngram has a frequency close to 0.0 or 1.0. This is currently not implemented, and it has to be verified
whether that is really possible and what advantages, if any, this gives.

Frequency encoding was originally implemented, but it only had a very small effect on precision at the cost
of adding additional complexity. Therefore, this optimization was dropped and instead of an encoded `Int`,
frequencies are now stored as `Float`, which requires the same amount of memory (32 bits).

## Model file

Optimizations implemented or considered for model files. This refers to the file storing ngram frequencies for a language,
and which is included in the final JAR. These optimizations cover both model size reduction and initial model loading
speed improvements.

### Implemented

#### Binary format

A custom binary format can be used for the model files. Compared to JSON or standard Java serialization this can be more
compact and faster to read and write. Additionally, since the model files are only used by this library and are not
expected to be used externally, the format does not need to use any specific structure and can be changed between
versions.

The data is directly read from a `DataInputStream` / `InputStream`.

#### Primitive array conversion using `ByteBuffer`

When primitive arrays other than byte arrays are read, `ByteBuffer` is used to first wrap the bytes, then a view of
the buffer for the target type is obtained, e.g. with `asShortBuffer()`, and finally the destination array is filled
with a bulk `get` call.

This might yield better performance than using `DataInputStream` or manually performing the conversion.

:construction: The actual performance gain (if any) has to be measured.

### Not implemented

#### Ngrams grouped by frequency

Often multiple ngrams share the same frequencies. The original Lingua JSON model relied on this and stored the
data grouped by frequency. This can reduce the model size significantly compared to storing the frequency for
each ngram separately.

For example, first the frequency could be written and then all ngrams which have that frequency. To account for
frequencies which are unique to one (or only few) ngrams, a custom encoding could be used. The following assumes
frequencies to be encoded with 32 bits, and that 0 is not a valid frequency value:
- If < 3 ngrams have the same frequency: Store each of them as frequency followed by ngram
- Else: Write 32-bit 0 (to differentiate it from a valid frequency), followed by frequency, followed by number of
  ngrams with that frequency, followed by all the ngrams

When using a [custom frequency encoding](#frequency-encoding) which might use 0 to represent the smallest possible
frequency, then to avoid a clash with the 32-bit 0 mentioned above, the frequency can be rounded up slightly to
be > 0. This will most likely have no effect on the accuracy.

This way for _n_ ngrams with the same frequency, in addition to the encoded ngrams themselves the following number of
bytes would be required (assumes frequencies to be encoded with 32 bits = 4 bytes):
- If _n_ < 3: _n_ * 4
- Else: 4 + 4 + 4 = 32-bit 0 + frequency + count (assuming count is 32-bit integer; 16 bit might suffice as well)

This was originally implemented but discarded when a [sorted array map structure](#sorted-array-map-structure) was
used for the in-memory model. It might still be possible to implement, but the implementation would be quite
complex to sort the ngrams again when the model is loaded, and possibly also restore the [shared frequency values structure](#shared-frequency-values).
This might also increase loading time (to be verified; smaller model file size on the other hand might decrease read time).

#### Trie-like model

Ngrams often have common prefixes, this could be utilized to store them in a trie-like model to reduce memory
usage for the common prefixes. However, especially for larger ngrams it might not make sense to fully extract
common prefixes but instead store the remainder bytes as is because there common prefixes can be rather rare.
Unless the encoded ngrams are converted when loaded, the effectiveness of this depends on the
[primitive in-memory encoding](#primitive-ngram-encoding). The more common a prefix of an encoded ngram is, the more
memory can be reduced.

This is not implemented because at the moment the most memory is used by the `Int`-to-`Float` maps, which already
use a [trie-like data structure](#trie-like-data-structure-for-int-to-float-map) for the in-memory model, which
is also written in this way to the model file. For other maps this trie-like model would likely not be worth it.
However, for the model file the memory reduction might be greater than for the in-memory model because the overhead
for nested arrays is smaller. For example to encode the length of a `Byte` array, only a `Byte` is needed (or if length 0
can occur, then to account for 256 possible lengths a `Short` would be necessary), whereas at runtime the
`Byte` array header requires multiple bytes.
