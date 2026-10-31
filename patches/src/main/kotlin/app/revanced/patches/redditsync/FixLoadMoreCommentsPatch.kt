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
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction

private const val MORE_URL = "api/morechildren?always_show_media=1&profile_img=true"

private const val APPEND = "Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;"
private const val LISTENER = "Lcom/android/volley/Response\$Listener;"
private const val ERROR_LISTENER = "Lcom/android/volley/Response\$ErrorListener;"

/** OAuthCommentsMoreRequest's constructor, the only user of the /api/morechildren URL. */
private val moreRequestConstructorFingerprint = fingerprint {
    strings(MORE_URL)
}

@Suppress("unused")
val fixLoadMoreCommentsPatch = bytecodePatch(
    name = "Fix load more comments",
    description = "Fixes \"load more comments\" failing since Reddit blocked /api/morechildren for third party " +
        "apps. Each child is loaded from /comments instead (up to 20 per tap).",
) {
    compatibleWith(SYNC_PACKAGE(SYNC_VERSION))
    extendWith(EXTENSION)

    execute {
        val constructor = moreRequestConstructorFingerprint.method
        val type = constructor.definingClass

        // Constructor: GET /comments/{post}/_/{firstChild}.json instead of POST /api/morechildren.
        val urlIndex = constructor.instructions.indexOfFirst { it.references(MORE_URL) }
        if (urlIndex < 0) throw PatchException("morechildren URL not found")

        // const/4 v2, 0x1 (POST) follows the toString() of the URL.
        val methodIndex = (urlIndex until constructor.instructions.size).firstOrNull {
            val instruction = constructor.getInstruction(it)
            instruction.opcode == Opcode.CONST_4 &&
                (instruction as OneRegisterInstruction).registerA == 2 &&
                (instruction as WideLiteralInstruction).wideLiteral == 1L
        } ?: throw PatchException("Request method not found")

        val returnIndex = constructor.instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }

        // Highest index first so the earlier ones stay valid.
        constructor.addInstructions(
            returnIndex,
            """
                move-object/from16 v0, p10
                invoke-static { v6, v7, v8, v0, p5 }, $EXTENSION_CLASS->register(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;)V
            """,
        )
        constructor.replaceInstruction(methodIndex, "const/4 v2, 0x0")
        constructor.replaceInstruction(urlIndex, "const-string v1, \".json?always_show_media=1&profile_img=true\"")
        constructor.addInstructions(
            urlIndex,
            """
                const-string v1, "comments/"
                invoke-virtual { v0, v1 }, $APPEND
                invoke-virtual { v0, p3 }, $APPEND
                const-string v1, "/_/"
                invoke-virtual { v0, v1 }, $APPEND
                invoke-static { p5 }, $EXTENSION_CLASS->first(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v1
                invoke-virtual { v0, v1 }, $APPEND
            """,
        )

        val parse = methodOf(type, "parseNetworkResponse")

        // After notifyChange: queue a request for the remaining children. Highest index first.
        val notifyIndex = parse.instructions.indexOfLast {
            it.references("Landroid/content/ContentResolver;->notifyChange(Landroid/net/Uri;Landroid/database/ContentObserver;)V")
        }
        if (notifyIndex < 0) throw PatchException("notifyChange not found")

        parse.addInstructionsWithLabels(
            notifyIndex + 1,
            """
                invoke-static { v1 }, $EXTENSION_CLASS->rest(Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v0
                invoke-static { v0 }, Lorg/apache/commons/lang3/StringUtils;->isNotEmpty(Ljava/lang/CharSequence;)Z
                move-result v3
                if-eqz v3, :chain_end
                new-instance v4, $type
                iget-object v5, v1, Lx8/a;->c:Landroid/content/Context;
                iget-object v6, v1, $type->t:Ljava/lang/String;
                invoke-static { v1 }, $EXTENSION_CLASS->link(Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v7
                iget-object v8, v1, $type->u:Ljava/lang/String;
                move-object v9, v0
                iget v10, v1, $type->v:I
                invoke-virtual { v15 }, Ljava/util/ArrayList;->size()I
                move-result v3
                add-int/2addr v10, v3
                iget v11, v1, $type->w:I
                invoke-static { v1 }, $EXTENSION_CLASS->sort(Ljava/lang/Object;)Ljava/lang/String;
                move-result-object v12
                iget-object v13, v1, Lx8/a;->q:$LISTENER
                invoke-static { v1 }, $EXTENSION_CLASS->errorListener(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v14
                check-cast v14, $ERROR_LISTENER
                invoke-direct/range { v4 .. v14 }, $type-><init>(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II${"Ljava/lang/String;"}$LISTENER$ERROR_LISTENER)V
                invoke-static { v4 }, Lm8/a;->d(Lx8/a;)V
            """,
            ExternalLabel("chain_end", parse.getInstruction(notifyIndex + 1)),
        )

        // Rows shift by the number of inserted comments, replies included.
        val subIndex = parse.instructions.indexOfFirst { it.opcode == Opcode.SUB_INT_2ADDR }
        if (subIndex < 0) throw PatchException("Position update not found")
        parse.addInstructions(
            subIndex,
            """
                invoke-virtual { v15 }, Ljava/util/ArrayList;->size()I
                move-result v6
            """,
        )

        // The response is [post, comment listing]; read the listing's children instead of json.data.things.
        val jsonIndex = parse.instructions.indexOfFirst { it.references("json") }
        val thingsIndex = parse.instructions.indexOfFirst { it.references("things") }
        if (jsonIndex < 2 || thingsIndex < 0) throw PatchException("Response parsing not found")

        // new-instance v6 JSONObject; invoke-direct { v6, v4 }; const-string v4 "json"; getJSONObject(String)
        parse.replaceInstruction(jsonIndex - 2, "new-instance v6, Lorg/json/JSONArray;")
        parse.replaceInstruction(
            jsonIndex - 1,
            "invoke-direct { v6, v4 }, Lorg/json/JSONArray;-><init>(Ljava/lang/String;)V",
        )
        parse.replaceInstruction(jsonIndex, "const/4 v4, 0x1")
        parse.replaceInstruction(
            jsonIndex + 1,
            "invoke-virtual { v6, v4 }, Lorg/json/JSONArray;->getJSONObject(I)Lorg/json/JSONObject;",
        )
        parse.replaceInstruction(thingsIndex, "const-string v6, \"children\"")
    }
}
