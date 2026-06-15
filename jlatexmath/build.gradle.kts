plugins {
  id(libs.plugins.android.library.get().pluginId)
  alias(libs.plugins.kotlin.android)
  id("tgx-module")
}

// Vendored from noties/jlatexmath-android (android branch), as the published artifact is only
// available on the defunct jcenter. Provides ru.noties.jlatexmath.JLatexMathDrawable for rendering
// LaTeX math (used by rich-message PageBlockMathematicalExpression). See jlatexmath/LICENSE.
dependencies {
  implementation(libs.androidx.annotation)
}

android {
  namespace = "ru.noties.jlatexmath.android"
}
