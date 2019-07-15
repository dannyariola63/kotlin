package org.jetbrains.konan.resolve.symbols.swift

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.swift.psi.SwiftDeclarationKind
import com.jetbrains.swift.psi.SwiftExpression
import com.jetbrains.swift.psi.types.SwiftType
import com.jetbrains.swift.symbols.*
import com.jetbrains.swift.symbols.impl.variable.TypeAnnotationInfo
import org.jetbrains.kotlin.backend.konan.objcexport.ObjCProperty

class KtSwiftPropertySymbol : KtSwiftMemberSymbol, SwiftPropertySymbol {
    private lateinit var type: SwiftType

    constructor(
        stub: ObjCProperty,
        project: Project,
        file: VirtualFile,
        containingTypeSymbol: SwiftTypeSymbol
    ) : super(stub, file, project, containingTypeSymbol)

    constructor() : super()

    override fun getDeclarationKind(): SwiftDeclarationKind = SwiftDeclarationKind.propertyDeclaration

    override fun getSwiftType(): SwiftType = this.type

    override fun isReadOnly(): Boolean {
        val modifiers = this.modifiers
        return if (isComputed) {
            !modifiers.hasModifier(SwiftModifierInfo.Modifier.set)
        } else {
            modifiers.hasModifier(SwiftModifierInfo.Modifier.let)
        }

    }

    override fun getModifierAttributes(modifier: SwiftModifierInfo.Modifier): SwiftAttributesInfo {
        return SwiftAttributesInfo.EMPTY //todo [medvedev]???
    }

    override fun getStaticness(): SwiftCanBeStatic.Staticness = SwiftCanBeStatic.Staticness.NOT_STATIC

    override fun getTypeInfo(): SwiftVariableTypeInfo = TypeAnnotationInfo(swiftType)

    override val modifiers: SwiftModifierInfo //todo [medvedev]???
        get() = SwiftModifierInfo.EMPTY

    override val initializer: SwiftExpression?
        get() = null

    fun setType(type: SwiftType) {
        this.type = type
    }
}