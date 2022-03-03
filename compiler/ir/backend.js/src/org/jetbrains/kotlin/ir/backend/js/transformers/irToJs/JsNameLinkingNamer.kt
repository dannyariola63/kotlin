/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.transformers.irToJs

import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.export.isExported
import org.jetbrains.kotlin.ir.backend.js.utils.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.utils.DFS
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class JsNameLinkingNamer(private val context: JsIrBackendContext, private val minimizedMemberNames: Boolean) : IrNamerBase() {

    val nameMap = mutableMapOf<IrDeclaration, JsName>()

    private fun IrDeclarationWithName.getName(prefix: String = ""): JsName {
        return nameMap.getOrPut(this) {
            val name = (this as? IrClass)?.let { context.localClassNames[this] } ?: let {
                this.nameIfPropertyAccessor() ?: getJsNameOrKotlinName().asString()
            }
            JsName(sanitizeName(prefix + name), true)
        }
    }

    val importedModules = mutableListOf<JsImportedModule>()
    val imports = mutableMapOf<IrDeclaration, JsExpression>()

    override fun getNameForStaticDeclaration(declaration: IrDeclarationWithName): JsName {

        if (declaration.isEffectivelyExternal()) {
            val jsModule: String? = declaration.getJsModule()
            val maybeParentFile: IrFile? = declaration.parent as? IrFile
            val fileJsModule: String? = maybeParentFile?.getJsModule()
            val jsQualifier: List<JsName>? = maybeParentFile?.getJsQualifier()?.split('.')?.map { JsName(it, false) }

            when {
                jsModule != null -> {
                    val name = JsName(declaration.getJsNameOrKotlinName().asString(), false)
                    importedModules += JsImportedModule(jsModule, name, name.makeRef())
                    return name
                }

                fileJsModule != null -> {
                    if (declaration !in nameMap) {
                        val moduleName = JsName(sanitizeName("\$module\$$fileJsModule"), true)
                        importedModules += JsImportedModule(fileJsModule, moduleName, null)
                        val qualifiedReference =
                            if (jsQualifier == null) moduleName.makeRef() else (listOf(moduleName) + jsQualifier).makeRef()
                        imports[declaration] = jsElementAccess(declaration.getJsNameOrKotlinName().identifier, qualifiedReference)
                        return declaration.getName()
                    }
                }

                else -> {
                    val name = declaration.getJsNameOrKotlinName().identifier

                    if (jsQualifier != null) {
                        imports[declaration] = jsElementAccess(name, jsQualifier.makeRef())
                        return declaration.getName()
                    }

                    return name.toJsName(temporary = false)
                }
            }
        }

        return declaration.getName()
    }

    override fun getNameForMemberFunction(function: IrSimpleFunction): JsName {
        require(function.dispatchReceiverParameter != null)
        val signature = jsFunctionSignature(function, context)
        val result = if (!function.hasStableJsName(context) && minimizedMemberNames) {
            function.parentAsClass.fieldData()[function]!!
        } else signature
        return result.toJsName()
    }

    override fun getNameForMemberField(field: IrField): JsName {
        require(!field.isStatic)
        // TODO this looks funny. Rethink.
        return JsName(field.parentAsClass.fieldData()[field]!!, false)
    }

    private fun IrClass.fieldData(): Map<IrDeclarationWithName, String> {
        return context.fieldDataCache.getOrPut(this) {
            val nameCnt = mutableMapOf<String, Int>()

            val allClasses = DFS.topologicalOrder(listOf(this)) { node ->
                node.superTypes.mapNotNull {
                    it.safeAs<IrSimpleType>()?.classifier.safeAs<IrClassSymbol>()?.owner
                }
            }

            val result = mutableMapOf<IrDeclarationWithName, String>()

            fun processFun(function: IrFunction) {
                val signature = jsFunctionSignature(function, context)
                val safeName = if (minimizedMemberNames && !function.hasStableJsName(context)) {
                    context.minimizedNameGenerator.nameBySignature(signature).also {
                        result[function] = it
                    }
                } else signature
                nameCnt[safeName] = 1 // avoid clashes with member functions
            }

            if (minimizedMemberNames) {
                allClasses.reversed().forEach {
                    it.declarations.forEach { declaration ->
                        when {
                            declaration is IrFunction && declaration.dispatchReceiverParameter != null -> {
                                val property = (declaration as? IrSimpleFunction)?.correspondingPropertySymbol?.owner
                                if (property?.isExported(context) == true || property?.isEffectivelyExternal() == true) {
                                    context.minimizedNameGenerator.reserveName(property.name.asString())
                                }
                                if (declaration.hasStableJsName(context)) {
                                    val signature = jsFunctionSignature(declaration, context)
                                    context.minimizedNameGenerator.reserveName(signature)
                                }
                            }
                            declaration is IrProperty -> {
                                if (declaration.isExported(context)) {
                                    context.minimizedNameGenerator.reserveName(declaration.name.asString())
                                }
                            }
                        }
                    }
                }
            }

            allClasses.reversed().forEach {
                it.declarations.forEach { declaration: IrDeclaration ->
                    when {
                        declaration is IrField -> {
                            val safeName = if (minimizedMemberNames) {
                                context.minimizedNameGenerator.generateNextName()
                            } else declaration.safeName()
                            val suffix = nameCnt.getOrDefault(safeName, 0) + 1
                            nameCnt[safeName] = suffix
                            result[declaration] = safeName + "_$suffix"
                        }
                        declaration is IrFunction && declaration.dispatchReceiverParameter != null -> {
                            processFun(declaration)
                        }
                        declaration is IrProperty -> {
                            declaration.getter?.let { processFun(it) }
                            declaration.setter?.let { processFun(it) }
                        }
                    }
                }
            }

            result
        }
    }
}

private fun IrField.safeName(): String {
    return sanitizeName(name.asString()).let {
        if (it.lastOrNull()!!.isDigit()) it + "_" else it // Avoid name clashes
    }
}

private fun List<JsName>.makeRef(): JsNameRef {
    var result = this[0].makeRef()
    for (i in 1 until this.size) {
        result = JsNameRef(this[i], result)
    }
    return result
}