package org.virtuslab.bazelsteward.github

import org.kohsuke.github.GHAsset
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GitHub
import org.virtuslab.bazelsteward.core.library.SimpleVersion
import org.virtuslab.bazelsteward.core.library.Version
import org.virtuslab.bazelsteward.core.rules.BazelRuleLibraryId
import org.virtuslab.bazelsteward.core.rules.RulesResolver
import java.io.FileInputStream
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.math.min


class GithubRulesResolver(private val gitHubClient: GitHub) : RulesResolver {
  private val sha256Regex = "\\b[A-Fa-f0-9]{64}\\b".toRegex()

  override fun resolveRuleVersions(ruleId: BazelRuleLibraryId): Map<BazelRuleLibraryId, Version> = // TODO: this should probably suspend
    ruleId.toRepositoryId().listReleases().associateWith { release ->
      val shas = sha256Regex.findAll(release.body).map { it.value }.toList()
      val assets = release.listAssets().toList()
      if (assets.size > 1) {
        return@associateWith assets
          .sortedBy { levenshtein(it.name, ruleId.artifactName) }
          .asSequence()
          .map { it to it.sha256() }
          .firstOrNull { shas.contains(it.second) }
          ?.let { ruleId.copy(url = it.first.browserDownloadUrl, sha256 = it.second) }
      }
      assets.singleOrNull()?.let {
        ruleId.copy(url = it.browserDownloadUrl, sha256 = shas.singleOrNull() ?: it.sha256())
      }
    }.filterValues { it != null }.map { it.value!! to SimpleVersion(it.key.tagName) }.toMap()


//  private fun tryShaFromBodyAndCurrentUrl(ruleId: BazelRuleLibraryId, release: GHRelease): Boolean = ruleId.url


  private fun RepositoryId.listReleases(): Sequence<GHRelease> =
    gitHubClient.getRepository(this.toString()).listReleases().asSequence()

  companion object {
    private fun BazelRuleLibraryId.toRepositoryId() = RepositoryId(repoName, ruleName)

    private fun GHAsset.sha256() = URL(browserDownloadUrl).getFileChecksum(MessageDigest.getInstance("SHA-256"))

    private data class RepositoryId(val user: String, val repository: String) {
      override fun toString(): String = "$user/$repository"
    }

    private data class VersionAndSha(val user: String, val repository: String) {
      override fun toString(): String = "$user/$repository"
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
      if (lhs == rhs) {
        return 0
      }
      if (lhs.isEmpty()) {
        return rhs.length
      }
      if (rhs.isEmpty()) {
        return lhs.length
      }

      val lhsLength = lhs.length + 1
      val rhsLength = rhs.length + 1

      var cost = Array(lhsLength) { it }
      var newCost = Array(lhsLength) { 0 }

      for (i in 1 until rhsLength) {
        newCost[0] = i

        for (j in 1 until lhsLength) {
          val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1

          val costReplace = cost[j - 1] + match
          val costInsert = cost[j] + 1
          val costDelete = newCost[j - 1] + 1

          newCost[j] = min(min(costInsert, costDelete), costReplace)
        }

        val swap = cost
        cost = newCost
        newCost = swap
      }

      return cost[lhsLength - 1]
    }

    private fun URL.getFileChecksum(digest: MessageDigest): String {
      var messageDigest: MessageDigest
      DigestInputStream(this.openStream(), digest).use { dis ->
        while (dis.read() != -1);
        messageDigest = dis.messageDigest
      }

      val result = StringBuilder()
      for (b in messageDigest.digest()) {
        result.append(String.format("%02x", b))
      }
      return result.toString()
    }
  }
}
