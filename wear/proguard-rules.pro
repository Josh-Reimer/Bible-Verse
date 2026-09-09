# R8 rules for the release build (minifyEnabled + shrinkResources are both on).
#
# No reflection surfaces of our own here (no Room, no JSON-to-object mapping via
# reflection — org.json is used directly). Compose's and Kotlin coroutines' consumer
# rules, shipped inside those libraries, cover the rest.
