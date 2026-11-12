package com.example.chat

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

const val EXECUTE_BASH = "execute_bash"

enum class ToolIcon { Terminal, Search, Globe, Book, Cpu, Stop, Folder, File }

@Immutable
data class ToolDisplay(
    val label: String,
    val activeLabel: String,
    val icon: ToolIcon,
    val filename: String? = null,
    val fullPath: String? = null,
    val lineRange: String? = null,
    val added: Int? = null,
    val removed: Int? = null,
    val target: String? = null
)

fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.takeIf { s -> s.isNotEmpty() } }
        .orEmpty()

private fun lineCount(text: String?): Int? {
    if (text.isNullOrEmpty()) return null
    return text.count { it == '\n' } + if (text.last() == '\n') 0 else 1
}

private fun JsonObject.firstString(): String? = keys.firstNotNullOfOrNull { str(it) }

private fun humanize(name: String): String =
    name.replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }

private fun baseName(path: String): String = path.trimEnd('/').substringAfterLast('/')

private fun fileDisplay(
    label: String,
    activeLabel: String,
    path: String?,
    lineRange: String? = null,
    added: Int? = null,
    removed: Int? = null
) = ToolDisplay(
    label = label,
    activeLabel = activeLabel,
    icon = ToolIcon.File,
    filename = path?.let(::baseName),
    fullPath = path,
    lineRange = lineRange,
    added = added,
    removed = removed
)

fun toolDisplay(name: String, args: JsonObject): ToolDisplay = when (name) {
    "view_file" -> {
        val start = args.int("StartLine")
        val end = args.int("EndLine")
        val range = when {
            start != null && end != null -> "#L$start-$end"
            start != null -> "#L$start"
            else -> null
        }
        fileDisplay("Read", "Reading", args.str("AbsolutePath"), lineRange = range)
    }
    "write_to_file" -> fileDisplay(
        "Wrote", "Writing", args.str("TargetFile"),
        added = lineCount(args.str("CodeContent"))
    )
    "replace_file_content" -> fileDisplay(
        "Edited", "Editing", args.str("TargetFile"),
        added = lineCount(args.str("ReplacementContent")),
        removed = lineCount(args.str("TargetContent"))
    )
    EXECUTE_BASH -> ToolDisplay("Ran", "Running", ToolIcon.Terminal, target = args.str("command"))
    "stop_command" -> ToolDisplay("Stopped Task", "Stopping Task", ToolIcon.Stop, target = args.str("task_id"))
    "get_command_status" -> ToolDisplay("Task Status", "Checking Task", ToolIcon.Cpu, target = args.str("task_id"))
    "read_skill" -> ToolDisplay("Read Skill", "Reading Skill", ToolIcon.Book, target = args.str("name"))
    "search" -> ToolDisplay(
        "Searched Web", "Searching Web", ToolIcon.Globe,
        target = args.strings("queries").joinToString(" · ").ifEmpty { null }
    )
    "browse" -> {
        val urls = args.strings("urls")
        ToolDisplay(
            "Browsed", "Browsing", ToolIcon.Globe,
            target = urls.firstOrNull()?.let { if (urls.size > 1) "$it +${urls.size - 1}" else it }
        )
    }
    "search_workspace" -> ToolDisplay("Searched Code", "Searching Code", ToolIcon.Search, target = args.str("query"))
    "find_by_name" -> ToolDisplay(
        "Found Files", "Finding Files", ToolIcon.Search,
        target = args.str("Pattern")
            ?: args.strings("Extensions").joinToString(", ") { "*.$it" }.ifEmpty { null }
            ?: args.str("SearchDirectory")
    )
    "list_dir" -> ToolDisplay(
        "Listed", "Listing", ToolIcon.Folder,
        fullPath = args.str("DirectoryPath"),
        target = args.str("DirectoryPath")
    )
    else -> humanize(name).let { label ->
        ToolDisplay(label, label, ToolIcon.Terminal, target = args.firstString()?.take(120))
    }
}
