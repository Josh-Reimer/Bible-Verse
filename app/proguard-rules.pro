# R8 rules for the release build (minifyEnabled + shrinkResources are both on).
#
# The reflection surfaces this app has are all covered by consumer rules shipped
# inside the libraries themselves, so there is deliberately very little here:
#   - Room resolves bookmark_database_Impl by name  -> room-runtime's proguard.txt
#     keeps every RoomDatabase subclass and its constructor.
#   - FragmentManager re-creates VerseActionsBottomSheet / SearchResultsBottomSheet
#     (and MaterialTimePicker) after process death via the default FragmentFactory
#     -> androidx.fragment keeps the public no-arg constructor of any *public*
#     Fragment subclass. Both of ours are public; making one package-private would
#     silently break restore, so keep them public.
#   - ViewModelProvider instantiates SearchResultsViewModel reflectively
#     -> androidx.lifecycle-viewmodel keeps ViewModel constructors.
#   - Activities/receivers named in the manifest and the framework views named in
#     the layouts are kept by the rules AGP generates from the merged manifest and
#     resources. No app class is named from XML, and nothing here uses
#     Resources.getIdentifier, so resource shrinking has nothing dynamic to miss.
#
# Assets (the Bible text, red_letter_*.json, similar_verses.bin, book_names.json)
# are not touched by either shrinker, so nothing in Tools.getFile needs protecting.

# Keep stack traces readable. R8 still renames the classes; the mapping file it
# writes to app/build/outputs/mapping/release/ is what Play de-obfuscates against,
# and it is bundled into the AAB automatically. -renamesourcefileattribute keeps
# the original file names out of the APK while leaving line numbers intact.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
