package com.apollographql.ijplugin.refactoring.migration.v3tov4.item

import com.apollographql.apollo3.gradle.api.Service
import com.apollographql.ijplugin.refactoring.migration.item.MigrationItem
import com.apollographql.ijplugin.refactoring.migration.item.MigrationItemUsageInfo
import com.apollographql.ijplugin.refactoring.migration.item.toMigrationItemUsageInfo
import com.apollographql.ijplugin.util.decapitalizeFirstLetter
import com.apollographql.ijplugin.util.findPsiFilesByName
import com.apollographql.ijplugin.util.getMethodName
import com.apollographql.ijplugin.util.lambdaBlockExpression
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

object EncloseInService : MigrationItem() {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    val usages = mutableListOf<MigrationItemUsageInfo>()
    val buildGradleKtsFiles: List<KtFile> = project.findPsiFilesByName("build.gradle.kts", searchScope).filterIsInstance<KtFile>()
    for (file in buildGradleKtsFiles) {
      file.accept(object : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)
          if (expression.getMethodName() == "apollo") {
            val blockExpression = expression.lambdaBlockExpression() ?: return
            val statements = blockExpression.statements
            // If there's already a service call, we can't automatically refactor
            if (statements.none { it is KtCallExpression && it.getMethodName() == "service" }) {
              usages.add(blockExpression.toMigrationItemUsageInfo())
            }
          }
        }
      })
    }
    return usages
  }

  private val apolloServiceSymbols: Set<String> by lazy {
    Service::class.java.declaredMethods.map { it.name.withoutGetter() }.toMutableSet().apply {
      // Include the fields that existed in v3, otherwise they'll be removed from the service block
      add("generateModelBuilder")
      add("customScalarsMapping")
    }
  }

  private fun String.withoutGetter() = if (startsWith("get")) {
    substring(3).decapitalizeFirstLetter()
  } else {
    this
  }

  private fun KtExpression.isApolloServiceExpression(): Boolean {
    val referenceExpression = when (this) {
      // e.g. srcDir("xxx")
      is KtCallExpression -> calleeExpression

      // e.g. packageName.set("xxx")
      is KtDotQualifiedExpression -> receiverExpression

      else -> return false
    } as? KtNameReferenceExpression ?: return false
    return referenceExpression.getReferencedName() in apolloServiceSymbols
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: MigrationItemUsageInfo) {
    val blockExpression = usage.element as KtBlockExpression
    val nonServiceStatements = blockExpression.statements.filter { !it.isApolloServiceExpression() }
    val nonServiceStatementsText = nonServiceStatements.map { it.text }
    nonServiceStatements.forEach { it.delete() }
    val ktFactory = KtPsiFactory(project)
    val newBlockExpression = ktFactory.createEmptyBody()
    for (statement in nonServiceStatementsText) {
      newBlockExpression.add(ktFactory.createNewLine())
      newBlockExpression.add(ktFactory.createExpression(statement))
    }
    newBlockExpression.add(ktFactory.createNewLine())
    newBlockExpression.add(ktFactory.createExpression("service(\"service\") {\n${blockExpression.text}\n}"))
    newBlockExpression.lBrace?.delete()
    newBlockExpression.rBrace?.delete()
    blockExpression.replace(newBlockExpression)
  }
}
