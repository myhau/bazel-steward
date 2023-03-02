package org.virtuslab.bazelsteward.app

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.virtuslab.bazelsteward.bazel.rules.RuleLibrary
import org.virtuslab.bazelsteward.bazel.rules.RuleLibraryId.ReleaseArtifact
import org.virtuslab.bazelsteward.bazel.version.BazelLibrary
import org.virtuslab.bazelsteward.common.CommonProvider
import org.virtuslab.bazelsteward.common.DependencyKindsFixture
import org.virtuslab.bazelsteward.core.FileFinder
import org.virtuslab.bazelsteward.core.common.TextFile
import org.virtuslab.bazelsteward.core.common.UpdateSuggestion
import org.virtuslab.bazelsteward.core.library.SimpleVersion
import org.virtuslab.bazelsteward.maven.MavenCoordinates
import java.nio.file.Path

class UpdateSuggestionsMapperTest {

  @Test
  fun `should return correct paths for MavenDependencyKind`(@TempDir tempDir: Path) {
    val workspace = prepareWorkspace(tempDir)
    val library = MavenCoordinates.of("org.virtuslab", "dep-a", "1.0.0")
    val suggestedVersion = SimpleVersion("1.2.0")
    val updateSuggestion = UpdateSuggestion(library, suggestedVersion)

    val result = testForDependencyKind(updateSuggestion, tempDir)

    result.map { it.path } shouldContainExactlyInAnyOrder listOf(
      workspace.resolve("WORKSPACE")
    )
  }

  @Test
  fun `should return correct paths for BazelVersionDependencyKind`(@TempDir tempDir: Path) {
    val workspace = prepareWorkspace(tempDir)
    val library = BazelLibrary(SimpleVersion("5.3.0"))
    val suggestedVersion = SimpleVersion("5.4.0")
    val updateSuggestion = UpdateSuggestion(library, suggestedVersion)

    val result = testForDependencyKind(updateSuggestion, tempDir)

    result.map { it.path } shouldContainExactlyInAnyOrder listOf(
      workspace.resolve("WORKSPACE"),
      workspace.resolve("app/BUILD"),
      workspace.resolve("core/BUILD")
    )
  }

  @Test
  fun `should return correct paths for BazelRulesDependencyKind`(@TempDir tempDir: Path) {
    val workspace = prepareWorkspace(tempDir)
    val library = RuleLibrary(
      ReleaseArtifact("", "", "", "", ""),
      SimpleVersion("5.3.0")
    )
    val suggestedVersion = SimpleVersion("5.4.0")
    val updateSuggestion = UpdateSuggestion(library, suggestedVersion)

    val result = testForDependencyKind(updateSuggestion, tempDir)

    result.map { it.path } shouldContainExactlyInAnyOrder listOf(
      workspace.resolve("app/BUILD"),
      workspace.resolve("core/BUILD")
    )
  }

  private fun prepareWorkspace(tempDir: Path): Path =
    CommonProvider.prepareLocalWorkspace(javaClass, tempDir, "config")

  private fun testForDependencyKind(
    updateSuggestions: UpdateSuggestion,
    tempDir: Path
  ): List<TextFile> {
    val dependencyKinds = DependencyKindsFixture(tempDir)
    val fileFinder = FileFinder(tempDir)
    val repoConfig = CommonProvider.loadRepoConfigFromResources(this::class.java.classLoader, "example-config.yaml")
    val searchPatternProvider = SearchPatternProvider(repoConfig.searchPaths, dependencyKinds.all)

    val updateSuggestionsMapper = UpdateSuggestionsMapper(
      searchPatternProvider,
      fileFinder
    )
    return updateSuggestionsMapper.map(updateSuggestions)
  }
}
