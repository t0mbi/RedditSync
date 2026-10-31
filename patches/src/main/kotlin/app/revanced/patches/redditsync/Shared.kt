package app.revanced.patches.redditsync

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

internal const val SYNC_PACKAGE = "com.laurencedawson.reddit_sync"

/** Sync's final release; the patches match its code by string constants but were written against this build. */
internal const val SYNC_VERSION = "v23.06.30-13:39"

internal const val EXTENSION = "extensions/extension.rve"
internal const val EXTENSION_CLASS = "Lapp/revanced/extension/redditsync/SyncFixes;"

/** The method [name] in the class [type]; throws if there isn't exactly one. */
internal fun BytecodePatchContext.methodOf(type: String, name: String): MutableMethod {
    val classDef = classes.firstOrNull { it.type == type } ?: throw PatchException("$type not found")
    return proxy(classDef).mutableClass.methods.singleOrNull { it.name == name }
        ?: throw PatchException("$type->$name not found or ambiguous")
}

/** True if this instruction references exactly [reference] (a method, field, string or type). */
internal fun Instruction.references(reference: String) =
    (this as? ReferenceInstruction)?.reference?.toString() == reference
