package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Location
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import io.gitlab.arturbosch.detekt.api.internal.Configuration
import io.gitlab.arturbosch.detekt.api.internal.RequiresTypeResolution
import io.gitlab.arturbosch.detekt.api.valuesWithReason
import io.gitlab.arturbosch.detekt.rules.fqNameOrNull
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.resolve.BindingContext

/**
 * This rule allows to set a list of forbidden annotations. This can be used to discourage the use
 * of language annotations which do not require explicit import.
 *
 * <noncompliant>
 * @@SuppressWarnings("unused")
 * class SomeClass()
 * </noncompliant>
 *
 */
@RequiresTypeResolution
class ForbiddenAnnotation(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "Mark forbidden annotations. A forbidden annotation could be an invocation of an unwanted " +
            "language annotation which does not require explicit import and hence you might want to mark it " +
            "as forbidden in order to get warned about the usage.",
        Debt.FIVE_MINS
    )

    @Configuration(
        "List of fully qualified annotation classes which are forbidden. " +
            "For example, `kotlin.jvm.Transient`."
    )
    private val annotations: Map<String, Forbidden> by config(
        valuesWithReason(
            "java.lang.SuppressWarnings" to "it is a java annotation. Use `Suppress` instead.",
        )
    ) { list ->
        list.associate { it.value to Forbidden(it.value, it.reason) }
    }

    override fun visitAnnotationEntry(annotation: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotation)
        if (annotations.isEmpty()) {
            return
        }

        val forbidden = annotation.typeReference?.fqNameOrNull()?.let {
            annotations[it.asString()]
        }

        if (forbidden != null) {
            val message = if (forbidden.reason != null) {
                "The annotation `${forbidden.name}` has been forbidden: ${forbidden.reason}"
            } else {
                "The annotation `${forbidden.name}` has been forbidden in the detekt config."
            }
            val location = Location.from(annotation).let { location ->
                location.copy(
                    text = location.text.copy(
                        end = annotation.children.firstOrNull()?.endOffset ?: location.text.end
                    )
                )
            }
            report(CodeSmell(issue, Entity.from(annotation, location), message))
        }
    }

    private data class Forbidden(val name: String, val reason: String?)

    private fun KtTypeReference.fqNameOrNull(): FqName? {
        return if (bindingContext != BindingContext.EMPTY) {
            bindingContext[BindingContext.TYPE, this]?.fqNameOrNull()
        } else {
            null
        }
    }
}
