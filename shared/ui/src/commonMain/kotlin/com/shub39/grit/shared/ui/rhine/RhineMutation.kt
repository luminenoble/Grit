package com.shub39.grit.shared.ui.rhine

import kotlinx.coroutines.flow.first

/** Resolve committed IDs against fresh summaries/counts, never the pre-save window. */
suspend fun RhineDataSource.createdSnapshot(
    id: Long,
    parent: Boolean,
): Pair<RhineCatalog, RhineSnapshot> {
    val item = if (parent) null else item(id).first { it != null }
    val parentId = if (parent) id else item!!.categoryId
    val catalog =
        catalog().first { catalog ->
            catalog.categories.any { it.id == parentId } &&
                catalog.counts.any {
                    it.categoryId == parentId && (parent || it.count > item!!.orderIndex)
                }
        }
    val v = catalog.categories.indexOfFirst { it.id == parentId }
    val u = if (parent) 0 else item!!.orderIndex + 1
    return catalog to
        RhineSnapshot(RhineCamera(u.toFloat(), v.toFloat()), RhineFocus(parentId, item?.id, u, v))
}

/** Deletion keeps the same ordinal successor, then the predecessor, then the zero block. */
fun deletionCamera(
    catalog: RhineCatalog,
    before: RhineSnapshot,
    parentDeleted: Boolean,
): RhineCamera {
    if (catalog.categories.isEmpty()) return RhineCamera(0f, 0f)
    val v =
        catalog.categories.indexOfFirst { it.id == before.focus.categoryId }.takeIf { it >= 0 }
            ?: before.focus.v.coerceIn(0, catalog.categories.lastIndex)
    val count = catalog.counts.firstOrNull { it.categoryId == catalog.categories[v].id }?.count ?: 0
    val u = if (parentDeleted) 0 else before.focus.u.coerceIn(0, count)
    return catalog.bounds.clamp(RhineCamera(u.toFloat(), v.toFloat()))
}

suspend fun RhineDataSource.deletedSnapshot(
    before: RhineSnapshot,
    parentDeleted: Boolean,
): Pair<RhineCatalog, RhineSnapshot> {
    val catalog =
        catalog().first { c ->
            c.categories.map { it.id }.toSet() == c.counts.map { it.categoryId }.toSet()
        }
    val camera = deletionCamera(catalog, before, parentDeleted)
    val window = SlotWindow(camera.u.toInt(), camera.u.toInt(), camera.v.toInt(), camera.v.toInt())
    val columns = window(catalog, window).first()
    val input = RhineSceneInput(layer, columns, camera.v.toInt(), camera.u, camera.v)
    return catalog to RhineSnapshot(camera, input.focus())
}
