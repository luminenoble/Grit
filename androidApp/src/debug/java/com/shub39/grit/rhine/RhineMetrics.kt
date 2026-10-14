package com.shub39.grit.rhine

import android.view.FrameMetrics
import com.shub39.grit.core.tasks.*
import com.shub39.grit.shared.ui.rhine.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

/** Bounded, debug-only counters. Inspection never exports or reads business bodies. */
class RhineMetrics : RhineDrawObserver {
    private var requests = 0
    private var emissions = 0
    private var bodyReads = 0
    private var active = 0
    private var peakActive = 0
    private var maxPage = 0
    private var maxLoadedPage = 0
    private var draws = 0
    private var maxBlocks = 0
    private var latest = JSONObject()
    private val ranges = ArrayDeque<JSONObject>()
    private val frames = ArrayDeque<JSONObject>()

    private fun <T> page(
        kind: String,
        parent: Long,
        offset: Int,
        limit: Int,
        source: Flow<List<T>>,
    ): Flow<List<T>> {
        requests++
        maxPage = maxOf(maxPage, limit)
        if (ranges.size == 64) ranges.removeFirst()
        ranges.addLast(
            JSONObject()
                .put("kind", kind)
                .put("parent", parent)
                .put("offset", offset)
                .put("limit", limit)
        )
        return source
            .onStart {
                active++
                peakActive = maxOf(peakActive, active)
            }
            .onEach {
                emissions++
                maxLoadedPage = maxOf(maxLoadedPage, it.size)
            }
            .onCompletion { active-- }
    }

    fun wrap(repo: TaskRepo): TaskRepo =
        object : TaskRepo by repo {
            override fun getTaskSummariesFlow(categoryId: Long, offset: Int, limit: Int) =
                page(
                    "Task",
                    categoryId,
                    offset,
                    limit,
                    repo.getTaskSummariesFlow(categoryId, offset, limit),
                )

            override fun getSectionSummariesFlow(taskId: Long, offset: Int, limit: Int) =
                page(
                    "Section",
                    taskId,
                    offset,
                    limit,
                    repo.getSectionSummariesFlow(taskId, offset, limit),
                )

            override suspend fun getTaskById(id: Long): Task? {
                bodyReads++
                return repo.getTaskById(id)
            }

            override suspend fun getSectionById(id: Long): Section? {
                bodyReads++
                return repo.getSectionById(id)
            }

            override fun getTasksFlow(): Flow<Map<Category, List<Task>>> {
                bodyReads++
                return repo.getTasksFlow()
            }

            override suspend fun getTasks(): List<Task> {
                bodyReads++
                return repo.getTasks()
            }
        }

    override fun drawn(layout: RhineLayout) {
        draws++
        maxBlocks = maxOf(maxBlocks, layout.blocks.size)
        latest =
            JSONObject()
                .put("blocks", layout.blocks.size)
                .put("u", layout.projection.camera.u)
                .put("v", layout.projection.camera.v)
        RhineBlockKind.entries.forEach { kind ->
            latest.put(kind.name, layout.blocks.count { it.slot.kind == kind })
        }
    }

    fun frame(frame: FrameMetrics) {
        if (frames.size == 2048) frames.removeFirst()
        frames.addLast(
            JSONObject()
                .put("totalNs", frame.getMetric(FrameMetrics.TOTAL_DURATION))
                .put("drawNs", frame.getMetric(FrameMetrics.DRAW_DURATION))
                .put("layoutNs", frame.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
                .put("first", frame.getMetric(FrameMetrics.FIRST_DRAW_FRAME))
        )
    }

    fun reset() {
        requests = 0
        emissions = 0
        bodyReads = 0
        draws = 0
        maxBlocks = 0
        maxPage = 0
        maxLoadedPage = 0
        peakActive = active
        ranges.clear()
        frames.clear()
    }

    fun json(): JSONObject =
        JSONObject()
            .put("requests", requests)
            .put("emissions", emissions)
            .put("bodyReads", bodyReads)
            .put("activePages", active)
            .put("peakActivePages", peakActive)
            .put("maxPage", maxPage)
            .put("maxLoadedPage", maxLoadedPage)
            .put("draws", draws)
            .put("maxBlocks", maxBlocks)
            .put("latest", latest)
            .put("rangeCapacity", 64)
            .put("ranges", JSONArray(ranges.toList()))
            .put("frameCapacity", 2048)
            .put("frames", JSONArray(frames.toList()))
            .put("textCacheCapacity", 384)
            .put("retainedFacePaths", 1)
}
