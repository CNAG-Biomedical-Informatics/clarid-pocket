# Reference data and acknowledgements

ClarID Pocket implements a subset of the biosample format documented and
implemented by [ClarID-Tools](https://github.com/CNAG-Biomedical-Informatics/clarid-tools),
developed by Manuel Rueda. Its decoding behavior and fixtures are based on release
0.04, commit `3789effd7481cef5928a251095f2b7f792b9fa3f`.

The copied codebook, schema, and reference resources retain the upstream
[Artistic License 2.0](core/src/main/resources/clarid/0.04/LICENSE-ClarID-Tools.txt).
The Kotlin implementation is a separate implementation and does not replace or
modify the original ClarID-Tools distribution. Compatibility is limited to the
forms described in the README.

The ICD-10-CM dictionary and its ordinal map are pinned to the same upstream
commit. The upstream [ICD-10 provenance notice](core/src/main/resources/clarid/0.04/README_ICD10.md)
records the CDC/NCHS 2026 source data and conversion. Keep the label dictionary
and ordinal map together; changing ordinal assignment changes stub meanings.

The app uses AndroidX/Jetpack Compose, CameraX, Google's bundled ML Kit barcode
scanner, Kotlin, SnakeYAML, and Gson. Their upstream licenses/terms apply to
those components. No institutional approval or endorsement is implied.
