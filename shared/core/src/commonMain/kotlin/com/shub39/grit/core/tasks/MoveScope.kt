package com.shub39.grit.core.tasks

/** Metadata only. Compared again inside the write transaction before applying a preview. */
enum class MoveKind {
    Category,
    Task,
    Section,
}

data class MoveScope(
    val kind: MoveKind,
    val parentId: Long?,
    val ancestorId: Long?,
    val ids: List<Long>,
    val generation: String,
) {
    fun validate(current: MoveScope) {
        require(this == current) { "同级顺序或归属已变化，请重新选择" }
    }
}
