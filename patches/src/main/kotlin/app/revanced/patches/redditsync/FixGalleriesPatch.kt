package app.revanced.patches.redditsync

import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val INFO_URL = "https://oauth.reddit.com/api/info.json?id=t3_"

/** OAuthGalleryRequest's URL builder: static String b(String), the only user of the /api/info URL. */
private val galleryUrlFingerprint = fingerprint {
    strings(INFO_URL)
}

@Suppress("unused")
val fixGalleriesPatch = bytecodePatch(
    name = "Fix Reddit galleries",
    description = "Fixes Reddit galleries failing to open since Reddit blocked /api/info for third party apps. " +
        "The post is loaded from /comments instead.",
) {
    compatibleWith(SYNC_PACKAGE(SYNC_VERSION))

    execute {
        val urlMethod = galleryUrlFingerprint.method
        val type = urlMethod.definingClass

        // const-string v1, URL; append(v1); y7.a.b(id); move-result; append(id)
        val urlIndex = urlMethod.instructions.indexOfFirst { it.references(INFO_URL) }
        if (urlIndex < 0 || urlMethod.getInstruction(urlIndex + 4).opcode != Opcode.INVOKE_VIRTUAL) {
            throw PatchException("Unexpected gallery URL builder")
        }

        urlMethod.addInstructions(
            urlIndex + 5,
            """
                const-string v1, ".json?limit=1"
                invoke-virtual { v0, v1 }, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
            """,
        )
        urlMethod.replaceInstruction(urlIndex, "const-string v1, \"https://oauth.reddit.com/comments/\"")

        // /comments returns [post, comments]; the parser expects the post listing object.
        val parse = methodOf(type, "parseNetworkResponse")
        val stringIndex = parse.instructions.indexOfFirst { it.references("Ljava/lang/String;-><init>([B)V") }
        if (stringIndex < 0) throw PatchException("Gallery response string not found")

        parse.addInstructionsWithLabels(
            stringIndex + 1,
            """
                const-string v5, "["
                invoke-virtual { v4, v5 }, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
                move-result v5
                if-eqz v5, :skip_array
                new-instance v5, Lorg/json/JSONArray;
                invoke-direct { v5, v4 }, Lorg/json/JSONArray;-><init>(Ljava/lang/String;)V
                const/4 v6, 0x0
                invoke-virtual { v5, v6 }, Lorg/json/JSONArray;->getJSONObject(I)Lorg/json/JSONObject;
                move-result-object v5
                invoke-virtual { v5 }, Lorg/json/JSONObject;->toString()Ljava/lang/String;
                move-result-object v4
            """,
            ExternalLabel("skip_array", parse.getInstruction(stringIndex + 1)),
        )
    }
}
